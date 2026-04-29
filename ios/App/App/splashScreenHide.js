//
//  splashScreenHide.js
//  App
//
//  Created by Daniel Gergely on 3/27/26.
//

(function() {
    // Function to safely hide the splash screen
    function hideSplash() {
        if (window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.SplashScreen) {
            window.Capacitor.Plugins.SplashScreen.hide();
        } else {
            console.warn("Capacitor SplashScreen plugin not found.");
        }
    }

    // 1. Check if the container ALREADY exists (in case it loaded extremely fast)
    if (document.getElementById('wrapwrap')) {
        hideSplash();
        return;
    }

    // 2. If not, wait until the specific Odoo wrapper container is added to the DOM
    const observer = new MutationObserver((mutations, obs) => {
        const odooAppContainer = document.getElementById('wrapwrap');
        
        if (odooAppContainer) {
            hideSplash();
            obs.disconnect();
        }
    });

    // Start observing the body for changes
    observer.observe(document.body, { childList: true, subtree: true });
})();
