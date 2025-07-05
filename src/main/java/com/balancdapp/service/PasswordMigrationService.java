package com.balancdapp.service;

import com.balancdapp.model.User;
import com.balancdapp.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Order(2)
public class PasswordMigrationService implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordService passwordService;

    @Override
    public void run(String... args) throws Exception {
        migratePasswords();
    }

    @Transactional
    public void migratePasswords() {
        List<User> users = userRepository.findAll();
        int migratedCount = 0;

        for (User user : users) {
            if (user.getPassword() != null && !passwordService.isEncoded(user.getPassword())) {
                // La contraseña está en texto plano, la ciframos
                String encodedPassword = passwordService.encodePassword(user.getPassword());
                user.setPassword(encodedPassword);
                userRepository.save(user);
                migratedCount++;
                System.out.println("Contraseña migrada para usuario: " + user.getUsername());
            }
        }

        if (migratedCount > 0) {
            System.out.println("Migración completada: " + migratedCount + " contraseñas cifradas");
        } else {
            System.out.println("No se encontraron contraseñas para migrar");
        }
    }
} 