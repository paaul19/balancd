package com.balancdapp.dto;

/**
 * Payload de registro. Deliberadamente NO expone "id" ni ningún otro campo de
 * la entidad User: vincular la entidad JPA directamente desde el formulario
 * (@ModelAttribute User) permitía a un atacante enviar id=<víctima> y
 * sobrescribir una cuenta existente en lugar de crear una nueva.
 */
public class RegisterRequest {
    private String username;
    private String email;
    private String password;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
