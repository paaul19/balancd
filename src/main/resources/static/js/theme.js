// Única fuente de verdad para el cambio de tema (evita duplicar esta lógica por página).
// Admite tres preferencias: 'dark', 'light' y 'system' (sigue el tema del sistema operativo
// y se actualiza en caliente si el usuario lo cambia mientras la pestaña está abierta).

function resolveTheme(pref) {
    if (pref === 'system') {
        return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    }
    return pref;
}

// iOS (PWA): WebKit solo muestrea el color de la barra de estado al cargar la página, así que al
// cambiar de tema en caliente se quedaba con el color anterior hasta cambiar de pestaña.
// Recrear la franja fija de 1px (y el theme-color) fuerza a WebKit a volver a muestrear.
function refreshStatusBar() {
    var bg = getComputedStyle(document.documentElement).getPropertyValue('--bg-primary').trim();
    if (!bg || !document.body) return;
    var meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute('content', bg);
    var old = document.getElementById('statusBarSampler');
    var strip = document.createElement('div');
    strip.id = 'statusBarSampler';
    strip.setAttribute('aria-hidden', 'true');
    strip.style.cssText = 'position:fixed;top:0;left:0;right:0;height:1px;z-index:0;pointer-events:none;background-color:' + bg;
    document.body.appendChild(strip);
    if (old) {
        requestAnimationFrame(function () { old.remove(); });
    }
}

// iOS no vuelve a muestrear el color de la barra de estado en caliente (ni recreando la franja
// ni cambiando theme-color), así que en la PWA instalada se recarga tras un cambio real de tema.
function reloadIfIosStandalone(prevResolved) {
    var standalone = window.navigator.standalone === true;
    var ios = /iPad|iPhone|iPod/.test(navigator.userAgent) ||
        (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
    if (standalone && ios && document.documentElement.getAttribute('data-theme') !== prevResolved) {
        setTimeout(function () { window.location.reload(); }, 120);
    }
}

function applyTheme(pref) {
    document.documentElement.setAttribute('data-theme', resolveTheme(pref));
    refreshStatusBar();
}

// Usado por el selector de 3 vías en /ajustes.
function setTheme(pref) {
    var prev = document.documentElement.getAttribute('data-theme');
    localStorage.setItem('theme', pref);
    applyTheme(pref);
    document.dispatchEvent(new CustomEvent('themechange', { detail: { pref: pref } }));
    reloadIfIosStandalone(prev);
}

// Mantenido por compatibilidad: lo siguen usando las páginas sin sesión (login, landing),
// que no tienen acceso a /ajustes y solo alternan entre oscuro y claro.
function toggleTheme() {
    const current = localStorage.getItem('theme') || 'dark';
    const resolved = resolveTheme(current);
    setTheme(resolved === 'dark' ? 'light' : 'dark');
}

document.addEventListener('DOMContentLoaded', function() {
    const savedPref = localStorage.getItem('theme') || 'dark';
    applyTheme(savedPref);
});

// Si la preferencia es 'system', sigue los cambios de tema del SO en caliente.
window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', function() {
    if ((localStorage.getItem('theme') || 'dark') === 'system') {
        var prev = document.documentElement.getAttribute('data-theme');
        applyTheme('system');
        reloadIfIosStandalone(prev);
    }
});
