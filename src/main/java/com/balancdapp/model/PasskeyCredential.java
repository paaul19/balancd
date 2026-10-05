package com.balancdapp.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Passkey (credencial WebAuthn) vinculada a un usuario. Solo se guarda la clave PÚBLICA:
 * la privada nunca sale del dispositivo (llavero de iCloud, Google Password Manager...).
 */
@Entity
@Table(name = "passkey_credentials")
public class PasskeyCredential {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Identificador de la credencial generado por el autenticador, en base64url. */
    @Column(name = "credential_id", nullable = false, unique = true, length = 512)
    private String credentialId;

    /** Clave pública en formato COSE, en base64url. */
    @Column(name = "public_key_cose", nullable = false, columnDefinition = "TEXT")
    private String publicKeyCose;

    @Column(name = "signature_count", nullable = false)
    private long signatureCount;

    /** Nombre descriptivo para que el usuario la reconozca en su perfil (p. ej. "iPhone"). */
    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getCredentialId() { return credentialId; }
    public void setCredentialId(String credentialId) { this.credentialId = credentialId; }

    public String getPublicKeyCose() { return publicKeyCose; }
    public void setPublicKeyCose(String publicKeyCose) { this.publicKeyCose = publicKeyCose; }

    public long getSignatureCount() { return signatureCount; }
    public void setSignatureCount(long signatureCount) { this.signatureCount = signatureCount; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(LocalDateTime lastUsedAt) { this.lastUsedAt = lastUsedAt; }
}
