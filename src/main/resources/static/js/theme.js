// Única fuente de verdad para el cambio de tema (evita duplicar esta lógica por página).
// Admite tres preferencias: 'dark', 'light' y 'system' (sigue el tema del sistema operativo
// y se actualiza en caliente si el usuario lo cambia mientras la pestaña está abierta).

function resolveTheme(pref) {
    if (pref === 'system') {
        return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    }
    return pref;
}

function applyTheme(pref) {
    document.documentElement.setAttribute('data-theme', resolveTheme(pref));
}

// Usado por el selector de 3 vías en /ajustes.
function setTheme(pref) {
    localStorage.setItem('theme', pref);
    applyTheme(pref);
    document.dispatchEvent(new CustomEvent('themechange', { detail: { pref: pref } }));
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
        applyTheme('system');
    }
});
