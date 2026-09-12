package com.balancdapp.service;

import com.balancdapp.model.Categoria;
import com.balancdapp.model.CategoriaDesactivada;
import com.balancdapp.model.Subcategoria;
import com.balancdapp.model.SubcategoriaDesactivada;
import com.balancdapp.model.TipoCategoria;
import com.balancdapp.model.User;
import com.balancdapp.repository.CategoriaDesactivadaRepository;
import com.balancdapp.repository.CategoriaRepository;
import com.balancdapp.repository.MovimientoRecurrenteRepository;
import com.balancdapp.repository.MovimientoRepository;
import com.balancdapp.repository.PresupuestoRepository;
import com.balancdapp.repository.SubcategoriaDesactivadaRepository;
import com.balancdapp.repository.SubcategoriaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Categorías/subcategorías: la mayoría son datos de referencia globales (compartidas por todos,
 * sembradas por CategoriaSeedService), pero cada usuario puede además crear las suyas propias
 * (Categoria.user informado) y ocultar cualquiera - global o propia - sin afectar a nadie más
 * (tablas categorias_desactivadas/subcategorias_desactivadas).
 */
@Service
public class CategoriaService {

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private SubcategoriaRepository subcategoriaRepository;

    @Autowired
    private CategoriaDesactivadaRepository categoriaDesactivadaRepository;

    @Autowired
    private SubcategoriaDesactivadaRepository subcategoriaDesactivadaRepository;

    @Autowired
    private MovimientoRepository movimientoRepository;

    @Autowired
    private MovimientoRecurrenteRepository movimientoRecurrenteRepository;

    @Autowired
    private PresupuestoRepository presupuestoRepository;

    // Mismo set de iconos ya dibujados en fragments/category-icons.html; una categoría propia
    // solo puede elegir uno de estos (no se admiten iconos arbitrarios/subidos).
    public static final Set<String> ICONOS_VALIDOS = new java.util.LinkedHashSet<>(List.of(
            "alimentacion", "vivienda", "transporte", "ocio", "compras", "salud", "educacion",
            "viajes", "mascotas", "seguros", "impuestos", "regalos", "suscripciones", "finanzas", "ingresos"
    ));

    // ---------- Árboles usados fuera de esta clase (sin cambios de firma para no romper llamadas antiguas) ----------

    public List<CategoriaDTO> getArbol(TipoCategoria tipo) {
        return categoriaRepository.findByTipoOrderByOrdenAsc(tipo).stream()
                .map(c -> toDTO(c, true, false))
                .collect(Collectors.toList());
    }

    public List<CategoriaDTO> getArbolCompleto() {
        return categoriaRepository.findAllByOrderByOrdenAsc().stream()
                .map(c -> toDTO(c, true, false))
                .collect(Collectors.toList());
    }

    // ---------- Árbol visible para un usuario (selectores de alta/edición/filtros) ----------

    /** Categorías (globales + propias) de un tipo, ya sin las que ese usuario ha desactivado. */
    public List<CategoriaDTO> getArbolVisibleParaUsuario(User user, TipoCategoria tipo) {
        Set<Long> categoriasOcultas = categoriaDesactivadaRepository.findCategoriaIdsDesactivadasPorUsuario(user);
        return categoriaRepository.findVisiblesPorTipo(tipo, user).stream()
                .filter(c -> !categoriasOcultas.contains(c.getId()))
                .map(c -> toDTOVisible(c, user))
                .collect(Collectors.toList());
    }

    // ---------- Árbol de gestión (pantalla de Ajustes: activar/desactivar, crear, eliminar) ----------

    /** Todas las categorías (globales + propias) de un tipo, incluidas las desactivadas, con su estado. */
    public List<CategoriaDTO> getArbolGestionParaUsuario(User user, TipoCategoria tipo) {
        Set<Long> categoriasOcultas = categoriaDesactivadaRepository.findCategoriaIdsDesactivadasPorUsuario(user);
        Set<Long> subcategoriasOcultas = subcategoriaDesactivadaRepository.findSubcategoriaIdsDesactivadasPorUsuario(user);
        return categoriaRepository.findVisiblesPorTipo(tipo, user).stream()
                .map(c -> {
                    boolean activa = !categoriasOcultas.contains(c.getId());
                    boolean personalizada = c.getUser() != null;
                    CategoriaDTO dto = toDTO(c, activa, personalizada);
                    dto.subcategorias = subcategoriaRepository.findByCategoriaOrderByOrdenAsc(c).stream()
                            .map(s -> {
                                SubcategoriaDTO sdto = toSubDTO(s);
                                sdto.activa = !subcategoriasOcultas.contains(s.getId());
                                return sdto;
                            })
                            .collect(Collectors.toList());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private CategoriaDTO toDTOVisible(Categoria categoria, User user) {
        Set<Long> subcategoriasOcultas = subcategoriaDesactivadaRepository.findSubcategoriaIdsDesactivadasPorUsuario(user);
        CategoriaDTO dto = toDTO(categoria, true, categoria.getUser() != null);
        dto.subcategorias = subcategoriaRepository.findByCategoriaOrderByOrdenAsc(categoria).stream()
                .filter(s -> !subcategoriasOcultas.contains(s.getId()))
                .map(this::toSubDTO)
                .collect(Collectors.toList());
        return dto;
    }

    private CategoriaDTO toDTO(Categoria categoria, boolean activa, boolean personalizada) {
        CategoriaDTO dto = new CategoriaDTO();
        dto.id = categoria.getId();
        dto.nombre = categoria.getNombre();
        dto.icono = categoria.getIcono();
        dto.tipo = categoria.getTipo().name();
        dto.activa = activa;
        dto.personalizada = personalizada;
        dto.subcategorias = subcategoriaRepository.findByCategoriaOrderByOrdenAsc(categoria).stream()
                .map(this::toSubDTO)
                .collect(Collectors.toList());
        return dto;
    }

    private SubcategoriaDTO toSubDTO(Subcategoria subcategoria) {
        SubcategoriaDTO dto = new SubcategoriaDTO();
        dto.id = subcategoria.getId();
        dto.nombre = subcategoria.getNombre();
        dto.activa = true;
        return dto;
    }

    // ---------- Activar/desactivar ----------

    @Transactional
    public void toggleCategoria(User user, Long categoriaId) {
        Categoria categoria = categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new IllegalArgumentException("Esa categoría no existe."));
        if (categoria.getUser() != null && !categoria.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Esa categoría no te pertenece.");
        }
        categoriaDesactivadaRepository.findByUserAndCategoria(user, categoria)
                .ifPresentOrElse(
                        categoriaDesactivadaRepository::delete,
                        () -> {
                            CategoriaDesactivada cd = new CategoriaDesactivada();
                            cd.setUser(user);
                            cd.setCategoria(categoria);
                            categoriaDesactivadaRepository.save(cd);
                        }
                );
    }

    @Transactional
    public void toggleSubcategoria(User user, Long subcategoriaId) {
        Subcategoria subcategoria = subcategoriaRepository.findById(subcategoriaId)
                .orElseThrow(() -> new IllegalArgumentException("Esa subcategoría no existe."));
        Categoria categoria = subcategoria.getCategoria();
        if (categoria.getUser() != null && !categoria.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Esa subcategoría no te pertenece.");
        }
        subcategoriaDesactivadaRepository.findByUserAndSubcategoria(user, subcategoria)
                .ifPresentOrElse(
                        subcategoriaDesactivadaRepository::delete,
                        () -> {
                            SubcategoriaDesactivada sd = new SubcategoriaDesactivada();
                            sd.setUser(user);
                            sd.setSubcategoria(subcategoria);
                            subcategoriaDesactivadaRepository.save(sd);
                        }
                );
    }

    // ---------- Categorías propias: crear / eliminar ----------

    @Transactional
    public Categoria crearCategoriaPersonalizada(User user, String nombre, String icono, TipoCategoria tipo, List<String> nombresSubcategorias) {
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre de la categoría no puede estar vacío.");
        }
        final String nombreLimpio = nombre.trim();
        if (nombreLimpio.length() > 100) {
            throw new IllegalArgumentException("El nombre de la categoría es demasiado largo.");
        }
        if (icono == null || !ICONOS_VALIDOS.contains(icono)) {
            throw new IllegalArgumentException("Elige un icono válido para la categoría.");
        }
        if (tipo == null) {
            throw new IllegalArgumentException("Indica si la categoría es de gasto o de ingreso.");
        }
        if (categoriaRepository.existsByUserAndTipoAndNombreIgnoreCase(user, tipo, nombreLimpio)
                || categoriaRepository.findVisiblesPorTipo(tipo, user).stream()
                        .anyMatch(c -> c.getNombre().equalsIgnoreCase(nombreLimpio))) {
            throw new IllegalArgumentException("Ya tienes (o ya existe) una categoría llamada \"" + nombreLimpio + "\".");
        }

        Categoria categoria = new Categoria();
        categoria.setNombre(nombreLimpio);
        categoria.setIcono(icono);
        categoria.setTipo(tipo);
        categoria.setOrden(1000); // las propias van siempre al final del listado
        categoria.setUser(user);
        Categoria guardada = categoriaRepository.save(categoria);

        agregarSubcategorias(guardada, nombresSubcategorias);
        return guardada;
    }

    /** Añade subcategorías nuevas a una categoría ya existente, saltando vacías y duplicadas (con las ya existentes o repetidas en la propia lista). */
    private void agregarSubcategorias(Categoria categoria, List<String> nombresSubcategorias) {
        if (nombresSubcategorias == null) return;
        List<Subcategoria> existentes = subcategoriaRepository.findByCategoriaOrderByOrdenAsc(categoria);
        int orden = existentes.size();
        List<String> vistos = existentes.stream().map(Subcategoria::getNombre).collect(Collectors.toList());
        for (String nombreSub : nombresSubcategorias) {
            if (nombreSub == null || nombreSub.trim().isEmpty()) continue;
            String limpio = nombreSub.trim();
            if (limpio.length() > 100) {
                throw new IllegalArgumentException("El nombre de una subcategoría es demasiado largo.");
            }
            if (vistos.stream().anyMatch(v -> v.equalsIgnoreCase(limpio))) continue;
            Subcategoria sub = new Subcategoria();
            sub.setCategoria(categoria);
            sub.setNombre(limpio);
            sub.setOrden(orden++);
            subcategoriaRepository.save(sub);
            vistos.add(limpio);
        }
    }

    @Transactional
    public void editarCategoriaPersonalizada(User user, Long categoriaId, String nombre, String icono, List<String> nuevasSubcategorias) {
        Categoria categoria = categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new IllegalArgumentException("Esa categoría no existe."));
        if (categoria.getUser() == null || !categoria.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Solo puedes editar categorías que hayas creado tú.");
        }
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre de la categoría no puede estar vacío.");
        }
        final String nombreLimpio = nombre.trim();
        if (nombreLimpio.length() > 100) {
            throw new IllegalArgumentException("El nombre de la categoría es demasiado largo.");
        }
        if (icono == null || !ICONOS_VALIDOS.contains(icono)) {
            throw new IllegalArgumentException("Elige un icono válido para la categoría.");
        }
        boolean nombreEnUso = categoriaRepository.findVisiblesPorTipo(categoria.getTipo(), user).stream()
                .anyMatch(c -> !c.getId().equals(categoria.getId()) && c.getNombre().equalsIgnoreCase(nombreLimpio));
        if (nombreEnUso) {
            throw new IllegalArgumentException("Ya tienes (o ya existe) una categoría llamada \"" + nombreLimpio + "\".");
        }
        categoria.setNombre(nombreLimpio);
        categoria.setIcono(icono);
        categoriaRepository.save(categoria);

        agregarSubcategorias(categoria, nuevasSubcategorias);
    }

    @Transactional
    public void eliminarCategoriaPersonalizada(User user, Long categoriaId) {
        Categoria categoria = categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new IllegalArgumentException("Esa categoría no existe."));
        if (categoria.getUser() == null || !categoria.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Solo puedes eliminar categorías que hayas creado tú.");
        }
        boolean enUso = movimientoRepository.existsByCategoria(categoria)
                || movimientoRecurrenteRepository.existsByCategoria(categoria)
                || presupuestoRepository.existsByCategoria(categoria);
        if (enUso) {
            throw new IllegalArgumentException("Esta categoría tiene movimientos o presupuestos asociados: desactívala en vez de eliminarla.");
        }
        for (Subcategoria sub : subcategoriaRepository.findByCategoriaOrderByOrdenAsc(categoria)) {
            boolean subEnUso = movimientoRepository.existsBySubcategoria(sub)
                    || movimientoRecurrenteRepository.existsBySubcategoria(sub)
                    || presupuestoRepository.existsBySubcategoria(sub);
            if (subEnUso) {
                throw new IllegalArgumentException("Una subcategoría de \"" + categoria.getNombre() + "\" tiene movimientos asociados: desactívala en vez de eliminarla.");
            }
        }
        categoriaRepository.delete(categoria); // ON DELETE CASCADE se lleva sus subcategorías y desactivaciones
    }

    public static class CategoriaDTO {
        public Long id;
        public String nombre;
        public String icono;
        public String tipo;
        public boolean activa;
        public boolean personalizada;
        public List<SubcategoriaDTO> subcategorias;

        public Long getId() { return id; }
        public String getNombre() { return nombre; }
        public String getIcono() { return icono; }
        public String getTipo() { return tipo; }
        public boolean isActiva() { return activa; }
        public boolean isPersonalizada() { return personalizada; }
        public List<SubcategoriaDTO> getSubcategorias() { return subcategorias; }
    }

    public static class SubcategoriaDTO {
        public Long id;
        public String nombre;
        public boolean activa;

        public Long getId() { return id; }
        public String getNombre() { return nombre; }
        public boolean isActiva() { return activa; }
    }
}
