// Runs before the page paints, so a dark-mode user never sees a flash of the light theme.
(function () {
    var saved = null;
    try { saved = localStorage.getItem('steelsight-theme'); } catch (e) { /* storage can be blocked */ }
    var dark = saved ? saved === 'dark' : window.matchMedia('(prefers-color-scheme: dark)').matches;
    document.documentElement.setAttribute('data-theme', dark ? 'dark' : 'light');
})();

// Listeners sit on the document, so they keep working after htmx swaps the page body.
document.addEventListener('click', function (event) {
    var root = document.documentElement;

    if (event.target.closest('[data-theme-toggle]')) {
        var next = root.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
        root.setAttribute('data-theme', next);
        try { localStorage.setItem('steelsight-theme', next); } catch (e) { /* the choice lasts this page only */ }
    }

    if (event.target.closest('[data-nav-toggle]')) {
        root.toggleAttribute('data-nav-open');
    }

    // A click anywhere else closes an open menu.
    document.querySelectorAll('details.menu[open]').forEach(function (menu) {
        if (!menu.contains(event.target)) {
            menu.removeAttribute('open');
        }
    });
});

document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape') {
        document.documentElement.removeAttribute('data-nav-open');
    }
});

// A photo over the limit is flagged as soon as it's chosen, so nobody waits for an upload that will be refused.
// The browser then won't send the form and shows the message next to the field. The server checks again.
document.addEventListener('change', function (event) {
    var input = event.target;
    if (!input.matches || !input.matches('input[type="file"][data-max-bytes]')) {
        return;
    }
    var max = Number(input.getAttribute('data-max-bytes'));
    var tooLarge = Array.prototype.some.call(input.files || [], function (file) { return file.size > max; });
    input.setCustomValidity(tooLarge ? input.getAttribute('data-too-large') || 'Too large' : '');
    if (tooLarge) {
        input.reportValidity();
    }
});

document.addEventListener('htmx:beforeSwap', function () {
    document.documentElement.removeAttribute('data-nav-open');
});
