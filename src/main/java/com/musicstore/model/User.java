package com.musicstore.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;


@Data
public class User {
    private Long id;

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;
}