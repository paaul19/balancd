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
    // --- Token de API ---
    const btnGenerarToken = document.getElementById('btnGenerarToken');
    const btnRevocarTokens = document.getElementById('btnRevocarTokens');
    const apiTokenResultado = document.getElementById('apiTokenResultado');
    const apiTokenValor = document.getElementById('apiTokenValor');
    const apiTokenCaduca = document.getElementById('apiTokenCaduca');
    const apiTokenMensaje = document.getElementById('apiTokenMensaje');
    const btnCopiarToken = document.getElementById('btnCopiarToken');
    const modalRevocar = document.getElementById('modalRevocarTokens');

    function mensajeToken(texto, esError) {
        apiTokenMensaje.textContent = texto;
        apiTokenMensaje.classList.toggle('error', !!esError);
        apiTokenMensaje.hidden = !texto;
    }

    function ocultarToken() {
        apiTokenValor.textContent = '';
        apiTokenCaduca.textContent = '';
        apiTokenResultado.hidden = true;
    }

    function peticionToken(method) {
        const headers = { 'Accept': 'application/json' };
        const csrf = window.getCsrfToken ? window.getCsrfToken() : null;
        if (csrf) headers['X-XSRF-TOKEN'] = csrf;
        return fetch('/api/ajustes/token', { method: method, headers: headers, credentials: 'same-origin' })
            .then(function (r) {
                return r.json().catch(function () { return {}; }).then(function (data) {
                    if (!r.ok) throw new Error(data.error || 'No se pudo completar la operación.');
                    return data;
                });
            });
    }

    if (btnGenerarToken) {
        btnGenerarToken.addEventListener('click', function () {
            btnGenerarToken.disabled = true;
            mensajeToken('');
            peticionToken('POST')
                .then(function (data) {
                    apiTokenValor.textContent = data.token;
                    const p = String(data.caduca).split('-');
                    apiTokenCaduca.textContent = p.length === 3 ? p[2] + '/' + p[1] + '/' + p[0] : data.caduca;
                    btnCopiarToken.textContent = 'Copiar';
                    apiTokenResultado.hidden = false;
                })
                .catch(function (e) { ocultarToken(); mensajeToken(e.message, true); })
                .finally(function () { btnGenerarToken.disabled = false; });
        });
    }

    if (btnCopiarToken) {
        btnCopiarToken.addEventListener('click', function () {
            const token = apiTokenValor.textContent;
            if (!token) return;
            navigator.clipboard.writeText(token)
                .then(function () { btnCopiarToken.textContent = 'Copiado'; })
                .catch(function () {
                    // Sin permiso de portapapeles: deja el token seleccionado para copiarlo a mano.
                    const range = document.createRange();
                    range.selectNodeContents(apiTokenValor);
                    const sel = window.getSelection();
                    sel.removeAllRanges();
                    sel.addRange(range);
                });
        });
    }

    if (btnRevocarTokens) btnRevocarTokens.addEventListener('click', function () { modalRevocar.classList.add('show'); });
    const cancelarRevocar = document.getElementById('cancelarRevocar');
    if (cancelarRevocar) cancelarRevocar.addEventListener('click', function () { modalRevocar.classList.remove('show'); });
    if (modalRevocar) {
        modalRevocar.addEventListener('click', function (e) {
            if (e.target === modalRevocar) modalRevocar.classList.remove('show');
        });
    }
    const confirmarRevocar = document.getElementById('confirmarRevocar');
    if (confirmarRevocar) {
        confirmarRevocar.addEventListener('click', function () {
            confirmarRevocar.disabled = true;
            peticionToken('DELETE')
                .then(function () {
                    ocultarToken();
                    mensajeToken('Tokens revocados. Los tokens anteriores ya no funcionan.');
                })
                .catch(function (e) { mensajeToken(e.message, true); })
                .finally(function () {
                    confirmarRevocar.disabled = false;
                    modalRevocar.classList.remove('show');
                });
        });
    }
});
