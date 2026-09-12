document.addEventListener('DOMContentLoaded', function () {
    // ---------- Presupuestos ----------
    const presupuestoModal = document.getElementById('presupuestoModal');
    const presupuestoForm = document.getElementById('presupuestoForm');
    const presupuestoTitle = document.getElementById('presupuestoModalTitle');
    const presupuestoConceptoInput = document.getElementById('presupuestoConcepto');
    const presupuestoLimiteInput = document.getElementById('presupuestoLimite');
    const CREAR_PRESUPUESTO_URL = presupuestoForm ? presupuestoForm.getAttribute('action') : '';

    function abrirModalCrearPresupuesto() {
        presupuestoForm.reset();
        presupuestoForm.setAttribute('action', CREAR_PRESUPUESTO_URL);
        presupuestoTitle.textContent = 'Nuevo presupuesto';
        window.CategoriaCascade.refreshCategoriaOptionsForTipo('presupuesto', 'gasto');
        window.CategoriaCascade.wireCategoriaCascade('presupuesto');
        presupuestoModal.classList.add('show');
    }

    function abrirModalEditarPresupuesto(btn) {
        const id = btn.getAttribute('data-id');
        presupuestoForm.reset();
        presupuestoForm.setAttribute('action', '/objetivos/presupuestos/' + id + '/editar');
        presupuestoTitle.textContent = 'Editar presupuesto';
        presupuestoConceptoInput.value = btn.getAttribute('data-concepto') || '';
        presupuestoLimiteInput.value = btn.getAttribute('data-limite') || '';
        window.CategoriaCascade.refreshCategoriaOptionsForTipo('presupuesto', 'gasto');
        window.CategoriaCascade.wireCategoriaCascade('presupuesto', btn.getAttribute('data-categoria-id'), btn.getAttribute('data-subcategoria-id'));
        presupuestoModal.classList.add('show');
    }

    const btnNuevoPresupuesto = document.getElementById('btnNuevoPresupuesto');
    if (btnNuevoPresupuesto) btnNuevoPresupuesto.addEventListener('click', abrirModalCrearPresupuesto);

    document.querySelectorAll('.btn-editar-presupuesto').forEach(function (btn) {
        btn.addEventListener('click', function () { abrirModalEditarPresupuesto(btn); });
    });

    function cerrarPresupuestoModal() { presupuestoModal.classList.remove('show'); }
    document.getElementById('closePresupuestoModal').addEventListener('click', cerrarPresupuestoModal);
    document.getElementById('btnCancelarPresupuesto').addEventListener('click', cerrarPresupuestoModal);
    presupuestoModal.addEventListener('click', function (e) { if (e.target === presupuestoModal) cerrarPresupuestoModal(); });

    // ---------- Objetivos de ahorro ----------
    const objetivoModal = document.getElementById('objetivoModal');
    const objetivoForm = document.getElementById('objetivoForm');
    const objetivoTitle = document.getElementById('objetivoModalTitle');
    const objetivoNombreInput = document.getElementById('objetivoNombre');
    const objetivoCuentaSelect = document.getElementById('objetivoCuenta');
    const objetivoImporteInput = document.getElementById('objetivoImporte');
    const objetivoFechaInput = document.getElementById('objetivoFechaLimite');
    const CREAR_OBJETIVO_URL = objetivoForm ? objetivoForm.getAttribute('action') : '';

    function abrirModalCrearObjetivo() {
        objetivoForm.reset();
        objetivoForm.setAttribute('action', CREAR_OBJETIVO_URL);
        objetivoTitle.textContent = 'Nuevo objetivo';
        objetivoModal.classList.add('show');
    }

    function abrirModalEditarObjetivo(btn) {
        const id = btn.getAttribute('data-id');
        objetivoForm.reset();
        objetivoForm.setAttribute('action', '/objetivos/metas/' + id + '/editar');
        objetivoTitle.textContent = 'Editar objetivo';
        objetivoNombreInput.value = btn.getAttribute('data-nombre') || '';
        objetivoCuentaSelect.value = btn.getAttribute('data-cuenta-id') || '';
        objetivoImporteInput.value = btn.getAttribute('data-objetivo') || '';
        objetivoFechaInput.value = btn.getAttribute('data-fecha-limite') || '';
        objetivoModal.classList.add('show');
    }

    const btnNuevoObjetivo = document.getElementById('btnNuevoObjetivo');
    if (btnNuevoObjetivo) btnNuevoObjetivo.addEventListener('click', abrirModalCrearObjetivo);

    document.querySelectorAll('.btn-editar-meta').forEach(function (btn) {
        btn.addEventListener('click', function () { abrirModalEditarObjetivo(btn); });
    });

    function cerrarObjetivoModal() { objetivoModal.classList.remove('show'); }
    document.getElementById('closeObjetivoModal').addEventListener('click', cerrarObjetivoModal);
    document.getElementById('btnCancelarObjetivo').addEventListener('click', cerrarObjetivoModal);
    objetivoModal.addEventListener('click', function (e) { if (e.target === objetivoModal) cerrarObjetivoModal(); });

    // ---------- Confirmación de eliminación (compartida) ----------
    const confirmModal = document.getElementById('modalConfirmarEliminarObjetivos');
    const confirmarBtn = document.getElementById('confirmarEliminarObjetivos');
    const cancelarBtn = document.getElementById('cancelarEliminarObjetivos');
    let accionEliminar = null;

    document.querySelectorAll('.btn-eliminar-presupuesto, .btn-eliminar-meta').forEach(function (btn) {
        btn.addEventListener('click', function () {
            accionEliminar = btn.getAttribute('data-action');
            confirmModal.classList.add('show');
        });
    });
    cancelarBtn.addEventListener('click', function () {
        confirmModal.classList.remove('show');
        accionEliminar = null;
    });
    confirmarBtn.addEventListener('click', function () {
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
