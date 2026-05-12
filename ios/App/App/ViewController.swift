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

class ViewController: CAPBridgeViewController, WKScriptMessageHandler {
    
    // Create native UI elements
    var loaderOverlay: UIVisualEffectView!
    var animationView: LottieAnimationView!

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
        """
        
        let script = WKUserScript(source: js, injectionTime: .atDocumentEnd, forMainFrameOnly: true)
        self.bridge?.webView?.configuration.userContentController.addUserScript(script)
        
        // Register the "nativeLoader" listener so Swift can hear the JS
        self.bridge?.webView?.configuration.userContentController.add(self, name: "nativeLoader")
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
        }
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
