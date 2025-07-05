package com.balancdapp.service;

import com.balancdapp.model.MovimientoRecurrente;
import com.balancdapp.model.User;
import com.balancdapp.repository.MovimientoRecurrenteRepository;
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

    /**
     * Crea un movimiento recurrente con datos cifrados
     */
    public MovimientoRecurrente createMovimientoRecurrente(User user, double cantidad, boolean ingreso, String asunto, LocalDate fechaInicio, String frecuencia, LocalDate fechaFin) {
        MovimientoRecurrente recurrente = new MovimientoRecurrente();
        recurrente.setUser(user);
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
    public void updateMovimientoRecurrente(MovimientoRecurrente recurrente, double cantidad, String asunto, boolean ingreso, LocalDate fechaInicio, String frecuencia) {
        recurrente.setIngreso(ingreso);
        recurrente.setFechaInicio(fechaInicio);
        recurrente.setFrecuencia(frecuencia);
        recurrente.setCantidadCifrada(encryptionService.encryptNumber(cantidad));
        recurrente.setAsuntoCifrado(encryptionService.encrypt(asunto));
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

    /**
     * Modifica un movimiento recurrente
     */
    public void modificarMovimientoRecurrente(Long id, User user, double cantidad, String asunto, boolean ingreso, LocalDate fechaInicio, String frecuencia) {
        MovimientoRecurrente recurrente = movimientoRecurrenteRepository.findById(id).orElse(null);
        if (recurrente != null && recurrente.getUser().getId().equals(user.getId())) {
            updateMovimientoRecurrente(recurrente, cantidad, asunto, ingreso, fechaInicio, frecuencia);
        }
    }
} 