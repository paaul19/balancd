package com.balancdapp.service;

import com.balancdapp.model.Categoria;
import com.balancdapp.model.Subcategoria;
import com.balancdapp.repository.CategoriaRepository;
import com.balancdapp.repository.MovimientoRecurrenteRepository;
import com.balancdapp.repository.MovimientoRepository;
import com.balancdapp.repository.SubcategoriaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Migración idempotente de la columna legada "categoria" (enum antiguo guardado como VARCHAR)
 * a las nuevas columnas categoria_id/subcategoria_id. La columna vieja no se toca ni se borra,
 * se conserva como copia de seguridad. Toda fila migrada recibe la subcategoría "Otros" porque
 * no existe forma segura de inferir una subcategoría concreta desde el dato antiguo sin inventar
 * información. Se ejecuta después de CategoriaSeedService (@Order(5)).
 */
@Service
@Order(6)
public class CategoriaMigrationService implements CommandLineRunner {

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private SubcategoriaRepository subcategoriaRepository;

    @Autowired
    private MovimientoRepository movimientoRepository;

    @Autowired
    private MovimientoRecurrenteRepository movimientoRecurrenteRepository;

    private static final Map<String, String> MAPEO = new HashMap<>();
    static {
        MAPEO.put("TRANSPORTE", "Transporte");
        MAPEO.put("COMIDA", "Alimentación");
        MAPEO.put("OCIO_ENTRETENIMIENTO", "Ocio y entretenimiento");
        MAPEO.put("HOGAR", "Vivienda");
        MAPEO.put("SALUD_BIENESTAR", "Salud y bienestar");
        MAPEO.put("EDUCACION_CURSOS", "Educación");
        MAPEO.put("COMPRAS", "Compras");
        MAPEO.put("COMPRAS_ONLINE", "Compras");
        MAPEO.put("SUSCRIPCION", "Suscripciones");
    }

    @Override
    public void run(String... args) {
        migrar();
    }

    @Transactional
    public void migrar() {
        int migrados = migrarMovimientos() + migrarRecurrentes();
        if (migrados > 0) {
            System.out.println("🏷️ Migración de categorías: " + migrados + " fila(s) migradas al nuevo sistema categoría/subcategoría.");
        } else {
            System.out.println("ℹ️ No hay filas pendientes de migrar al nuevo sistema de categorías.");
        }
    }

    private int migrarMovimientos() {
        List<Object[]> filas = movimientoRepository.findLegacyCategoriaRows();
        int migradas = 0;
        for (Object[] fila : filas) {
            Long id = ((Number) fila[0]).longValue();
            String categoriaVieja = (String) fila[1];
            Subcategoria otros = resolverOtros(categoriaVieja);
            if (otros == null) continue;
            movimientoRepository.actualizarCategoriaMigrada(id, otros.getCategoria().getId(), otros.getId());
            migradas++;
        }
        return migradas;
    }

    private int migrarRecurrentes() {
        List<Object[]> filas = movimientoRecurrenteRepository.findLegacyCategoriaRows();
        int migradas = 0;
        for (Object[] fila : filas) {
            Long id = ((Number) fila[0]).longValue();
            String categoriaVieja = (String) fila[1];
            Subcategoria otros = resolverOtros(categoriaVieja);
            if (otros == null) continue;
            movimientoRecurrenteRepository.actualizarCategoriaMigrada(id, otros.getCategoria().getId(), otros.getId());
            migradas++;
        }
        return migradas;
    }

    private Subcategoria resolverOtros(String categoriaVieja) {
        String nombreNuevo = MAPEO.get(categoriaVieja);
        if (nombreNuevo == null) {
            return null; // valor desconocido: se deja tal cual, sin inventar una categoría
        }
        Categoria categoria = categoriaRepository.findByNombreIgnoreCase(nombreNuevo).orElse(null);
        if (categoria == null) {
            return null; // el seed aún no ha creado la categoría (no debería pasar, pero por seguridad)
        }
        return subcategoriaRepository.findByCategoriaAndNombreIgnoreCase(categoria, "Otros").orElse(null);
    }
}
