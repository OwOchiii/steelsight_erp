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

document.addEventListener('htmx:beforeSwap', function () {
    document.documentElement.removeAttribute('data-nav-open');
});
