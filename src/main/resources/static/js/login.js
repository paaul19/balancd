document.addEventListener('DOMContentLoaded', function() {
    const loginForm = document.querySelector('.login-form');
    const registerForm = document.querySelector('.register-form');
    const showRegisterBtn = document.getElementById('showRegister');
    const showLoginBtn = document.getElementById('showLogin');
    const tabs = document.getElementById('authTabs');

    function switchTo(target) {
        const toRegister = target === 'register';
        const from = toRegister ? loginForm : registerForm;
        const to = toRegister ? registerForm : loginForm;
        if (to.classList.contains('active')) return;
        tabs.dataset.active = target;
        showLoginBtn.classList.toggle('active', !toRegister);
        showRegisterBtn.classList.toggle('active', toRegister);
        from.classList.remove('fade-in');
        from.classList.add('fade-out');
        setTimeout(function() {
            from.classList.remove('active', 'fade-out');
            to.classList.add('active', 'fade-in');
        }, 180);
    }

    showRegisterBtn.addEventListener('click', function() { switchTo('register'); });
    showLoginBtn.addEventListener('click', function() { switchTo('login'); });

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
