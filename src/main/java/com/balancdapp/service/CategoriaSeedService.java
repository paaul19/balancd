package com.balancdapp.service;

import com.balancdapp.model.Categoria;
import com.balancdapp.model.Subcategoria;
import com.balancdapp.model.TipoCategoria;
import com.balancdapp.repository.CategoriaRepository;
import com.balancdapp.repository.SubcategoriaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Siembra idempotente del árbol fijo de categorías y subcategorías de la app.
 * Se ejecuta en cada arranque; solo crea lo que falte, nunca duplica ni borra.
 */
@Service
@Order(5)
public class CategoriaSeedService implements CommandLineRunner {

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private SubcategoriaRepository subcategoriaRepository;

    private static final Map<String, String[]> GASTOS = new LinkedHashMap<>();
    private static final Map<String, String[]> INGRESOS = new LinkedHashMap<>();

    static {
        GASTOS.put("Alimentación|alimentacion", new String[]{"Supermercado", "Restaurantes", "Delivery", "Cafeterías", "Comida para llevar", "Otros"});
        GASTOS.put("Vivienda|vivienda", new String[]{"Alquiler o hipoteca", "Electricidad", "Agua", "Gas", "Internet", "Móvil", "Mantenimiento", "Muebles y decoración", "Otros"});
        GASTOS.put("Transporte|transporte", new String[]{"Transporte público", "Combustible", "Coche y moto", "Parking", "Peajes", "Mantenimiento", "Taxi y VTC", "Otros"});
        GASTOS.put("Ocio y entretenimiento|ocio", new String[]{"Cine", "Series y streaming", "Videojuegos", "Música", "Eventos", "Hobbies", "Libros", "Otros"});
        GASTOS.put("Compras|compras", new String[]{"Ropa", "Calzado", "Tecnología y electrónica", "Menaje y hogar", "Accesorios", "Otros"});
        GASTOS.put("Salud y bienestar|salud", new String[]{"Médico", "Farmacia", "Dentista", "Gimnasio y deporte", "Cuidado personal", "Otros"});
        GASTOS.put("Educación|educacion", new String[]{"Universidad", "Cursos y formación", "Material educativo", "Otros"});
        GASTOS.put("Viajes|viajes", new String[]{"Vuelos", "Alojamiento", "Transporte", "Restaurantes y comida", "Actividades", "Otros"});
        GASTOS.put("Mascotas|mascotas", new String[]{"Alimentación", "Veterinario", "Accesorios", "Otros"});
        GASTOS.put("Seguros|seguros", new String[]{"Coche y moto", "Hogar", "Salud", "Vida", "Otros"});
        GASTOS.put("Impuestos y tasas|impuestos", new String[]{"Impuestos", "Tasas", "Multas", "Otros"});
        GASTOS.put("Regalos y donaciones|regalos", new String[]{"Regalos", "Donaciones", "Otros"});
        GASTOS.put("Suscripciones|suscripciones", new String[]{"Streaming", "Software y apps", "Servicios", "Otros"});
        GASTOS.put("Finanzas|finanzas", new String[]{"Comisiones", "Intereses", "Otros"});
        GASTOS.put("Otros gastos|otros_gastos", new String[]{"Otros"});

        INGRESOS.put("Ingresos|ingresos", new String[]{"Nómina y salario", "Freelance y trabajos extra", "Negocio", "Intereses", "Dividendos",
                "Reembolsos y devoluciones", "Regalos recibidos", "Prestaciones y ayudas", "Otros ingresos"});
    }

    @Override
    public void run(String... args) {
        sembrar();
    }

    @Transactional
    public void sembrar() {
        int creadas = 0;
        creadas += sembrarGrupo(GASTOS, TipoCategoria.EXPENSE);
        creadas += sembrarGrupo(INGRESOS, TipoCategoria.INCOME);
        if (creadas > 0) {
            System.out.println("📂 Categorías: " + creadas + " categoría(s) nueva(s) sembrada(s).");
        } else {
            System.out.println("ℹ️ Categorías ya sembradas, nada que hacer.");
        }
    }

    private int sembrarGrupo(Map<String, String[]> grupo, TipoCategoria tipo) {
        int creadas = 0;
        int orden = 0;
        for (Map.Entry<String, String[]> entry : grupo.entrySet()) {
            String[] partes = entry.getKey().split("\\|");
            String nombre = partes[0];
            String icono = partes[1];
            List<String> subnombres = List.of(entry.getValue());

            Categoria categoria = categoriaRepository.findByNombreIgnoreCase(nombre).orElse(null);
            if (categoria == null) {
                categoria = new Categoria();
                categoria.setNombre(nombre);
                categoria.setIcono(icono);
                categoria.setTipo(tipo);
                categoria.setOrden(orden);
                categoria = categoriaRepository.save(categoria);
                creadas++;
            }

            int subOrden = 0;
            for (String subnombre : subnombres) {
                if (subcategoriaRepository.findByCategoriaAndNombreIgnoreCase(categoria, subnombre).isEmpty()) {
                    Subcategoria sub = new Subcategoria();
                    sub.setCategoria(categoria);
                    sub.setNombre(subnombre);
                    sub.setOrden(subOrden);
                    subcategoriaRepository.save(sub);
                }
                subOrden++;
            }
            orden++;
        }
        return creadas;
    }
}
