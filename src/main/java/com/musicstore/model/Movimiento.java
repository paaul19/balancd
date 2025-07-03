package com.musicstore.model;

import java.time.LocalDate;

public class Movimiento {
    private Long id;
    private Long userId;
    private double cantidad;
    private boolean ingreso; // true = ingreso, false = gasto
    private Long asuntoId;
    private LocalDate fecha;

    public Movimiento() {}
    public Movimiento(Long id, Long userId, double cantidad, boolean ingreso) {
        this.id = id;
        this.userId = userId;
        this.cantidad = cantidad;
        this.ingreso = ingreso;
    }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public double getCantidad() { return cantidad; }
    public void setCantidad(double cantidad) { this.cantidad = cantidad; }
    public boolean isIngreso() { return ingreso; }
    public void setIngreso(boolean ingreso) { this.ingreso = ingreso; }
    public Long getAsuntoId() { return asuntoId; }
    public void setAsuntoId(Long asuntoId) { this.asuntoId = asuntoId; }
    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }
}