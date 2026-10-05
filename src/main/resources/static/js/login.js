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

    // Login con passkey: solo se muestra si el navegador soporta WebAuthn.
    const passkeyLogin = document.getElementById('passkeyLogin');
    const btnPasskey = document.getElementById('btnPasskeyLogin');
    const passkeyError = document.getElementById('passkeyLoginError');
    if (passkeyLogin && window.Passkey && Passkey.isSupported()) {
        passkeyLogin.hidden = false;
        btnPasskey.addEventListener('click', async function () {
            passkeyError.hidden = true;
            btnPasskey.disabled = true;
            try {
                const result = await Passkey.login();
                window.location.href = result.redirect || '/movimientos';
            } catch (err) {
                const msg = Passkey.friendlyError(err);
                if (msg) {
                    passkeyError.textContent = msg;
                    passkeyError.hidden = false;
                }
                btnPasskey.disabled = false;
            }
        });
    }
});
