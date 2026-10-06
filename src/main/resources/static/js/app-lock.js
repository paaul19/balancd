/**
 * app-lock.js — lado cliente del bloqueo con passkey (ver AppLockService).
 *
 * El servidor ya bloquea cualquier petición tras el tiempo de inactividad, pero una página que
 * estaba abierta en segundo plano sigue mostrando sus datos al volver a ella. Por eso:
 *   - Al volver a la app tras más de N minutos oculta, se tapa la página y se recarga, para que
 *     el servidor redirija a /desbloquear.
 *   - Igual si el navegador restaura la página desde su caché (botón atrás, bfcache).
 *   - Si una llamada fetch() recibe el 401 de "app bloqueada", se va a /desbloquear.
 */
(function () {
    const script = document.currentScript;
    const idleMs = (parseInt(script && script.dataset.idleMinutes, 10) || 5) * 60000;
    let hiddenAt = null;

    function bloquear() {
        document.documentElement.style.visibility = 'hidden';
        window.location.reload();
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
