package com.balancdapp.service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

@Service
public class DataEncryptionService {

    @Value("${app.encryption.key:defaultSecretKey123456789012345678901234}")
    private String secretKeyString;

    private SecretKey secretKey;
    private static final String ALGORITHM = "AES";

    @PostConstruct
    public void init() {
        try {
            // Usar la clave configurada o generar una nueva
            byte[] keyBytes = secretKeyString.getBytes("UTF-8");
            // Asegurar que la clave tenga 32 bytes para AES-256
            byte[] paddedKey = new byte[32];
            System.arraycopy(keyBytes, 0, paddedKey, 0, Math.min(keyBytes.length, 32));
            this.secretKey = new SecretKeySpec(paddedKey, ALGORITHM);
        } catch (Exception e) {
            throw new RuntimeException("Error inicializando cifrado", e);
        }
    }

    /**
     * Cifra un texto usando AES
     * @param plainText El texto a cifrar
     * @return El texto cifrado en Base64
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.trim().isEmpty()) {
            return plainText;
        }

        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("Error cifrando datos", e);
        }
    }

    /**
     * Descifra un texto cifrado con AES
     * @param encryptedText El texto cifrado en Base64
     * @return El texto original
     */
    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.trim().isEmpty()) {
            return encryptedText;
        }

        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedText);
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, "UTF-8");
        } catch (Exception e) {
            throw new RuntimeException("Error descifrando datos", e);
        }
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

        try {
            // Intentar decodificar Base64
            Base64.getDecoder().decode(text);
            // Si no lanza excepción, probablemente está cifrado
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Cifra un número como string
     * @param number El número a cifrar
     * @return El número cifrado como string
     */
    public String encryptNumber(Double number) {
        if (number == null) {
            return null;
        }
        return encrypt(number.toString());
    }

    /**
     * Descifra un número
     * @param encryptedNumber El número cifrado como string
     * @return El número original
     */
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