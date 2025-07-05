package com.musicstore.service;

import com.musicstore.model.User;
import com.musicstore.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class UserService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordService passwordService;

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    public User saveUser(User user) {
        // Cifrar la contraseña si no está cifrada
        if (user.getPassword() != null && !passwordService.isEncoded(user.getPassword())) {
            user.setPassword(passwordService.encodePassword(user.getPassword()));
        }
        
        if (user.getId() == null) {
            if (userRepository.existsByUsername(user.getUsername())) {
                throw new RuntimeException("Username already exists");
            }
        } else {
            Optional<User> existingUser = userRepository.findById(user.getId());
            if (existingUser.isPresent()) {
                User existing = existingUser.get();
                // Verificar si el nuevo username ya existe en otro usuario
                if (!existing.getUsername().equals(user.getUsername()) && 
                    userRepository.existsByUsername(user.getUsername())) {
                    throw new RuntimeException("Username already exists");
                }
                
                // Preservar la contraseña si no se proporciona en la actualización
                if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
                    user.setPassword(existing.getPassword());
                }
            }
        }
        return userRepository.save(user);
    }

    public Optional<User> authenticateUser(String username, String password) {
        return userRepository.findByUsername(username)
                .filter(user -> passwordService.matches(password, user.getPassword()));
    }

    public User registerUser(User user) {
        if (user == null) {
            throw new RuntimeException("User cannot be null");
        }
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            throw new RuntimeException("Username cannot be empty");
        }
        if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
            throw new RuntimeException("Password cannot be empty");
        }
        return saveUser(user);
    }

    public User updateUser(User updatedUser) {
        if (updatedUser == null || updatedUser.getId() == null) {
            throw new RuntimeException("User or user ID cannot be null");
        }

        Optional<User> existingUserOpt = userRepository.findById(updatedUser.getId());
        if (!existingUserOpt.isPresent()) {
            throw new RuntimeException("User not found with ID: " + updatedUser.getId());
        }

        User existingUser = existingUserOpt.get();

        // Verificar si el nuevo username ya existe en otro usuario
        if (!existingUser.getUsername().equals(updatedUser.getUsername()) && 
            userRepository.existsByUsername(updatedUser.getUsername())) {
            throw new RuntimeException("Username already exists");
        }

        // Preservar la contraseña si no se proporciona en la actualización
        if (updatedUser.getPassword() == null || updatedUser.getPassword().trim().isEmpty()) {
            updatedUser.setPassword(existingUser.getPassword());
        } else if (!passwordService.isEncoded(updatedUser.getPassword())) {
            // Cifrar la nueva contraseña si no está cifrada
            updatedUser.setPassword(passwordService.encodePassword(updatedUser.getPassword()));
        }

        return userRepository.save(updatedUser);
    }
}