package com.musicstore.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicstore.model.Asunto;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AsuntoService {
    private final String FILE_PATH = "data/asuntos.json";
    private final ObjectMapper objectMapper;

    public AsuntoService() {
        this.objectMapper = new ObjectMapper();
        createFileIfNotExists();
    }

    private void createFileIfNotExists() {
        File file = new File(FILE_PATH);
        if (!file.exists()) {
            try {
                objectMapper.writeValue(file, new ArrayList<Asunto>());
            } catch (IOException e) {
                throw new RuntimeException("No se pudo inicializar el archivo de asuntos", e);
            }
        }
    }

    public List<Asunto> getAsuntosByUserId(Long userId) {
        return getAllAsuntos().stream()
                .filter(a -> a.getUserId().equals(userId))
                .collect(Collectors.toList());
    }

    public List<Asunto> getAllAsuntos() {
        try {
            return objectMapper.readValue(new File(FILE_PATH), new TypeReference<List<Asunto>>() {});
        } catch (IOException e) {
            throw new RuntimeException("No se pudieron leer los asuntos", e);
        }
    }

    public Asunto addAsunto(Asunto asunto) {
        List<Asunto> asuntos = getAllAsuntos();
        asunto.setId(generateId(asuntos));
        asuntos.add(asunto);
        saveAllAsuntos(asuntos);
        return asunto;
    }

    private Long generateId(List<Asunto> asuntos) {
        return asuntos.stream().mapToLong(Asunto::getId).max().orElse(0L) + 1;
    }

    private void saveAllAsuntos(List<Asunto> asuntos) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(FILE_PATH), asuntos);
        } catch (IOException e) {
            throw new RuntimeException("No se pudieron guardar los asuntos", e);
        }
    }
} 