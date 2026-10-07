// Función para abrir el modal de edición
function openEditModal(button) {
    const id = button.getAttribute('data-id');
    const asunto = button.getAttribute('data-asunto');
    const cantidad = button.getAttribute('data-cantidad');
    const ingreso = button.getAttribute('data-ingreso');
    const fechaInicio = button.getAttribute('data-fecha');
    const frecuencia = button.getAttribute('data-frecuencia');
    const categoriaId = button.getAttribute('data-categoria-id') || '';
    const subcategoriaId = button.getAttribute('data-subcategoria-id') || '';

    document.getElementById('editRecurrenteId').value = id;
    const cuentaSelect = document.getElementById('editRecurrenteCuenta');
    if (cuentaSelect) {
        cuentaSelect.value = button.getAttribute('data-cuenta-id') || '';
    }
    document.getElementById('editRecurrenteCantidad').value = cantidad;
    document.getElementById('editRecurrenteAsunto').value = asunto;
    document.getElementById('editRecurrenteTipo').value = ingreso;
    document.getElementById('editRecurrenteFecha').value = fechaInicio;
    document.getElementById('editRecurrenteFrecuencia').value = frecuencia;
    window.CategoriaCascade.refreshCategoriaOptionsForTipo('editRecurrente', ingreso === 'true' ? 'ingreso' : 'gasto');
    window.CategoriaCascade.wireCategoriaCascade('editRecurrente', categoriaId, subcategoriaId);

    document.getElementById('editRecurrenteModal').classList.add('show');
}

// Función para cerrar el modal
function closeEditModal() {
    document.getElementById('editRecurrenteModal').classList.remove('show');
}

// Event listeners
document.addEventListener('DOMContentLoaded', function() {
    // El tema ya lo aplica fragments/theme-switcher :: theme-script-immediate (en el <head>,
    // antes del primer pintado) resolviendo "system" contra prefers-color-scheme. Fijarlo aquí
    // otra vez con el valor SIN resolver pisaba ese resultado: si la preferencia guardada era
    // literalmente "system", data-theme="system" no coincide con el selector [data-theme="dark"]
    // (que exige coincidencia exacta) y la página caía al tema claro por defecto aunque el
    // sistema estuviera en oscuro.

    // Poblar el selector de cuenta del modal de edición
    const cuentaSelect = document.getElementById('editRecurrenteCuenta');
    if (cuentaSelect) {
        const cuentas = (window.cuentasUsuario || []).filter(c => c.activa !== false);
        // c.nombre es texto libre del usuario: se construye el <option> vía DOM API en vez de
        // interpolarlo en un string HTML, para que nunca pueda romper el marcado (hallazgo M7).
        cuentaSelect.innerHTML = '';
        cuentas.forEach(c => {
            const option = document.createElement('option');
            option.value = c.id;
            option.textContent = c.nombre;
            cuentaSelect.appendChild(option);
        });
    }

    // Si el usuario cambia el Tipo (ingreso/gasto) en el modal de edición, refrescar las categorías disponibles
    const editRecurrenteTipoSelect = document.getElementById('editRecurrenteTipo');
    if (editRecurrenteTipoSelect) {
        editRecurrenteTipoSelect.addEventListener('change', () => {
            window.CategoriaCascade.refreshCategoriaOptionsForTipo('editRecurrente', editRecurrenteTipoSelect.value === 'true' ? 'ingreso' : 'gasto');
        });
    }

    // Event listeners para botones de modificar
    document.querySelectorAll('.btn-modificar').forEach(function(button) {
        button.addEventListener('click', function() {
            openEditModal(this);
        });
    });

    // Cerrar modal con el botón cancelar
    document.getElementById('btnCancelarRecurrente').addEventListener('click', closeEditModal);
    const closeBtn = document.getElementById('closeEditRecurrente');
    if (closeBtn) closeBtn.addEventListener('click', closeEditModal);

    // Cerrar modal haciendo clic fuera
    document.getElementById('editRecurrenteModal').addEventListener('click', function(e) {
        if (e.target === this) {
            closeEditModal();
        }
    });

    // Manejar envío del formulario
    document.getElementById('editRecurrenteForm').addEventListener('submit', function(e) {
        e.preventDefault();

        const formData = new FormData(this);
        const id = formData.get('id');

        const errorEl = document.getElementById('editRecurrenteError');
        if (errorEl) errorEl.style.display = 'none';

        fetch(`/movimientos/recurrentes/modificar/${id}`, {
            method: 'POST',
            headers: window.getCsrfToken ? { 'X-XSRF-TOKEN': window.getCsrfToken() } : {},
            body: formData
        })
            .then(response => {
                if (response.ok) {
                    window.location.reload();
                } else if (errorEl) {
                    errorEl.textContent = 'Error al modificar el movimiento recurrente';
                    errorEl.style.display = 'block';
                }
            })
            .catch(error => {
                console.error('Error:', error);
                if (errorEl) {
                    errorEl.textContent = 'Error al modificar el movimiento recurrente';
                    errorEl.style.display = 'block';
                }
            });
    });

    // --- Modal de confirmación de borrado de recurrente ---
    // El botón "Borrar" ya no es un <a href> (era GET, vulnerable a CSRF vía <img>/link -
    // hallazgo H2 de la auditoría): ahora es un <button type="submit"> dentro de un
    // <form method="post">. Por eso aquí ya no se navega por href, sino que se envía el
    // formulario que contiene al botón pulsado.
    let formularioAEliminar = null;
    document.querySelectorAll('.btn-danger').forEach(function(btn) {
        btn.addEventListener('click', function(e) {
            e.preventDefault();
            formularioAEliminar = this.closest('form');
            document.getElementById('modalConfirmarEliminarRecurrente').classList.add('show');
        });
    });
    // Terminar: mismo patrón que el borrado (el botón vive dentro de un <form method="post">).
    let formularioATerminar = null;
    const modalTerminar = document.getElementById('modalConfirmarTerminarRecurrente');
    document.querySelectorAll('.rec-end').forEach(function(btn) {
        btn.addEventListener('click', function(e) {
            e.preventDefault();
            formularioATerminar = this.closest('form');
            modalTerminar.classList.add('show');
        });
    });
    document.getElementById('cancelarTerminarRecurrente').addEventListener('click', function() {
        modalTerminar.classList.remove('show');
        formularioATerminar = null;
    });
    document.getElementById('confirmarTerminarRecurrente').addEventListener('click', function() {
        if (formularioATerminar) {
            formularioATerminar.requestSubmit();
        }
    });
    modalTerminar.addEventListener('click', function(e) {
        if (e.target === this) {
            this.classList.remove('show');
            formularioATerminar = null;
        }
    });

    document.getElementById('cancelarEliminarRecurrente').addEventListener('click', function() {
        document.getElementById('modalConfirmarEliminarRecurrente').classList.remove('show');
        formularioAEliminar = null;
    });
    document.getElementById('confirmarEliminarRecurrente').addEventListener('click', function() {
        if (formularioAEliminar) {
            formularioAEliminar.requestSubmit();
        }
    });
});