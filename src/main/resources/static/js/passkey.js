/**
 * passkey.js — helpers WebAuthn compartidos por /login y /perfil.
 *
 * El servidor (librería de Yubico) envía las opciones con los campos binarios en base64url;
 * la API del navegador espera ArrayBuffer, y su respuesta hay que devolverla otra vez en
 * base64url. Se hace la conversión a mano para no depender de parseCreationOptionsFromJSON(),
 * que solo existe en navegadores muy recientes.
 */
(function () {
    function b64urlToBuffer(value) {
        const base64 = value.replace(/-/g, '+').replace(/_/g, '/');
        const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
        const binary = atob(padded);
        const bytes = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
        return bytes.buffer;
    }

    function bufferToB64url(buffer) {
        const bytes = new Uint8Array(buffer);
        let binary = '';
        for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
        return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    }

    async function postJson(url, body) {
        const headers = { 'Content-Type': 'application/json' };
        const token = window.getCsrfToken ? window.getCsrfToken() : null;
        if (token) headers['X-XSRF-TOKEN'] = token;
        const res = await fetch(url, {
            method: 'POST',
            headers: headers,
            credentials: 'same-origin',
            body: body === undefined ? undefined : JSON.stringify(body)
        });
        let data = null;
        try { data = await res.json(); } catch (e) { /* respuesta sin cuerpo JSON */ }
        if (!res.ok) {
            throw new Error((data && data.error) || 'Error inesperado. Inténtalo de nuevo.');
        }
        return data;
    }

    function isSupported() {
        return !!(window.PublicKeyCredential && navigator.credentials && navigator.credentials.create);
    }

    /** Traduce los errores del navegador a mensajes entendibles. null = el usuario canceló. */
    function friendlyError(err) {
        if (!err) return 'Error inesperado.';
        if (err.name === 'NotAllowedError' || err.name === 'AbortError') return null;
        if (err.name === 'InvalidStateError') return 'Este dispositivo ya tiene una passkey vinculada a tu cuenta.';
        if (err.name === 'SecurityError') return 'Las passkeys solo funcionan sobre HTTPS en el dominio de la app.';
        return err.message || 'Error inesperado.';
    }

    async function register(nombre) {
        const options = (await postJson('/perfil/passkeys/opciones')).publicKey;
        options.challenge = b64urlToBuffer(options.challenge);
        options.user.id = b64urlToBuffer(options.user.id);
        (options.excludeCredentials || []).forEach(function (c) { c.id = b64urlToBuffer(c.id); });

        const cred = await navigator.credentials.create({ publicKey: options });
        const credential = {
            id: cred.id,
            rawId: bufferToB64url(cred.rawId),
            type: cred.type,
            response: {
                clientDataJSON: bufferToB64url(cred.response.clientDataJSON),
                attestationObject: bufferToB64url(cred.response.attestationObject),
                transports: cred.response.getTransports ? cred.response.getTransports() : []
            },
            clientExtensionResults: cred.getClientExtensionResults ? cred.getClientExtensionResults() : {}
        };
        return postJson('/perfil/passkeys', { nombre: nombre, credential: credential });
    }

    /** Pide la passkey al dispositivo con las opciones de optionsUrl y envía la firma a finishUrl. */
    async function authenticate(optionsUrl, finishUrl) {
        const options = (await postJson(optionsUrl)).publicKey;
        options.challenge = b64urlToBuffer(options.challenge);
        (options.allowCredentials || []).forEach(function (c) { c.id = b64urlToBuffer(c.id); });

        const cred = await navigator.credentials.get({ publicKey: options });
        const credential = {
            id: cred.id,
            rawId: bufferToB64url(cred.rawId),
            type: cred.type,
            response: {
                clientDataJSON: bufferToB64url(cred.response.clientDataJSON),
                authenticatorData: bufferToB64url(cred.response.authenticatorData),
                signature: bufferToB64url(cred.response.signature),
                userHandle: cred.response.userHandle ? bufferToB64url(cred.response.userHandle) : null
            },
            clientExtensionResults: cred.getClientExtensionResults ? cred.getClientExtensionResults() : {}
        };
        return postJson(finishUrl, credential);
    }

    function login() {
        return authenticate('/login/passkey/opciones', '/login/passkey');
    }

    /** Desbloqueo de la app con una passkey del usuario que ya tiene la sesión iniciada. */
    function unlock() {
        return authenticate('/desbloquear/opciones', '/desbloquear');
    }

    /** Nombre por defecto sugerido para la passkey según el dispositivo actual. */
    function defaultDeviceName() {
        const ua = navigator.userAgent;
        if (/iPhone/.test(ua)) return 'iPhone';
        if (/iPad/.test(ua) || (/Macintosh/.test(ua) && navigator.maxTouchPoints > 1)) return 'iPad';
        if (/Macintosh/.test(ua)) return 'Mac';
        if (/Android/.test(ua)) return 'Android';
        if (/Windows/.test(ua)) return 'Windows';
        return 'Mi dispositivo';
    }

    window.Passkey = {
        isSupported: isSupported,
        register: register,
        login: login,
        unlock: unlock,
        friendlyError: friendlyError,
        defaultDeviceName: defaultDeviceName
    };
})();
