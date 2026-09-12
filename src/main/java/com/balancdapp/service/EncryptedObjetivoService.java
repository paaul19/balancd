package com.balancdapp.service;

import com.balancdapp.model.Cuenta;
import com.balancdapp.model.Objetivo;
import com.balancdapp.model.User;
import com.balancdapp.repository.ObjetivoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class EncryptedObjetivoService {

    @Autowired
    private ObjetivoRepository objetivoRepository;

    @Autowired
    private DataEncryptionService encryptionService;

    @Autowired
    private EncryptedCuentaService encryptedCuentaService;

    public Objetivo crearObjetivo(User user, Cuenta cuenta, String nombre, double objetivo, LocalDate fechaLimite) {
        Objetivo o = new Objetivo();
        o.setUser(user);
        o.setCuenta(cuenta);
        o.setNombre(nombre);
        o.setObjetivoCifrado(encryptionService.encryptNumber(objetivo));
        o.setFechaLimite(fechaLimite);
        o.setActivo(true);
        o.setFechaCreacion(LocalDateTime.now());
        return objetivoRepository.save(o);
    }

    public void actualizarObjetivo(Objetivo o, Cuenta cuenta, String nombre, double objetivo, LocalDate fechaLimite) {
        o.setCuenta(cuenta);
        o.setNombre(nombre);
        o.setObjetivoCifrado(encryptionService.encryptNumber(objetivo));
        o.setFechaLimite(fechaLimite);
        objetivoRepository.save(o);
    }

    public void eliminar(Objetivo o) {
        objetivoRepository.delete(o);
    }

    public double getImporteObjetivo(Objetivo o) {
        Double valor = encryptionService.decryptNumber(o.getObjetivoCifrado());
        return valor == null ? 0.0 : valor;
    }

    public List<ObjetivoDTO> getObjetivosConProgreso(User user) {
        return objetivoRepository.findByUserAndActivoTrue(user).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    private ObjetivoDTO convertToDTO(Objetivo o) {
        ObjetivoDTO dto = new ObjetivoDTO();
        dto.setId(o.getId());
        dto.setNombre(o.getNombre());
        dto.setCuentaId(o.getCuenta().getId());
        dto.setCuentaNombre(o.getCuenta().getNombre());
        double objetivo = getImporteObjetivo(o);
        double saldoActual = encryptedCuentaService.getCuentasByUser(o.getUser()).stream()
                .filter(c -> c.getId().equals(o.getCuenta().getId()))
                .findFirst()
                .map(EncryptedCuentaService.CuentaDTO::getSaldoActual)
                .orElse(0.0);
        dto.setObjetivo(objetivo);
        dto.setSaldoActual(saldoActual);
        dto.setPorcentaje(objetivo > 0 ? Math.max(0.0, Math.min(100.0, (saldoActual / objetivo) * 100.0)) : 0.0);
        dto.setCompletado(saldoActual >= objetivo && objetivo > 0);
        dto.setFechaLimite(o.getFechaLimite());
        return dto;
    }

    public static class ObjetivoDTO {
        private Long id;
        private String nombre;
        private Long cuentaId;
        private String cuentaNombre;
        private double objetivo;
        private double saldoActual;
        private double porcentaje;
        private boolean completado;
        private LocalDate fechaLimite;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }

        public Long getCuentaId() { return cuentaId; }
        public void setCuentaId(Long cuentaId) { this.cuentaId = cuentaId; }

        public String getCuentaNombre() { return cuentaNombre; }
        public void setCuentaNombre(String cuentaNombre) { this.cuentaNombre = cuentaNombre; }

        public double getObjetivo() { return objetivo; }
        public void setObjetivo(double objetivo) { this.objetivo = objetivo; }

        public double getSaldoActual() { return saldoActual; }
        public void setSaldoActual(double saldoActual) { this.saldoActual = saldoActual; }

        public double getPorcentaje() { return porcentaje; }
        public void setPorcentaje(double porcentaje) { this.porcentaje = porcentaje; }

        public boolean isCompletado() { return completado; }
        public void setCompletado(boolean completado) { this.completado = completado; }

        public LocalDate getFechaLimite() { return fechaLimite; }
        public void setFechaLimite(LocalDate fechaLimite) { this.fechaLimite = fechaLimite; }
    }
}
