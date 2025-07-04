package com.musicstore.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicstore.model.User;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;

@Service
public class UserService {
    private final String FILE_PATH = "data/users.json";
    private final ObjectMapper objectMapper;

    public UserService() {
        this.objectMapper = new ObjectMapper();
    }

    private void createFileIfNotExists() {
        File file = new File(FILE_PATH);
        if (!file.exists()) {
            try {
                // Asegurar que el directorio padre existe
                file.getParentFile().mkdirs();
                objectMapper.writeValue(file, new ArrayList<User>());
            } catch (IOException e) {
                throw new RuntimeException("Could not initialize storage file", e);
            }
        }
    }

    public List<User> getAllUsers() {
        createFileIfNotExists();
        try {
            return objectMapper.readValue(new File(FILE_PATH), new TypeReference<List<User>>() {});
        } catch (IOException e) {
            throw new RuntimeException("Could not read users", e);
        }
    }

    public Optional<User> getUserByUsername(String username) {
        return getAllUsers().stream()
                .filter(user -> user.getUsername().equals(username))
                .findFirst();
    }

    public Optional<User> getUserById(Long id) {
        return getAllUsers().stream()
                .filter(user -> user.getId().equals(id))
                .findFirst();
    }

    public User saveUser(User user) {
        List<User> users = getAllUsers();
        if (user.getId() == null) {
            if (getUserByUsername(user.getUsername()).isPresent()) {
                throw new RuntimeException("Username already exists");
            }
            user.setId(generateId(users));
            users.add(user);
        } else {
            int index = findUserIndex(users, user.getId());
            if (index != -1) {
                users.set(index, user);
            } else {
                users.add(user);
            }
        }
        saveAllUsers(users);
        return user;
    }

    public Optional<User> authenticateUser(String username, String password) {
        return getAllUsers().stream()
                .filter(user -> user.getUsername().equals(username) 
                        && user.getPassword().equals(password))
                .findFirst();
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

    private Long generateId(List<User> users) {
        return users.stream()
                .mapToLong(User::getId)
                .max()
                .orElse(0L) + 1;
    }

    private int findUserIndex(List<User> users, Long id) {
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).getId().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private void saveAllUsers(List<User> users) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(FILE_PATH), users);
        } catch (IOException e) {
            throw new RuntimeException("Could not save users", e);
        }
    }
    public User updateUser(User updatedUser) {
        if (updatedUser == null || updatedUser.getId() == null) {
            throw new RuntimeException("User or user ID cannot be null");
        }

        List<User> users = getAllUsers();
        int userIndex = findUserIndex(users, updatedUser.getId());

        if (userIndex == -1) {
            throw new RuntimeException("User not found with ID: " + updatedUser.getId());
        }

        // Get the existing user to preserve data that shouldn't be updated
        User existingUser = users.get(userIndex);

        // Check if the new username is already taken by another user
        boolean usernameExists = users.stream()
                .filter(user -> !user.getId().equals(updatedUser.getId()))
                .anyMatch(user -> user.getUsername().equals(updatedUser.getUsername()));

        if (usernameExists) {
            throw new RuntimeException("Username already exists");
        }

        // Preserve the password if not provided in the update
        if (updatedUser.getPassword() == null || updatedUser.getPassword().trim().isEmpty()) {
            updatedUser.setPassword(existingUser.getPassword());
        }

        // Update the user
        users.set(userIndex, updatedUser);
        saveAllUsers(users);

        return updatedUser;
    }
}