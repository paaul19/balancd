package com.balancdapp.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;
import com.balancdapp.model.Movimiento;
import java.math.BigDecimal;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    @NotBlank(message = "Username is required")
    private String username;

    @Column(unique = true, nullable = false)
    @NotBlank(message = "Email is required")
    private String email;

    @Column(nullable = false)
    @NotBlank(message = "Password is required")
    private String password;

    @Column(name = "is_verified", nullable = false)
    private boolean isVerified = false;

    @Column(name = "tutorial_visto", nullable = false)
    private boolean tutorialVisto = false;

    @Column(name = "balance_total", nullable = true)
    private BigDecimal balanceTotal;

    /** Divisa preferida del usuario (solo preferencia visual guardada; no recalcula importes). */
    @Column(name = "moneda", nullable = false)
    private String moneda = "EUR";

    /** Gestionado desde el panel de administración externo (puerto 9093). */
    /** Identificador estable del usuario en Apple ("sub" del identity token); null si nunca usó Sign in with Apple. */
    @Column(name = "apple_sub", unique = true)
    private String appleSub;

    @Column(name = "baneado", nullable = false)
    private boolean baneado = false;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<Movimiento> movimientos;

    // Getters y setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public boolean isVerified() { return isVerified; }
    public void setVerified(boolean isVerified) { this.isVerified = isVerified; }

    public boolean isTutorialVisto() { return tutorialVisto; }
    public void setTutorialVisto(boolean tutorialVisto) { this.tutorialVisto = tutorialVisto; }

    public BigDecimal getBalanceTotal() { return balanceTotal; }
    public void setBalanceTotal(BigDecimal balanceTotal) { this.balanceTotal = balanceTotal; }

    public String getMoneda() { return moneda; }
    public void setMoneda(String moneda) { this.moneda = moneda; }

    public String getAppleSub() { return appleSub; }
    public void setAppleSub(String appleSub) { this.appleSub = appleSub; }

    public boolean isBaneado() { return baneado; }
    public void setBaneado(boolean baneado) { this.baneado = baneado; }

    public List<Movimiento> getMovimientos() { return movimientos; }
    public void setMovimientos(List<Movimiento> movimientos) { this.movimientos = movimientos; }
}