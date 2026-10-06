/**
 * app-lock.js — lado cliente del bloqueo con passkey (ver AppLockService).
 *
 * El servidor ya bloquea cualquier petición tras el tiempo de inactividad, pero una página que
 * estaba abierta en segundo plano sigue mostrando sus datos al volver a ella. Por eso:
 *   - Al abrir la app (PWA reabierta, pestaña nueva) sessionStorage empieza vacío: si esta
 *     pestaña no se ha desbloqueado todavía, se tapa la página, se bloquea la sesión en el
 *     servidor y se va a /desbloquear, que pide la passkey automáticamente.
 *   - Al volver a la app tras más de N minutos oculta, se tapa la página y se recarga, para que
 *     el servidor redirija a /desbloquear.
 *   - Igual si el navegador restaura la página desde su caché (botón atrás, bfcache).
 *   - Si una llamada fetch() recibe el 401 de "app bloqueada", se va a /desbloquear.
 */
(function () {
    const script = document.currentScript;
    const idleMs = (parseInt(script && script.dataset.idleMinutes, 10) || 5) * 60000;
    const STORAGE_KEY = 'balancdDesbloqueado';
    let hiddenAt = null;

    function bloquear() {
        document.documentElement.style.visibility = 'hidden';
        window.location.reload();
    }

    function getCookie(name) {
        const match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'));
        return match ? decodeURIComponent(match[1]) : null;
    }

    // ¿Se acaba de abrir la app? El servidor marca la primera página tras desbloquear (login o
    // passkey); a partir de ahí esta pestaña lo recuerda hasta que se cierre.
    let desbloqueadaAqui = true;
    try {
        if (script && script.dataset.recienDesbloqueado === 'true') {
            sessionStorage.setItem(STORAGE_KEY, '1');
        }
        desbloqueadaAqui = sessionStorage.getItem(STORAGE_KEY) === '1';
    } catch (e) {
        // Sin sessionStorage (modo privado estricto): queda solo el bloqueo del servidor.
    }
    if (!desbloqueadaAqui) {
        document.documentElement.style.visibility = 'hidden';
        const headers = {};
        const token = getCookie('XSRF-TOKEN');
        if (token) headers['X-XSRF-TOKEN'] = token;
        fetch('/desbloquear/bloquear', { method: 'POST', headers: headers, credentials: 'same-origin' })
            .finally(function () { window.location.replace('/desbloquear'); });
        return;
    }

    document.addEventListener('visibilitychange', function () {
        if (document.visibilityState === 'hidden') {
            hiddenAt = Date.now();
        } else if (hiddenAt !== null && Date.now() - hiddenAt >= idleMs) {
            bloquear();
        }
    });

    window.addEventListener('pageshow', function (event) {
        if (event.persisted) bloquear();
    });

    const originalFetch = window.fetch;
    window.fetch = function () {
        return originalFetch.apply(this, arguments).then(function (res) {
            if (res.status === 401 && res.headers.get('X-App-Locked')) {
                window.location.href = '/desbloquear';
            }
            return res;
        });
    };
})();
