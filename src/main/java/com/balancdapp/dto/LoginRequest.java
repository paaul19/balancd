package com.balancdapp.dto;

/**
 * Payload de login. Igual que RegisterRequest, evita bindear la entidad User
 * completa desde un formulario/request no confiable.
 */
public class LoginRequest {
    private String username;
    private String password;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
