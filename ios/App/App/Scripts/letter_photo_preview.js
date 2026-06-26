// Letters (/b2s_image PDFs) and timeline child photos (/web/image/...) are
// plain <a> links. Without interception, letters open in the system browser
// and photos render raw inside the WebView with no controls (no close/share,
// exit only by swiping back). Route both through the native QuickLook viewer
// via nativeDownload() (defined in native_download.js), so they open the same
// way letters already do from the timeline.
document.addEventListener('click', function(e) {
    const link = e.target.closest('a[href]');
    if (!link) return;
    const href = link.getAttribute('href') || '';
    const isLetter = href.indexOf('/b2s_image') !== -1;
    const isPhoto = href.indexOf('/web/image/compassion.child.pictures/') !== -1;
    if (!isLetter && !isPhoto) return;
    e.preventDefault();
    e.stopPropagation();
    const abs = href.indexOf('http') === 0 ? href : window.location.origin + href;
    nativeDownload(abs);
}, true);

// The letters page opens the envelope via window.open() (see
// my2_child_letters.js), not an <a> click, so the listener above can't catch
// it and the WKWebView hands it to the system browser. Override window.open so
// letter / PDF URLs go to the native QuickLook viewer instead; every other
// window.open (e.g. external "Learn More" links) keeps its normal behaviour.
(function () {
    if (window._nativeOpenOverridden) return;
    window._nativeOpenOverridden = true;
    const origOpen = window.open;
    window.open = function (url, target, features) {
        if (typeof url === 'string') {
            const isPdf =
                url.indexOf('/b2s_image') !== -1 ||
                url.indexOf('file_type=pdf') !== -1 ||
                /\.pdf($|\?)/i.test(url) ||
                url.indexOf('/report/pdf/') !== -1 ||
                url.indexOf('/preview_pdf') !== -1 ||
                url.indexOf('/my/download/') !== -1;
            if (isPdf) {
                const abs = url.indexOf('http') === 0 ? url : window.location.origin + url;
                nativeDownload(abs);
                return null;
            }
        }
        return origOpen.apply(this, arguments);
    };
})();
