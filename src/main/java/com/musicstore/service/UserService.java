package com.musicstore.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicstore.model.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {
    private final String FILE_PATH = "data/users.json";
    private final ObjectMapper objectMapper;

    public UserService() {
        this.objectMapper = new ObjectMapper();
        createFileIfNotExists();
    }

    private void createFileIfNotExists() {
        File file = new File(FILE_PATH);
        if (!file.exists()) {
            try {
                objectMapper.writeValue(file, new ArrayList<User>());
            } catch (IOException e) {
                throw new RuntimeException("Could not initialize storage file", e);
            }
        }
    }

    public List<User> getAllUsers() {
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

    public void addFavoriteAlbum(Long userId, Long albumId, HttpSession session) {
        List<User> users = getAllUsers();

        Optional<User> optionalUser = users.stream()
                .filter(user -> user.getId().equals(userId))
                .findFirst();

        if (optionalUser.isPresent()) {
            User user = optionalUser.get();

            if (!user.getFavoriteAlbumIds().contains(albumId)) {
                user.getFavoriteAlbumIds().add(albumId);

                saveAllUsers(users);

                session.setAttribute("user", user);
            }
        } else {
            throw new IllegalArgumentException("Usuario no encontrado: " + userId);
        }
    }

    public List<Long> getFavoriteAlbums(String username) {
        Optional<User> userOpt = getUserByUsername(username);
        if (userOpt.isPresent()) {
            return userOpt.get().getFavoriteAlbumIds();
        }
        return new ArrayList<>();
    }

    public void deleteFavoriteAlbum(Long userId, Long albumId, HttpSession session) {
        List<User> users = getAllUsers();

        Optional<User> optionalUser = users.stream()
                .filter(user -> user.getId().equals(userId))
                .findFirst();

        if (optionalUser.isPresent()) {
            User user = optionalUser.get();

            if (user.getFavoriteAlbumIds().contains(albumId)) {
                user.getFavoriteAlbumIds().remove(albumId);

                saveAllUsers(users);

                session.setAttribute("user", user);
            } else {
                throw new IllegalArgumentException("El álbum no está en los favoritos de este usuario.");
            }
        } else {
            throw new IllegalArgumentException("Usuario no encontrado: " + userId);
        }
    }

    public void saveUserWithProfileImage(User user, MultipartFile profileImage) throws IOException {
        if (profileImage == null || profileImage.isEmpty()) {
            throw new RuntimeException("Profile image cannot be null or empty");
        }

        // Validate file type
        String contentType = profileImage.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new RuntimeException("Only image files are allowed");
        }

        // Generate a unique filename
        String originalFilename = profileImage.getOriginalFilename();
        String fileExtension = originalFilename != null ? originalFilename.substring(originalFilename.lastIndexOf(".")) : ".jpg";
        String filename = "user_" + user.getId() + "_" + System.currentTimeMillis() + fileExtension;

        // Create images directory if it doesn't exist
        String projectDir = System.getProperty("user.dir");
        File uploadDir = new File(projectDir + "/src/main/resources/static/images");
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

        // Save the file
        File destFile = new File(uploadDir.getAbsolutePath() + File.separator + filename);
        profileImage.transferTo(destFile);

        // Update user with image URL
        user.setImageUrl("/images/" + filename);
        updateUser(user);
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

    public void followUser(Long followerId, Long targetUserId, HttpSession session) {
        if (followerId.equals(targetUserId)) {
            throw new RuntimeException("Users cannot follow themselves");
        }

        List<User> users = getAllUsers();
        User follower = getUserById(followerId)
                .orElseThrow(() -> new RuntimeException("Follower user not found"));
        User target = getUserById(targetUserId)
                .orElseThrow(() -> new RuntimeException("Target user not found"));

        if (!follower.getFollowing().contains(targetUserId)) {
            follower.getFollowing().add(targetUserId);
            target.getFollowers().add(followerId);

            // Update both users
            updateUser(follower);
            updateUser(target);

            // Update session with the updated follower user
            session.setAttribute("user", follower);
        }
    }

    public void unfollowUser(Long followerId, Long targetUserId, HttpSession session) {
        List<User> users = getAllUsers();
        User follower = getUserById(followerId)
                .orElseThrow(() -> new RuntimeException("Follower user not found"));
        User target = getUserById(targetUserId)
                .orElseThrow(() -> new RuntimeException("Target user not found"));

        if (follower.getFollowing().contains(targetUserId)) {
            follower.getFollowing().remove(targetUserId);
            target.getFollowers().remove(followerId);

            // Update both users
            updateUser(follower);
            updateUser(target);

            // Update session with the updated follower user
            session.setAttribute("user", follower);
        }
    }

    public boolean isFollowing(Long followerId, Long targetUserId) {
        return getUserById(followerId)
                .map(user -> user.getFollowing().contains(targetUserId))
                .orElse(false);
    }
}