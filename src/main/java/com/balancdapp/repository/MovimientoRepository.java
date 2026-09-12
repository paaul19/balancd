package com.balancdapp.repository;

import com.balancdapp.model.Categoria;
import com.balancdapp.model.Cuenta;
import com.balancdapp.model.Movimiento;
import com.balancdapp.model.Subcategoria;
import com.balancdapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

@Repository
public interface MovimientoRepository extends JpaRepository<Movimiento, Long> {

    List<Movimiento> findByUser(User user);

    List<Movimiento> findByUserAndMesAsignadoAndAnioAsignado(User user, int mes, int anio);

    @Query("SELECT DISTINCT m.anioAsignado, m.mesAsignado FROM Movimiento m WHERE m.user = :user")
    List<Object[]> findDistinctYearMonthsByUser(@Param("user") User user);

    boolean existsByUserAndMesAsignadoAndAnioAsignado(User user, int mes, int anio);

    boolean existsByUserAndMesAsignadoAndAnioAsignadoAndCantidadCifradaAndIngresoAndAsuntoCifradoAndFechaCifrada(User user, int mesAsignado, int anioAsignado, String cantidadCifrada, boolean ingreso, String asuntoCifrado, String fechaCifrada);

    @Modifying
    @Query("DELETE FROM Movimiento m WHERE m.user = :user AND m.id = :id")
    void deleteByUserAndId(@Param("user") User user, @Param("id") Long id);

    // --- Soporte para el sistema de cuentas ---
    List<Movimiento> findByUserAndCuentaIsNull(User user);

    List<Movimiento> findByCuenta(Cuenta cuenta);

    boolean existsByCuenta(Cuenta cuenta);

    // --- Soporte para categorías personalizadas: no se puede borrar una categoría/subcategoría en uso ---
    boolean existsByCategoria(Categoria categoria);
    boolean existsBySubcategoria(Subcategoria subcategoria);

    // --- Soporte para la migración del sistema de categorías legado ---
    @Query(value = "SELECT id, categoria FROM movimientos WHERE categoria IS NOT NULL AND categoria_id IS NULL", nativeQuery = true)
    List<Object[]> findLegacyCategoriaRows();

    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query(value = "UPDATE movimientos SET categoria_id = :catId, subcategoria_id = :subId WHERE id = :id", nativeQuery = true)
    void actualizarCategoriaMigrada(@Param("id") Long id, @Param("catId") Long categoriaId, @Param("subId") Long subcategoriaId);
}