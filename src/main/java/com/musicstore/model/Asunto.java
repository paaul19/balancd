package com.musicstore.model;

public class Asunto {
    private Long id;
    private Long userId;
    private String nombre;

    public Asunto() {}
    public Asunto(Long id, Long userId, String nombre) {
        this.id = id;
        this.userId = userId;
        this.nombre = nombre;
    }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
} 