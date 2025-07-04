package com.musicstore.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.musicstore.model.Movimiento;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.YearMonth;

@Service
public class MovimientoService {
    private final String FILE_PATH = "data/movimientos.json";
    private final String FILE_MESES_MANUALES = "data/meses_creados.json";
    private final ObjectMapper objectMapper;

    public MovimientoService() {
        this.objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    private void createFileIfNotExists() {
        File file = new File(FILE_PATH);
        if (!file.exists()) {
            try {
                // Asegurar que el directorio padre existe
                file.getParentFile().mkdirs();
                objectMapper.writeValue(file, new ArrayList<Movimiento>());
            } catch (IOException e) {
                throw new RuntimeException("No se pudo inicializar el archivo de movimientos", e);
            }
        }
    }

    public List<Movimiento> getMovimientosByUserId(Long userId) {
        return getAllMovimientos().stream()
                .filter(m -> m.getUserId().equals(userId))
                .collect(Collectors.toList());
    }

    public List<Movimiento> getAllMovimientos() {
        createFileIfNotExists();
        try {
            return objectMapper.readValue(new File(FILE_PATH), new TypeReference<List<Movimiento>>() {});
        } catch (IOException e) {
            throw new RuntimeException("No se pudieron leer los movimientos", e);
        }
    }

    public Movimiento addMovimiento(Movimiento movimiento) {
        List<Movimiento> movimientos = getAllMovimientos();
        movimiento.setId(generateId(movimientos));
        movimientos.add(movimiento);
        saveAllMovimientos(movimientos);
        return movimiento;
    }

    public void deleteMovimiento(Long movimientoId, Long userId) {
        List<Movimiento> movimientos = getAllMovimientos();
        movimientos.removeIf(m -> m.getId().equals(movimientoId) && m.getUserId().equals(userId));
        saveAllMovimientos(movimientos);
    }

    public Movimiento getMovimientoById(Long id) {
        return getAllMovimientos().stream()
                .filter(m -> m.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    public void updateMovimiento(Movimiento movimientoActualizado) {
        List<Movimiento> movimientos = getAllMovimientos();
        for (int i = 0; i < movimientos.size(); i++) {
            if (movimientos.get(i).getId().equals(movimientoActualizado.getId())) {
                movimientos.set(i, movimientoActualizado);
                break;
            }
        }
        saveAllMovimientos(movimientos);
    }

    private Long generateId(List<Movimiento> movimientos) {
        return movimientos.stream().mapToLong(Movimiento::getId).max().orElse(0L) + 1;
    }

    private void saveAllMovimientos(List<Movimiento> movimientos) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(FILE_PATH), movimientos);
        } catch (IOException e) {
            throw new RuntimeException("No se pudieron guardar los movimientos", e);
        }
    }

    public void crearMesVacioSiNoExiste(Long userId, int mes, int anio) {
        List<Movimiento> movimientos = getAllMovimientos();
        boolean existe = movimientos.stream().anyMatch(m -> m.getUserId().equals(userId) && m.getMesAsignado() == mes && m.getAnioAsignado() == anio);
        if (!existe) {
            Movimiento m = new Movimiento();
            m.setUserId(userId);
            m.setCantidad(0.0);
            m.setIngreso(true);
            m.setAsunto("Mes creado manualmente");
            m.setFecha(java.time.LocalDate.of(anio, mes, 1));
            m.setMesAsignado(mes);
            m.setAnioAsignado(anio);
            addMovimiento(m);
        }
    }

    // Estructura auxiliar para guardar meses manuales por usuario
    private Map<Long, Set<String>> leerMesesManuales() {
        createFileIfNotExistsMeses();
        try {
            return objectMapper.readValue(new File(FILE_MESES_MANUALES), new TypeReference<Map<Long, Set<String>>>() {});
        } catch (IOException e) {
            return new HashMap<>();
        }
    }

    private void guardarMesesManuales(Map<Long, Set<String>> data) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(FILE_MESES_MANUALES), data);
        } catch (IOException e) {
            throw new RuntimeException("No se pudieron guardar los meses manuales", e);
        }
    }

    private void createFileIfNotExistsMeses() {
        File file = new File(FILE_MESES_MANUALES);
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                objectMapper.writeValue(file, new HashMap<Long, Set<String>>());
            } catch (IOException e) {
                throw new RuntimeException("No se pudo inicializar el archivo de meses manuales", e);
            }
        }
    }

    public void crearMesManual(Long userId, int mes, int anio) {
        Map<Long, Set<String>> data = leerMesesManuales();
        String clave = anio + "-" + (mes < 10 ? ("0"+mes) : mes);
        data.computeIfAbsent(userId, k -> new HashSet<>()).add(clave);
        guardarMesesManuales(data);
    }

    public Set<YearMonth> getMesesManuales(Long userId) {
        Map<Long, Set<String>> data = leerMesesManuales();
        Set<YearMonth> res = new HashSet<>();
        if (data.containsKey(userId)) {
            for (String clave : data.get(userId)) {
                String[] partes = clave.split("-");
                int anio = Integer.parseInt(partes[0]);
                int mes = Integer.parseInt(partes[1]);
                res.add(YearMonth.of(anio, mes));
            }
        }
        return res;
    }

    public void eliminarMesManual(Long userId, int mes, int anio) {
        Map<Long, Set<String>> data = leerMesesManuales();
        String clave = anio + "-" + (mes < 10 ? ("0"+mes) : mes);
        if (data.containsKey(userId)) {
            data.get(userId).remove(clave);
            if (data.get(userId).isEmpty()) {
                data.remove(userId);
            }
            guardarMesesManuales(data);
        }
    }
}