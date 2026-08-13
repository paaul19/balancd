document.addEventListener('DOMContentLoaded', function() {
    const loginForm = document.querySelector('.login-form');
    const registerForm = document.querySelector('.register-form');
    const showRegisterBtn = document.getElementById('showRegister');
    const showLoginBtn = document.getElementById('showLogin');
    const loginIndicator = document.getElementById('loginIndicator');
    const registerIndicator = document.getElementById('registerIndicator');

    // Mostrar formulario de registro
    showRegisterBtn.addEventListener('click', function() {
        loginForm.classList.remove('fade-in');
        loginForm.classList.add('fade-out');
        setTimeout(function() {
            loginForm.classList.remove('active', 'fade-out');
            registerForm.classList.add('active', 'fade-in');
            loginIndicator.style.display = 'none';
            registerIndicator.style.display = 'block';
            showRegisterBtn.style.display = 'none';
            showLoginBtn.style.display = 'block';
        }, 250);
    });

    // Mostrar formulario de login
    showLoginBtn.addEventListener('click', function() {
        registerForm.classList.remove('fade-in');
        registerForm.classList.add('fade-out');
        setTimeout(function() {
            registerForm.classList.remove('active', 'fade-out');
            loginForm.classList.add('active', 'fade-in');
            registerIndicator.style.display = 'none';
            loginIndicator.style.display = 'block';
            showLoginBtn.style.display = 'none';
            showRegisterBtn.style.display = 'block';
        }, 250);
    });
});