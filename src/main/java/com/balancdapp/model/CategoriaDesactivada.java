package com.balancdapp.model;

import jakarta.persistence.*;

/** Existe una fila = esa categoría (global o propia) está oculta para ese usuario. */
@Entity
@Table(name = "categorias_desactivadas", indexes = {
        @Index(name = "idx_categorias_desactivadas_user", columnList = "user_id")
})
public class CategoriaDesactivada {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Categoria getCategoria() { return categoria; }
    public void setCategoria(Categoria categoria) { this.categoria = categoria; }
}
