package com.musicstore.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {
    
    private final BCryptPasswordEncoder passwordEncoder;
    
    public PasswordService() {
        this.passwordEncoder = new BCryptPasswordEncoder();
    }
    
    /**
     * Cifra una contraseña usando BCrypt
     * @param rawPassword La contraseña en texto plano
     * @return La contraseña cifrada
     */
    public String encodePassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }
    
    /**
     * Verifica si una contraseña coincide con su versión cifrada
     * @param rawPassword La contraseña en texto plano
     * @param encodedPassword La contraseña cifrada
     * @return true si coinciden, false en caso contrario
     */
    public boolean matches(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
    
    /**
     * Verifica si una contraseña ya está cifrada
     * @param password La contraseña a verificar
     * @return true si ya está cifrada, false si está en texto plano
     */
    public boolean isEncoded(String password) {
        return password != null && password.startsWith("$2a$");
    }
} 