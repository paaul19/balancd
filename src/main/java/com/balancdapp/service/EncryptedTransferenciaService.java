package com.balancdapp.service;

import com.balancdapp.model.Cuenta;
import com.balancdapp.model.Transferencia;
import com.balancdapp.model.User;
import com.balancdapp.repository.TransferenciaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class EncryptedTransferenciaService {

    @Autowired
    private TransferenciaRepository transferenciaRepository;

    @Autowired
    private DataEncryptionService encryptionService;

    public Transferencia crearTransferencia(User user, Cuenta origen, Cuenta destino, double importe, LocalDate fecha, String descripcion) {
        if (origen.getId().equals(destino.getId())) {
            throw new IllegalArgumentException("La cuenta de origen y destino no pueden ser la misma");
        }
        if (importe <= 0) {
            throw new IllegalArgumentException("El importe de la transferencia debe ser mayor que cero");
        }
        Transferencia t = new Transferencia();
        t.setUser(user);
        t.setCuentaOrigen(origen);
        t.setCuentaDestino(destino);
        t.setImporteCifrado(encryptionService.encryptNumber(importe));
        t.setFecha(fecha);
        if (descripcion != null && !descripcion.isBlank()) {
            t.setDescripcionCifrada(encryptionService.encrypt(descripcion.trim()));
        }
        t.setFechaCreacion(LocalDateTime.now());
        return transferenciaRepository.save(t);
    }

    public Transferencia getById(Long id) {
        return transferenciaRepository.findById(id).orElse(null);
    }

    public void eliminar(Transferencia transferencia) {
        transferenciaRepository.delete(transferencia);
    }

    public double getImporte(Transferencia t) {
        Double valor = encryptionService.decryptNumber(t.getImporteCifrado());
        return valor == null ? 0.0 : valor;
    }

    public String getDescripcion(Transferencia t) {
        if (t.getDescripcionCifrada() == null) {
            return null;
        }
        return encryptionService.decrypt(t.getDescripcionCifrada());
    }

    public List<TransferenciaDTO> getTransferenciasByUser(User user) {
        return transferenciaRepository.findByUser(user).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<TransferenciaDTO> getTransferenciasByUserAndMesAnio(User user, int mes, int anio) {
        return getTransferenciasByUser(user).stream()
                .filter(t -> t.getFecha().getMonthValue() == mes && t.getFecha().getYear() == anio)
                .collect(Collectors.toList());
    }

    private TransferenciaDTO convertToDTO(Transferencia t) {
        TransferenciaDTO dto = new TransferenciaDTO();
        dto.setId(t.getId());
        dto.setCuentaOrigenId(t.getCuentaOrigen().getId());
        dto.setCuentaOrigenNombre(t.getCuentaOrigen().getNombre());
        dto.setCuentaDestinoId(t.getCuentaDestino().getId());
        dto.setCuentaDestinoNombre(t.getCuentaDestino().getNombre());
        dto.setImporte(getImporte(t));
        dto.setFecha(t.getFecha());
        dto.setDescripcion(getDescripcion(t));
        return dto;
    }

    public static class TransferenciaDTO {
        private Long id;
        private Long cuentaOrigenId;
        private String cuentaOrigenNombre;
        private Long cuentaDestinoId;
        private String cuentaDestinoNombre;
        private double importe;
        private LocalDate fecha;
        private String descripcion;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public Long getCuentaOrigenId() { return cuentaOrigenId; }
        public void setCuentaOrigenId(Long cuentaOrigenId) { this.cuentaOrigenId = cuentaOrigenId; }

        public String getCuentaOrigenNombre() { return cuentaOrigenNombre; }
        public void setCuentaOrigenNombre(String cuentaOrigenNombre) { this.cuentaOrigenNombre = cuentaOrigenNombre; }

        public Long getCuentaDestinoId() { return cuentaDestinoId; }
        public void setCuentaDestinoId(Long cuentaDestinoId) { this.cuentaDestinoId = cuentaDestinoId; }

        public String getCuentaDestinoNombre() { return cuentaDestinoNombre; }
        public void setCuentaDestinoNombre(String cuentaDestinoNombre) { this.cuentaDestinoNombre = cuentaDestinoNombre; }

        public double getImporte() { return importe; }
        public void setImporte(double importe) { this.importe = importe; }

        public LocalDate getFecha() { return fecha; }
        public void setFecha(LocalDate fecha) { this.fecha = fecha; }

        public String getDescripcion() { return descripcion; }
        public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    }
}
