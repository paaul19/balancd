package com.balancdapp.repository;

import com.balancdapp.model.Categoria;
import com.balancdapp.model.TipoCategoria;
import com.balancdapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    List<Categoria> findByTipoOrderByOrdenAsc(TipoCategoria tipo);

    List<Categoria> findAllByOrderByOrdenAsc();

    Optional<Categoria> findByNombreIgnoreCase(String nombre);

    /** Categorías visibles para un usuario: las globales (sin dueño) + las suyas propias. */
    @Query("SELECT c FROM Categoria c WHERE c.tipo = :tipo AND (c.user IS NULL OR c.user = :user) ORDER BY c.orden ASC, c.id ASC")
    List<Categoria> findVisiblesPorTipo(@Param("tipo") TipoCategoria tipo, @Param("user") User user);

    /** Igual que findByNombreIgnoreCase pero sin salirse del alcance del usuario (globales + propias). */
    @Query("SELECT c FROM Categoria c WHERE LOWER(c.nombre) = LOWER(:nombre) AND (c.user IS NULL OR c.user = :user)")
    Optional<Categoria> findByNombreIgnoreCaseVisiblePara(@Param("nombre") String nombre, @Param("user") User user);

    boolean existsByUserAndTipoAndNombreIgnoreCase(User user, TipoCategoria tipo, String nombre);

    List<Categoria> findByUser(User user);
}
