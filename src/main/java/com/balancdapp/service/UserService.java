package com.balancdapp.service;

import com.balancdapp.model.User;
import com.balancdapp.model.VerificationToken;
import com.balancdapp.repository.UserRepository;
import com.balancdapp.repository.VerificationTokenRepository;
import com.balancdapp.repository.MovimientoRepository;
import com.balancdapp.service.EmailService;
import com.balancdapp.service.PasswordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;

@Service
@Transactional
public class UserService {

    // Hallazgo M2: longitud mínima de contraseña, aplicada en backend en todos los flujos
    // (registro, reset y cambio de contraseña). Sin límite máximo artificial ni reglas de
    // composición (mayúsculas/números/símbolos obligatorios): la longitud es el factor que
    // más aporta a la resistencia frente a fuerza bruta sin penalizar la usabilidad.
    public static final int MIN_PASSWORD_LENGTH = 8;

    // Hallazgo H5: expiración razonable y distinta para cada tipo de token.
    private static final long EMAIL_VERIFICATION_HOURS = 48;
    private static final long PASSWORD_RESET_HOURS = 1;
    private static final long EMAIL_CHANGE_HOURS = 24;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private MovimientoRepository movimientoRepository;

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
        // Los "exists" de arriba comprueban-y-luego-actúan: dos altas casi simultáneas (doble
        // toque en "Registrarse", o un registro por duplicado) pueden pasar ambas esa comprobación
        // antes de que ninguna haya hecho commit todavía, y la segunda choca con la restricción
        // UNIQUE de la base de datos al guardar. Sin este catch, esa excepción técnica (con el
        // SQL y el nombre de la restricción) se mostraba tal cual al usuario - se traduce aquí al
        // mismo mensaje limpio que el chequeo de arriba, venga de donde venga la carrera.
        try {
            return userRepository.save(user);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            String detalle = e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage();
            if (detalle != null && detalle.toLowerCase().contains("email")) {
                throw new RuntimeException("Email already in use");
            }
            throw new RuntimeException("Username already exists");
        }
    }

    public Optional<User> authenticateUser(String identifier, String password) {
        // Permitir login por email o username
        Optional<User> userOpt = identifier.contains("@") ? userRepository.findByEmail(identifier) : userRepository.findByUsername(identifier);
        return userOpt.filter(user -> passwordService.matches(password, user.getPassword()))
                .filter(User::isVerified);
    }

    /** Hallazgo M2: aplicado en todos los flujos que fijan una contraseña nueva. */
    public void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new RuntimeException("La contraseña debe tener al menos " + MIN_PASSWORD_LENGTH + " caracteres");
        }
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
        validatePassword(user.getPassword());
        user.setVerified(false);
        User savedUser = saveUser(user);
        // Generar token y guardar
        String token = UUID.randomUUID().toString();
        VerificationToken verificationToken = new VerificationToken();
        verificationToken.setToken(token);
        verificationToken.setUser(savedUser);
        verificationToken.setTipo(VerificationToken.TipoToken.EMAIL_VERIFICATION);
        verificationToken.setExpiresAt(LocalDateTime.now().plusHours(EMAIL_VERIFICATION_HOURS));
        verificationTokenRepository.save(verificationToken);
        String verificationUrl = "https://balancd.es/verify?token=" + token;
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
        // Hallazgo H5: un token solo es válido para verificar un email si es del tipo correcto
        // y no ha expirado - así un enlace de reset de contraseña nunca sirve para esto.
        if (verificationToken == null
                || verificationToken.getTipo() != VerificationToken.TipoToken.EMAIL_VERIFICATION
                || verificationToken.isExpired()) {
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
            // Hallazgo M6: no se distingue en el valor de retorno si el email existe o no;
            // el llamador (AuthController) siempre muestra el mismo mensaje al usuario.
            return false;
        }
        User user = userOpt.get();
        String token = UUID.randomUUID().toString();
        VerificationToken resetToken = new VerificationToken();
        resetToken.setToken(token);
        resetToken.setUser(user);
        resetToken.setTipo(VerificationToken.TipoToken.PASSWORD_RESET);
        resetToken.setExpiresAt(LocalDateTime.now().plusHours(PASSWORD_RESET_HOURS));
        verificationTokenRepository.save(resetToken);
        String resetUrl = "https://balancd.es/reset-password?token=" + token;
        try {
            emailService.sendPasswordResetEmail(user.getEmail(), resetUrl);
        } catch (Exception e) {
            System.err.println("Error sending password reset email: " + e.getMessage());
            // No lanzar excepción, solo loguear
        }
        return true;
    }

    public boolean isValidResetToken(String token) {
        return verificationTokenRepository.findByToken(token)
                .filter(t -> t.getTipo() == VerificationToken.TipoToken.PASSWORD_RESET)
                .filter(t -> !t.isExpired())
                .isPresent();
    }

    public boolean resetPassword(String token, String newPassword) {
        Optional<VerificationToken> tokenOpt = verificationTokenRepository.findByToken(token)
                .filter(t -> t.getTipo() == VerificationToken.TipoToken.PASSWORD_RESET)
                .filter(t -> !t.isExpired());
        if (tokenOpt.isEmpty()) {
            return false;
        }
        validatePassword(newPassword);
        VerificationToken verificationToken = tokenOpt.get();
        User user = verificationToken.getUser();
        user.setPassword(newPassword);
        saveUser(user);
        verificationTokenRepository.delete(verificationToken);
        return true;
    }

    /**
     * Hallazgo M5: cambiar el email ya no lo aplica al instante. Se envía un enlace de
     * confirmación a la dirección NUEVA y el cambio solo se aplica cuando ese enlace se
     * visita - así, aunque alguien comprometa la sesión/cuenta, no puede secuestrarla de
     * forma silenciosa apuntando la recuperación de cuenta a un correo propio.
     */
    public void requestEmailChange(User user, String newEmail) {
        if (newEmail == null || newEmail.trim().isEmpty()) {
            throw new RuntimeException("El correo electrónico no puede estar vacío");
        }
        if (userRepository.existsByEmail(newEmail)) {
            throw new RuntimeException("Ese correo electrónico ya está en uso");
        }
        String token = UUID.randomUUID().toString();
        VerificationToken changeToken = new VerificationToken();
        changeToken.setToken(token);
        changeToken.setUser(user);
        changeToken.setTipo(VerificationToken.TipoToken.EMAIL_CHANGE);
        changeToken.setPayload(newEmail);
        changeToken.setExpiresAt(LocalDateTime.now().plusHours(EMAIL_CHANGE_HOURS));
        verificationTokenRepository.save(changeToken);
        String confirmUrl = "https://balancd.es/confirmar-cambio-email?token=" + token;
        try {
            emailService.sendEmailChangeConfirmation(newEmail, confirmUrl);
        } catch (Exception e) {
            System.err.println("Error sending email change confirmation: " + e.getMessage());
        }
    }

    /** @return el usuario cuyo email se acaba de confirmar, o null si el token no es válido. */
    public User confirmEmailChange(String token) {
        VerificationToken changeToken = verificationTokenRepository.findByToken(token).orElse(null);
        if (changeToken == null
                || changeToken.getTipo() != VerificationToken.TipoToken.EMAIL_CHANGE
                || changeToken.isExpired()) {
            return null;
        }
        String newEmail = changeToken.getPayload();
        if (newEmail == null || userRepository.existsByEmail(newEmail)) {
            // El email pudo quedar ocupado por otra cuenta entre la petición y la confirmación.
            verificationTokenRepository.delete(changeToken);
            return null;
        }
        User user = changeToken.getUser();
        user.setEmail(newEmail);
        User saved = saveUser(user);
        verificationTokenRepository.delete(changeToken);
        return saved;
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

    public void marcarTutorialVisto(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setTutorialVisto(true);
            userRepository.save(user);
        });
    }
}