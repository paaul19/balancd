package com.balancdapp.repository;

import com.balancdapp.model.Categoria;
import com.balancdapp.model.Cuenta;
import com.balancdapp.model.MovimientoRecurrente;
import com.balancdapp.model.Subcategoria;
import com.balancdapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MovimientoRecurrenteRepository extends JpaRepository<MovimientoRecurrente, Long> {
    List<MovimientoRecurrente> findByUser(User user);
    List<MovimientoRecurrente> findByActivoTrue();

    // --- Soporte para el sistema de cuentas ---
    List<MovimientoRecurrente> findByUserAndCuentaIsNull(User user);
    boolean existsByCuenta(Cuenta cuenta);

    // --- Soporte para categorías personalizadas: no se puede borrar una categoría/subcategoría en uso ---
    boolean existsByCategoria(Categoria categoria);
    boolean existsBySubcategoria(Subcategoria subcategoria);

    // --- Soporte para la migración del sistema de categorías legado ---
    @Query(value = "SELECT id, categoria FROM movimientos_recurrentes WHERE categoria IS NOT NULL AND categoria_id IS NULL", nativeQuery = true)
    List<Object[]> findLegacyCategoriaRows();

    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query(value = "UPDATE movimientos_recurrentes SET categoria_id = :catId, subcategoria_id = :subId WHERE id = :id", nativeQuery = true)
    void actualizarCategoriaMigrada(@Param("id") Long id, @Param("catId") Long categoriaId, @Param("subId") Long subcategoriaId);
}