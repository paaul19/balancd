package com.balancdapp.repository;

import com.balancdapp.model.Categoria;
import com.balancdapp.model.Presupuesto;
import com.balancdapp.model.Subcategoria;
import com.balancdapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PresupuestoRepository extends JpaRepository<Presupuesto, Long> {

    List<Presupuesto> findByUser(User user);

    List<Presupuesto> findByUserAndActivoTrue(User user);

    // --- Soporte para categorías personalizadas: no se puede borrar una categoría/subcategoría en uso ---
    boolean existsByCategoria(Categoria categoria);
    boolean existsBySubcategoria(Subcategoria subcategoria);
}
