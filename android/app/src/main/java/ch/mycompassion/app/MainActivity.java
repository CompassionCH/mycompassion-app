package ch.mycompassion.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.widget.RelativeLayout;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import com.getcapacitor.BridgeWebViewClient;
import android.view.HapticFeedbackConstants;
import androidx.activity.OnBackPressedCallback;

import com.airbnb.lottie.LottieAnimationView;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private RelativeLayout loaderOverlay;
    private LottieAnimationView animationView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupNativeLoader();

        WebView webView = this.bridge.getWebView();

        // Register the JS Interface
        webView.addJavascriptInterface(new NativeLoaderInterface(), "nativeLoader");

        // Inject JS
        webView.setWebViewClient(new BridgeWebViewClient(this.bridge) {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (isPdfDownloadUrl(url)) {
                    showLoader();
                    downloadPdfWithJs(view, url);
                    return true;
                }
                return super.shouldOverrideUrlLoading(view, request);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectJavascriptObserver(view);
                hideLoader();
            }
        });

        // Intercept the Android edge-swipe / physical back button
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                WebView webView = bridge.getWebView();

                webView.evaluateJavascript("javascript:(function() { " +

                        // Close Bootstrap modals if open
                        "var modal = document.querySelector('.modal.show');" +
                        "if (modal) {" +
                        "    var closeBtn = modal.querySelector('.btn-close, [data-bs-dismiss=\"modal\"]');" +
                        "    if (closeBtn) { closeBtn.click(); return; }" +
                        "}" +

                        // Remember the URL, then tell the browser to go back
                        "var beforeHref = window.location.href;" +
                        "window.history.back();" +

                        // Wait safely inside the browser's clock
                        "setTimeout(function() {" +
                        // Send the 'exit' command over the bridge to Java if URL didn't change
                        "    if (window.location.href === beforeHref) {" +
                        "        if (window.nativeLoader) window.nativeLoader.postMessage('exit');" +
                        "    }" +

                        "}, 150);" +
                        "})();", null);
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
        loaderOverlay.setVisibility(View.GONE);
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
            } else if ("exit".equals(command)) {
                // If Javascript tells us to exit, trigger the soft-close natively!
                new Handler(Looper.getMainLooper()).post(() -> moveTaskToBack(true));
            } else if (command.startsWith("open:")) {
                String url = command.substring(5);
                new Handler(Looper.getMainLooper()).post(() -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                });
            }
        }
    }

    private void showLoader() {
        // Must run on main UI thread
        new Handler(Looper.getMainLooper()).post(() -> {
            loaderOverlay.setVisibility(View.VISIBLE);
            animationView.playAnimation();

            loaderOverlay.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);

            // Failsafe: auto-hide after 10 seconds
            new Handler().postDelayed(this::hideLoader, 10000);
        });
    }

    private void hideLoader() {
        new Handler(Looper.getMainLooper()).post(() -> {
            // only animate if currently visible
            if (loaderOverlay.getVisibility() == View.VISIBLE) {
                // add a tiny 150ms delay and then fade out over 300ms
                loaderOverlay.animate()
                        .setStartDelay(500)
                        .alpha(0f)
                        .setDuration(300)
                        .withEndAction(() -> {
                            loaderOverlay.setVisibility(View.GONE);
                            loaderOverlay.setAlpha(1f);
                            animationView.cancelAnimation();
                        })
                        .start();
            }
        });
    }

    // ---------------------------------------------------------
    // PDF DOWNLOAD HANDLING
    // ---------------------------------------------------------
    private boolean isPdfDownloadUrl(String url) {
        try {
            String path = Uri.parse(url).getPath();
            return path != null && (path.contains("/my/download/") || path.contains("/report/pdf/"));
        } catch (Exception e) {
            return false;
        }
    }

    private void downloadPdfWithJs(WebView view, String url) {
        String path = Uri.parse(url).getPath();
        String lastSegment = (path != null && path.contains("/"))
                ? path.substring(path.lastIndexOf('/') + 1)
                : "document";
        String filename = (lastSegment.isEmpty() ? "document" : lastSegment) + ".pdf";

        String escapedUrl = url.replace("\\", "\\\\").replace("'", "\\'");
        String escapedFilename = filename.replace("\\", "\\\\").replace("'", "\\'");

        String js = "(function() {" +
            "var FS = window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.Filesystem;" +
            "var FO = window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.FileOpener;" +
            "if (!FS || !FO) {" +
            "    if (window.nativeLoader) window.nativeLoader.postMessage('hide');" +
            "    return;" +
            "}" +
            "fetch('" + escapedUrl + "')" +
            "    .then(function(r) { if (!r.ok) throw new Error('HTTP ' + r.status); return r.blob(); })" +
            "    .then(function(b) {" +
            "        return new Promise(function(res, rej) {" +
            "            var rd = new FileReader();" +
            "            rd.onloadend = function() { res(rd.result.split(',')[1]); };" +
            "            rd.onerror = rej;" +
            "            rd.readAsDataURL(b);" +
            "        });" +
            "    })" +
            "    .then(function(d) { return FS.writeFile({ path: '" + escapedFilename + "', data: d, directory: 'CACHE' }); })" +
            "    .then(function(f) { return FO.open({ filePath: f.uri, contentType: 'application/pdf' }); })" +
            "    .catch(function(e) { console.error('PDF download error:', e); alert('Could not open document. Please try again.'); })" +
            "    .finally(function() { if (window.nativeLoader) window.nativeLoader.postMessage('hide'); });" +
            "})();";

        view.evaluateJavascript(js, null);
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
                "    let tgt = target.getAttribute('target');" +
                "    if (target.hasAttribute('data-toggle') || !href || href.startsWith('#') || href.startsWith('javascript:')) return;" +
                "    if (tgt === '_blank' || tgt === '_system') return;" +
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

                "function lockAppOrientation() {" +
                "    try {" +
                "        if (window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.ScreenOrientation) {" +
                "            window.Capacitor.Plugins.ScreenOrientation.lock({ orientation: 'portrait' });" +
                "        } else if (window.Capacitor) {" +
                "            window.Capacitor.triggerPluginCall('ScreenOrientation', 'lock', { orientation: 'portrait' });" +
                "        } else if (window.screen && window.screen.orientation && window.screen.orientation.lock) {" +
                "            window.screen.orientation.lock('portrait').catch(function(e){});" +
                "        }" +
                "    } catch(e) {}" +
                "}" +

                "const observer = new MutationObserver((mutations, obs) => {" +
                "   if (document.getElementById('wrapwrap')) {" +
                "       hideCapacitorSplash();" +
                "       hideNativeLoader();" +
                "   } else {" +
                "       observer.observe(document.documentElement, { childList: true, subtree: true });" + // <-- CHANGED HERE
                "   }" +
                "});" +

                "if (document.getElementById('wrapwrap')) {" +
                "    hideCapacitorSplash();" +
                "    hideNativeLoader();" +
                "} else {" +
                "    observer.observe(document.body, { childList: true, subtree: true });" +
                "}" +

                "if (!window._systemOpenOverridden) {" +
                "    window._systemOpenOverridden = true;" +
                "    var _origOpen = window.open;" +
                "    window.open = function(url, target, features) {" +
                "        var isExternal = typeof url === 'string' && (url.startsWith('http://') || url.startsWith('https://'));" +
                "        var isExternalTarget = target === '_system' || target === '_blank';" +
                "        if (isExternal && isExternalTarget) {" +
                "            if (window.nativeLoader) { window.nativeLoader.postMessage('open:' + url); return null; }" +
                "        }" +
                "        return _origOpen.apply(this, arguments);" +
                "    };" +
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