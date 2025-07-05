/*package com.balancdapp.service;

import com.balancdapp.model.Movimiento;
import com.balancdapp.model.User;
import com.balancdapp.repository.MovimientoRepository;
import com.balancdapp.repository.UserRepository;
import com.balancdapp.repository.MesManualRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.YearMonth;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@Order(1)
public class DataMigrationService implements CommandLineRunner {
    
    @Autowired
    private MovimientoRepository movimientoRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private MesManualRepository mesManualRepository;
    
    @Autowired
    private DataEncryptionService encryptionService;
    
    private final ObjectMapper objectMapper;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    public DataMigrationService() {
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public void run(String... args) throws Exception {
        System.out.println("🔄 Iniciando migración de datos...");
        
        // Solo migrar si existen archivos JSON
        if (new File("data/users.json").exists()) {
            migrateUsers();
        }
        
        if (new File("data/movimientos.json").exists()) {
            migrateMovimientos();
        }
        
        if (new File("data/meses_creados.json").exists()) {
            migrateMesesManuales();
        }
        
        // Comentar temporalmente la migración de cifrado hasta que la tabla esté recreada
        // migrateMovimientosToEncrypted();
        
        System.out.println("✅ Migración completada.");
    }
    
    private void migrateUsers() {
        try {
            File usersFile = new File("data/users.json");
            List<Map<String, Object>> usersData = objectMapper.readValue(usersFile, List.class);
            for (Map<String, Object> userData : usersData) {
                String username = (String) userData.get("username");
                String password = (String) userData.get("password");
                
                if (userRepository.findByUsername(username).isEmpty()) {
                    User user = new User();
                    user.setUsername(username);
                    user.setPassword(password);
                    userRepository.save(user);
                    System.out.println("✅ Usuario migrado: " + username);
                }
            }
        } catch (IOException e) {
            System.err.println("❌ Error migrando usuarios: " + e.getMessage());
        }
    }
    
    @Transactional
    public void migrateMovimientos() {
        List<Movimiento> movimientos = movimientoRepository.findAll();
        int migratedCount = 0;
        
        for (Movimiento movimiento : movimientos) {
            boolean needsUpdate = false;
            
            // Verificar si la cantidad está cifrada
            if (movimiento.getCantidadCifrada() == null || movimiento.getCantidadCifrada().isEmpty()) {
                // Migrar cantidad de campo numérico a cifrado
                if (movimiento.getCantidad() != 0.0) {
                    String cantidadCifrada = encryptionService.encryptNumber(movimiento.getCantidad());
                    movimiento.setCantidadCifrada(cantidadCifrada);
                    needsUpdate = true;
                }
            }
            
            // Verificar si el asunto está cifrado
            if (movimiento.getAsuntoCifrado() == null || movimiento.getAsuntoCifrado().isEmpty()) {
                // Migrar asunto de campo texto a cifrado
                if (movimiento.getAsunto() != null && !movimiento.getAsunto().isEmpty()) {
                    String asuntoCifrado = encryptionService.encrypt(movimiento.getAsunto());
                    movimiento.setAsuntoCifrado(asuntoCifrado);
                    needsUpdate = true;
                }
            }
            
            // Verificar si la fecha está cifrada
            if (movimiento.getFechaCifrada() == null || movimiento.getFechaCifrada().isEmpty()) {
                // Migrar fecha de campo fecha a cifrado
                if (movimiento.getFecha() != null) {
                    String fechaCifrada = encryptionService.encrypt(movimiento.getFecha().toString());
                    movimiento.setFechaCifrada(fechaCifrada);
                    needsUpdate = true;
                }
            }
            
            if (needsUpdate) {
                movimientoRepository.save(movimiento);
                migratedCount++;
                System.out.println("Movimiento migrado: ID " + movimiento.getId());
            }
        }
        
        if (migratedCount > 0) {
            System.out.println("Migración de movimientos completada: " + migratedCount + " registros actualizados");
        } else {
            System.out.println("No se encontraron movimientos para migrar");
        }
    }
    
    @Transactional
    public void migrateMesesManuales() {
        List<Movimiento> movimientos = movimientoRepository.findAll();
        int createdCount = 0;
        
        for (Movimiento movimiento : movimientos) {
            if (movimiento.getUser() != null) {
                int anio = movimiento.getAnioAsignado();
                int mes = movimiento.getMesAsignado();
                
                // Verificar si ya existe el mes manual
                if (!mesManualRepository.existsByUserAndAnioAndMes(movimiento.getUser(), anio, mes)) {
                    com.balancdapp.model.MesManual mesManual = new com.balancdapp.model.MesManual();
                    mesManual.setUser(movimiento.getUser());
                    mesManual.setAnio(anio);
                    mesManual.setMes(mes);
                    mesManualRepository.save(mesManual);
                    createdCount++;
                    System.out.println("Mes manual creado: " + anio + "-" + mes + " para usuario " + movimiento.getUser().getUsername());
                }
            }
        }
        
        if (createdCount > 0) {
            System.out.println("Migración de meses manuales completada: " + createdCount + " registros creados");
        } else {
            System.out.println("No se encontraron meses manuales para crear");
        }
    }
    
    @Transactional
    public void migrateMovimientosToEncrypted() {
        System.out.println("🔐 Iniciando migración de datos a formato cifrado...");
        
        try {
            List<Movimiento> movimientos = movimientoRepository.findAll();
            int migratedCount = 0;
            
            for (Movimiento movimiento : movimientos) {
                try {
                    // Verificar si ya está cifrado
                    if (isAlreadyEncrypted(movimiento)) {
                        continue;
                    }
                    
                    // Migrar cantidad (usar el campo original si existe)
                    if (movimiento.getCantidadCifrada() == null) {
                        double cantidadOriginal = movimiento.getCantidad();
                        if (cantidadOriginal != 0.0) {
                            String cantidadCifrada = encryptionService.encryptNumber(cantidadOriginal);
                            movimiento.setCantidadCifrada(cantidadCifrada);
                        }
                    }
                    
                    // Migrar asunto (usar el campo original si existe)
                    if (movimiento.getAsuntoCifrado() == null) {
                        String asuntoOriginal = movimiento.getAsunto();
                        if (asuntoOriginal != null && !asuntoOriginal.isEmpty()) {
                            String asuntoCifrado = encryptionService.encrypt(asuntoOriginal);
                            movimiento.setAsuntoCifrado(asuntoCifrado);
                        }
                    }
                    
                    // Migrar fecha (usar el campo original si existe)
                    if (movimiento.getFechaCifrada() == null) {
                        LocalDate fechaOriginal = movimiento.getFecha();
                        if (fechaOriginal != null) {
                            String fechaCifrada = encryptionService.encrypt(fechaOriginal.format(DATE_FORMATTER));
                            movimiento.setFechaCifrada(fechaCifrada);
                        }
                    }
                    
                    // Guardar el movimiento actualizado
                    movimientoRepository.save(movimiento);
                    migratedCount++;
                    
                } catch (Exception e) {
                    System.err.println("❌ Error migrando movimiento ID " + movimiento.getId() + ": " + e.getMessage());
                }
            }
            
            if (migratedCount > 0) {
                System.out.println("📊 Migrados " + migratedCount + " movimientos a formato cifrado.");
            } else {
                System.out.println("ℹ️ No se encontraron movimientos para migrar.");
            }
            
        } catch (Exception e) {
            System.err.println("❌ Error general en migración de cifrado: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private boolean isAlreadyEncrypted(Movimiento movimiento) {
        // Verificar si los campos ya están cifrados
        boolean cantidadCifrada = movimiento.getCantidadCifrada() != null && 
                                 encryptionService.isEncrypted(movimiento.getCantidadCifrada());
        boolean asuntoCifrado = movimiento.getAsuntoCifrado() != null && 
                               encryptionService.isEncrypted(movimiento.getAsuntoCifrado());
        boolean fechaCifrada = movimiento.getFechaCifrada() != null && 
                              encryptionService.isEncrypted(movimiento.getFechaCifrada());
        
        return cantidadCifrada && asuntoCifrado && fechaCifrada;
    }
    
    public Set<YearMonth> getMesesManuales(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            return mesManualRepository.findByUser(user).stream()
                .map(mes -> YearMonth.of(mes.getAnio(), mes.getMes()))
                .collect(java.util.stream.Collectors.toSet());
        }
        return Set.of();
    }
} */