package com.balancdapp.model;

import jakarta.persistence.*;

@Entity
@Table(name = "subcategorias", indexes = {
        @Index(name = "idx_subcategorias_categoria", columnList = "categoria_id")
})
public class Subcategoria {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private int orden = 0;

    /** NULL = subcategoría global (o de una categoría propia creada antes de existir esta columna).
     *  Informado = subcategoría que ese usuario ha añadido, solo visible para él. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Categoria getCategoria() { return categoria; }
    public void setCategoria(Categoria categoria) { this.categoria = categoria; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public int getOrden() { return orden; }
    public void setOrden(int orden) { this.orden = orden; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    /** Global o propia de este usuario: la única forma en que alguien puede verla o usarla. */
    public boolean esVisiblePara(User u) {
        return user == null || (u != null && user.getId().equals(u.getId()));
    }
}
