/**
 * Soporte de protección CSRF (ver hallazgo C4 de la auditoría de seguridad).
 *
 * Spring Security emite el token CSRF en una cookie legible por JS (XSRF-TOKEN, vía
 * CookieCsrfTokenRepository.withHttpOnlyFalse()). Este script:
 *   1) Inyecta automáticamente un campo oculto "_csrf" en cualquier <form method="post">
 *      que se envíe de forma nativa y que aún no lo tenga - incluidos los formularios
 *      construidos dinámicamente por JS (footer-modals.js, lista.js), sin tener que tocar
 *      cada uno de ellos uno a uno.
 *   2) Expone window.getCsrfToken() para las llamadas fetch() que necesiten mandar el
 *      token como cabecera X-XSRF-TOKEN en vez de como campo de formulario.
 *
 * Las rutas /api/** NO usan esta cookie (se autentican por JWT en la cabecera Authorization)
 * y están explícitamente excluidas de CSRF en SecurityConfig - este script no les afecta.
 */
(function () {
    function getCookie(name) {
        const match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'));
        return match ? decodeURIComponent(match[1]) : null;
    }

    function getCsrfToken() {
        return getCookie('XSRF-TOKEN');
    }

    window.getCsrfToken = getCsrfToken;

    // Fase de captura: se ejecuta antes de que el propio formulario dispare la navegación,
    // así el campo añadido viaja en el envío.
    document.addEventListener('submit', function (event) {
        const form = event.target;
        if (!(form instanceof HTMLFormElement)) return;
        const method = (form.getAttribute('method') || 'get').toLowerCase();
        if (method !== 'post') return;
        if (form.querySelector('input[name="_csrf"]')) return;

        const token = getCsrfToken();
        if (!token) return;

        const input = document.createElement('input');
        input.type = 'hidden';
        input.name = '_csrf';
        input.value = token;
        form.appendChild(input);
    }, true);
})();
