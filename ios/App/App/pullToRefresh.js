//
//  pullToRefresh.js
//  App
//
//  Created by Daniel Gergely on 3/27/26.
//
(function() {
    if (window.hasPTR) return;
    window.hasPTR = true;

    // 1. Create the Spinner UI
    let ptrContainer = document.createElement('div');
    ptrContainer.style.position = 'fixed';
    
    ptrContainer.style.top = 'env(safe-area-inset-top, 47px)';
    ptrContainer.style.left = '0';
    ptrContainer.style.width = '100%';
    ptrContainer.style.height = '0px';
    ptrContainer.style.zIndex = '99999';
    ptrContainer.style.backgroundColor = '#ffffff';
    ptrContainer.style.display = 'flex';
    ptrContainer.style.alignItems = 'center';
    ptrContainer.style.justifyContent = 'center';
    ptrContainer.style.overflow = 'hidden';
    //ptrContainer.style.boxShadow = '0 4px 10px rgba(0,0,0,0.1)';
    
    // Spinner
    ptrContainer.innerHTML = '<div style="width: 24px; height: 24px; border: 3px solid #f0f0f0; border-top-color: #2a5eec; border-radius: 50%; animation: ptr-spin 0.8s linear infinite;"></div><style>@keyframes ptr-spin { to { transform: rotate(360deg); } }</style>';
    document.body.appendChild(ptrContainer);

    let startY = 0;
    let isPulling = false;
    
    // Odoo's main scroll container
    let wrap = document.getElementById('wrapwrap') || document.documentElement;

    // 2. Listen for Pull Gestures safely
    wrap.addEventListener('touchstart', function(e) {
        if (wrap.scrollTop <= 0) {
            startY = e.touches[0].clientY;
            isPulling = true;
            ptrContainer.style.transition = 'none'; // Instant follow during drag
        }
    }, {passive: true});

    wrap.addEventListener('touchmove', function(e) {
        if (!isPulling) return;
        let currentY = e.touches[0].clientY;
        let pullDistance = currentY - startY;
        
        // Only expand if pulling downwards from the very top
        if (pullDistance > 0 && wrap.scrollTop <= 0) {
            if (e.cancelable) e.preventDefault(); // Stop iOS from bouncing the whole page
            ptrContainer.style.height = Math.min(pullDistance / 2.5, 75) + 'px';
        } else {
            isPulling = false;
            ptrContainer.style.height = '0px';
        }
    }, {passive: false});

    wrap.addEventListener('touchend', function(e) {
        if (!isPulling) return;
        isPulling = false;
        let currentY = e.changedTouches[0].clientY;
        let pullDistance = currentY - startY;
        
        ptrContainer.style.transition = 'height 0.3s ease'; // Smooth snap
        
        // If pulled far enough, trigger the reload
        if (pullDistance > 120 && wrap.scrollTop <= 0) {
            ptrContainer.style.height = '80px'; // Lock spinner open
            setTimeout(() => location.reload(), 400); // Reload
        } else {
            ptrContainer.style.height = '0px'; // Cancel pull
        }
    });
})();
