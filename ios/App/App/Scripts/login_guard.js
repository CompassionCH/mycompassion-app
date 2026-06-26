// Prevent double-submit of the login form (double-tap causes a CSRF 400
// because the first login rotates the session token)
document.addEventListener('submit', function(e) {
    const form = e.target;
    if (!(form.action || '').includes('/web/login')) return;
    if (form.dataset.submitted) { e.preventDefault(); return; }
    form.dataset.submitted = 'true';
    const btn = form.querySelector('button[type="submit"]');
    if (btn) btn.disabled = true;
}, true);
