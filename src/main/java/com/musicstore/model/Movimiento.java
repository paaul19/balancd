package com.musicstore.model;

import java.time.LocalDate;

public class Movimiento {
    private Long id;
    private Long userId;
    private double cantidad;
    private boolean ingreso; // true = ingreso, false = gasto
    private String asunto; // Texto libre
    private LocalDate fecha;
    private int mesAsignado; // mes lógico asignado (1-12)
    private int anioAsignado; // año lógico asignado

    public Movimiento() {}
    public Movimiento(Long id, Long userId, double cantidad, boolean ingreso, String asunto) {
        this.id = id;
        this.userId = userId;
        this.cantidad = cantidad;
        this.ingreso = ingreso;
        this.asunto = asunto;
    }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public double getCantidad() { return cantidad; }
    public void setCantidad(double cantidad) { this.cantidad = cantidad; }
    public boolean isIngreso() { return ingreso; }
    public void setIngreso(boolean ingreso) { this.ingreso = ingreso; }
    public String getAsunto() { return asunto; }
    public void setAsunto(String asunto) { this.asunto = asunto; }
    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }
    public int getMesAsignado() {
        return mesAsignado;
    }
    public void setMesAsignado(int mesAsignado) {
        this.mesAsignado = mesAsignado;
    }
    public int getAnioAsignado() {
        return anioAsignado;
    }
    public void setAnioAsignado(int anioAsignado) {
        this.anioAsignado = anioAsignado;
    }
}