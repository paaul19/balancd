package com.balancdapp.repository;

import com.balancdapp.model.Subcategoria;
import com.balancdapp.model.SubcategoriaDesactivada;
import com.balancdapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface SubcategoriaDesactivadaRepository extends JpaRepository<SubcategoriaDesactivada, Long> {

    @Query("SELECT sd.subcategoria.id FROM SubcategoriaDesactivada sd WHERE sd.user = :user")
    Set<Long> findSubcategoriaIdsDesactivadasPorUsuario(@Param("user") User user);

    Optional<SubcategoriaDesactivada> findByUserAndSubcategoria(User user, Subcategoria subcategoria);

    boolean existsByUserAndSubcategoria(User user, Subcategoria subcategoria);

    List<SubcategoriaDesactivada> findBySubcategoria(Subcategoria subcategoria);
}
