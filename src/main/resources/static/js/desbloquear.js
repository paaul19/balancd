/**
 * desbloquear.js — pide la passkey nada más abrir /desbloquear (al reabrir la app).
 * Si el navegador no permite lanzarla sin un toque del usuario, o se cancela, queda el botón.
 */
document.addEventListener('DOMContentLoaded', function () {
    const btn = document.getElementById('btnUnlock');
    const errorBox = document.getElementById('unlockError');

    if (!window.Passkey || !Passkey.isSupported()) {
        btn.disabled = true;
        errorBox.textContent = 'Este navegador no es compatible con passkeys. Cierra sesión y entra con tu contraseña.';
        errorBox.hidden = false;
        return;
    }

    let enCurso = false;
    async function desbloquear() {
        if (enCurso) return;
        enCurso = true;
        errorBox.hidden = true;
        btn.disabled = true;
        try {
            const result = await Passkey.unlock();
            window.location.replace(result.redirect || '/movimientos');
        } catch (err) {
            const msg = Passkey.friendlyError(err);
            if (msg) {
                errorBox.textContent = msg;
                errorBox.hidden = false;
            }
            btn.disabled = false;
            enCurso = false;
        }
    }

    btn.addEventListener('click', desbloquear);
    desbloquear();
});
