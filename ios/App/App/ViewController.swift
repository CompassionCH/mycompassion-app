//
//  ViewController.swift
//  App
//
//  Created by Daniel Gergely on 3/27/26.
//
import UIKit
import Capacitor
import WebKit
import Lottie
import QuickLook

class ViewController: CAPBridgeViewController, WKScriptMessageHandler, QLPreviewControllerDataSource {

    // Create native UI elements
    var loaderOverlay: UIVisualEffectView!
    var animationView: LottieAnimationView!
    var previewFileURL: URL?

    override func viewDidLoad() {
        super.viewDidLoad()
        
        setupNativeLoader()
        
        // Listen for clicks and send a "show" or "hide" message to Swift.
        let js = """
        // Show Native Lottie Loader on click
        document.addEventListener('click', function(e) {
            let target = e.target.closest('a, button, input[type="submit"], .stretched-link');
            if (!target) return;
            
            let href = target.getAttribute("href");
            if (target.hasAttribute("data-toggle") || !href || href.startsWith("#") || href.startsWith("javascript:")) return;
            
            window.webkit.messageHandlers.nativeLoader.postMessage("show");
        });
        
        // Hide Native Lottie Loader
        function hideNativeLoader() {
            if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.nativeLoader) {
                window.webkit.messageHandlers.nativeLoader.postMessage("hide");
            }
        }

        // Hide Capacitor Static Splash Screen
        function hideCapacitorSplash() {
            if (window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.SplashScreen) {
                window.Capacitor.Plugins.SplashScreen.hide();
            } else if (window.Capacitor) {
                window.Capacitor.triggerPluginCall("SplashScreen", "hide");
            }
        }

        // Wait for Odoo's #wrapwrap, then hide Capacitor
        const observer = new MutationObserver((mutations, obs) => {
            if (document.getElementById("wrapwrap")) {
                hideCapacitorSplash();
                hideNativeLoader(); // Just in case
                obs.disconnect();
            }
        });

        if (document.getElementById("wrapwrap")) {
            hideCapacitorSplash();
            hideNativeLoader();
        } else {
            // Watch the DOM until Odoo actually loads
            observer.observe(document.body, { childList: true, subtree: true });
        }

        // Hide Lottie Loader on AJAX stop or Page Show
        window.addEventListener('pageshow', hideNativeLoader);

        let jqCheck = setInterval(() => {
            if (typeof window.jQuery !== 'undefined') {
                clearInterval(jqCheck);
                window.jQuery(document).ajaxStop(function () {
                    hideNativeLoader();
                });
            }
        }, 100);

        // Download /my/download/ PDFs natively and preview them with QuickLook.
        // These pages use a <form> (tax receipt) and onclick-divs (payment slips)
        // instead of <a> links, so the WKWebView would otherwise render the PDF
        // inline without Done/Share buttons.
        async function nativeDownload(url) {
            window.webkit.messageHandlers.nativeLoader.postMessage("show");
            try {
                const response = await fetch(url);
                if (!response.ok) throw new Error("Network response was not ok");
                const blob = await response.blob();
                const reader = new FileReader();
                reader.onloadend = async () => {
                    try {
                        const base64data = reader.result.split(",")[1];
                        const raw = url.split("/").pop().split("?")[0];
                        const filename = raw.endsWith(".pdf") ? raw : raw + ".pdf";
                        const saved = await window.Capacitor.Plugins.Filesystem.writeFile({
                            path: filename,
                            data: base64data,
                            directory: "CACHE",
                        });
                        window.webkit.messageHandlers.nativePdf.postMessage(saved.uri);
                    } catch (error) {
                        window.webkit.messageHandlers.nativeLoader.postMessage("hide");
                        alert("Could not load the document. Please try again.");
                    }
                };
                reader.readAsDataURL(blob);
            } catch (error) {
                window.webkit.messageHandlers.nativeLoader.postMessage("hide");
                alert("Could not load the document. Please try again.");
            }
        }

        // Payment slip / QR invoice buttons: <div onclick="location.href='/my/download/...'">
        document.addEventListener('click', function(e) {
            const btn = e.target.closest('[onclick]');
            if (!btn) return;
            const oc = btn.getAttribute('onclick') || '';
            if (!oc.includes('/my/download/')) return;
            const start = oc.indexOf("'") + 1;
            const end = oc.lastIndexOf("'");
            if (start <= 0 || end <= start) return;
            e.preventDefault();
            e.stopPropagation();
            nativeDownload(oc.substring(start, end));
        }, true);

        // Tax receipt: plain GET form posting to /my/download/tax_receipt
        document.addEventListener('submit', function(e) {
            const action = e.target.getAttribute('action') || '';
            if (!action.includes('/my/download/')) return;
            e.preventDefault();
            e.stopPropagation();
            const params = new URLSearchParams(new FormData(e.target)).toString();
            nativeDownload(action + (params ? '?' + params : ''));
        }, true);
        """
        
        let script = WKUserScript(source: js, injectionTime: .atDocumentEnd, forMainFrameOnly: true)
        self.bridge?.webView?.configuration.userContentController.addUserScript(script)
        
        // Register the "nativeLoader" listener so Swift can hear the JS
        self.bridge?.webView?.configuration.userContentController.add(self, name: "nativeLoader")
        // Register the "nativePdf" listener for the QuickLook PDF preview
        self.bridge?.webView?.configuration.userContentController.add(self, name: "nativePdf")
    }

    // ---------------------------------------------------------
    // THE NATIVE LOTTIE LOADER
    // ---------------------------------------------------------
    func setupNativeLoader() {
        // Native Frosted Glass
        let blurEffect = UIBlurEffect(style: .systemUltraThinMaterialLight)
        loaderOverlay = UIVisualEffectView(effect: blurEffect)
        loaderOverlay.frame = self.view.bounds
        loaderOverlay.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        loaderOverlay.isHidden = false
        self.view.addSubview(loaderOverlay)
        
        // Native Lottie View
        animationView = LottieAnimationView()
        animationView.contentMode = .scaleAspectFit
        animationView.loopMode = .loop
        
        loaderOverlay.contentView.addSubview(animationView)
        
        animationView.translatesAutoresizingMaskIntoConstraints = false
        loaderOverlay.contentView.addSubview(animationView)
        
        NSLayoutConstraint.activate([
            animationView.widthAnchor.constraint(equalToConstant: 160),
            animationView.heightAnchor.constraint(equalToConstant: 160),
            animationView.centerXAnchor.constraint(equalTo: loaderOverlay.contentView.centerXAnchor),
            animationView.centerYAnchor.constraint(equalTo: loaderOverlay.contentView.centerYAnchor)
        ])
        
        DotLottieFile.named("loading") { result in
            switch result {
            case .success(let dotLottie):
                self.animationView.loadAnimation(from: dotLottie)
                // If the overlay is currently visible, start playing!
                if !self.loaderOverlay.isHidden {
                    self.animationView.play()
                }
            case .failure(let error):
                print("Failed to load .lottie file: \(error)")
            }
        }
    }

    // ---------------------------------------------------------
    // BRIDGE COMMUNICATION & EXECUTION
    // ---------------------------------------------------------
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        if message.name == "nativeLoader", let command = message.body as? String {
            if command == "show" { showLoader() }
            else if command == "hide" { hideLoader() }
        } else if message.name == "nativePdf", let filePath = message.body as? String {
            presentPdfPreview(filePath)
        }
    }

    // ---------------------------------------------------------
    // NATIVE PDF PREVIEW (QuickLook)
    // ---------------------------------------------------------
    func presentPdfPreview(_ filePath: String) {
        DispatchQueue.main.async {
            self.hideLoader()
            guard let url = URL(string: filePath) else { return }
            self.previewFileURL = url
            let preview = QLPreviewController()
            preview.dataSource = self
            self.present(preview, animated: true)
        }
    }

    func numberOfPreviewItems(in controller: QLPreviewController) -> Int {
        return previewFileURL == nil ? 0 : 1
    }

    func previewController(_ controller: QLPreviewController, previewItemAt index: Int) -> QLPreviewItem {
        return previewFileURL! as NSURL
    }

    func showLoader() {
        DispatchQueue.main.async {
            self.loaderOverlay.isHidden = false
            self.animationView.play()
            
            // Native Swift Haptics
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            
            // Failsafe: auto-hide after 10 seconds
            DispatchQueue.main.asyncAfter(deadline: .now() + 10.0) {
                self.hideLoader()
            }
        }
    }

    func hideLoader() {
        DispatchQueue.main.async {
            self.loaderOverlay.isHidden = true
            self.animationView.stop()
        }
    }

    // ---------------------------------------------------------
    // UI CLEANUP
    // ---------------------------------------------------------
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        self.view.backgroundColor = UIColor.white
        self.bridge?.webView?.isOpaque = true
        self.bridge?.webView?.backgroundColor = UIColor.white
        self.bridge?.webView?.scrollView.backgroundColor = UIColor.white
        self.bridge?.webView?.allowsBackForwardNavigationGestures = true
    }
}
