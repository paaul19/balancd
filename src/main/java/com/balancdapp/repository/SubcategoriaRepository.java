package com.balancdapp.repository;

import com.balancdapp.model.Categoria;
import com.balancdapp.model.Subcategoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubcategoriaRepository extends JpaRepository<Subcategoria, Long> {

    List<Subcategoria> findByCategoriaOrderByOrdenAsc(Categoria categoria);

    Optional<Subcategoria> findByCategoriaAndNombreIgnoreCase(Categoria categoria, String nombre);

    boolean existsByCategoriaAndNombreIgnoreCase(Categoria categoria, String nombre);
}
