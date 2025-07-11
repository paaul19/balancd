// Función para cambiar tema
function toggleTheme() {
    const html = document.documentElement;
    const currentTheme = html.getAttribute('data-theme');
    const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
    html.setAttribute('data-theme', newTheme);
    localStorage.setItem('theme', newTheme);
}

// Función para abrir el modal de edición
function openEditModal(button) {
    const id = button.getAttribute('data-id');
    const asunto = button.getAttribute('data-asunto');
    const cantidad = button.getAttribute('data-cantidad');
    const ingreso = button.getAttribute('data-ingreso');
    const fechaInicio = button.getAttribute('data-fecha');
    const frecuencia = button.getAttribute('data-frecuencia');
    const categoria = button.getAttribute('data-categoria') || '';

    document.getElementById('editRecurrenteId').value = id;
    document.getElementById('editRecurrenteCantidad').value = cantidad;
    document.getElementById('editRecurrenteAsunto').value = asunto;
    document.getElementById('editRecurrenteTipo').value = ingreso;
    document.getElementById('editRecurrenteFecha').value = fechaInicio;
    document.getElementById('editRecurrenteFrecuencia').value = frecuencia;
    document.getElementById('categoriaRecurrente').value = categoria;

    document.getElementById('editRecurrenteModal').classList.add('show');
}

// Función para cerrar el modal
function closeEditModal() {
    document.getElementById('editRecurrenteModal').classList.remove('show');
}

// Event listeners
document.addEventListener('DOMContentLoaded', function() {
    const savedTheme = localStorage.getItem('theme') || 'dark';
    document.documentElement.setAttribute('data-theme', savedTheme);

    // Event listeners para botones de modificar
    document.querySelectorAll('.btn-modificar').forEach(function(button) {
        button.addEventListener('click', function() {
            openEditModal(this);
        });
    });

    // Cerrar modal con el botón cancelar
    document.getElementById('btnCancelarRecurrente').addEventListener('click', closeEditModal);

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

        fetch(`/movimientos/recurrentes/modificar/${id}`, {
            method: 'POST',
            body: formData
        })
            .then(response => {
                if (response.ok) {
                    window.location.reload();
                } else {
                    alert('Error al modificar el movimiento recurrente');
                }
            })
            .catch(error => {
                console.error('Error:', error);
                alert('Error al modificar el movimiento recurrente');
            });
    });

    // --- Modal de confirmación de borrado de recurrente ---
    let recurrenteAEliminar = null;
    document.querySelectorAll('.btn-danger').forEach(function(btn) {
        btn.addEventListener('click', function(e) {
            e.preventDefault();
            recurrenteAEliminar = this.getAttribute('href');
            document.getElementById('modalConfirmarEliminarRecurrente').classList.add('show');
        });
    });
    document.getElementById('cancelarEliminarRecurrente').addEventListener('click', function() {
        document.getElementById('modalConfirmarEliminarRecurrente').classList.remove('show');
        recurrenteAEliminar = null;
    });
    document.getElementById('confirmarEliminarRecurrente').addEventListener('click', function() {
        if (recurrenteAEliminar) {
            window.location.href = recurrenteAEliminar;
        }
    });
});