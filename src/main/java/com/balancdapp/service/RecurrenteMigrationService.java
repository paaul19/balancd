package com.balancdapp.service;

import com.balancdapp.model.MovimientoRecurrente;
import com.balancdapp.repository.MovimientoRecurrenteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Order(3)
public class RecurrenteMigrationService implements CommandLineRunner {

    @Autowired
    private MovimientoRecurrenteRepository movimientoRecurrenteRepository;

    @Autowired
    private DataEncryptionService encryptionService;

    @Override
    public void run(String... args) throws Exception {
        migrateRecurrentes();
    }

    @Transactional
    public void migrateRecurrentes() {
        System.out.println("🔄 Iniciando migración de movimientos recurrentes a formato cifrado...");

        try {
            List<MovimientoRecurrente> recurrentes = movimientoRecurrenteRepository.findAll();
            int migratedCount = 0;

            for (MovimientoRecurrente recurrente : recurrentes) {
                try {
                    // Verificar si ya está cifrado
                    if (isAlreadyEncrypted(recurrente)) {
                        continue;
                    }

                    // Migrar cantidad (usar el campo original si existe)
                    if (recurrente.getCantidadCifrada() == null) {
                        // Intentar obtener cantidad del campo original (si existe)
                        double cantidadOriginal = 0.0;
                        try {
                            // Usar reflexión para acceder al campo original si existe
                            java.lang.reflect.Field cantidadField = MovimientoRecurrente.class.getDeclaredField("cantidad");
                            cantidadField.setAccessible(true);
                            cantidadOriginal = (Double) cantidadField.get(recurrente);
                        } catch (Exception e) {
                            // Campo no existe, usar valor por defecto
                            cantidadOriginal = 0.0;
                        }

                        if (cantidadOriginal != 0.0) {
                            String cantidadCifrada = encryptionService.encryptNumber(cantidadOriginal);
                            recurrente.setCantidadCifrada(cantidadCifrada);
                        }
                    }

                    // Migrar asunto (usar el campo original si existe)
                    if (recurrente.getAsuntoCifrado() == null) {
                        String asuntoOriginal = "";
                        try {
                            // Usar reflexión para acceder al campo original si existe
                            java.lang.reflect.Field asuntoField = MovimientoRecurrente.class.getDeclaredField("asunto");
                            asuntoField.setAccessible(true);
                            asuntoOriginal = (String) asuntoField.get(recurrente);
                        } catch (Exception e) {
                            // Campo no existe, usar valor por defecto
                            asuntoOriginal = "";
                        }

                        if (asuntoOriginal != null && !asuntoOriginal.isEmpty()) {
                            String asuntoCifrado = encryptionService.encrypt(asuntoOriginal);
                            recurrente.setAsuntoCifrado(asuntoCifrado);
                        }
                    }

                    // Guardar el movimiento recurrente actualizado
                    movimientoRecurrenteRepository.save(recurrente);
                    migratedCount++;

                } catch (Exception e) {
                    System.err.println("❌ Error migrando movimiento recurrente ID " + recurrente.getId() + ": " + e.getMessage());
                }
            }

            if (migratedCount > 0) {
                System.out.println("📊 Migrados " + migratedCount + " movimientos recurrentes a formato cifrado.");
            } else {
                System.out.println("ℹ️ No se encontraron movimientos recurrentes para migrar.");
            }

        } catch (Exception e) {
            System.err.println("❌ Error general en migración de recurrentes: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean isAlreadyEncrypted(MovimientoRecurrente recurrente) {
        // Verificar si los campos ya están cifrados
        boolean cantidadCifrada = recurrente.getCantidadCifrada() != null &&
                encryptionService.isEncrypted(recurrente.getCantidadCifrada());
        boolean asuntoCifrado = recurrente.getAsuntoCifrado() != null &&
                encryptionService.isEncrypted(recurrente.getAsuntoCifrado());

        return cantidadCifrada && asuntoCifrado;
    }
} 