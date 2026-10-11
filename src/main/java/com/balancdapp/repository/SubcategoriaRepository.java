package com.balancdapp.repository;

import com.balancdapp.model.Categoria;
import com.balancdapp.model.Subcategoria;
import com.balancdapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubcategoriaRepository extends JpaRepository<Subcategoria, Long> {

    List<Subcategoria> findByCategoriaOrderByOrdenAsc(Categoria categoria);

    Optional<Subcategoria> findByCategoriaAndNombreIgnoreCase(Categoria categoria, String nombre);

    boolean existsByCategoriaAndNombreIgnoreCase(Categoria categoria, String nombre);

    /** Solo las globales (las del seed), ignorando las que haya añadido cualquier usuario. */
    Optional<Subcategoria> findByCategoriaAndUserIsNullAndNombreIgnoreCase(Categoria categoria, String nombre);

    /** Subcategorías de una categoría visibles para un usuario: las globales + las que ha añadido él. */
    @Query("SELECT s FROM Subcategoria s WHERE s.categoria = :categoria AND (s.user IS NULL OR s.user = :user) ORDER BY s.orden ASC, s.id ASC")
    List<Subcategoria> findVisiblesPorCategoria(@Param("categoria") Categoria categoria, @Param("user") User user);

    @Query("SELECT s FROM Subcategoria s WHERE s.categoria = :categoria AND LOWER(s.nombre) = LOWER(:nombre) AND (s.user IS NULL OR s.user = :user)")
    Optional<Subcategoria> findByCategoriaAndNombreIgnoreCaseVisiblePara(@Param("categoria") Categoria categoria, @Param("nombre") String nombre, @Param("user") User user);
}
