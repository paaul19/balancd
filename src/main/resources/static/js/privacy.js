// Modo secreto: oculta todas las cifras en euros (.js-money) sustituyéndolas por puntos.
// El estado se aplica ya antes de pintar la página (ver theme-script-immediate en
// theme-switcher.html); esto solo maneja el toggle y mantiene sincronizado el icono del botón.
function togglePrivacy() {
    const html = document.documentElement;
    const isOn = html.getAttribute('data-privacy') === 'on';
    const next = isOn ? 'off' : 'on';
    html.setAttribute('data-privacy', next);
    localStorage.setItem('privacyMode', next);
}

document.addEventListener('DOMContentLoaded', function () {
    const saved = localStorage.getItem('privacyMode') || 'off';
    document.documentElement.setAttribute('data-privacy', saved);
});
