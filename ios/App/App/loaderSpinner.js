//
//  loaderSpinner.js
//  App
//
//  Created by Daniel Gergely on 3/27/26.
//
(function() {
    if (window.hasClickLoader) return;
    window.hasClickLoader = true;

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

    // 2. Intercept Clicks Globally
    document.addEventListener('click', function(e) {
        // Check if the user clicked a link or a button
        let target = e.target.closest('a, button');
        
        if (!target) {
            let container = e.target.closest('.position-relative, .card, [class*==card=]');
            if (container) {
                let stretchedLink = container.querySelector('.stertched-link');
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
        
        if (!document.body.contains(overlay)) {
            document.body.appendChild(overlay);
        }

        // Show the overlay immediately
        overlay.style.display = 'flex';

        // FAILSAFE: Auto-hide the spinner after 6 seconds.
        // (Just in case an action fails or it was an AJAX background request, we don't want to trap the user)
        setTimeout(() => {
            overlay.style.display = 'none';
        }, 6000);
        
    }, { capture: true });

    // 3. Clear the spinner if the user hits the "Back" button (iOS caching fix)
    window.addEventListener('pageshow', function() {
        overlay.style.display = 'none';
    });
})();
