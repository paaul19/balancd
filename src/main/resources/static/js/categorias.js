document.addEventListener('DOMContentLoaded', function () {
    // --- Abrir/cerrar modales (crear categoría + uno de edición por categoría propia) ---
    const modalCrear = document.getElementById('modalCrearCategoria');
    const btnAbrirCrear = document.getElementById('btnCrearCategoria');
    if (btnAbrirCrear && modalCrear) {
        btnAbrirCrear.addEventListener('click', function () { modalCrear.classList.add('show'); });
    }

    // Cualquier botón con data-modal-target abre ese modal (usado por el lápiz de "editar").
    document.querySelectorAll('[data-modal-target]').forEach(function (btn) {
        btn.addEventListener('click', function () {
            const modal = document.getElementById(btn.getAttribute('data-modal-target'));
            if (modal) modal.classList.add('show');
        });
    });

    // Cierre: la "x", los botones "Cancelar" (data-modal-close) y click fuera del contenido.
    document.querySelectorAll('.modal').forEach(function (modal) {
        modal.addEventListener('click', function (e) {
            if (e.target === modal) modal.classList.remove('show');
        });
    });
    document.getElementById('closeCrearCategoria')?.addEventListener('click', function () { modalCrear.classList.remove('show'); });
    document.getElementById('cancelarCrearCategoria')?.addEventListener('click', function () { modalCrear.classList.remove('show'); });
    document.querySelectorAll('.modal-close[data-modal-close], .btn-cancel[data-modal-close]').forEach(function (btn) {
        btn.addEventListener('click', function () {
            const modal = btn.closest('.modal');
            if (modal) modal.classList.remove('show');
        });
    });

    // --- Subcategorías dinámicas: delegado, funciona igual en el modal de crear que en cada uno de editar ---
    document.addEventListener('click', function (e) {
        const btn = e.target.closest('.js-add-sub');
        if (!btn) return;
        const contenedor = btn.parentElement.querySelector('.js-subs-inputs') || btn.previousElementSibling;
        if (!contenedor) return;
        const input = document.createElement('input');
        input.type = 'text';
        input.name = 'subcategorias';
        input.maxLength = 100;
        input.placeholder = 'Otra subcategoría';
        contenedor.appendChild(input);
        input.focus();
    });

    // --- Plegar/desplegar subcategorías de una categoría ---
    document.querySelectorAll('.categoria-row-expand-btn').forEach(function (btn) {
        btn.addEventListener('click', function () {
            btn.closest('.categoria-row').classList.toggle('categoria-row--expanded');
        });
    });

    // --- Confirmar eliminación de categoría propia (modal, no el confirm() feo del navegador) ---
    const modalEliminar = document.getElementById('modalConfirmarEliminarCategoria');
    const textoEliminar = document.getElementById('textoConfirmarEliminarCategoria');
    const btnConfirmarEliminar = document.getElementById('confirmarEliminarCategoria');
    const btnCancelarEliminar = document.getElementById('cancelarEliminarCategoria');
    let accionEliminarCategoria = null;

    document.querySelectorAll('.js-eliminar-categoria').forEach(function (btn) {
        btn.addEventListener('click', function () {
            accionEliminarCategoria = btn.getAttribute('data-action');
            textoEliminar.textContent = '¿Eliminar la categoría "' + btn.getAttribute('data-nombre') + '"?';
            modalEliminar.classList.add('show');
        });
    });
    if (btnCancelarEliminar) {
        btnCancelarEliminar.addEventListener('click', function () {
            modalEliminar.classList.remove('show');
            accionEliminarCategoria = null;
        });
    }
    if (btnConfirmarEliminar) {
        btnConfirmarEliminar.addEventListener('click', function () {
            if (!accionEliminarCategoria) return;
            const f = document.createElement('form');
            f.method = 'post';
            f.action = accionEliminarCategoria;
            f.style.display = 'none';
            document.body.appendChild(f);
            // requestSubmit() (no .submit()): .submit() no dispara el evento 'submit', así que
            // csrf.js nunca inyectaría el token y la petición daría 403.
            f.requestSubmit();
        });
    }
});
