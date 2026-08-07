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

// Reveal the app once its shell is present. Normally that's Odoo's #wrapwrap,
// but a logged-out launch lands on /web/login, which has no #wrapwrap — detect
// the login form too, or the splash stays stuck (T3304).
function appShellReady() {
    return !!(document.getElementById("wrapwrap")
        || document.querySelector(".oe_login_form, form[action^='/web/login'], input[name='login']"));
}
function revealApp() {
    hideCapacitorSplash();
    hideNativeLoader();
}
const observer = new MutationObserver((mutations, obs) => {
    if (appShellReady()) { revealApp(); obs.disconnect(); }
});

if (appShellReady()) {
    revealApp();
} else {
    observer.observe(document.body, { childList: true, subtree: true });
    // Last-resort failsafe: never leave the splash up indefinitely on an
    // unrecognised page.
    setTimeout(function () { observer.disconnect(); revealApp(); }, 8000);
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
