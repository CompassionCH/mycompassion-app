package ch.mycompassion.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import com.getcapacitor.PluginHandle;
import com.capacitorjs.plugins.splashscreen.SplashScreen;
import com.capacitorjs.plugins.splashscreen.SplashScreenSettings;
import java.lang.reflect.Field;
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
    // True while a PDF is being generated/downloaded, so the loader stays up
    // for the whole (slow) download instead of being hidden by unrelated
    // ajaxStop/pageshow events or the short generic failsafe.
    private boolean pdfLoading = false;

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
                if (isPdfDownloadUrl(url) || isImageDownloadUrl(url)) {
                    pdfLoading = true;
                    showLoader();
                    downloadFileWithJs(view, url);
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

            // Backend unreachable on a COLD start (no connection at launch):
            // Capacitor (super) loads the local maintenance page, but that page
            // is served from the local origin where window.Capacitor is absent
            // (server.url is remote), so it can't hide the launch splash itself.
            // Hide it natively here, or the app is stuck on the splash. (Mid-
            // session failures already work: the splash was hidden long ago.)
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    forceHideCapacitorSplash();
                    hideLoader();
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                super.onReceivedHttpError(view, request, errorResponse);
                if (request.isForMainFrame() && errorResponse.getStatusCode() >= 500) {
                    forceHideCapacitorSplash();
                    hideLoader();
                }
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

    @Override
    public void onPause() {
        super.onPause();
        // Persist cookies to disk so the session survives a force-close right
        // after login (Android WebView otherwise only flushes periodically).
        CookieManager.getInstance().flush();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Clear the loader if it was left showing after opening an external
        // link in the browser (the in-app page never navigated, so nothing
        // else would hide it).
        hideLoader();
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
                // Ignore generic hides (ajaxStop / pageshow) while a PDF is loading.
                if (!pdfLoading) hideLoader();
            } else if ("pdfDone".equals(command)) {
                pdfLoading = false;
                hideLoader();
            } else if ("reload".equals(command)) {
                // Sent from the maintenance error page: re-open the remote app URL.
                new Handler(Looper.getMainLooper()).post(() -> {
                    showLoader();
                    bridge.getWebView().loadUrl(bridge.getServerUrl());
                });
            } else if ("exit".equals(command)) {
                // If Javascript tells us to exit, trigger the soft-close natively!
                new Handler(Looper.getMainLooper()).post(() -> moveTaskToBack(true));
            } else if (command.startsWith("open:")) {
                String url = command.substring(5);
                new Handler(Looper.getMainLooper()).post(() -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                });
            } else if (command.startsWith("pdf:")) {
                // PDFs opened via window.open (e.g. letters) can't open a new
                // window in the Android WebView, so download and open natively.
                String url = command.substring(4);
                new Handler(Looper.getMainLooper()).post(() -> {
                    pdfLoading = true;
                    showLoader();
                    downloadFileWithJs(bridge.getWebView(), url);
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

            // Failsafe: auto-hide after 10 seconds (skipped during a PDF
            // download, which stays up until the PDF actually opens).
            new Handler().postDelayed(() -> {
                if (!pdfLoading) hideLoader();
            }, 10000);

            // Backstop: if a PDF hasn't loaded within 30s, stop waiting.
            new Handler().postDelayed(() -> {
                if (pdfLoading) {
                    pdfLoading = false;
                    hideLoader();
                }
            }, 30000);
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

    // Hide the Capacitor launch splash from native code. Used on the error path
    // (see onReceivedError): the maintenance page can't hide it via JS because
    // window.Capacitor isn't present on the local error origin. The plugin has
    // no native hide API, so we reach its SplashScreen via reflection — guarded
    // so a Capacitor upgrade can only turn this into a no-op, never a crash.
    // usingDialog is false in this app, so hide(settings) is the correct call.
    private void forceHideCapacitorSplash() {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                PluginHandle handle = this.bridge.getPlugin("SplashScreen");
                if (handle == null || handle.getInstance() == null) return;
                Field field = handle.getInstance().getClass().getDeclaredField("splashScreen");
                field.setAccessible(true);
                SplashScreen splash = (SplashScreen) field.get(handle.getInstance());
                if (splash != null) {
                    splash.hide(new SplashScreenSettings());
                }
            } catch (Throwable t) {
                // Plugin internals changed or unavailable — safe no-op.
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

    private boolean isImageDownloadUrl(String url) {
        // Timeline child photos are <a href="/web/image/compassion.child.pictures/<id>/fullshot/">
        // links (a full navigation, not window.open), which the WebView would
        // otherwise render as a raw, chrome-less image. Open them natively.
        try {
            String path = Uri.parse(url).getPath();
            return path != null && path.contains("/web/image/compassion.child.pictures/");
        } catch (Exception e) {
            return false;
        }
    }

    private void downloadFileWithJs(WebView view, String url) {
        // Derive a base filename (no extension) from the URL path; the actual
        // extension + content type are chosen at runtime from the response's
        // MIME type, so this handles both PDFs (letters, reports, downloads)
        // and images (timeline child photos).
        String path = Uri.parse(url).getPath();
        String base = "document";
        if (path != null) {
            String p = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
            int slash = p.lastIndexOf('/');
            String seg = slash >= 0 ? p.substring(slash + 1) : p;
            if (!seg.isEmpty()) {
                int dot = seg.lastIndexOf('.');
                base = dot > 0 ? seg.substring(0, dot) : seg;
            }
        }

        String escapedUrl = url.replace("\\", "\\\\").replace("'", "\\'");
        String escapedBase = base.replace("\\", "\\\\").replace("'", "\\'");

        String js = "(function() {" +
            "var FS = window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.Filesystem;" +
            "var FO = window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.FileOpener;" +
            "if (!FS || !FO) {" +
            "    if (window.nativeLoader) window.nativeLoader.postMessage('pdfDone');" +
            "    return;" +
            "}" +
            "fetch('" + escapedUrl + "')" +
            "    .then(function(r) { if (!r.ok) throw new Error('HTTP ' + r.status); return r.blob(); })" +
            "    .then(function(b) {" +
            "        var ext, ct;" +
            "        if (b.type === 'image/jpeg') { ext = 'jpg'; ct = 'image/jpeg'; }" +
            "        else if (b.type === 'image/png') { ext = 'png'; ct = 'image/png'; }" +
            "        else { ext = 'pdf'; ct = 'application/pdf'; }" +
            "        var fname = '" + escapedBase + "' + '.' + ext;" +
            "        return new Promise(function(res, rej) {" +
            "            var rd = new FileReader();" +
            "            rd.onloadend = function() { res(rd.result.split(',')[1]); };" +
            "            rd.onerror = rej;" +
            "            rd.readAsDataURL(b);" +
            "        })" +
            "        .then(function(d) { return FS.writeFile({ path: fname, data: d, directory: 'CACHE' }); })" +
            "        .then(function(f) { return FO.open({ filePath: f.uri, contentType: ct }); });" +
            "    })" +
            "    .catch(function(e) { console.error('File download error:', e); alert('Could not open document. Please try again.'); })" +
            "    .finally(function() { if (window.nativeLoader) window.nativeLoader.postMessage('pdfDone'); });" +
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
                "    if (tgt === '_blank' || tgt === '_system') {" +
                // External links (e.g. the volunteer "Learn More" buttons) open in
                // the real browser, with the loader as immediate tap feedback.
                "        if (href.indexOf('http://') === 0 || href.indexOf('https://') === 0) {" +
                "            e.preventDefault();" +
                "            if (window.nativeLoader) {" +
                "                window.nativeLoader.postMessage('show');" +
                "                window.nativeLoader.postMessage('open:' + href);" +
                "            }" +
                "        }" +
                "        return;" +
                "    }" +
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

                // Reveal once the app shell is present. Normally Odoo's #wrapwrap,
                // but a logged-out launch lands on /web/login (no #wrapwrap) —
                // detect the login form too, or the splash stays stuck (T3304).
                "function appShellReady() {" +
                "    return !!(document.getElementById('wrapwrap')" +
                "        || document.querySelector(\".oe_login_form, form[action^='/web/login'], input[name='login']\"));" +
                "}" +
                "function revealApp() { hideCapacitorSplash(); hideNativeLoader(); }" +
                "const observer = new MutationObserver((mutations, obs) => {" +
                "   if (appShellReady()) { revealApp(); obs.disconnect(); }" +
                "});" +

                "if (appShellReady()) {" +
                "    revealApp();" +
                "} else {" +
                "    observer.observe(document.body, { childList: true, subtree: true });" +
                // Last-resort failsafe: never leave the splash up indefinitely.
                "    setTimeout(function () { observer.disconnect(); revealApp(); }, 8000);" +
                "}" +

                "if (!window._systemOpenOverridden) {" +
                "    window._systemOpenOverridden = true;" +
                "    var _origOpen = window.open;" +
                "    window.open = function(url, target, features) {" +
                "        if (typeof url === 'string') {" +
                // PDFs (letters via /b2s_image, reports, downloads) can't open a
                // new window in the Android WebView, so open them natively.
                "            var isPdf = url.indexOf('file_type=pdf') !== -1 || /\\.pdf($|\\?)/i.test(url) || url.indexOf('/report/pdf/') !== -1 || url.indexOf('/preview_pdf') !== -1 || url.indexOf('/my/download/') !== -1;" +
                "            var abs = url.indexOf('http') === 0 ? url : (window.location.origin + url);" +
                "            if (isPdf) {" +
                "                if (window.nativeLoader) { window.nativeLoader.postMessage('pdf:' + abs); return null; }" +
                "            }" +
                "            var isExternal = url.startsWith('http://') || url.startsWith('https://');" +
                "            var isExternalTarget = target === '_system' || target === '_blank';" +
                "            if (isExternal && isExternalTarget) {" +
                "                if (window.nativeLoader) { window.nativeLoader.postMessage('open:' + url); return null; }" +
                "            }" +
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