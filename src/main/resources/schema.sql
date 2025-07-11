-- Script de inicialización de la base de datos
-- Este script se ejecutará automáticamente al iniciar la aplicación

-- Crear tabla de usuarios si no existe

CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    balance_total DECIMAL(19,2) NULL
);

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
    categoria VARCHAR(40) NULL,
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
    cantidad_cifrada VARCHAR(255) NOT NULL,
    ingreso BOOLEAN NOT NULL,
    asunto_cifrado VARCHAR(255) NOT NULL,
    fecha_inicio DATE NOT NULL,
    frecuencia VARCHAR(50) NOT NULL,
    fecha_fin DATE,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    ultima_fecha_ejecutada DATE,
    categoria VARCHAR(40) NULL,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Crear índices para mejorar el rendimiento (solo si no existen)
-- Nota: MySQL no soporta CREATE INDEX IF NOT EXISTS, por lo que usamos una sintaxis alternativa
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'movimientos' 
     AND INDEX_NAME = 'idx_movimientos_user') = 0,
    'CREATE INDEX idx_movimientos_user ON movimientos(user_id);',
    'SELECT "Índice idx_movimientos_user ya existe" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'movimientos' 
     AND INDEX_NAME = 'idx_movimientos_mes_anio') = 0,
    'CREATE INDEX idx_movimientos_mes_anio ON movimientos(mes_asignado, anio_asignado);',
    'SELECT "Índice idx_movimientos_mes_anio ya existe" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'movimientos_recurrentes' 
     AND INDEX_NAME = 'idx_recurrentes_user') = 0,
    'CREATE INDEX idx_recurrentes_user ON movimientos_recurrentes(user_id);',
    'SELECT "Índice idx_recurrentes_user ya existe" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'movimientos_recurrentes' 
     AND INDEX_NAME = 'idx_recurrentes_activo') = 0,
    'CREATE INDEX idx_recurrentes_activo ON movimientos_recurrentes(activo);',
    'SELECT "Índice idx_recurrentes_activo ya existe" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'meses_manuales' 
     AND INDEX_NAME = 'idx_meses_user') = 0,
    'CREATE INDEX idx_meses_user ON meses_manuales(user_id);',
    'SELECT "Índice idx_meses_user ya existe" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Migración de datos existentes de movimientos recurrentes (si es necesario)
-- Esta migración se ejecutará solo si existen columnas antiguas
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'movimientos_recurrentes' 
     AND COLUMN_NAME = 'cantidad') > 0,
    'ALTER TABLE movimientos_recurrentes 
     ADD COLUMN cantidad_cifrada VARCHAR(255) AFTER user_id,
     ADD COLUMN asunto_cifrado VARCHAR(255) AFTER ingreso,
     DROP COLUMN cantidad,
     DROP COLUMN asunto;',
    'SELECT "Migración no necesaria" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Nota: Los índices se crearán automáticamente por JPA usando las anotaciones @Index 
-- Crear tabla de tokens de verificación si no existe
CREATE TABLE IF NOT EXISTS verification_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);