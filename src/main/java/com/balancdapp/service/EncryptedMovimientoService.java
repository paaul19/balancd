package com.balancdapp.service;

import com.balancdapp.model.Movimiento;
import com.balancdapp.model.User;
import com.balancdapp.repository.MovimientoRepository;
import com.balancdapp.repository.UserRepository;
import com.balancdapp.repository.MovimientoRecurrenteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import jakarta.annotation.PostConstruct;

@Service
@Transactional
public class EncryptedMovimientoService {

    @Autowired
    private MovimientoRepository movimientoRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DataEncryptionService encryptionService;

    @Autowired
    private MovimientoRecurrenteRepository movimientoRecurrenteRepository;

    @Autowired
    private EncryptedMovimientoRecurrenteService encryptedRecurrenteService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Crea un movimiento con datos cifrados
     */
    public Movimiento createMovimiento(User user, double cantidad, boolean ingreso, String asunto, LocalDate fecha, int mesAsignado, int anioAsignado) {
        Movimiento movimiento = new Movimiento();
        movimiento.setUser(user);
        movimiento.setIngreso(ingreso);
        movimiento.setMesAsignado(mesAsignado);
        movimiento.setAnioAsignado(anioAsignado);

        // Cifrar datos sensibles
        movimiento.setCantidadCifrada(encryptionService.encryptNumber(cantidad));
        movimiento.setAsuntoCifrado(encryptionService.encrypt(asunto));
        movimiento.setFechaCifrada(encryptionService.encrypt(fecha.format(DATE_FORMATTER)));

        return movimientoRepository.save(movimiento);
    }

    /**
     * Obtiene la cantidad descifrada de un movimiento
     */
    public double getCantidad(Movimiento movimiento) {
        if (movimiento.getCantidadCifrada() == null) {
            return 0.0;
        }
        return encryptionService.decryptNumber(movimiento.getCantidadCifrada());
    }

    /**
     * Obtiene el asunto descifrado de un movimiento
     */
    public String getAsunto(Movimiento movimiento) {
        if (movimiento.getAsuntoCifrado() == null) {
            return "";
        }
        return encryptionService.decrypt(movimiento.getAsuntoCifrado());
    }

    /**
     * Obtiene la fecha descifrada de un movimiento
     */
    public LocalDate getFecha(Movimiento movimiento) {
        if (movimiento.getFechaCifrada() == null) {
            return LocalDate.now();
        }
        String fechaDescifrada = encryptionService.decrypt(movimiento.getFechaCifrada());
        System.out.println("[DEBUG] Fecha cifrada: " + movimiento.getFechaCifrada() + " | Descifrada: '" + fechaDescifrada + "'");
        return LocalDate.parse(fechaDescifrada, DATE_FORMATTER);
    }

    /**
     * Actualiza un movimiento con datos cifrados
     */
    public void updateMovimiento(Movimiento movimiento, double cantidad, String asunto, boolean ingreso, LocalDate fecha) {
        movimiento.setIngreso(ingreso);
        movimiento.setCantidadCifrada(encryptionService.encryptNumber(cantidad));
        movimiento.setAsuntoCifrado(encryptionService.encrypt(asunto));
        movimiento.setFechaCifrada(encryptionService.encrypt(fecha.toString()));
        movimiento.setMesAsignado(fecha.getMonthValue());
        movimiento.setAnioAsignado(fecha.getYear());
        movimientoRepository.save(movimiento);
    }

    /**
     * Obtiene movimientos de un usuario con datos descifrados
     */
    public List<MovimientoDTO> getMovimientosByUserId(Long userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            List<Movimiento> movimientos = movimientoRepository.findByUser(userOpt.get());
            return movimientos.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    /**
     * Convierte un Movimiento a DTO con datos descifrados
     */
    public MovimientoDTO convertToDTO(Movimiento movimiento) {
        MovimientoDTO dto = new MovimientoDTO();
        dto.setId(movimiento.getId());
        dto.setUserId(movimiento.getUserId());
        dto.setCantidad(getCantidad(movimiento));
        dto.setIngreso(movimiento.isIngreso());
        dto.setAsunto(getAsunto(movimiento));
        dto.setFecha(getFecha(movimiento));
        dto.setMesAsignado(movimiento.getMesAsignado());
        dto.setAnioAsignado(movimiento.getAnioAsignado());
        return dto;
    }

    /**
     * DTO para movimientos con datos descifrados
     */
    public static class MovimientoDTO {
        private Long id;
        private Long userId;
        private double cantidad;
        private boolean ingreso;
        private String asunto;
        private LocalDate fecha;
        private int mesAsignado;
        private int anioAsignado;

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

        public LocalDate getFecha() { return fecha; }
        public void setFecha(LocalDate fecha) { this.fecha = fecha; }

        public int getMesAsignado() { return mesAsignado; }
        public void setMesAsignado(int mesAsignado) { this.mesAsignado = mesAsignado; }

        public int getAnioAsignado() { return anioAsignado; }
        public void setAnioAsignado(int anioAsignado) { this.anioAsignado = anioAsignado; }
    }

    /**
     * Crea movimientos recurrentes según frecuencia
     */
    public void createMovimientosRecurrentes(User user, double cantidad, boolean ingreso, String asunto, LocalDate fechaInicio, String frecuencia, int repeticiones) {
        LocalDate fecha = fechaInicio;
        for (int i = 0; i < repeticiones; i++) {
            createMovimiento(user, cantidad, ingreso, asunto, fecha, fecha.getMonthValue(), fecha.getYear());
            switch (frecuencia) {
                case "semanal":
                    fecha = fecha.plusWeeks(1);
                    break;
                case "bi-semanal":
                    fecha = fecha.plusWeeks(2);
                    break;
                case "mensual":
                    fecha = fecha.plusMonths(1);
                    break;
                case "bi-mensual":
                    fecha = fecha.plusMonths(2);
                    break;
                case "anual":
                    fecha = fecha.plusYears(1);
                    break;
                default:
                    fecha = fecha.plusMonths(1);
            }
        }
    }

    public void crearMovimientoRecurrente(User user, double cantidad, boolean ingreso, String asunto, LocalDate fechaInicio, String frecuencia, Integer repeticiones, LocalDate fechaFin) {
        com.balancdapp.model.MovimientoRecurrente rec = encryptedRecurrenteService.createMovimientoRecurrente(user, cantidad, ingreso, asunto, fechaInicio, frecuencia, fechaFin);
        // Si la fecha de inicio es hoy, crear el movimiento real ya
        LocalDate hoy = LocalDate.now(java.time.ZoneId.of("Europe/Madrid"));
        if (fechaInicio.equals(hoy)) {
            createMovimiento(user, cantidad, ingreso, asunto, hoy, hoy.getMonthValue(), hoy.getYear());
            rec.setUltimaFechaEjecutada(hoy);
            movimientoRecurrenteRepository.save(rec);
        }
    }

    @PostConstruct
    public void logZonaHoraria() {
        System.out.println("[DEBUG] Zona horaria del sistema: " + java.time.ZoneId.systemDefault());
    }

    @Scheduled(cron = "0 0 0 * * *") // Cada día a las 00:00 AM
    public void procesarMovimientosRecurrentes() {
        System.out.println("[DEBUG] Ejecutando scheduler de recurrentes: " + java.time.LocalDateTime.now());
        List<com.balancdapp.model.MovimientoRecurrente> recurrentes = encryptedRecurrenteService.getRecurrentesActivos();
        LocalDate hoy = LocalDate.now(java.time.ZoneId.of("Europe/Madrid"));

        for (com.balancdapp.model.MovimientoRecurrente rec : recurrentes) {
            if (rec.getFechaInicio().isAfter(hoy)) continue;
            if (rec.getFechaFin() != null && hoy.isAfter(rec.getFechaFin())) continue;

            // Verificar si debe ejecutarse hoy según la frecuencia
            if (debeEjecutarseHoy(rec, hoy)) {
                double cantidad = encryptedRecurrenteService.getCantidad(rec);
                String asunto = encryptedRecurrenteService.getAsunto(rec);
                String cantidadCifrada = encryptionService.encryptNumber(cantidad);
                String asuntoCifrado = encryptionService.encrypt(asunto);
                String fechaCifrada = encryptionService.encrypt(hoy.format(DATE_FORMATTER));

                boolean existe = movimientoRepository.existsByUserAndMesAsignadoAndAnioAsignadoAndCantidadCifradaAndIngresoAndAsuntoCifradoAndFechaCifrada(
                        rec.getUser(), hoy.getMonthValue(), hoy.getYear(), cantidadCifrada, rec.isIngreso(), asuntoCifrado, fechaCifrada);

                if (!existe) {
                    System.out.println("[DEBUG] Creando movimiento recurrente: " + asunto + " - " + rec.getFrecuencia() + " - " + hoy);
                    createMovimiento(rec.getUser(), cantidad, rec.isIngreso(), asunto, hoy, hoy.getMonthValue(), hoy.getYear());
                    rec.setUltimaFechaEjecutada(hoy);
                    movimientoRecurrenteRepository.save(rec);
                } else {
                    System.out.println("[DEBUG] Movimiento recurrente ya existe para hoy: " + asunto);
                }
            }
        }
    }

    private boolean debeEjecutarseHoy(com.balancdapp.model.MovimientoRecurrente rec, LocalDate hoy) {
        LocalDate inicio = rec.getFechaInicio();
        String frecuencia = rec.getFrecuencia();

        switch (frecuencia) {
            case "semana":
                // Cada semana: verificar si han pasado exactamente 7 días desde el inicio
                return !hoy.isBefore(inicio) && (diasEntre(inicio, hoy) % 7 == 0);

            case "dos_semanas":
                // Cada dos semanas: verificar si han pasado exactamente 14 días desde el inicio
                return !hoy.isBefore(inicio) && (diasEntre(inicio, hoy) % 14 == 0);

            case "mes":
                // Cada mes: mismo día del mes
                return hoy.getDayOfMonth() == inicio.getDayOfMonth();

            case "dos_meses":
                // Cada dos meses: mismo día, pero cada dos meses
                if (hoy.getDayOfMonth() != inicio.getDayOfMonth()) return false;

                // Calcular meses transcurridos desde el inicio
                long mesesTranscurridos = java.time.temporal.ChronoUnit.MONTHS.between(
                        inicio.withDayOfMonth(1), hoy.withDayOfMonth(1));

                // Debe ser un múltiplo de 2 (cada dos meses)
                return mesesTranscurridos % 2 == 0;

            case "anio":
                // Cada año: mismo día y mes
                return hoy.getDayOfMonth() == inicio.getDayOfMonth() &&
                        hoy.getMonthValue() == inicio.getMonthValue();

            default:
                return false;
        }
    }

    private long diasEntre(LocalDate inicio, LocalDate fin) {
        return java.time.temporal.ChronoUnit.DAYS.between(inicio, fin);
    }

    public List<com.balancdapp.model.MovimientoRecurrente> obtenerRecurrentesDeUsuario(User user) {
        return movimientoRecurrenteRepository.findByUser(user);
    }

    public void terminarMovimientoRecurrente(Long id, User user) {
        encryptedRecurrenteService.terminarMovimientoRecurrente(id, user);
    }

    public void borrarMovimientoRecurrente(Long id, User user) {
        encryptedRecurrenteService.borrarMovimientoRecurrente(id, user);
    }

    /**
     * Modifica un movimiento recurrente
     */
    public void modificarMovimientoRecurrente(Long id, User user, double cantidad, String asunto, boolean ingreso, LocalDate fechaInicio, String frecuencia) {
        encryptedRecurrenteService.modificarMovimientoRecurrente(id, user, cantidad, asunto, ingreso, fechaInicio, frecuencia);
    }
} 