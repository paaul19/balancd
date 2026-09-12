package com.balancdapp.service;

import com.balancdapp.model.Categoria;
import com.balancdapp.model.Cuenta;
import com.balancdapp.model.MovimientoRecurrente;
import com.balancdapp.model.Subcategoria;
import com.balancdapp.model.TipoCategoria;
import com.balancdapp.model.User;
import com.balancdapp.repository.CategoriaRepository;
import com.balancdapp.repository.MovimientoRecurrenteRepository;
import com.balancdapp.repository.SubcategoriaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class EncryptedMovimientoRecurrenteService {

    @Autowired
    private MovimientoRecurrenteRepository movimientoRecurrenteRepository;

    @Autowired
    private DataEncryptionService encryptionService;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private SubcategoriaRepository subcategoriaRepository;

    private record CategoriaResuelta(Categoria categoria, Subcategoria subcategoria) {}

    private CategoriaResuelta resolverCategoria(boolean ingreso, Long categoriaId, Long subcategoriaId, User user) {
        if (categoriaId == null) {
            return new CategoriaResuelta(null, null);
        }
        Categoria categoria = categoriaRepository.findById(categoriaId).orElse(null);
        TipoCategoria tipoEsperado = ingreso ? TipoCategoria.INCOME : TipoCategoria.EXPENSE;
        boolean pertenece = categoria != null && (categoria.getUser() == null || categoria.getUser().getId().equals(user.getId()));
        if (categoria == null || categoria.getTipo() != tipoEsperado || !pertenece) {
            return new CategoriaResuelta(null, null);
        }
        Subcategoria sub = null;
        if (subcategoriaId != null) {
            Subcategoria candidata = subcategoriaRepository.findById(subcategoriaId).orElse(null);
            if (candidata != null && candidata.getCategoria().getId().equals(categoria.getId())) {
                sub = candidata;
            }
        }
        return new CategoriaResuelta(categoria, sub);
    }

    /**
     * Crea un movimiento recurrente con datos cifrados
     */
    public MovimientoRecurrente createMovimientoRecurrente(User user, Cuenta cuenta, double cantidad, boolean ingreso, String asunto, LocalDate fechaInicio, String frecuencia, LocalDate fechaFin, Long categoriaId, Long subcategoriaId) {
        MovimientoRecurrente recurrente = new MovimientoRecurrente();
        recurrente.setUser(user);
        recurrente.setCuenta(cuenta);
        recurrente.setIngreso(ingreso);
        recurrente.setFechaInicio(fechaInicio);
        recurrente.setFrecuencia(frecuencia);
        recurrente.setActivo(true);
        if (fechaFin != null) {
            recurrente.setFechaFin(fechaFin);
        }
        // Cifrar datos sensibles
        recurrente.setCantidadCifrada(encryptionService.encryptNumber(cantidad));
        recurrente.setAsuntoCifrado(encryptionService.encrypt(asunto));
        CategoriaResuelta resuelta = resolverCategoria(ingreso, categoriaId, subcategoriaId, user);
        recurrente.setCategoria(resuelta.categoria());
        recurrente.setSubcategoria(resuelta.subcategoria());
        return movimientoRecurrenteRepository.save(recurrente);
    }

    /**
     * Obtiene la cantidad descifrada de un movimiento recurrente
     */
    public double getCantidad(MovimientoRecurrente recurrente) {
        if (recurrente.getCantidadCifrada() == null) {
            return 0.0;
        }
        return encryptionService.decryptNumber(recurrente.getCantidadCifrada());
    }

    /**
     * Obtiene el asunto descifrado de un movimiento recurrente
     */
    public String getAsunto(MovimientoRecurrente recurrente) {
        if (recurrente.getAsuntoCifrado() == null) {
            return "";
        }
        return encryptionService.decrypt(recurrente.getAsuntoCifrado());
    }

    /**
     * Actualiza un movimiento recurrente con datos cifrados
     */
    public void updateMovimientoRecurrente(MovimientoRecurrente recurrente, Cuenta cuenta, double cantidad, String asunto, boolean ingreso, LocalDate fechaInicio, String frecuencia, Long categoriaId, Long subcategoriaId) {
        recurrente.setCuenta(cuenta);
        recurrente.setIngreso(ingreso);
        recurrente.setFechaInicio(fechaInicio);
        recurrente.setFrecuencia(frecuencia);
        recurrente.setCantidadCifrada(encryptionService.encryptNumber(cantidad));
        recurrente.setAsuntoCifrado(encryptionService.encrypt(asunto));
        CategoriaResuelta resuelta = resolverCategoria(ingreso, categoriaId, subcategoriaId, recurrente.getUser());
        recurrente.setCategoria(resuelta.categoria());
        recurrente.setSubcategoria(resuelta.subcategoria());
        movimientoRecurrenteRepository.save(recurrente);
    }

    /**
     * Obtiene movimientos recurrentes de un usuario con datos descifrados
     */
    public List<MovimientoRecurrenteDTO> getRecurrentesByUser(User user) {
        List<MovimientoRecurrente> recurrentes = movimientoRecurrenteRepository.findByUser(user);
        return recurrentes.stream()
                .map(this::convertToDTO)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Convierte un MovimientoRecurrente a DTO con datos descifrados
     */
    public MovimientoRecurrenteDTO convertToDTO(MovimientoRecurrente recurrente) {
        MovimientoRecurrenteDTO dto = new MovimientoRecurrenteDTO();
        dto.setId(recurrente.getId());
        dto.setUserId(recurrente.getUser().getId());
        dto.setCantidad(getCantidad(recurrente));
        dto.setIngreso(recurrente.isIngreso());
        dto.setAsunto(getAsunto(recurrente));
        dto.setFechaInicio(recurrente.getFechaInicio());
        dto.setFrecuencia(recurrente.getFrecuencia());
        dto.setFechaFin(recurrente.getFechaFin());
        dto.setActivo(recurrente.isActivo());
        dto.setUltimaFechaEjecutada(recurrente.getUltimaFechaEjecutada());
        if (recurrente.getCategoria() != null) {
            dto.setCategoriaId(recurrente.getCategoria().getId());
            dto.setCategoriaNombre(recurrente.getCategoria().getNombre());
            dto.setCategoriaIcono(recurrente.getCategoria().getIcono());
        }
        if (recurrente.getSubcategoria() != null) {
            dto.setSubcategoriaId(recurrente.getSubcategoria().getId());
            dto.setSubcategoriaNombre(recurrente.getSubcategoria().getNombre());
        }
        if (recurrente.getCuenta() != null) {
            dto.setCuentaId(recurrente.getCuenta().getId());
            dto.setCuentaNombre(recurrente.getCuenta().getNombre());
        }
        return dto;
    }

    /**
     * DTO para movimientos recurrentes con datos descifrados
     */
    public static class MovimientoRecurrenteDTO {
        private Long id;
        private Long userId;
        private double cantidad;
        private boolean ingreso;
        private String asunto;
        private LocalDate fechaInicio;
        private String frecuencia;
        private LocalDate fechaFin;
        private boolean activo;
        private LocalDate ultimaFechaEjecutada;
        private Long categoriaId;
        private String categoriaNombre;
        private String categoriaIcono;
        private Long subcategoriaId;
        private String subcategoriaNombre;
        private Long cuentaId;
        private String cuentaNombre;

        // Getters y setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }

        public double getCantidad() { return cantidad; }
        public void setCantidad(double cantidad) { this.cantidad = cantidad; }

        public boolean isIngreso() { return ingreso; }
        public void setIngreso(boolean ingreso) { this.ingreso = ingreso; }

        public String getAsunto() { return asunto; }
        public void setAsunto(String asunto) { this.asunto = asunto; }

        public LocalDate getFechaInicio() { return fechaInicio; }
        public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }

        public String getFrecuencia() { return frecuencia; }
        public void setFrecuencia(String frecuencia) { this.frecuencia = frecuencia; }

        public LocalDate getFechaFin() { return fechaFin; }
        public void setFechaFin(LocalDate fechaFin) { this.fechaFin = fechaFin; }

        public boolean isActivo() { return activo; }
        public void setActivo(boolean activo) { this.activo = activo; }

        public LocalDate getUltimaFechaEjecutada() { return ultimaFechaEjecutada; }
        public void setUltimaFechaEjecutada(LocalDate ultimaFechaEjecutada) { this.ultimaFechaEjecutada = ultimaFechaEjecutada; }

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

        public Long getCuentaId() { return cuentaId; }
        public void setCuentaId(Long cuentaId) { this.cuentaId = cuentaId; }

        public String getCuentaNombre() { return cuentaNombre; }
        public void setCuentaNombre(String cuentaNombre) { this.cuentaNombre = cuentaNombre; }
    }

    /**
     * Obtiene todos los movimientos recurrentes activos
     */
    public List<MovimientoRecurrente> getRecurrentesActivos() {
        return movimientoRecurrenteRepository.findByActivoTrue();
    }

    /**
     * Termina un movimiento recurrente (lo marca como inactivo)
     */
    public void terminarMovimientoRecurrente(Long id, User user) {
        MovimientoRecurrente recurrente = movimientoRecurrenteRepository.findById(id).orElse(null);
        if (recurrente != null && recurrente.getUser().getId().equals(user.getId())) {
            recurrente.setActivo(false);
            movimientoRecurrenteRepository.save(recurrente);
        }
    }

    /**
     * Borra un movimiento recurrente
     */
    public void borrarMovimientoRecurrente(Long id, User user) {
        MovimientoRecurrente recurrente = movimientoRecurrenteRepository.findById(id).orElse(null);
        if (recurrente != null && recurrente.getUser().getId().equals(user.getId())) {
            movimientoRecurrenteRepository.delete(recurrente);
        }
    }
}