// Tutorial interactivo con Intro.js para usuarios nuevos en la página de movimientos

document.addEventListener('DOMContentLoaded', function() {
    if (typeof window.tutorialVisto !== 'undefined' && window.tutorialVisto === false) {
        setTimeout(function() {
            introJs()
                .setOptions({
                    nextLabel: 'Siguiente',
                    prevLabel: 'Anterior',
                    doneLabel: '¡Entendido!',
                    skipLabel: 'Saltar',
                    overlayOpacity: 0.6,
                    tooltipClass: 'custom-introjs-tooltip',
                    highlightClass: 'custom-introjs-highlight',
                    showProgress: false,
                    showBullets: false
                })
                .oncomplete(function() {
                    window.location.href = "/movimientos?tutorialVisto=1";
                })
                .onexit(function() {
                    window.location.href = "/movimientos?tutorialVisto=1";
                })
                .start();
        }, 600);
    }
}); 