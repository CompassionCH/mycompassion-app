import Capacitor
import UIKit

// iOS 26 and later terminate apps that do not adopt the UIScene lifecycle, and
// once adopted the AppDelegate url/activity callbacks are never called again.
class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?

    private var pendingURL: URL?

    func scene(
        _ scene: UIScene,
        willConnectTo session: UISceneSession,
        options connectionOptions: UIScene.ConnectionOptions
    ) {
        connectionOptions.userActivities.forEach(store)
        forward(connectionOptions.urlContexts)
    }

    func scene(_ scene: UIScene, continue userActivity: NSUserActivity) {
        store(userActivity)
        openPending()
    }

    func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
        forward(URLContexts)
    }

    func sceneDidBecomeActive(_ scene: UIScene) {
        // A cold start reaches willConnectTo before the bridge exists.
        openPending()
    }

    /// The mycompassion:// payment hand-off, which Capacitor turns into the
    /// .capacitorOpenURL notification the payment sheet listens for (T3378).
    private func forward(_ urlContexts: Set<UIOpenURLContext>) {
        for context in urlContexts {
            _ = ApplicationDelegateProxy.shared.application(
                UIApplication.shared, open: context.url, options: [:]
            )
        }
    }

    private func store(_ userActivity: NSUserActivity) {
        guard userActivity.activityType == NSUserActivityTypeBrowsingWeb,
              let url = userActivity.webpageURL
        else { return }
        pendingURL = url
    }

    /// Unread, the tapped App Link is lost and the webview stays on server.url (T3481).
    private func openPending() {
        guard let url = pendingURL,
              let bridge = (window?.rootViewController as? CAPBridgeViewController)?.bridge,
              let webView = bridge.webView
        else { return }
        pendingURL = nil
        guard url.host == bridge.config.serverURL.host else { return }
        webView.load(URLRequest(url: url))
    }
}
