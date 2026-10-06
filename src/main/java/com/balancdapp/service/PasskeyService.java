package com.balancdapp.service;

import com.balancdapp.model.PasskeyCredential;
import com.balancdapp.model.User;
import com.balancdapp.repository.PasskeyCredentialRepository;
import com.balancdapp.repository.UserRepository;
import com.yubico.webauthn.*;
import com.yubico.webauthn.data.*;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.data.exception.Base64UrlException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Registro y login con passkeys (WebAuthn) usando la librería de Yubico.
 *
 * Flujo (tanto en registro como en login):
 *   1) start*: el servidor genera un reto aleatorio y unas opciones que el navegador pasa a
 *      navigator.credentials.create()/get(). Las opciones se guardan en la sesión HTTP.
 *   2) finish*: el navegador devuelve la respuesta firmada por el dispositivo (tras Face ID /
 *      Touch ID) y el servidor la verifica contra las opciones guardadas: reto, origen, rpId,
 *      firma y verificación de usuario.
 *
 * El "user handle" de WebAuthn es el id numérico del usuario en 8 bytes: es estable aunque
 * cambie el username o el email, y no contiene datos personales.
 */
@Service
public class PasskeyService {

    private final PasskeyCredentialRepository passkeyRepository;
    private final UserRepository userRepository;
    private final RelyingParty relyingParty;

    public PasskeyService(PasskeyCredentialRepository passkeyRepository,
                          UserRepository userRepository,
                          @Value("${app.webauthn.rp-id:localhost}") String rpId,
                          @Value("${app.webauthn.origins:https://balancd.es,http://localhost:8080,http://localhost:8081}") String origins) {
        this.passkeyRepository = passkeyRepository;
        this.userRepository = userRepository;
        Set<String> allowedOrigins = Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        this.relyingParty = RelyingParty.builder()
                .identity(RelyingPartyIdentity.builder().id(rpId).name("balanc*d").build())
                .credentialRepository(new JpaCredentialRepository())
                .origins(allowedOrigins)
                .build();
    }

    // ---------------------------------------------------------------- Registro

    /** Devuelve las opciones de creación. {@code toJson()} va a la sesión y {@code toCredentialsCreateJson()} al navegador. */
    public PublicKeyCredentialCreationOptions startRegistration(User user) {
        return relyingParty.startRegistration(StartRegistrationOptions.builder()
                .user(UserIdentity.builder()
                        .name(user.getEmail())
                        .displayName(user.getUsername())
                        .id(toUserHandle(user.getId()))
                        .build())
                .authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                        // Credencial "descubrible": permite iniciar sesión sin escribir usuario.
                        .residentKey(ResidentKeyRequirement.REQUIRED)
                        // Obliga a Face ID / Touch ID / PIN, no basta con "tener" el dispositivo.
                        .userVerification(UserVerificationRequirement.REQUIRED)
                        .build())
                .timeout(120_000L)
                .build());
    }

    @Transactional
    public PasskeyCredential finishRegistration(User user, String requestJson, String responseJson, String nombre)
            throws IOException, RegistrationFailedException {
        PublicKeyCredentialCreationOptions request = PublicKeyCredentialCreationOptions.fromJson(requestJson);
        // Comprobación defensiva: las opciones de la sesión deben ser de este mismo usuario.
        if (!request.getUser().getId().equals(toUserHandle(user.getId()))) {
            throw new IllegalStateException("La petición de registro no corresponde a este usuario.");
        }
        PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> pkc =
                PublicKeyCredential.parseRegistrationResponseJson(responseJson);
        RegistrationResult result = relyingParty.finishRegistration(FinishRegistrationOptions.builder()
                .request(request)
                .response(pkc)
                .build());

        PasskeyCredential credential = new PasskeyCredential();
        credential.setUserId(user.getId());
        credential.setCredentialId(result.getKeyId().getId().getBase64Url());
        credential.setPublicKeyCose(result.getPublicKeyCose().getBase64Url());
        credential.setSignatureCount(result.getSignatureCount());
        credential.setNombre(sanitizeNombre(nombre));
        return passkeyRepository.save(credential);
    }

    // ---------------------------------------------------------------- Login

    /** Login "sin usuario": no se indica cuenta, el dispositivo ofrece las passkeys que tenga para este dominio. */
    public AssertionRequest startAssertion() {
        return relyingParty.startAssertion(StartAssertionOptions.builder()
                .userVerification(UserVerificationRequirement.REQUIRED)
                .timeout(120_000L)
                .build());
    }

    /** Desbloqueo de la app: solo se aceptan las passkeys del usuario ya identificado en la sesión. */
    public AssertionRequest startAssertion(User user) {
        return relyingParty.startAssertion(StartAssertionOptions.builder()
                .username(user.getEmail())
                .userVerification(UserVerificationRequirement.REQUIRED)
                .timeout(120_000L)
                .build());
    }

    /** Verifica la firma y devuelve el usuario dueño de la passkey, o vacío si la verificación falla. */
    @Transactional
    public Optional<User> finishAssertion(String requestJson, String responseJson) throws IOException {
        AssertionRequest request = AssertionRequest.fromJson(requestJson);
        PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> pkc =
                PublicKeyCredential.parseAssertionResponseJson(responseJson);
        AssertionResult result;
        try {
            result = relyingParty.finishAssertion(FinishAssertionOptions.builder()
                    .request(request)
                    .response(pkc)
                    .build());
        } catch (AssertionFailedException e) {
            return Optional.empty();
        }
        if (!result.isSuccess()) {
            return Optional.empty();
        }

        String credentialId = result.getCredential().getCredentialId().getBase64Url();
        passkeyRepository.findByCredentialId(credentialId).ifPresent(c -> {
            c.setSignatureCount(result.getSignatureCount());
            c.setLastUsedAt(LocalDateTime.now());
            passkeyRepository.save(c);
        });
        return fromUserHandle(result.getCredential().getUserHandle()).flatMap(userRepository::findById);
    }

    // ---------------------------------------------------------------- Gestión desde el perfil

    public List<PasskeyCredential> listForUser(Long userId) {
        return passkeyRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public boolean delete(Long userId, Long passkeyId) {
        return passkeyRepository.findByIdAndUserId(passkeyId, userId)
                .map(c -> { passkeyRepository.delete(c); return true; })
                .orElse(false);
    }

    // ---------------------------------------------------------------- Utilidades

    private static String sanitizeNombre(String nombre) {
        String limpio = nombre == null ? "" : nombre.trim();
        if (limpio.isEmpty()) limpio = "Passkey";
        return limpio.length() > 100 ? limpio.substring(0, 100) : limpio;
    }

    private static ByteArray toUserHandle(Long userId) {
        return new ByteArray(ByteBuffer.allocate(Long.BYTES).putLong(userId).array());
    }

    private static Optional<Long> fromUserHandle(ByteArray handle) {
        byte[] bytes = handle.getBytes();
        if (bytes.length != Long.BYTES) return Optional.empty();
        return Optional.of(ByteBuffer.wrap(bytes).getLong());
    }

    private static ByteArray fromBase64Url(String value) {
        try {
            return ByteArray.fromBase64Url(value);
        } catch (Base64UrlException e) {
            throw new IllegalStateException("Credencial almacenada con formato inválido", e);
        }
    }

    private RegisteredCredential toRegistered(PasskeyCredential c) {
        return RegisteredCredential.builder()
                .credentialId(fromBase64Url(c.getCredentialId()))
                .userHandle(toUserHandle(c.getUserId()))
                .publicKeyCose(fromBase64Url(c.getPublicKeyCose()))
                .signatureCount(c.getSignatureCount())
                .build();
    }

    /**
     * Puente entre la librería de Yubico y la base de datos. El "username" que maneja la
     * librería es el email del usuario (es lo que se pasa como UserIdentity.name).
     */
    private class JpaCredentialRepository implements CredentialRepository {
        @Override
        public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
            return userRepository.findByEmail(username)
                    .map(u -> passkeyRepository.findByUserIdOrderByCreatedAtDesc(u.getId()).stream()
                            .map(c -> PublicKeyCredentialDescriptor.builder()
                                    .id(fromBase64Url(c.getCredentialId()))
                                    .build())
                            .collect(Collectors.toSet()))
                    .orElse(Collections.emptySet());
        }

        @Override
        public Optional<ByteArray> getUserHandleForUsername(String username) {
            return userRepository.findByEmail(username).map(u -> toUserHandle(u.getId()));
        }

        @Override
        public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
            return fromUserHandle(userHandle).flatMap(userRepository::findById).map(User::getEmail);
        }

        @Override
        public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
            return passkeyRepository.findByCredentialId(credentialId.getBase64Url())
                    .filter(c -> toUserHandle(c.getUserId()).equals(userHandle))
                    .map(PasskeyService.this::toRegistered);
        }

        @Override
        public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
            return passkeyRepository.findByCredentialId(credentialId.getBase64Url())
                    .map(PasskeyService.this::toRegistered)
                    .map(Set::of)
                    .orElse(Collections.emptySet());
        }
    }
}
