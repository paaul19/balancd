package com.balancdapp.service;

import com.balancdapp.model.User;
import com.balancdapp.model.VerificationToken;
import com.balancdapp.repository.UserRepository;
import com.balancdapp.repository.VerificationTokenRepository;
import com.balancdapp.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private EmailService emailService;

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
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

    public Optional<User> authenticateUser(String identifier, String password) {
        // Permitir login por email o username
        Optional<User> userOpt = identifier.contains("@") ? userRepository.findByEmail(identifier) : userRepository.findByUsername(identifier);
        return userOpt.filter(user -> passwordService.matches(password, user.getPassword()))
                .filter(User::isVerified);
    }

    public User registerUser(User user) {
        if (user == null) {
            throw new RuntimeException("User cannot be null");
        }
        if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            throw new RuntimeException("Email cannot be empty");
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Email already in use");
        }
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            throw new RuntimeException("Username cannot be empty");
        }
        if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
            throw new RuntimeException("Password cannot be empty");
        }
        user.setVerified(false);
        User savedUser = saveUser(user);
        // Generar token y guardar
        String token = UUID.randomUUID().toString();
        VerificationToken verificationToken = new VerificationToken();
        verificationToken.setToken(token);
        verificationToken.setUser(savedUser);
        verificationTokenRepository.save(verificationToken);
        String verificationUrl = "https://balancd.site:8080/verify?token=" + token;
        try {
            emailService.sendVerificationEmail(savedUser.getEmail(), verificationUrl);
        } catch (Exception e) {
            System.err.println("Error sending verification email: " + e.getMessage());
            // No lanzar excepción, solo loguear
        }
        return savedUser;
    }

    public boolean verifyUser(String token) {
        VerificationToken verificationToken = verificationTokenRepository.findByToken(token).orElse(null);
        if (verificationToken == null) {
            return false;
        }
        User user = verificationToken.getUser();
        user.setVerified(true);
        saveUser(user);
        verificationTokenRepository.delete(verificationToken);
        return true;
    }

    public boolean sendPasswordResetToken(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return false;
        }
        User user = userOpt.get();
        String token = UUID.randomUUID().toString();
        VerificationToken resetToken = new VerificationToken();
        resetToken.setToken(token);
        resetToken.setUser(user);
        verificationTokenRepository.save(resetToken);
        String resetUrl = "https://balancd.site/reset-password?token=" + token;
        try {
            emailService.sendPasswordResetEmail(user.getEmail(), resetUrl);
        } catch (Exception e) {
            System.err.println("Error sending password reset email: " + e.getMessage());
            // No lanzar excepción, solo loguear
        }
        return true;
    }

    public boolean isValidResetToken(String token) {
        return verificationTokenRepository.findByToken(token).isPresent();
    }

    public boolean resetPassword(String token, String newPassword) {
        Optional<VerificationToken> tokenOpt = verificationTokenRepository.findByToken(token);
        if (tokenOpt.isEmpty()) {
            return false;
        }
        VerificationToken verificationToken = tokenOpt.get();
        User user = verificationToken.getUser();
        user.setPassword(newPassword);
        saveUser(user);
        verificationTokenRepository.delete(verificationToken);
        return true;
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