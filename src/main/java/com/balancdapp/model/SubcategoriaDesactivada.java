package com.balancdapp.model;

import jakarta.persistence.*;

/** Existe una fila = esa subcategoría (global o propia) está oculta para ese usuario. */
@Entity
@Table(name = "subcategorias_desactivadas", indexes = {
        @Index(name = "idx_subcategorias_desactivadas_user", columnList = "user_id")
})
public class SubcategoriaDesactivada {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subcategoria_id", nullable = false)
    private Subcategoria subcategoria;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Subcategoria getSubcategoria() { return subcategoria; }
    public void setSubcategoria(Subcategoria subcategoria) { this.subcategoria = subcategoria; }
}
