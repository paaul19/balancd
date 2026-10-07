// Capa de movimiento compartida. Todo lo "bonito" que necesita JS vive aquí; el resto es CSS en
// fragments/theme-switcher.html. Respeta prefers-reduced-motion (el CSS ya degrada a fade).
(function () {
    'use strict';

    // (La entrada escalonada del contenido es solo CSS: ver fragments/theme-switcher.html.)

    // 2) Header: marca .is-scrolled al bajar para que aparezca su separación.
    function wireHeaderScroll() {
        var header = document.querySelector('.header');
        if (!header) return;
        var ticking = false;
        function update() {
            header.classList.toggle('is-scrolled', window.scrollY > 4);
            ticking = false;
        }
        window.addEventListener('scroll', function () {
            if (!ticking) { ticking = true; requestAnimationFrame(update); }
        }, { passive: true });
        update();
    }

    // 3) Estado "enviando" en el botón de envío: feedback inmediato y evita dobles envíos.
    function wireBusySubmit() {
        document.addEventListener('submit', function (e) {
            var form = e.target;
            if (!(form instanceof HTMLFormElement)) return;
            var btn = e.submitter || form.querySelector('[type="submit"]');
            if (!btn || btn.classList.contains('is-busy')) return;
            // Los formularios que se envían por fetch hacen preventDefault: ahí no hay navegación,
            // así que no se marca nada. Se comprueba tras el resto de listeners.
            setTimeout(function () {
                if (e.defaultPrevented) return;
                btn.style.setProperty('--busy-color', getComputedStyle(btn).color);
                btn.classList.add('is-busy');
            }, 0);
        });
        // Al volver con bfcache, el botón no debe quedarse en estado "enviando".
        window.addEventListener('pageshow', function (e) {
            if (!e.persisted) return;
            document.querySelectorAll('.is-busy').forEach(function (b) { b.classList.remove('is-busy'); });
        });
    }

    function init() {
        wireHeaderScroll();
        wireBusySubmit();
    }
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
    else init();
})();
