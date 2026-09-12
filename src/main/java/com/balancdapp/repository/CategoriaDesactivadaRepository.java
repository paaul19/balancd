package com.balancdapp.repository;

import com.balancdapp.model.Categoria;
import com.balancdapp.model.CategoriaDesactivada;
import com.balancdapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface CategoriaDesactivadaRepository extends JpaRepository<CategoriaDesactivada, Long> {

    @Query("SELECT cd.categoria.id FROM CategoriaDesactivada cd WHERE cd.user = :user")
    Set<Long> findCategoriaIdsDesactivadasPorUsuario(@Param("user") User user);

    Optional<CategoriaDesactivada> findByUserAndCategoria(User user, Categoria categoria);

    boolean existsByUserAndCategoria(User user, Categoria categoria);

    List<CategoriaDesactivada> findByCategoria(Categoria categoria);
}
