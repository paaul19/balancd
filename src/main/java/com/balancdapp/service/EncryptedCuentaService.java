package com.balancdapp.service;

import com.balancdapp.model.Cuenta;
import com.balancdapp.model.Movimiento;
import com.balancdapp.model.TipoCuenta;
import com.balancdapp.model.Transferencia;
import com.balancdapp.model.User;
import com.balancdapp.repository.CuentaRepository;
import com.balancdapp.repository.MovimientoRepository;
import com.balancdapp.repository.TransferenciaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class EncryptedCuentaService {

    @Autowired
    private CuentaRepository cuentaRepository;

    @Autowired
    private MovimientoRepository movimientoRepository;

    @Autowired
    private TransferenciaRepository transferenciaRepository;

    @Autowired
    private DataEncryptionService encryptionService;

    public Cuenta crearCuenta(User user, String nombre, TipoCuenta tipo, double saldoInicial) {
        return crearCuenta(user, nombre, tipo, saldoInicial, null);
    }

    public Cuenta crearCuenta(User user, String nombre, TipoCuenta tipo, double saldoInicial, String ultimosDigitos) {
        Cuenta cuenta = new Cuenta();
        cuenta.setUser(user);
        cuenta.setNombre(nombre);
        cuenta.setTipo(tipo);
        cuenta.setSaldoInicialCifrado(encryptionService.encryptNumber(saldoInicial));
        cuenta.setUltimosDigitos(ultimosDigitos);
        cuenta.setFechaCreacion(LocalDateTime.now());
        cuenta.setActiva(true);
        return cuentaRepository.save(cuenta);
    }

    public void actualizarCuenta(Cuenta cuenta, String nombre, TipoCuenta tipo, double saldoInicial) {
        actualizarCuenta(cuenta, nombre, tipo, saldoInicial, cuenta.getUltimosDigitos());
    }

    public void actualizarCuenta(Cuenta cuenta, String nombre, TipoCuenta tipo, double saldoInicial, String ultimosDigitos) {
        cuenta.setNombre(nombre);
        cuenta.setTipo(tipo);
        cuenta.setSaldoInicialCifrado(encryptionService.encryptNumber(saldoInicial));
        cuenta.setUltimosDigitos(ultimosDigitos);
        cuentaRepository.save(cuenta);
    }

    public void desactivar(Cuenta cuenta) {
        cuenta.setActiva(false);
        cuentaRepository.save(cuenta);
    }

    public void activar(Cuenta cuenta) {
        cuenta.setActiva(true);
        cuentaRepository.save(cuenta);
    }

    /**
     * Elimina una cuenta solo si no tiene movimientos ni transferencias asociadas.
     * @return true si se ha eliminado, false si tiene datos asociados y no se puede eliminar.
     */
    public boolean eliminarSiEstaVacia(Cuenta cuenta) {
        boolean tieneMovimientos = movimientoRepository.existsByCuenta(cuenta);
        boolean tieneTransferencias = transferenciaRepository.existsByCuentaOrigenOrCuentaDestino(cuenta, cuenta);
        if (tieneMovimientos || tieneTransferencias) {
            return false;
        }
        cuentaRepository.delete(cuenta);
        return true;
    }

    public Cuenta getCuentaById(Long id) {
        return cuentaRepository.findById(id).orElse(null);
    }

    public double getSaldoInicial(Cuenta cuenta) {
        Double valor = encryptionService.decryptNumber(cuenta.getSaldoInicialCifrado());
        return valor == null ? 0.0 : valor;
    }

    /**
     * Calcula el saldo actual de una cuenta: saldo inicial + ingresos - gastos de esa cuenta
     * - transferencias salientes + transferencias entrantes.
     */
    public double calcularSaldoActual(Cuenta cuenta, List<Movimiento> movimientosCuenta, List<Transferencia> transferenciasUsuario) {
        double saldo = getSaldoInicial(cuenta);
        for (Movimiento m : movimientosCuenta) {
            Double cantidad = encryptionService.decryptNumber(m.getCantidadCifrada());
            double valor = cantidad == null ? 0.0 : cantidad;
            saldo += m.isIngreso() ? valor : -valor;
        }
        for (Transferencia t : transferenciasUsuario) {
            Double importe = encryptionService.decryptNumber(t.getImporteCifrado());
            double valor = importe == null ? 0.0 : importe;
            if (t.getCuentaOrigen().getId().equals(cuenta.getId())) {
                saldo -= valor;
            }
            if (t.getCuentaDestino().getId().equals(cuenta.getId())) {
                saldo += valor;
            }
        }
        return saldo;
    }

    /**
     * Obtiene todas las cuentas de un usuario (activas e inactivas) con su saldo actual calculado.
     */
    public List<CuentaDTO> getCuentasByUser(User user) {
        List<Cuenta> cuentas = cuentaRepository.findByUser(user);
        List<Movimiento> movimientos = movimientoRepository.findByUser(user);
        List<Transferencia> transferencias = transferenciaRepository.findByUser(user);
        return cuentas.stream().map(cuenta -> {
            List<Movimiento> movimientosCuenta = movimientos.stream()
                    .filter(m -> m.getCuenta() != null && m.getCuenta().getId().equals(cuenta.getId()))
                    .collect(Collectors.toList());
            double saldoActual = calcularSaldoActual(cuenta, movimientosCuenta, transferencias);
            return convertToDTO(cuenta, saldoActual);
        }).collect(Collectors.toList());
    }

    public List<CuentaDTO> getCuentasActivasByUser(User user) {
        return getCuentasByUser(user).stream().filter(CuentaDTO::isActiva).collect(Collectors.toList());
    }

    public double getBalanceTotal(User user) {
        return getCuentasByUser(user).stream()
                .filter(CuentaDTO::isActiva)
                .mapToDouble(CuentaDTO::getSaldoActual)
                .sum();
    }

    private CuentaDTO convertToDTO(Cuenta cuenta, double saldoActual) {
        CuentaDTO dto = new CuentaDTO();
        dto.setId(cuenta.getId());
        dto.setNombre(cuenta.getNombre());
        dto.setTipo(cuenta.getTipo().name());
        dto.setSaldoInicial(getSaldoInicial(cuenta));
        dto.setSaldoActual(saldoActual);
        dto.setFechaCreacion(cuenta.getFechaCreacion());
        dto.setActiva(cuenta.isActiva());
        dto.setUltimosDigitos(cuenta.getUltimosDigitos());
        return dto;
    }

    public static class CuentaDTO {
        private Long id;
        private String nombre;
        private String tipo;
        private double saldoInicial;
        private double saldoActual;
        private java.time.LocalDateTime fechaCreacion;
        private boolean activa;
        private String ultimosDigitos;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }

        public String getTipo() { return tipo; }
        public void setTipo(String tipo) { this.tipo = tipo; }

        public double getSaldoInicial() { return saldoInicial; }
        public void setSaldoInicial(double saldoInicial) { this.saldoInicial = saldoInicial; }

        public double getSaldoActual() { return saldoActual; }
        public void setSaldoActual(double saldoActual) { this.saldoActual = saldoActual; }

        public java.time.LocalDateTime getFechaCreacion() { return fechaCreacion; }
        public void setFechaCreacion(java.time.LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

        public boolean isActiva() { return activa; }
        public void setActiva(boolean activa) { this.activa = activa; }

        public String getUltimosDigitos() { return ultimosDigitos; }
        public void setUltimosDigitos(String ultimosDigitos) { this.ultimosDigitos = ultimosDigitos; }
    }
}
