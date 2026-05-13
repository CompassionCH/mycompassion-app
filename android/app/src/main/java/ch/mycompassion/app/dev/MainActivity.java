package ch.mycompassion.app.dev;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.RelativeLayout;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import com.getcapacitor.BridgeWebViewClient;

import com.airbnb.lottie.LottieAnimationView;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private RelativeLayout loaderOverlay;
    private LottieAnimationView animationView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupNativeLoader();
    }

    @Override
    public void onStart() {
        super.onStart();
        WebView webView = this.bridge.getWebView();

        // Register the JS Interface
        webView.addJavascriptInterface(new NativeLoaderInterface(), "nativeLoader");

        // Inject JS
        webView.setWebViewClient(new BridgeWebViewClient(this.bridge) {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectJavascriptObserver(view);

                hideLoader();
            }
        });
    }

    // ---------------------------------------------------------
    // THE NATIVE LOTTIE LOADER
    // ---------------------------------------------------------
    private void setupNativeLoader() {
        // Native Overlay
        loaderOverlay = new RelativeLayout(this);
        loaderOverlay.setBackgroundColor(Color.parseColor("#B3FFFFFF"));
        loaderOverlay.setLayoutParams(new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        loaderOverlay.setVisibility(android.view.View.GONE);
        loaderOverlay.setClickable(true);

        // Native Lottie View
        animationView = new LottieAnimationView(this);
        animationView.setAnimation("loading.lottie");
        animationView.loop(true);

        RelativeLayout.LayoutParams lottieParams = new RelativeLayout.LayoutParams(
                450, 450
        );
        lottieParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
        loaderOverlay.addView(animationView, lottieParams);

        addContentView(loaderOverlay, loaderOverlay.getLayoutParams());
    }

    // ---------------------------------------------------------
    // BRIDGE COMMUNICATION
    // ---------------------------------------------------------
    public class NativeLoaderInterface {
        @JavascriptInterface
        public void postMessage(String command) {
            if ("show".equals(command)) {
                showLoader();
            } else if ("hide".equals(command)) {
                hideLoader();
            }
        }
    }

    private void showLoader() {
        // Must run on main UI thread
        new Handler(Looper.getMainLooper()).post(() -> {
            loaderOverlay.setVisibility(android.view.View.VISIBLE);
            animationView.playAnimation();

            // Failsafe: auto-hide after 10 seconds
            new Handler().postDelayed(this::hideLoader, 10000);
        });
    }

    private void hideLoader() {
        new Handler(Looper.getMainLooper()).post(() -> {
            // only animate if currently visible
            if (loaderOverlay.getVisibility() == android.view.View.VISIBLE) {
                // add a tiny 150ms delay and then fade out over 300ms
                loaderOverlay.animate()
                        .setStartDelay(500)
                        .alpha(0f)
                        .setDuration(300)
                        .withEndAction(() -> {
                            loaderOverlay.setVisibility(android.view.View.GONE);
                            loaderOverlay.setAlpha(1f);
                            animationView.cancelAnimation();
                        })
                        .start();
            }
        });
    }

    // ---------------------------------------------------------
    // JS INJECTION
    // ---------------------------------------------------------
    private void injectJavascriptObserver(WebView view) {
        String js = "javascript:(function() {" +
                "document.addEventListener('click', function(e) {" +
                "    let target = e.target.closest('a, button, input[type=\"submit\"], .stretched-link');" +
                "    if (!target) return;" +
                "    let href = target.getAttribute('href');" +
                "    if (target.hasAttribute('data-toggle') || !href || href.startsWith('#') || href.startsWith('javascript:')) return;" +
                "    if (window.nativeLoader) window.nativeLoader.postMessage('show');" +
                "});" +

                "function hideNativeLoader() {" +
                "    if (window.nativeLoader) window.nativeLoader.postMessage('hide');" +
                "}" +

                "function hideCapacitorSplash() {" +
                "    if (window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.SplashScreen) {" +
                "        window.Capacitor.Plugins.SplashScreen.hide();" +
                "    } else if (window.Capacitor) {" +
                "        window.Capacitor.triggerPluginCall('SplashScreen', 'hide');" +
                "    }" +
                "}" +

                "const observer = new MutationObserver((mutations, obs) => {" +
                "    if (document.getElementById('wrapwrap')) {" +
                "        hideCapacitorSplash();" +
                "        hideNativeLoader();" +
                "        obs.disconnect();" +
                "    }" +
                "});" +

                "if (document.getElementById('wrapwrap')) {" +
                "    hideCapacitorSplash();" +
                "    hideNativeLoader();" +
                "} else {" +
                "    observer.observe(document.body, { childList: true, subtree: true });" +
                "}" +

                "window.addEventListener('pageshow', hideNativeLoader);" +

                "let jqCheck = setInterval(() => {" +
                "    if (typeof window.jQuery !== 'undefined') {" +
                "        clearInterval(jqCheck);" +
                "        window.jQuery(document).ajaxStop(function () {" +
                "            hideNativeLoader();" +
                "        });" +
                "    }" +
                "}, 100);" +
                "})();";

        view.evaluateJavascript(js, null);
    }
}