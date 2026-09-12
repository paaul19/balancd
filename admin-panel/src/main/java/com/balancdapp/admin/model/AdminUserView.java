package com.balancdapp.admin.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Vista JPA de la tabla "users" del sitio principal, con solo los campos que este panel
 * necesita mostrar. Deliberadamente NO mapea "password" ni otros campos sensibles/cifrados:
 * este panel nunca hace un save() de la entidad completa (evitaría pisar columnas que no
 * conoce), toda escritura pasa por UPDATE explícitos en AdminUserRepository.
 */
@Entity
@Table(name = "users")
public class AdminUserView {

    @Id
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String email;

    @Column(name = "is_verified", nullable = false)
    private boolean verified;

    @Column(name = "baneado", nullable = false)
    private boolean baneado;

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public boolean isVerified() { return verified; }
    public boolean isBaneado() { return baneado; }
}
