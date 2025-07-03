package com.musicstore;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

@SpringBootApplication
public class MusicStoreApplication {

	public static void main(String[] args) {
		SpringApplication.run(MusicStoreApplication.class, args);
	}

	@Component
	public static class DataInitializer implements CommandLineRunner {
		
		@Override
		public void run(String... args) throws Exception {
			initializeDataFiles();
		}
		
		private void initializeDataFiles() {
			ObjectMapper mapper = new ObjectMapper();
			
			// Lista de archivos JSON que necesitan ser inicializados
			String[] files = {
				"data/movimientos.json",
				"data/asuntos.json", 
				"data/users.json"
			};
			
			for (String filePath : files) {
				Path path = Paths.get(filePath);
				
				try {
					// Crea la carpeta si no existe
					Files.createDirectories(path.getParent());
					
					// Crea el archivo si no existe
					if (!Files.exists(path)) {
						mapper.writeValue(path.toFile(), new ArrayList<>());
						System.out.println("Archivo creado: " + filePath);
					}
				} catch (IOException e) {
					System.err.println("Error al inicializar " + filePath + ": " + e.getMessage());
				}
			}
		}
	}
}
