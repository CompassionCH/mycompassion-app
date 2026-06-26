// Download files natively and preview them with QuickLook (Done/Share).
//
// Used for /my/download/ PDFs, which use a <form> (tax receipt) and
// onclick-divs (payment slips) instead of <a> links, so the WKWebView would
// otherwise render the PDF inline without Done/Share buttons. Also used by
// letter_photo_preview.js to open letters and timeline photos.
//
// The cache filename extension is derived from the response's MIME type so
// QuickLook (which keys off the extension) shows PDFs and images correctly.
async function nativeDownload(url) {
    window.webkit.messageHandlers.nativeLoader.postMessage("show");
    try {
        const response = await fetch(url);
        if (!response.ok) throw new Error("Network response was not ok");
        const blob = await response.blob();
        const reader = new FileReader();
        reader.onloadend = async () => {
            try {
                const base64data = reader.result.split(",")[1];
                const raw = url.split("/").pop().split("?")[0];
                let filename;
                if (blob.type === "image/jpeg") {
                    filename = raw.endsWith(".jpg") || raw.endsWith(".jpeg") ? raw : (raw || "image") + ".jpg";
                } else if (blob.type === "image/png") {
                    filename = raw.endsWith(".png") ? raw : (raw || "image") + ".png";
                } else {
                    filename = raw.endsWith(".pdf") ? raw : raw + ".pdf";
                }
                const saved = await window.Capacitor.Plugins.Filesystem.writeFile({
                    path: filename,
                    data: base64data,
                    directory: "CACHE",
                });
                window.webkit.messageHandlers.nativePreview.postMessage(saved.uri);
            } catch (error) {
                window.webkit.messageHandlers.nativeLoader.postMessage("hide");
                alert("Could not load the document. Please try again.");
            }
        };
        reader.readAsDataURL(blob);
    } catch (error) {
        window.webkit.messageHandlers.nativeLoader.postMessage("hide");
        alert("Could not load the document. Please try again.");
    }
}

// Payment slip / QR invoice buttons: <div onclick="location.href='/my/download/...'">
document.addEventListener('click', function(e) {
    const btn = e.target.closest('[onclick]');
    if (!btn) return;
    const oc = btn.getAttribute('onclick') || '';
    if (!oc.includes('/my/download/')) return;
    const start = oc.indexOf("'") + 1;
    const end = oc.lastIndexOf("'");
    if (start <= 0 || end <= start) return;
    e.preventDefault();
    e.stopPropagation();
    nativeDownload(oc.substring(start, end));
}, true);

// Tax receipt: plain GET form posting to /my/download/tax_receipt
document.addEventListener('submit', function(e) {
    const action = e.target.getAttribute('action') || '';
    if (!action.includes('/my/download/')) return;
    e.preventDefault();
    e.stopPropagation();
    const params = new URLSearchParams(new FormData(e.target)).toString();
    nativeDownload(action + (params ? '?' + params : ''));
}, true);
