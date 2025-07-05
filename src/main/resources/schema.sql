-- Script de inicialización de la base de datos
-- Este script se ejecutará automáticamente al iniciar la aplicación

-- Crear tabla de usuarios si no existe
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL
);

-- Eliminar tabla movimientos si existe (para recrearla con la estructura correcta)

-- Crear tabla de movimientos con campos cifrados
CREATE TABLE IF NOT EXISTS movimientos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    cantidad_cifrada VARCHAR(255) NOT NULL,
    ingreso BOOLEAN NOT NULL,
    asunto_cifrado VARCHAR(255) NOT NULL,
    fecha_cifrada VARCHAR(255) NOT NULL,
    mes_asignado INT NOT NULL,
    anio_asignado INT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Crear tabla de meses manuales si no existe
CREATE TABLE IF NOT EXISTS meses_manuales (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    anio INT NOT NULL,
    mes INT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY unique_user_month (user_id, anio, mes)
);

-- Crear tabla de movimientos recurrentes si no existe
CREATE TABLE IF NOT EXISTS movimientos_recurrentes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    cantidad DOUBLE NOT NULL,
    ingreso BOOLEAN NOT NULL,
    asunto VARCHAR(255),
    fecha_inicio DATE NOT NULL,
    fecha_fin DATE,
    frecuencia VARCHAR(50) NOT NULL,
    activo BOOLEAN DEFAULT TRUE,
    ultima_fecha_ejecutada DATE,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Nota: Los índices se crearán automáticamente por JPA usando las anotaciones @Index 