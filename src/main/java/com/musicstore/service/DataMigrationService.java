/*package com.musicstore.service;

import com.musicstore.model.Movimiento;
import com.musicstore.model.User;
import com.musicstore.repository.MovimientoRepository;
import com.musicstore.repository.UserRepository;
import com.musicstore.repository.MesManualRepository;
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
    /*
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
    
    private void migrateMovimientos() {
        try {
            File movimientosFile = new File("data/movimientos.json");
            List<Map<String, Object>> movimientosData = objectMapper.readValue(movimientosFile, List.class);
            for (Map<String, Object> movData : movimientosData) {
                Long userId = Long.valueOf(movData.get("userId").toString());
                Double cantidad = Double.valueOf(movData.get("cantidad").toString());
                Boolean ingreso = Boolean.valueOf(movData.get("ingreso").toString());
                String asunto = (String) movData.get("asunto");
                String fechaStr = (String) movData.get("fecha");
                Integer mesAsignado = Integer.valueOf(movData.get("mesAsignado").toString());
                Integer anioAsignado = Integer.valueOf(movData.get("anioAsignado").toString());
                
                User user = userRepository.findById(userId).orElse(null);
                if (user != null) {
                    Movimiento movimiento = new Movimiento();
                    movimiento.setUser(user);
                    movimiento.setIngreso(ingreso);
                    movimiento.setAsunto(asunto);
                    movimiento.setFecha(LocalDate.parse(fechaStr));
                    movimiento.setMesAsignado(mesAsignado);
                    movimiento.setAnioAsignado(anioAsignado);
                    movimientoRepository.save(movimiento);
                }
            }
            System.out.println("✅ Movimientos migrados desde JSON");
        } catch (IOException e) {
            System.err.println("❌ Error migrando movimientos: " + e.getMessage());
        }
    }
    
    private void migrateMesesManuales() {
        try {
            File mesesFile = new File("data/meses_creados.json");
            Map<String, List<Map<String, Object>>> mesesData = objectMapper.readValue(mesesFile, Map.class);
            for (Map.Entry<String, List<Map<String, Object>>> entry : mesesData.entrySet()) {
                Long userId = Long.valueOf(entry.getKey());
                List<Map<String, Object>> meses = entry.getValue();
                
                User user = userRepository.findById(userId).orElse(null);
                if (user != null) {
                    for (Map<String, Object> mesData : meses) {
                        Integer anio = Integer.valueOf(mesData.get("anio").toString());
                        Integer mes = Integer.valueOf(mesData.get("mes").toString());
                        
                        // Verificar si ya existe usando el método correcto
                        if (!mesManualRepository.existsByUserAndAnioAndMes(user, anio, mes)) {
                            com.musicstore.model.MesManual mesManual = new com.musicstore.model.MesManual();
                            mesManual.setUser(user);
                            mesManual.setAnio(anio);
                            mesManual.setMes(mes);
                            mesManualRepository.save(mesManual);
                        }
                    }
                }
            }
            System.out.println("✅ Meses manuales migrados desde JSON");
        } catch (IOException e) {
            System.err.println("❌ Error migrando meses manuales: " + e.getMessage());
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