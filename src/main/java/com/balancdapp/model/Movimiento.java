package com.balancdapp.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "movimientos", indexes = {
        @Index(name = "idx_movimientos_user", columnList = "user_id"),
        @Index(name = "idx_movimientos_mes_anio", columnList = "mes_asignado, anio_asignado")
})
public class Movimiento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "cantidad_cifrada", nullable = false)
    private String cantidadCifrada; // Cantidad cifrada como string

    @Column(nullable = false)
    private boolean ingreso; // true = ingreso, false = gasto

    @Column(name = "asunto_cifrado", nullable = false)
    private String asuntoCifrado; // Asunto cifrado

    @Column(name = "fecha_cifrada", nullable = false)
    private String fechaCifrada; // Fecha cifrada como string

    @Column(name = "mes_asignado", nullable = false)
    private int mesAsignado; // mes lógico asignado (1-12) - NO cifrado para consultas

    @Column(name = "anio_asignado", nullable = false)
    private int anioAsignado; // año lógico asignado - NO cifrado para consultas

    public Movimiento() {}

    public Movimiento(Long id, User user, String cantidadCifrada, boolean ingreso, String asuntoCifrado, String fechaCifrada, int mesAsignado, int anioAsignado) {
        this.id = id;
        this.user = user;
        this.cantidadCifrada = cantidadCifrada;
        this.ingreso = ingreso;
        this.asuntoCifrado = asuntoCifrado;
        this.fechaCifrada = fechaCifrada;
        this.mesAsignado = mesAsignado;
        this.anioAsignado = anioAsignado;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Long getUserId() {
        return user != null ? user.getId() : null;
    }

    public void setUserId(Long userId) {
        // Este método se mantiene para compatibilidad con el código existente
        // pero no se usa en la nueva implementación
    }

    // Métodos para cantidad (cifrada)
    public String getCantidadCifrada() { return cantidadCifrada; }
    public void setCantidadCifrada(String cantidadCifrada) { this.cantidadCifrada = cantidadCifrada; }

    // Métodos para asunto (cifrado)
    public String getAsuntoCifrado() { return asuntoCifrado; }
    public void setAsuntoCifrado(String asuntoCifrado) { this.asuntoCifrado = asuntoCifrado; }

    // Métodos para fecha (cifrada)
    public String getFechaCifrada() { return fechaCifrada; }
    public void setFechaCifrada(String fechaCifrada) { this.fechaCifrada = fechaCifrada; }

    public boolean isIngreso() { return ingreso; }
    public void setIngreso(boolean ingreso) { this.ingreso = ingreso; }

    public String getAsunto() {
        // Este método se mantiene para compatibilidad pero no se usa directamente
        return "";
    }
    public void setAsunto(String asunto) {
        // Este método se mantiene para compatibilidad pero no se usa directamente
    }

    public LocalDate getFecha() {
        // Este método se mantiene para compatibilidad pero no se usa directamente
        return LocalDate.now();
    }
    public void setFecha(LocalDate fecha) {
        // Este método se mantiene para compatibilidad pero no se usa directamente
    }

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