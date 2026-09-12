package com.balancdapp.service;

import com.balancdapp.model.Categoria;
import com.balancdapp.model.Movimiento;
import com.balancdapp.model.Presupuesto;
import com.balancdapp.model.Subcategoria;
import com.balancdapp.model.TipoCategoria;
import com.balancdapp.model.User;
import com.balancdapp.repository.MovimientoRepository;
import com.balancdapp.repository.PresupuestoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional
public class EncryptedPresupuestoService {

    @Autowired
    private PresupuestoRepository presupuestoRepository;

    @Autowired
    private MovimientoRepository movimientoRepository;

    @Autowired
    private DataEncryptionService encryptionService;

    /**
     * Valida la combinación categoría/subcategoría/concepto y comprueba que no exista ya un
     * presupuesto activo con exactamente el mismo alcance (mismo trío categoría+subcategoría+concepto).
     * @param idAExcluir si se está editando un presupuesto, su propio id, para no chocar consigo mismo.
     */
    private void validarAlcance(User user, Categoria categoria, Subcategoria subcategoria, String concepto, Long idAExcluir) {
        if (categoria == null && (concepto == null || concepto.isBlank())) {
            throw new IllegalArgumentException("Indica al menos una categoría o un concepto para el presupuesto.");
        }
        if (categoria != null && categoria.getTipo() != TipoCategoria.EXPENSE) {
            throw new IllegalArgumentException("Solo se pueden crear presupuestos para categorías de gasto.");
        }
        if (subcategoria != null && (categoria == null || !subcategoria.getCategoria().getId().equals(categoria.getId()))) {
            throw new IllegalArgumentException("La subcategoría no pertenece a la categoría elegida.");
        }
        String conceptoNormalizado = normalizarConcepto(concepto);
        boolean duplicado = presupuestoRepository.findByUserAndActivoTrue(user).stream()
                .filter(p -> !p.getId().equals(idAExcluir))
                .anyMatch(p -> Objects.equals(idOrNull(p.getCategoria()), idOrNull(categoria))
                        && Objects.equals(idOrNull(p.getSubcategoria()), idOrNull(subcategoria))
                        && Objects.equals(normalizarConcepto(p.getConcepto()), conceptoNormalizado));
        if (duplicado) {
            throw new IllegalArgumentException("Ya tienes un presupuesto activo con ese mismo alcance.");
        }
    }

    private Long idOrNull(Categoria c) { return c != null ? c.getId() : null; }
    private Long idOrNull(Subcategoria s) { return s != null ? s.getId() : null; }

    private String normalizarConcepto(String concepto) {
        if (concepto == null) return null;
        String limpio = concepto.trim().toLowerCase();
        return limpio.isEmpty() ? null : limpio;
    }

    public Presupuesto crearPresupuesto(User user, Categoria categoria, Subcategoria subcategoria, String concepto, double limite) {
        validarAlcance(user, categoria, subcategoria, concepto, null);
        Presupuesto presupuesto = new Presupuesto();
        presupuesto.setUser(user);
        presupuesto.setCategoria(categoria);
        presupuesto.setSubcategoria(subcategoria);
        presupuesto.setConcepto(concepto != null && !concepto.isBlank() ? concepto.trim() : null);
        presupuesto.setLimiteCifrado(encryptionService.encryptNumber(limite));
        presupuesto.setActivo(true);
        presupuesto.setFechaCreacion(java.time.LocalDateTime.now());
        return presupuestoRepository.save(presupuesto);
    }

    public void actualizarPresupuesto(Presupuesto presupuesto, Categoria categoria, Subcategoria subcategoria, String concepto, double limite) {
        validarAlcance(presupuesto.getUser(), categoria, subcategoria, concepto, presupuesto.getId());
        presupuesto.setCategoria(categoria);
        presupuesto.setSubcategoria(subcategoria);
        presupuesto.setConcepto(concepto != null && !concepto.isBlank() ? concepto.trim() : null);
        presupuesto.setLimiteCifrado(encryptionService.encryptNumber(limite));
        presupuestoRepository.save(presupuesto);
    }

    public void eliminar(Presupuesto presupuesto) {
        presupuestoRepository.delete(presupuesto);
    }

    public double getLimite(Presupuesto presupuesto) {
        Double valor = encryptionService.decryptNumber(presupuesto.getLimiteCifrado());
        return valor == null ? 0.0 : valor;
    }

    /**
     * Gasto real que cae dentro del alcance del presupuesto en un mes/año concretos: se filtra
     * por categoría (si está informada), subcategoría (si está informada) y por que el asunto
     * contenga el concepto (si está informado) — los tres filtros son acumulativos (AND).
     */
    private double getGastoEnAlcance(Presupuesto presupuesto, int mes, int anio) {
        List<Movimiento> movimientos = movimientoRepository.findByUserAndMesAsignadoAndAnioAsignado(presupuesto.getUser(), mes, anio);
        Categoria categoria = presupuesto.getCategoria();
        Subcategoria subcategoria = presupuesto.getSubcategoria();
        String concepto = normalizarConcepto(presupuesto.getConcepto());

        return movimientos.stream()
                .filter(m -> !m.isIngreso())
                .filter(m -> categoria == null || (m.getCategoria() != null && m.getCategoria().getId().equals(categoria.getId())))
                .filter(m -> subcategoria == null || (m.getSubcategoria() != null && m.getSubcategoria().getId().equals(subcategoria.getId())))
                .filter(m -> {
                    if (concepto == null) return true;
                    String asunto = encryptionService.decrypt(m.getAsuntoCifrado());
                    return asunto != null && asunto.toLowerCase().contains(concepto);
                })
                .mapToDouble(m -> {
                    Double cantidad = encryptionService.decryptNumber(m.getCantidadCifrada());
                    return cantidad == null ? 0.0 : cantidad;
                })
                .sum();
    }

    public List<PresupuestoDTO> getPresupuestosConProgreso(User user, int mes, int anio) {
        return presupuestoRepository.findByUserAndActivoTrue(user).stream()
                .map(p -> convertToDTO(p, mes, anio))
                .collect(Collectors.toList());
    }

    private PresupuestoDTO convertToDTO(Presupuesto presupuesto, int mes, int anio) {
        PresupuestoDTO dto = new PresupuestoDTO();
        dto.setId(presupuesto.getId());
        Categoria categoria = presupuesto.getCategoria();
        Subcategoria subcategoria = presupuesto.getSubcategoria();
        if (categoria != null) {
            dto.setCategoriaId(categoria.getId());
            dto.setCategoriaNombre(categoria.getNombre());
            dto.setCategoriaIcono(categoria.getIcono());
        }
        if (subcategoria != null) {
            dto.setSubcategoriaId(subcategoria.getId());
            dto.setSubcategoriaNombre(subcategoria.getNombre());
        }
        dto.setConcepto(presupuesto.getConcepto());
        dto.setEtiqueta(construirEtiqueta(categoria, subcategoria, presupuesto.getConcepto()));

        double limite = getLimite(presupuesto);
        double gastado = getGastoEnAlcance(presupuesto, mes, anio);
        dto.setLimite(limite);
        dto.setGastado(gastado);
        dto.setRestante(limite - gastado);
        dto.setPorcentaje(limite > 0 ? Math.min(100.0, (gastado / limite) * 100.0) : 0.0);
        dto.setExcedido(gastado > limite);
        return dto;
    }

    private String construirEtiqueta(Categoria categoria, Subcategoria subcategoria, String concepto) {
        StringBuilder sb = new StringBuilder();
        if (categoria != null) {
            sb.append(categoria.getNombre());
            if (subcategoria != null) {
                sb.append(" · ").append(subcategoria.getNombre());
            }
        }
        if (concepto != null && !concepto.isBlank()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("\"").append(concepto.trim()).append("\"");
        }
        return sb.toString();
    }

    public static class PresupuestoDTO {
        private Long id;
        private Long categoriaId;
        private String categoriaNombre;
        private String categoriaIcono;
        private Long subcategoriaId;
        private String subcategoriaNombre;
        private String concepto;
        private String etiqueta;
        private double limite;
        private double gastado;
        private double restante;
        private double porcentaje;
        private boolean excedido;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public Long getCategoriaId() { return categoriaId; }
        public void setCategoriaId(Long categoriaId) { this.categoriaId = categoriaId; }

        public String getCategoriaNombre() { return categoriaNombre; }
        public void setCategoriaNombre(String categoriaNombre) { this.categoriaNombre = categoriaNombre; }

        public String getCategoriaIcono() { return categoriaIcono; }
        public void setCategoriaIcono(String categoriaIcono) { this.categoriaIcono = categoriaIcono; }

        public Long getSubcategoriaId() { return subcategoriaId; }
        public void setSubcategoriaId(Long subcategoriaId) { this.subcategoriaId = subcategoriaId; }

        public String getSubcategoriaNombre() { return subcategoriaNombre; }
        public void setSubcategoriaNombre(String subcategoriaNombre) { this.subcategoriaNombre = subcategoriaNombre; }

        public String getConcepto() { return concepto; }
        public void setConcepto(String concepto) { this.concepto = concepto; }

        public String getEtiqueta() { return etiqueta; }
        public void setEtiqueta(String etiqueta) { this.etiqueta = etiqueta; }

        public double getLimite() { return limite; }
        public void setLimite(double limite) { this.limite = limite; }

        public double getGastado() { return gastado; }
        public void setGastado(double gastado) { this.gastado = gastado; }

        public double getRestante() { return restante; }
        public void setRestante(double restante) { this.restante = restante; }

        public double getPorcentaje() { return porcentaje; }
        public void setPorcentaje(double porcentaje) { this.porcentaje = porcentaje; }

        public boolean isExcedido() { return excedido; }
        public void setExcedido(boolean excedido) { this.excedido = excedido; }
    }
}
