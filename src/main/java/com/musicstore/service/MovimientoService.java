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

@Service
public class MovimientoService {
    private final String FILE_PATH = "data/movimientos.json";
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
}