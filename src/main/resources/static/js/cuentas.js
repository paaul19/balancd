document.addEventListener('DOMContentLoaded', function () {
    const modal = document.getElementById('cuentaModal');
    const form = document.getElementById('cuentaForm');
    const title = document.getElementById('cuentaModalTitle');
    const nombreInput = document.getElementById('cuentaNombre');
    const tipoInput = document.getElementById('cuentaTipo');
    const saldoInput = document.getElementById('cuentaSaldoInicial');
    const digitosInput = document.getElementById('cuentaUltimosDigitos');

    function abrirModalCrear() {
        form.reset();
        form.setAttribute('action', '/cuentas');
        title.textContent = 'Nueva cuenta';
        saldoInput.value = '0';
        modal.classList.add('show');
    }

    function abrirModalEditar(btn) {
        const id = btn.getAttribute('data-id');
        nombreInput.value = btn.getAttribute('data-nombre') || '';
        tipoInput.value = btn.getAttribute('data-tipo') || 'OTRA';
        saldoInput.value = btn.getAttribute('data-saldo-inicial') || '0';
        digitosInput.value = btn.getAttribute('data-ultimos-digitos') || '';
        form.setAttribute('action', '/cuentas/' + id + '/editar');
        title.textContent = 'Editar cuenta';
        modal.classList.add('show');
    }

    function cerrarModal() {
        modal.classList.remove('show');
    }

    const btnNueva = document.getElementById('btnNuevaCuenta');
    if (btnNueva) btnNueva.addEventListener('click', abrirModalCrear);

    document.querySelectorAll('.btn-editar-cuenta').forEach(function (btn) {
        btn.addEventListener('click', function () { abrirModalEditar(btn); });
    });

    const closeBtn = document.getElementById('closeCuentaModal');
    if (closeBtn) closeBtn.addEventListener('click', cerrarModal);
    const cancelBtn = document.getElementById('btnCancelarCuenta');
    if (cancelBtn) cancelBtn.addEventListener('click', cerrarModal);
    modal.addEventListener('click', function (e) {
        if (e.target === modal) cerrarModal();
    });

    // --- Confirmación de eliminación ---
    const confirmModal = document.getElementById('modalConfirmarEliminarCuenta');
    const confirmarBtn = document.getElementById('confirmarEliminarCuenta');
    const cancelarBtn = document.getElementById('cancelarEliminarCuenta');
    let accionEliminar = null;

    document.querySelectorAll('.btn-eliminar-cuenta').forEach(function (btn) {
        btn.addEventListener('click', function () {
            accionEliminar = btn.getAttribute('data-action');
            confirmModal.classList.add('show');
        });
    });
    if (cancelarBtn) cancelarBtn.addEventListener('click', function () {
        confirmModal.classList.remove('show');
        accionEliminar = null;
    });
    if (confirmarBtn) confirmarBtn.addEventListener('click', function () {
        if (!accionEliminar) return;
        const f = document.createElement('form');
        f.method = 'post';
        f.action = accionEliminar;
        f.style.display = 'none';
        document.body.appendChild(f);
        // requestSubmit() (no .submit()): .submit() no dispara el evento 'submit', así que el
        // listener de csrf.js que inyecta el token nunca se ejecutaba y la acción daba 403.
        f.requestSubmit();
    });
});
