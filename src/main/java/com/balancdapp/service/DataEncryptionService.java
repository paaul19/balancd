package com.balancdapp.service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

/**
 * Cifrado de datos sensibles (importes, asuntos, fechas...).
 *
 * Hallazgo H4 de la auditoría: la versión anterior usaba Cipher.getInstance("AES"), que Java
 * resuelve a AES/ECB/PKCS5Padding - un cifrado determinista (el mismo texto claro produce
 * siempre el mismo texto cifrado), lo que filtra patrones de igualdad a quien tenga acceso a
 * la base de datos. Ahora se cifra con AES/GCM/NoPadding: IV aleatorio de 12 bytes por
 * operación (nunca reutilizado con la misma clave) y tag de autenticación de 128 bits, así que
 * dos cifrados del mismo valor son siempre distintos y además detectan manipulación.
 *
 * Formato nuevo:  "GCM:" + Base64(IV[12 bytes] + ciphertext + tag[16 bytes])
 * Formato legacy: Base64(ciphertext) puro, sin prefijo -> AES/ECB (datos ya existentes en BD).
 *
 * decrypt() detecta automáticamente cuál de los dos formatos tiene delante, así que los datos
 * cifrados con la versión anterior se siguen leyendo sin problema; encrypt() escribe siempre en
 * el formato nuevo. La migración explícita de los datos ya existentes (para que dejen de
 * depender del modo legacy) la hace EcbToGcmMigrationService en el arranque.
 */
@Service
public class DataEncryptionService {

    // Sin valor por defecto a propósito, igual que app.jwt.secret (hallazgo C2): la app no debe
    // arrancar con una clave de cifrado conocida/predecible.
    @Value("${app.encryption.key}")
    private String secretKeyString;

    private SecretKey secretKey;
    private static final String GCM_ALGORITHM = "AES/GCM/NoPadding";
    private static final String LEGACY_ECB_ALGORITHM = "AES";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final String GCM_PREFIX = "GCM:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @PostConstruct
    public void init() {
        if (secretKeyString == null || secretKeyString.isBlank()) {
            throw new IllegalStateException(
                "app.encryption.key (variable de entorno APP_ENCRYPTION_KEY) no está configurada. " +
                "La aplicación no puede arrancar sin una clave de cifrado explícita.");
        }
        try {
            byte[] keyBytes = secretKeyString.getBytes("UTF-8");
            if (keyBytes.length < 32) {
                throw new IllegalStateException(
                    "app.encryption.key es demasiado corta (" + keyBytes.length + " bytes); se requieren al menos 32 bytes para AES-256.");
            }
            byte[] key32 = new byte[32];
            System.arraycopy(keyBytes, 0, key32, 0, 32);
            this.secretKey = new SecretKeySpec(key32, "AES");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException("Error inicializando cifrado", e);
        }
    }

    public String encrypt(String plainText) {
        if (plainText == null || plainText.trim().isEmpty()) {
            return plainText;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(GCM_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cipherBytes = cipher.doFinal(plainText.getBytes("UTF-8")); // incluye el tag al final

            byte[] combined = new byte[iv.length + cipherBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherBytes, 0, combined, iv.length, cipherBytes.length);

            return GCM_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Error cifrando datos", e);
        }
    }

    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.trim().isEmpty()) {
            return encryptedText;
        }
        try {
            if (encryptedText.startsWith(GCM_PREFIX)) {
                return decryptGcm(encryptedText.substring(GCM_PREFIX.length()));
            }
            return decryptLegacyEcb(encryptedText);
        } catch (Exception e) {
            throw new RuntimeException("Error descifrando datos", e);
        }
    }

    private String decryptGcm(String base64Payload) throws Exception {
        byte[] combined = Base64.getDecoder().decode(base64Payload);
        byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH_BYTES);
        byte[] cipherBytes = Arrays.copyOfRange(combined, GCM_IV_LENGTH_BYTES, combined.length);

        Cipher cipher = Cipher.getInstance(GCM_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
        byte[] plainBytes = cipher.doFinal(cipherBytes);
        return new String(plainBytes, "UTF-8");
    }

    /** Compatibilidad de lectura con datos cifrados antes de la migración a GCM (hallazgo H4). */
    private String decryptLegacyEcb(String encryptedText) throws Exception {
        Cipher cipher = Cipher.getInstance(LEGACY_ECB_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedText);
        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
        return new String(decryptedBytes, "UTF-8");
    }

    /** @return true si el valor está en el formato legacy AES/ECB (sin el prefijo "GCM:"). */
    public boolean isLegacyFormat(String text) {
        return text != null && !text.isBlank() && !text.startsWith(GCM_PREFIX);
    }

    /**
     * Verifica si un texto está cifrado
     * @param text El texto a verificar
     * @return true si está cifrado, false si no
     */
    public boolean isEncrypted(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        String payload = text.startsWith(GCM_PREFIX) ? text.substring(GCM_PREFIX.length()) : text;
        try {
            Base64.getDecoder().decode(payload);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public String encryptNumber(Double number) {
        if (number == null) {
            return null;
        }
        return encrypt(number.toString());
    }

    public Double decryptNumber(String encryptedNumber) {
        if (encryptedNumber == null || encryptedNumber.trim().isEmpty()) {
            return null;
        }
        try {
            String decrypted = decrypt(encryptedNumber);
            return Double.parseDouble(decrypted);
        } catch (NumberFormatException e) {
            throw new RuntimeException("Error descifrando número", e);
        }
    }
}
