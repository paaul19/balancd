package com.balancdapp.service;

import com.balancdapp.model.Movimiento;
import com.balancdapp.model.User;
import com.balancdapp.model.MesManual;
import com.balancdapp.repository.MovimientoRepository;
import com.balancdapp.repository.MesManualRepository;
import com.balancdapp.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;

@Service
@Transactional
public class MovimientoService {

    @Autowired
    private MovimientoRepository movimientoRepository;

    @Autowired
    private MesManualRepository mesManualRepository;

    @Autowired
    private UserRepository userRepository;

    public List<Movimiento> getMovimientosByUserId(Long userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            return movimientoRepository.findByUser(userOpt.get());
        }
        return List.of();
    }

    public List<Movimiento> getAllMovimientos() {
        return movimientoRepository.findAll();
    }

    public Movimiento addMovimiento(Movimiento movimiento) {
        // El usuario ya debe estar establecido en el movimiento
        if (movimiento.getUser() == null) {
            throw new RuntimeException("User cannot be null for movimiento");
        }
        return movimientoRepository.save(movimiento);
    }

    public void deleteMovimiento(Long movimientoId, Long userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            movimientoRepository.deleteByUserAndId(user, movimientoId);
        }
    }

    public Movimiento getMovimientoById(Long id) {
        return movimientoRepository.findById(id).orElse(null);
    }

    public void updateMovimiento(Movimiento movimientoActualizado) {
        if (movimientoActualizado.getId() == null) {
            throw new RuntimeException("Movimiento ID cannot be null for update");
        }

        Optional<Movimiento> existingOpt = movimientoRepository.findById(movimientoActualizado.getId());
        if (!existingOpt.isPresent()) {
            throw new RuntimeException("Movimiento not found with ID: " + movimientoActualizado.getId());
        }

        Movimiento existing = existingOpt.get();

        // Preservar el usuario si no se proporciona
        if (movimientoActualizado.getUser() == null) {
            movimientoActualizado.setUser(existing.getUser());
        }

        movimientoRepository.save(movimientoActualizado);
    }

    public void crearMesVacioSiNoExiste(Long userId, int mes, int anio) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (!userOpt.isPresent()) {
            throw new RuntimeException("User not found with ID: " + userId);
        }

        User user = userOpt.get();
        boolean existe = movimientoRepository.existsByUserAndMesAsignadoAndAnioAsignado(user, mes, anio);

        if (!existe) {
            Movimiento m = new Movimiento();
            m.setUser(user);
            m.setIngreso(true);
            m.setAsunto("Mes creado manualmente");
            m.setFecha(LocalDate.of(anio, mes, 1));
            m.setMesAsignado(mes);
            m.setAnioAsignado(anio);
            movimientoRepository.save(m);
        }
    }

    public void crearMesManual(Long userId, int mes, int anio) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (!userOpt.isPresent()) {
            throw new RuntimeException("User not found with ID: " + userId);
        }

        User user = userOpt.get();

        if (!mesManualRepository.existsByUserAndAnioAndMes(user, anio, mes)) {
            MesManual mesManual = new MesManual(user, anio, mes);
            mesManualRepository.save(mesManual);
        }
    }

    public Set<YearMonth> getMesesManuales(Long userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            List<Object[]> anioMesList = mesManualRepository.findAnioMesByUser(user);
            Set<YearMonth> yearMonths = new HashSet<>();

            for (Object[] anioMes : anioMesList) {
                Integer anio = (Integer) anioMes[0];
                Integer mes = (Integer) anioMes[1];
                yearMonths.add(YearMonth.of(anio, mes));
            }

            return yearMonths;
        }
        return Set.of();
    }

    public void eliminarMesManual(Long userId, int mes, int anio) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            mesManualRepository.deleteByUserAndAnioAndMes(user, anio, mes);
        }
    }
}
