package com.balancdapp.model;

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

    @Column(name = "cantidad_cifrada", nullable = false)
    private String cantidadCifrada; // Cantidad cifrada como string

    @Column(nullable = false)
    private boolean ingreso;

    @Column(name = "asunto_cifrado", nullable = false)
    private String asuntoCifrado; // Asunto cifrado

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

    // Métodos para cantidad (cifrada)
    public String getCantidadCifrada() { return cantidadCifrada; }
    public void setCantidadCifrada(String cantidadCifrada) { this.cantidadCifrada = cantidadCifrada; }

    // Métodos para asunto (cifrado)
    public String getAsuntoCifrado() { return asuntoCifrado; }
    public void setAsuntoCifrado(String asuntoCifrado) { this.asuntoCifrado = asuntoCifrado; }

    // Métodos de compatibilidad (para uso interno del servicio)
    public double getCantidad() {
        // Este método se mantiene para compatibilidad pero no se usa directamente
        return 0.0;
    }
    public void setCantidad(double cantidad) {
        // Este método se mantiene para compatibilidad pero no se usa directamente
    }

    public String getAsunto() {
        // Este método se mantiene para compatibilidad pero no se usa directamente
        return "";
    }
    public void setAsunto(String asunto) {
        // Este método se mantiene para compatibilidad pero no se usa directamente
    }

    public boolean isIngreso() { return ingreso; }
    public void setIngreso(boolean ingreso) { this.ingreso = ingreso; }
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