package com.musicstore.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "movimientos_recurrentes")
public class MovimientoRecurrente {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private double cantidad;

    @Column(nullable = false)
    private boolean ingreso;

    @Column(nullable = false)
    private String asunto;

    @Column(nullable = false)
    private LocalDate fechaInicio;

    @Column(nullable = false)
    private String frecuencia; // semanal, bi-semanal, mensual, bi-mensual

    private LocalDate fechaFin; // opcional

    @Column(nullable = false)
    private boolean activo = true;

    private LocalDate ultimaFechaEjecutada;

    // Getters y setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public double getCantidad() { return cantidad; }
    public void setCantidad(double cantidad) { this.cantidad = cantidad; }
    public boolean isIngreso() { return ingreso; }
    public void setIngreso(boolean ingreso) { this.ingreso = ingreso; }
    public String getAsunto() { return asunto; }
    public void setAsunto(String asunto) { this.asunto = asunto; }
    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }
    public String getFrecuencia() { return frecuencia; }
    public void setFrecuencia(String frecuencia) { this.frecuencia = frecuencia; }
    public LocalDate getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDate fechaFin) { this.fechaFin = fechaFin; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public LocalDate getUltimaFechaEjecutada() { return ultimaFechaEjecutada; }
    public void setUltimaFechaEjecutada(LocalDate ultimaFechaEjecutada) { this.ultimaFechaEjecutada = ultimaFechaEjecutada; }
} 