document.addEventListener('DOMContentLoaded', function () {
    // --- Selector de tema (Oscuro / Sistema / Claro) ---
    const temaSelector = document.getElementById('temaSelector');
    const temaOpciones = temaSelector ? temaSelector.querySelectorAll('.tema-opcion') : [];

    function marcarTemaActivo() {
        const actual = localStorage.getItem('theme') || 'dark';
        temaOpciones.forEach(function (btn) {
            btn.classList.toggle('activo', btn.getAttribute('data-tema') === actual);
        });
    }
    temaOpciones.forEach(function (btn) {
        btn.addEventListener('click', function () {
            setTheme(btn.getAttribute('data-tema'));
            marcarTemaActivo();
        });
    });
    marcarTemaActivo();

    // --- Modal: idioma (informativo) ---
    const modalIdioma = document.getElementById('modalIdioma');
    const btnIdioma = document.getElementById('btnIdioma');
    const closeIdioma = document.getElementById('closeIdioma');
    if (btnIdioma) btnIdioma.addEventListener('click', function () { modalIdioma.classList.add('show'); });
    if (closeIdioma) closeIdioma.addEventListener('click', function () { modalIdioma.classList.remove('show'); });
    if (modalIdioma) {
        modalIdioma.addEventListener('click', function (e) {
            if (e.target === modalIdioma) modalIdioma.classList.remove('show');
        });
    }

    // --- Modal: divisa preferida ---
    const modalDivisa = document.getElementById('modalDivisa');
    const btnDivisa = document.getElementById('btnDivisa');
    const closeDivisa = document.getElementById('closeDivisa');
    const cancelarDivisa = document.getElementById('cancelarDivisa');
    if (btnDivisa) btnDivisa.addEventListener('click', function () { modalDivisa.classList.add('show'); });
    function cerrarModalDivisa() { modalDivisa.classList.remove('show'); }
    if (closeDivisa) closeDivisa.addEventListener('click', cerrarModalDivisa);
    if (cancelarDivisa) cancelarDivisa.addEventListener('click', cerrarModalDivisa);
    if (modalDivisa) {
        modalDivisa.addEventListener('click', function (e) {
            if (e.target === modalDivisa) cerrarModalDivisa();
        });
    }

    // --- Eliminar cuenta ---
    const btnEliminarCuenta = document.getElementById('btnEliminarCuenta');
    const modalEliminar = document.getElementById('modalConfirmarEliminar');
    const cancelarEliminar = document.getElementById('cancelarEliminar');
    const confirmarEliminar = document.getElementById('confirmarEliminar');
    if (btnEliminarCuenta) {
        btnEliminarCuenta.addEventListener('click', function () { modalEliminar.classList.add('show'); });
    }
    if (cancelarEliminar) {
        cancelarEliminar.addEventListener('click', function () { modalEliminar.classList.remove('show'); });
    }
    if (confirmarEliminar) {
        confirmarEliminar.addEventListener('click', function () {
            document.getElementById('formEliminarCuenta').requestSubmit();
        });
    }
});
