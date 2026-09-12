// perfil.js — modales de la página /perfil (editar identidad y cambiar contraseña).
document.addEventListener('DOMContentLoaded', function () {
    // --- Modal: editar nombre y correo ---
    const modalIdentidad = document.getElementById('modalEditarIdentidad');
    const btnEditarIdentidad = document.getElementById('btnEditarIdentidad');
    const closeIdentidad = document.getElementById('closeEditarIdentidad');
    const cancelarIdentidad = document.getElementById('cancelarEditarIdentidad');

    if (btnEditarIdentidad) {
        btnEditarIdentidad.addEventListener('click', function () {
            modalIdentidad.classList.add('show');
        });
    }
    function cerrarModalIdentidad() {
        modalIdentidad.classList.remove('show');
    }
    if (closeIdentidad) closeIdentidad.addEventListener('click', cerrarModalIdentidad);
    if (cancelarIdentidad) cancelarIdentidad.addEventListener('click', cerrarModalIdentidad);
    if (modalIdentidad) {
        modalIdentidad.addEventListener('click', function (e) {
            if (e.target === modalIdentidad) cerrarModalIdentidad();
        });
    }

    // --- Modal: cambiar contraseña ---
    const modalPassword = document.getElementById('modalCambiarPassword');
    const btnCambiarPassword = document.getElementById('btnCambiarPasswordPerfil');
    const closePassword = document.getElementById('closeCambiarPassword');
    const cancelarPassword = document.getElementById('cancelarCambiarPassword');
    const formPassword = document.getElementById('formCambiarPassword');
    const nuevaInput = document.getElementById('passwordNuevaPerfil');
    const repetirInput = document.getElementById('passwordRepetirPerfil');
    const mismatchError = document.getElementById('passwordMismatchError');

    if (btnCambiarPassword) {
        btnCambiarPassword.addEventListener('click', function () {
            modalPassword.classList.add('show');
        });
    }
    function cerrarModalPassword() {
        modalPassword.classList.remove('show');
        formPassword.reset();
        mismatchError.style.display = 'none';
    }
    if (closePassword) closePassword.addEventListener('click', cerrarModalPassword);
    if (cancelarPassword) cancelarPassword.addEventListener('click', cerrarModalPassword);
    if (modalPassword) {
        modalPassword.addEventListener('click', function (e) {
            if (e.target === modalPassword) cerrarModalPassword();
        });
    }

    // El campo "repetir" es solo validación en el cliente - no se envía al backend
    // (no tiene atributo name), que ya valida longitud mínima por su cuenta.
    if (formPassword) {
        formPassword.addEventListener('submit', function (e) {
            if (nuevaInput.value !== repetirInput.value) {
                e.preventDefault();
                mismatchError.style.display = 'block';
            } else {
                mismatchError.style.display = 'none';
            }
        });
    }
});
