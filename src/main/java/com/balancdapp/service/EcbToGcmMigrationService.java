package com.balancdapp.service;

import com.balancdapp.model.Cuenta;
import com.balancdapp.model.Movimiento;
import com.balancdapp.model.MovimientoRecurrente;
import com.balancdapp.model.Objetivo;
import com.balancdapp.model.Presupuesto;
import com.balancdapp.model.Transferencia;
import com.balancdapp.repository.CuentaRepository;
import com.balancdapp.repository.MovimientoRecurrenteRepository;
import com.balancdapp.repository.MovimientoRepository;
import com.balancdapp.repository.ObjetivoRepository;
import com.balancdapp.repository.PresupuestoRepository;
import com.balancdapp.repository.TransferenciaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Function;

/**
 * Hallazgo H4: migra en caliente, de forma idempotente, todos los valores cifrados que
 * todavía estén en el formato legacy AES/ECB al nuevo formato AES/GCM.
 *
 * Estrategia (tal como pide la auditoría): 1) detectar formato antiguo (sin el prefijo
 * "GCM:"), 2) descifrarlo (DataEncryptionService.decrypt ya sabe leer ambos formatos),
 * 3) volver a cifrarlo (DataEncryptionService.encrypt siempre escribe el formato nuevo),
 * 4) guardar. Ningún valor se pierde: se decodifica exactamente el mismo texto claro que
 * había antes, solo cambia cómo queda cifrado en la base de datos.
 *
 * Se ejecuta después de las demás migraciones de datos (@Order 1-6) para no interferir con
 * ellas, y es seguro relanzarlo en cada arranque: las filas ya migradas (con el prefijo
 * "GCM:") se detectan y se saltan sin volver a tocarlas.
 */
@Service
@Order(7)
public class EcbToGcmMigrationService implements CommandLineRunner {

    @Autowired
    private DataEncryptionService encryptionService;
    @Autowired
    private MovimientoRepository movimientoRepository;
    @Autowired
    private MovimientoRecurrenteRepository movimientoRecurrenteRepository;
    @Autowired
    private CuentaRepository cuentaRepository;
    @Autowired
    private TransferenciaRepository transferenciaRepository;
    @Autowired
    private PresupuestoRepository presupuestoRepository;
    @Autowired
    private ObjetivoRepository objetivoRepository;

    @Override
    public void run(String... args) {
        migrar();
    }

    @Transactional
    public void migrar() {
        System.out.println("🔐 Comprobando migración de cifrado AES/ECB -> AES/GCM...");
        int total = 0;

        total += migrarLista(movimientoRepository.findAll(), Movimiento::getCantidadCifrada, Movimiento::setCantidadCifrada,
                Movimiento::getAsuntoCifrado, Movimiento::setAsuntoCifrado, Movimiento::getFechaCifrada, Movimiento::setFechaCifrada,
                movimientoRepository::save);

        total += migrarLista(movimientoRecurrenteRepository.findAll(), MovimientoRecurrente::getCantidadCifrada, MovimientoRecurrente::setCantidadCifrada,
                MovimientoRecurrente::getAsuntoCifrado, MovimientoRecurrente::setAsuntoCifrado, null, null,
                movimientoRecurrenteRepository::save);

        total += migrarUnCampo(cuentaRepository.findAll(), Cuenta::getSaldoInicialCifrado, Cuenta::setSaldoInicialCifrado, cuentaRepository::save);

        for (Transferencia t : transferenciaRepository.findAll()) {
            boolean cambiado = false;
            if (encryptionService.isLegacyFormat(t.getImporteCifrado())) {
                t.setImporteCifrado(encryptionService.encrypt(encryptionService.decrypt(t.getImporteCifrado())));
                cambiado = true;
            }
            if (t.getDescripcionCifrada() != null && encryptionService.isLegacyFormat(t.getDescripcionCifrada())) {
                t.setDescripcionCifrada(encryptionService.encrypt(encryptionService.decrypt(t.getDescripcionCifrada())));
                cambiado = true;
            }
            if (cambiado) {
                transferenciaRepository.save(t);
                total++;
            }
        }

        total += migrarUnCampo(presupuestoRepository.findAll(), Presupuesto::getLimiteCifrado, Presupuesto::setLimiteCifrado, presupuestoRepository::save);
        total += migrarUnCampo(objetivoRepository.findAll(), Objetivo::getObjetivoCifrado, Objetivo::setObjetivoCifrado, objetivoRepository::save);

        if (total > 0) {
            System.out.println("✅ Migración AES/GCM completada: " + total + " fila(s) re-cifradas.");
        } else {
            System.out.println("ℹ️ No había valores cifrados en formato legacy pendientes de migrar.");
        }
    }

    private <T> int migrarUnCampo(List<T> filas, Function<T, String> getter, java.util.function.BiConsumer<T, String> setter,
                                   Function<T, T> saver) {
        int count = 0;
        for (T fila : filas) {
            String valor = getter.apply(fila);
            if (valor != null && encryptionService.isLegacyFormat(valor)) {
                setter.accept(fila, encryptionService.encrypt(encryptionService.decrypt(valor)));
                saver.apply(fila);
                count++;
            }
        }
        return count;
    }

    private <T> int migrarLista(
            List<T> filas,
            Function<T, String> getA, java.util.function.BiConsumer<T, String> setA,
            Function<T, String> getB, java.util.function.BiConsumer<T, String> setB,
            Function<T, String> getC, java.util.function.BiConsumer<T, String> setC,
            Function<T, T> saver) {
        int count = 0;
        for (T fila : filas) {
            boolean cambiado = false;
            String a = getA.apply(fila);
            if (a != null && encryptionService.isLegacyFormat(a)) {
                setA.accept(fila, encryptionService.encrypt(encryptionService.decrypt(a)));
                cambiado = true;
            }
            String b = getB.apply(fila);
            if (b != null && encryptionService.isLegacyFormat(b)) {
                setB.accept(fila, encryptionService.encrypt(encryptionService.decrypt(b)));
                cambiado = true;
            }
            if (getC != null) {
                String c = getC.apply(fila);
                if (c != null && encryptionService.isLegacyFormat(c)) {
                    setC.accept(fila, encryptionService.encrypt(encryptionService.decrypt(c)));
                    cambiado = true;
                }
            }
            if (cambiado) {
                saver.apply(fila);
                count++;
            }
        }
        return count;
    }
}
