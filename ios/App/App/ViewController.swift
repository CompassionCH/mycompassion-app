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
import SafariServices

class ViewController: CAPBridgeViewController, WKScriptMessageHandler, QLPreviewControllerDataSource, SFSafariViewControllerDelegate, WKNavigationDelegate {

    // Create native UI elements
    var loaderOverlay: UIVisualEffectView!
    var animationView: LottieAnimationView!
    var previewFileURL: URL?

    // Capacitor installs its own WKNavigationDelegate; we insert ourselves in
    // front of it (see viewDidLoad) to show the maintenance page on load
    // failures, and forward everything else back to Capacitor.
    private weak var capacitorNavDelegate: WKNavigationDelegate?

    // JavaScript injected into the Odoo WebView, one file per concern, kept in
    // the Scripts/ group and loaded at runtime so they can be edited and
    // debugged as real .js files. They are concatenated and injected as a
    // single WKUserScript, so they share one script scope and ORDER MATTERS:
    // native_download defines nativeDownload(), which letter_photo_preview calls.
    private static let injectedScriptNames = [
        "loader",
        "native_download",
        "letter_photo_preview",
        "login_guard",
    ]

    override func viewDidLoad() {
        super.viewDidLoad()

        setupNativeLoader()

        // Listen for clicks / downloads / previews and talk to Swift via the
        // nativeLoader and nativePreview message handlers (see Scripts/*.js).
        let js = ViewController.loadInjectedScripts()
        let script = WKUserScript(source: js, injectionTime: .atDocumentEnd, forMainFrameOnly: true)
        self.bridge?.webView?.configuration.userContentController.addUserScript(script)

        // Insert ourselves as the navigation delegate, keeping Capacitor's as
        // the fallback. WKNavigationDelegate calls we don't implement are
        // forwarded to it via forwardingTarget(for:) below.
        if let webView = self.bridge?.webView {
            capacitorNavDelegate = webView.navigationDelegate
            webView.navigationDelegate = self
        }

        // Register the "nativeLoader" listener so Swift can hear the JS
        self.bridge?.webView?.configuration.userContentController.add(self, name: "nativeLoader")
        // Register the "nativePreview" listener for the QuickLook file preview
        self.bridge?.webView?.configuration.userContentController.add(self, name: "nativePreview")
        // Register the "nativePayment" listener so the page can close the
        // payment sheet once it knows the payment settled.
        self.bridge?.webView?.configuration.userContentController.add(self, name: "nativePayment")

        // WebKit suspends the WebView's timers while the sheet covers it, so a
        // mycompassion:// call from the payment browser is the only signal that
        // can reach us while the donor is still on the gateway page (T3378).
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleAppReturnURL(_:)),
            name: .capacitorOpenURL,
            object: nil
        )
    }

    // ---------------------------------------------------------
    // INJECTED JAVASCRIPT LOADING
    // ---------------------------------------------------------
    // Reads each Scripts/*.js from the app bundle and concatenates them into a
    // single script string. A missing file means it was not added to the App
    // target in Xcode — fail loudly in DEBUG so it is caught on the first run
    // instead of silently disabling the injected behaviour in production.
    private static func loadInjectedScripts() -> String {
        var parts: [String] = []
        for name in injectedScriptNames {
            guard let url = Bundle.main.url(forResource: name, withExtension: "js")
                    ?? Bundle.main.url(forResource: name, withExtension: "js", subdirectory: "Scripts"),
                  let contents = try? String(contentsOf: url, encoding: .utf8)
            else {
                assertionFailure("Missing bundled script Scripts/\(name).js — add it to the App target in Xcode")
                print("⚠️ Missing bundled script \(name).js — injected WebView behaviour will be incomplete")
                continue
            }
            parts.append(contents)
        }
        return parts.joined(separator: "\n")
    }

    // ---------------------------------------------------------
    // PAYMENT FLOW (SFSafariViewController)
    // ---------------------------------------------------------
    // Donations are collected outside the app's WebView (Guideline 3.2.2).
    // Cancelling here rather than calling stopLoading() afterwards keeps the
    // WebView from reporting a failed navigation, which used to race the
    // maintenance page onto the screen behind the sheet (T3378).
    func webView(
        _ webView: WKWebView,
        decidePolicyFor navigationAction: WKNavigationAction,
        decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
    ) {
        if let url = navigationAction.request.url,
           url.host?.contains("postfinance.ch") == true,
           navigationAction.targetFrame?.isMainFrame != false {
            decisionHandler(.cancel)
            presentPaymentSheet(url)
            return
        }
        guard let delegate = capacitorNavDelegate else {
            decisionHandler(.allow)
            return
        }
        if case .none = delegate.webView?(
            webView, decidePolicyFor: navigationAction, decisionHandler: decisionHandler
        ) {
            decisionHandler(.allow)
        }
    }

    private func presentPaymentSheet(_ url: URL) {
        // Avoid stacking sheets on gateway redirects.
        if presentedViewController is SFSafariViewController { return }
        let safari = SFSafariViewController(url: url)
        safari.delegate = self
        present(safari, animated: true)
    }

    /// The confirmation page opens mycompassion:// on success only, so a failed
    /// payment stays on screen to be read.
    @objc func handleAppReturnURL(_ notification: Notification) {
        guard let payload = notification.object as? [String: Any],
              let url = payload["url"] as? URL,
              url.scheme == "mycompassion"
        else { return }
        dismissPaymentSheet()
    }

    /// Only the app can dismiss the sheet it presented (T3378). The poller was
    /// suspended behind it and decides where to go next, so wake it after.
    func dismissPaymentSheet() {
        DispatchQueue.main.async {
            guard self.presentedViewController is SFSafariViewController else { return }
            self.dismiss(animated: true) {
                self.resumePaymentPolling()
            }
        }
    }

    private func resumePaymentPolling() {
        self.bridge?.webView?.evaluateJavaScript(
            "window.my2ResumePaymentPolling && window.my2ResumePaymentPolling()"
        )
    }

    func safariViewControllerDidFinish(_ controller: SFSafariViewController) {
        // Closed by hand, so the scheme never fired.
        resumePaymentPolling()
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
            else if command == "reload" { reloadApp() }
        } else if message.name == "nativePreview", let filePath = message.body as? String {
            presentFilePreview(filePath)
        } else if message.name == "nativePayment", let command = message.body as? String {
            if command == "close" { dismissPaymentSheet() }
        }
    }

    // ---------------------------------------------------------
    // NATIVE FILE PREVIEW (QuickLook)
    // ---------------------------------------------------------
    func presentFilePreview(_ filePath: String) {
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
    // MAINTENANCE / OFFLINE SCREEN
    // ---------------------------------------------------------
    // Forward every WKNavigationDelegate/WKUIDelegate method we don't implement
    // ourselves back to Capacitor's handler, so its bridge, allowNavigation and
    // PostFinance handling keep working untouched.
    override func responds(to aSelector: Selector!) -> Bool {
        if super.responds(to: aSelector) { return true }
        return capacitorNavDelegate?.responds(to: aSelector) ?? false
    }

    override func forwardingTarget(for aSelector: Selector!) -> Any? {
        if let delegate = capacitorNavDelegate, delegate.responds(to: aSelector) {
            return delegate
        }
        return super.forwardingTarget(for: aSelector)
    }

    // A navigation stopped on purpose is not a backend failure.
    private func isCancelled(_ error: Error) -> Bool {
        let nsError = error as NSError
        if nsError.domain == NSURLErrorDomain, nsError.code == NSURLErrorCancelled {
            return true
        }
        return nsError.domain == "WebKitErrorDomain" && [101, 102, 204].contains(nsError.code)
    }

    private func showMaintenance() {
        // Never behind a sheet: the donor cannot see it and the page underneath
        // is still intact.
        guard presentedViewController == nil,
              let webView = self.bridge?.webView,
              let errorURL = self.bridge?.config.errorPathURL else { return }
        DispatchQueue.main.async {
            self.hideLoader()
            webView.load(URLRequest(url: errorURL))
        }
    }

    private func reloadApp() {
        guard let webView = self.bridge?.webView,
              let serverURL = self.bridge?.config.appStartServerURL else { return }
        DispatchQueue.main.async {
            self.showLoader()
            webView.load(URLRequest(url: serverURL))
        }
    }

    // Backend unreachable (connection refused / timeout / DNS) — e.g. Odoo down
    // during a maintenance restart.
    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        handleLoadFailure(error)
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        handleLoadFailure(error)
    }

    private func handleLoadFailure(_ error: Error) {
        if isCancelled(error) { return }
        print("⚡️  MyCompassion: load failed, showing maintenance - \(error as NSError)")
        showMaintenance()
    }

    // Reverse proxy up but Odoo restarting → 5xx (e.g. nginx 502) on the main
    // frame. Capacitor doesn't implement this method, so we own it fully.
    func webView(_ webView: WKWebView, decidePolicyFor navigationResponse: WKNavigationResponse, decisionHandler: @escaping (WKNavigationResponsePolicy) -> Void) {
        if navigationResponse.isForMainFrame,
           let httpResponse = navigationResponse.response as? HTTPURLResponse,
           httpResponse.statusCode >= 500 {
            decisionHandler(.cancel)
            showMaintenance()
            return
        }
        decisionHandler(.allow)
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
