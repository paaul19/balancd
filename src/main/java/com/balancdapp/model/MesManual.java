package com.balancdapp.model;

import jakarta.persistence.*;
import java.time.YearMonth;

@Entity
@Table(name = "meses_manuales", indexes = {
        @Index(name = "idx_meses_manuales_user", columnList = "user_id")
})
public class MesManual {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "anio", nullable = false)
    private int anio;

    @Column(name = "mes", nullable = false)
    private int mes;

    public MesManual() {}

    public MesManual(User user, int anio, int mes) {
        this.user = user;
        this.anio = anio;
        this.mes = mes;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public int getAnio() { return anio; }
    public void setAnio(int anio) { this.anio = anio; }

    public int getMes() { return mes; }
    public void setMes(int mes) { this.mes = mes; }

    public YearMonth getYearMonth() {
        return YearMonth.of(anio, mes);
    }
} 