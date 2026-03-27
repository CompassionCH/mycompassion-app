//
//  ViewController.swift
//  App
//
//  Created by Daniel Gergely on 3/27/26.
//
import UIKit
import Capacitor
import WebKit

class ViewController: CAPBridgeViewController {

    override func viewDidLoad() {
        super.viewDidLoad()
        
        // ---------------------------------------------------------
        // LOAD AND INJECT JAVASCRIPT COMPONENTS
        // ---------------------------------------------------------
        // Pull to refresh
        if let fileURL = Bundle.main.url(forResource: "pullToRefresh", withExtension: "js"),
           let ptrScript = try? String(contentsOf: fileURL) {
            
            let script = WKUserScript(source: ptrScript, injectionTime: .atDocumentEnd, forMainFrameOnly: true)
            self.bridge?.webView?.configuration.userContentController.addUserScript(script)
            
        } else {
            print("❌ WARNING: Could not find or load pullToRefresh.js")
        }
        // Loading spinner
        if let fileURL = Bundle.main.url(forResource: "loaderSpinner", withExtension: "js"),
           let loaderScript = try? String(contentsOf: fileURL) {

            let script = WKUserScript(source: loaderScript, injectionTime: .atDocumentEnd, forMainFrameOnly: true)
            self.bridge?.webView?.configuration.userContentController.addUserScript(script)
        } else {
            print("❌ WARNING: Could not find or load loaderSpinner.js")
        }
        
        
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        
        // ---------------------------------------------------------
        // 2. CLEAN UP BACKGROUNDS
        // ---------------------------------------------------------
        self.view.backgroundColor = UIColor.white
        self.bridge?.webView?.isOpaque = true
        self.bridge?.webView?.backgroundColor = UIColor.white
        self.bridge?.webView?.scrollView.backgroundColor = UIColor.white
        
        // ---------------------------------------------------------
        // 3. ENABLE NATIVE SWIPE TO GO BACK
        // ---------------------------------------------------------
        // This continues to work seamlessly on the native layer
        self.bridge?.webView?.allowsBackForwardNavigationGestures = true
    }
}
