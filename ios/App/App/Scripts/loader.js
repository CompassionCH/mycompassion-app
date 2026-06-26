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
