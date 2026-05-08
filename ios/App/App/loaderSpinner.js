//
//  loaderSpinner.js
//  App
//
//  Created by Daniel Gergely on 3/27/26.
//
(function() {
    if (window.hasClickLoader) return;
    window.hasClickLoader = true;

    // Explicitly declare the timeout variable to prevent ReferenceErrors
    let spinnerTimeout = null;

    // 1. Create the full-screen blurred overlay
    let overlay = document.createElement('div');
    overlay.style.position = 'fixed';
    overlay.style.top = '0';
    overlay.style.left = '0';
    overlay.style.width = '100vw';
    overlay.style.height = '100vh';
    overlay.style.backgroundColor = 'rgba(255, 255, 255, 0.5)'; // Semi-transparent white
    overlay.style.backdropFilter = 'blur(6px)'; // Apple Frosted Glass
    overlay.style.webkitBackdropFilter = 'blur(6px)';
    overlay.style.zIndex = '9999999';
    overlay.style.display = 'none';
    overlay.style.alignItems = 'center';
    overlay.style.justifyContent = 'center';
    
    // Add the core-blue spinner and animation
    overlay.innerHTML = `
        <div style="width: 44px; height: 44px; border: 4px solid #e0e0e0; border-top-color: #2a5eec; border-radius: 50%; animation: click-spin 0.8s linear infinite;"></div>
        <style>@keyframes click-spin { to { transform: rotate(360deg); } }</style>
    `;

    // Intercept Clicks Globally
    document.addEventListener('click', function(e) {
        
        // FIX Catch standard input form submissions in addition to buttons and links
        let target = e.target.closest('a, button, input[type="submit"]');
        
        if (!target) {
            let container = e.target.closest('.position-relative, .card, [class*="card"], .my2-bottom-nav');
            if (container) {
                let stretchedLink = container.querySelector('.stretched-link');
                if (stretchedLink) {
                    target = stretchedLink;
                }
            }
        }
        
        if (!target) return;

        // Exclude specific links (Javascript links, anchor jumps, emails, phone numbers)
        if (target.tagName === 'A') {
            let href = target.getAttribute('href');
            if (!href || href === '#' || href.startsWith('javascript:') || href.startsWith('tel:') || href.startsWith('mailto:') || target.getAttribute('target') === '_blank') {
                return;
            }
        }
        
        // Exclude UI toggles (like Odoo dropdown menus, language selectors, closing modals)
        if (target.hasAttribute('data-toggle') || target.hasAttribute('data-dismiss')) {
            return;
        }
        
        // Failsafe to ensure the body exists before appending
        if (!document.body) return;

        if (!document.body.contains(overlay)) {
            document.body.appendChild(overlay);
        }
        
        // Native Haptic Feedback
        try {
            if (window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.Haptics) {
                // If the JS wrapper exists, use it
                window.Capacitor.Plugins.Haptics.impact({ style: 'LIGHT' }).catch(()=>{});
            } else if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.bridge) {
                // Bypass the missing NPM package and talk directly to the iOS Swift layer!
                window.webkit.messageHandlers.bridge.postMessage({
                    type: 'message',
                    callbackId: 'haptic_click_' + Date.now(),
                    pluginId: 'Haptics',
                    methodName: 'impact',
                    options: { style: 'LIGHT' }
                });
                console.log("Capacitor Bridge: Fired raw haptic message to Swift layer.");
            }
        } catch (err) {
            console.log("Haptic bridge failed:", err);
        }

        // Prevent race condition: clear existing timeout before starting a new one
        if (spinnerTimeout) {
            clearTimeout(spinnerTimeout);
        }

        overlay.style.display = 'flex';

        // Extended Failsafe timeout
        spinnerTimeout = setTimeout(() => {
            overlay.style.display = 'none';
        }, 15000);
        
    }, { capture: true });

    // Clear the spinner if the user hits the "Back" button (iOS caching fix)
    window.addEventListener('pageshow', function() {
        if (spinnerTimeout) clearTimeout(spinnerTimeout);
        overlay.style.display = 'none';
    });

    // Odoo Integration: Clear spinner automatically on AJAX/RPC completion
    document.addEventListener("DOMContentLoaded", function() {
        if (typeof window.jQuery !== 'undefined') {
            window.jQuery(document).ajaxStop(function () {
                if (spinnerTimeout) clearTimeout(spinnerTimeout);
                overlay.style.display = 'none';
            });
        }
    });
})();
