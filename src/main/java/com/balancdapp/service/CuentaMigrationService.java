package com.balancdapp.service;

import com.balancdapp.model.Cuenta;
import com.balancdapp.model.Movimiento;
import com.balancdapp.model.MovimientoRecurrente;
import com.balancdapp.model.TipoCuenta;
import com.balancdapp.model.User;
import com.balancdapp.repository.CuentaRepository;
import com.balancdapp.repository.MovimientoRecurrenteRepository;
import com.balancdapp.repository.MovimientoRepository;
import com.balancdapp.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Migración idempotente: cualquier movimiento/recurrente ya existente que no tenga
 * cuenta asignada (porque se creó antes del sistema de cuentas) se asocia automáticamente
 * a una "Cuenta principal" del usuario, sin duplicar ni perder ningún dato.
 * Sigue el mismo patrón que RecurrenteMigrationService (@Order(3)).
 */
@Service
@Order(4)
public class CuentaMigrationService implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CuentaRepository cuentaRepository;

    @Autowired
    private MovimientoRepository movimientoRepository;

    @Autowired
    private MovimientoRecurrenteRepository movimientoRecurrenteRepository;

    @Autowired
    private EncryptedCuentaService encryptedCuentaService;

    private static final String NOMBRE_CUENTA_PRINCIPAL = "Cuenta principal";

    @Override
    public void run(String... args) {
        migrarCuentasPorDefecto();
    }

    @Transactional
    public void migrarCuentasPorDefecto() {
        System.out.println("🔄 Comprobando migración de movimientos a cuentas...");
        int usuariosMigrados = 0;

        for (User user : userRepository.findAll()) {
            List<Movimiento> movimientosSinCuenta = movimientoRepository.findByUserAndCuentaIsNull(user);
            List<MovimientoRecurrente> recurrentesSinCuenta = movimientoRecurrenteRepository.findByUserAndCuentaIsNull(user);

            if (movimientosSinCuenta.isEmpty() && recurrentesSinCuenta.isEmpty()) {
                continue;
            }

            Cuenta cuentaPrincipal = obtenerOCrearCuentaPrincipal(user);

            for (Movimiento m : movimientosSinCuenta) {
                m.setCuenta(cuentaPrincipal);
                movimientoRepository.save(m);
            }
            for (MovimientoRecurrente r : recurrentesSinCuenta) {
                r.setCuenta(cuentaPrincipal);
                movimientoRecurrenteRepository.save(r);
            }
            usuariosMigrados++;
            System.out.println("✅ Usuario " + user.getUsername() + ": " + movimientosSinCuenta.size()
                    + " movimientos y " + recurrentesSinCuenta.size() + " recurrentes asociados a \"" + NOMBRE_CUENTA_PRINCIPAL + "\".");
        }

        if (usuariosMigrados > 0) {
            System.out.println("📊 Migración de cuentas completada: " + usuariosMigrados + " usuario(s) actualizados.");
        } else {
            System.out.println("ℹ️ No se encontraron movimientos pendientes de migrar a cuentas.");
        }
    }

    private Cuenta obtenerOCrearCuentaPrincipal(User user) {
        Optional<Cuenta> existente = cuentaRepository.findByUser(user).stream()
                .filter(c -> NOMBRE_CUENTA_PRINCIPAL.equalsIgnoreCase(c.getNombre()))
                .findFirst();
        if (existente.isPresent()) {
            return existente.get();
        }
        // Saldo inicial 0: el histórico migrado ya representa el saldo completo,
        // así que el saldo derivado de la cuenta no cambia ni un céntimo.
        return encryptedCuentaService.crearCuenta(user, NOMBRE_CUENTA_PRINCIPAL, TipoCuenta.OTRA, 0.0);
    }
}
