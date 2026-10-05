-- Script de inicialización de la base de datos
-- Este script se ejecutará automáticamente al iniciar la aplicación

-- Crear tabla de usuarios si no existe

CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    balance_total DECIMAL(19,2) NULL,
    tutorial_visto BOOLEAN NOT NULL DEFAULT FALSE
);

-- Añadir columna tutorial_visto si la tabla ya existía sin ella
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'users'
     AND COLUMN_NAME = 'tutorial_visto') = 0,
    'ALTER TABLE users ADD COLUMN tutorial_visto BOOLEAN NOT NULL DEFAULT FALSE;',
    'SELECT "Columna tutorial_visto ya existe" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Inicio de sesión con Apple: identificador estable ("sub") del usuario en Apple, único y opcional
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'users'
     AND COLUMN_NAME = 'apple_sub') = 0,
    'ALTER TABLE users ADD COLUMN apple_sub VARCHAR(255) NULL UNIQUE;',
    'SELECT "Columna apple_sub ya existe" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

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

-- ==========================================================
-- Sistema de múltiples cuentas de dinero
-- ==========================================================

-- Crear tabla de cuentas si no existe
CREATE TABLE IF NOT EXISTS cuentas (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    saldo_inicial_cifrado VARCHAR(255) NOT NULL,
    fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Crear tabla de transferencias entre cuentas si no existe
CREATE TABLE IF NOT EXISTS transferencias (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    cuenta_origen_id BIGINT NOT NULL,
    cuenta_destino_id BIGINT NOT NULL,
    importe_cifrado VARCHAR(255) NOT NULL,
    fecha DATE NOT NULL,
    descripcion_cifrada VARCHAR(255) NULL,
    fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (cuenta_origen_id) REFERENCES cuentas(id),
    FOREIGN KEY (cuenta_destino_id) REFERENCES cuentas(id)
);

-- Añadir columna cuenta_id a movimientos si no existe (nullable: se rellena vía migración)
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'movimientos'
     AND COLUMN_NAME = 'cuenta_id') = 0,
    'ALTER TABLE movimientos ADD COLUMN cuenta_id BIGINT NULL, ADD CONSTRAINT fk_movimientos_cuenta FOREIGN KEY (cuenta_id) REFERENCES cuentas(id);',
    'SELECT "Columna cuenta_id ya existe en movimientos" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Añadir columna cuenta_id a movimientos_recurrentes si no existe
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'movimientos_recurrentes'
     AND COLUMN_NAME = 'cuenta_id') = 0,
    'ALTER TABLE movimientos_recurrentes ADD COLUMN cuenta_id BIGINT NULL, ADD CONSTRAINT fk_recurrentes_cuenta FOREIGN KEY (cuenta_id) REFERENCES cuentas(id);',
    'SELECT "Columna cuenta_id ya existe en movimientos_recurrentes" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Índices de rendimiento para las tablas nuevas
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'cuentas'
     AND INDEX_NAME = 'idx_cuentas_user') = 0,
    'CREATE INDEX idx_cuentas_user ON cuentas(user_id);',
    'SELECT "Índice idx_cuentas_user ya existe" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'transferencias'
     AND INDEX_NAME = 'idx_transferencias_user') = 0,
    'CREATE INDEX idx_transferencias_user ON transferencias(user_id);',
    'SELECT "Índice idx_transferencias_user ya existe" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'movimientos'
     AND INDEX_NAME = 'idx_movimientos_cuenta') = 0,
    'CREATE INDEX idx_movimientos_cuenta ON movimientos(cuenta_id);',
    'SELECT "Índice idx_movimientos_cuenta ya existe" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ==========================================================
-- Sistema de categorías y subcategorías
-- ==========================================================

-- Tabla de categorías principales (referencia global, no por usuario)
CREATE TABLE IF NOT EXISTS categorias (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    icono VARCHAR(40) NOT NULL,
    tipo VARCHAR(10) NOT NULL,
    orden INT NOT NULL DEFAULT 0
);

-- Tabla de subcategorías
CREATE TABLE IF NOT EXISTS subcategorias (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    categoria_id BIGINT NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    orden INT NOT NULL DEFAULT 0,
    FOREIGN KEY (categoria_id) REFERENCES categorias(id) ON DELETE CASCADE
);

-- Añadir columnas categoria_id/subcategoria_id a movimientos (la columna vieja "categoria" se conserva intacta)
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'movimientos'
     AND COLUMN_NAME = 'categoria_id') = 0,
    'ALTER TABLE movimientos
       ADD COLUMN categoria_id BIGINT NULL,
       ADD COLUMN subcategoria_id BIGINT NULL,
       ADD CONSTRAINT fk_movimientos_categoria FOREIGN KEY (categoria_id) REFERENCES categorias(id),
       ADD CONSTRAINT fk_movimientos_subcategoria FOREIGN KEY (subcategoria_id) REFERENCES subcategorias(id);',
    'SELECT "Columnas categoria_id/subcategoria_id ya existen en movimientos" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Añadir columnas categoria_id/subcategoria_id a movimientos_recurrentes
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'movimientos_recurrentes'
     AND COLUMN_NAME = 'categoria_id') = 0,
    'ALTER TABLE movimientos_recurrentes
       ADD COLUMN categoria_id BIGINT NULL,
       ADD COLUMN subcategoria_id BIGINT NULL,
       ADD CONSTRAINT fk_recurrentes_categoria FOREIGN KEY (categoria_id) REFERENCES categorias(id),
       ADD CONSTRAINT fk_recurrentes_subcategoria FOREIGN KEY (subcategoria_id) REFERENCES subcategorias(id);',
    'SELECT "Columnas categoria_id/subcategoria_id ya existen en movimientos_recurrentes" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Índices de rendimiento para categorías
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'subcategorias'
     AND INDEX_NAME = 'idx_subcategorias_categoria') = 0,
    'CREATE INDEX idx_subcategorias_categoria ON subcategorias(categoria_id);',
    'SELECT "Índice idx_subcategorias_categoria ya existe" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'movimientos'
     AND INDEX_NAME = 'idx_movimientos_categoria') = 0,
    'CREATE INDEX idx_movimientos_categoria ON movimientos(categoria_id);',
    'SELECT "Índice idx_movimientos_categoria ya existe" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Últimos 4 dígitos de la cuenta (opcional): ayuda a distinguir cuentas con nombres parecidos,
-- tanto en la web como al resolver la cuenta por nombre desde la API.
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'cuentas'
     AND COLUMN_NAME = 'ultimos_digitos') = 0,
    'ALTER TABLE cuentas ADD COLUMN ultimos_digitos VARCHAR(4) NULL;',
    'SELECT "Columna ultimos_digitos ya existe en cuentas" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ==========================================================
-- Presupuestos mensuales por categoría y objetivos de ahorro
-- ==========================================================

-- Presupuesto mensual recurrente: un límite de gasto por categoría que se evalúa
-- cada mes contra el gasto real de esa categoría (sin guardar mes/año: se aplica
-- de forma continua mientras esté activo).
CREATE TABLE IF NOT EXISTS presupuestos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    categoria_id BIGINT NOT NULL,
    limite_cifrado VARCHAR(255) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (categoria_id) REFERENCES categorias(id)
);

-- Objetivo de ahorro: una cuenta debe alcanzar un importe objetivo. El progreso
-- se calcula comparando el saldo actual real de la cuenta contra el importe objetivo.
CREATE TABLE IF NOT EXISTS objetivos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    cuenta_id BIGINT NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    objetivo_cifrado VARCHAR(255) NOT NULL,
    fecha_limite DATE NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (cuenta_id) REFERENCES cuentas(id) ON DELETE CASCADE
);

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'presupuestos'
     AND INDEX_NAME = 'idx_presupuestos_user') = 0,
    'CREATE INDEX idx_presupuestos_user ON presupuestos(user_id);',
    'SELECT "Índice idx_presupuestos_user ya existe" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'objetivos'
     AND INDEX_NAME = 'idx_objetivos_user') = 0,
    'CREATE INDEX idx_objetivos_user ON objetivos(user_id);',
    'SELECT "Índice idx_objetivos_user ya existe" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Un presupuesto ahora puede acotarse por subcategoría y/o por un concepto de texto libre
-- (p. ej. "solo gastar 30€ al mes en perfumes"), además de o en vez de la categoría.
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'presupuestos' AND COLUMN_NAME = 'subcategoria_id') = 0,
    'ALTER TABLE presupuestos ADD COLUMN subcategoria_id BIGINT NULL, ADD CONSTRAINT fk_presupuestos_subcategoria FOREIGN KEY (subcategoria_id) REFERENCES subcategorias(id);',
    'SELECT "Columna subcategoria_id ya existe en presupuestos" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'presupuestos' AND COLUMN_NAME = 'concepto') = 0,
    'ALTER TABLE presupuestos ADD COLUMN concepto VARCHAR(50) NULL;',
    'SELECT "Columna concepto ya existe en presupuestos" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- La categoría pasa a ser opcional: un presupuesto puede definirse solo por concepto.
SET @sql = (SELECT IF(
    (SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'presupuestos' AND COLUMN_NAME = 'categoria_id') = 'NO',
    'ALTER TABLE presupuestos MODIFY COLUMN categoria_id BIGINT NULL;',
    'SELECT "categoria_id ya es nullable en presupuestos" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ==========================================================
-- Hallazgo H4: migración de cifrado AES/ECB -> AES/GCM
-- ==========================================================
-- AES/GCM añade un IV (12 bytes) + tag de autenticación (16 bytes) por valor cifrado, que
-- ECB no necesitaba. Se ensanchan las columnas cifradas para que quepan con margen (incluidos
-- asuntos/descripciones largos) sin arriesgar truncamiento. Idempotente: cada bloque solo
-- actúa si la columna sigue en su longitud antigua.
-- Nota: se repite el mismo patrón SET @sql/PREPARE/EXECUTE por columna (en vez de un
-- procedimiento almacenado reutilizable) porque Spring ejecuta este script con un separador
-- de sentencias simple por ";" que no entiende el cuerpo de un CREATE PROCEDURE (necesitaría
-- DELIMITER, que solo existe en el cliente `mysql`, no es SQL real vía JDBC).
SET @sql = (SELECT IF(
    (SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'movimientos' AND COLUMN_NAME = 'cantidad_cifrada') < 500,
    'ALTER TABLE movimientos MODIFY COLUMN cantidad_cifrada VARCHAR(500) NOT NULL;',
    'SELECT "cantidad_cifrada en movimientos ya esta ensanchada" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    (SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'movimientos' AND COLUMN_NAME = 'asunto_cifrado') < 500,
    'ALTER TABLE movimientos MODIFY COLUMN asunto_cifrado VARCHAR(500) NOT NULL;',
    'SELECT "asunto_cifrado en movimientos ya esta ensanchada" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    (SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'movimientos' AND COLUMN_NAME = 'fecha_cifrada') < 500,
    'ALTER TABLE movimientos MODIFY COLUMN fecha_cifrada VARCHAR(500) NOT NULL;',
    'SELECT "fecha_cifrada en movimientos ya esta ensanchada" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    (SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'movimientos_recurrentes' AND COLUMN_NAME = 'cantidad_cifrada') < 500,
    'ALTER TABLE movimientos_recurrentes MODIFY COLUMN cantidad_cifrada VARCHAR(500) NOT NULL;',
    'SELECT "cantidad_cifrada en movimientos_recurrentes ya esta ensanchada" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    (SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'movimientos_recurrentes' AND COLUMN_NAME = 'asunto_cifrado') < 500,
    'ALTER TABLE movimientos_recurrentes MODIFY COLUMN asunto_cifrado VARCHAR(500) NOT NULL;',
    'SELECT "asunto_cifrado en movimientos_recurrentes ya esta ensanchada" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    (SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cuentas' AND COLUMN_NAME = 'saldo_inicial_cifrado') < 500,
    'ALTER TABLE cuentas MODIFY COLUMN saldo_inicial_cifrado VARCHAR(500) NOT NULL;',
    'SELECT "saldo_inicial_cifrado en cuentas ya esta ensanchada" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    (SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'transferencias' AND COLUMN_NAME = 'importe_cifrado') < 500,
    'ALTER TABLE transferencias MODIFY COLUMN importe_cifrado VARCHAR(500) NOT NULL;',
    'SELECT "importe_cifrado en transferencias ya esta ensanchada" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    (SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'transferencias' AND COLUMN_NAME = 'descripcion_cifrada') < 500,
    'ALTER TABLE transferencias MODIFY COLUMN descripcion_cifrada VARCHAR(500) NULL;',
    'SELECT "descripcion_cifrada en transferencias ya esta ensanchada" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    (SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'presupuestos' AND COLUMN_NAME = 'limite_cifrado') < 500,
    'ALTER TABLE presupuestos MODIFY COLUMN limite_cifrado VARCHAR(500) NOT NULL;',
    'SELECT "limite_cifrado en presupuestos ya esta ensanchada" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    (SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'objetivos' AND COLUMN_NAME = 'objetivo_cifrado') < 500,
    'ALTER TABLE objetivos MODIFY COLUMN objetivo_cifrado VARCHAR(500) NOT NULL;',
    'SELECT "objetivo_cifrado en objetivos ya esta ensanchada" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- Hallazgo H5: separar tokens de verificación de email y de reset de contraseña,
-- con expiración y marca de uso único.
-- ==========================================================
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'verification_tokens' AND COLUMN_NAME = 'tipo') = 0,
    -- Los tokens ya existentes (previos a esta migración) se consideran EMAIL_VERIFICATION:
    -- es el flujo que llevaba más tiempo activo y el valor por defecto más seguro (evita que
    -- un token de verificación antiguo, ya en circulación, se reinterprete como reset).
    'ALTER TABLE verification_tokens ADD COLUMN tipo VARCHAR(30) NOT NULL DEFAULT ''EMAIL_VERIFICATION'';',
    'SELECT "Columna tipo ya existe en verification_tokens" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'verification_tokens' AND COLUMN_NAME = 'expires_at') = 0,
    -- Tokens ya existentes: se les da 48h de margen desde esta migración para no invalidar
    -- de golpe enlaces que ya estén en camino en una bandeja de entrada.
    'ALTER TABLE verification_tokens ADD COLUMN expires_at TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP + INTERVAL 48 HOUR);',
    'SELECT "Columna expires_at ya existe en verification_tokens" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
-- Nota sobre "uso único": no se añade una columna "used" separada porque el propio
-- UserService borra la fila de verification_tokens en cuanto el token se consume
-- (verifyUser/resetPassword) - un token borrado no puede reutilizarse, es la garantía
-- de uso único más simple posible aquí y ya era el comportamiento previo del código.

-- payload: usado únicamente por tokens de tipo EMAIL_CHANGE, para guardar el nuevo email
-- pendiente de confirmar (hallazgo M5). NULL para el resto de tipos.
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'verification_tokens' AND COLUMN_NAME = 'payload') = 0,
    'ALTER TABLE verification_tokens ADD COLUMN payload VARCHAR(255) NULL;',
    'SELECT "Columna payload ya existe en verification_tokens" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ==========================================================
-- Preferencia de divisa (pantalla de Ajustes)
-- ==========================================================
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'moneda') = 0,
    'ALTER TABLE users ADD COLUMN moneda VARCHAR(3) NOT NULL DEFAULT ''EUR'';',
    'SELECT "Columna moneda ya existe en users" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ==========================================================
-- Plan de usuario (Free/Premium) y visibilidad del badge premium en el header
-- ==========================================================
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'tipo_plan') = 0,
    'ALTER TABLE users ADD COLUMN tipo_plan VARCHAR(10) NOT NULL DEFAULT ''FREE'';',
    'SELECT "Columna tipo_plan ya existe en users" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'mostrar_badge_premium') = 0,
    'ALTER TABLE users ADD COLUMN mostrar_badge_premium BOOLEAN NOT NULL DEFAULT TRUE;',
    'SELECT "Columna mostrar_badge_premium ya existe en users" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ==========================================================
-- Baneo de cuentas (panel de administración externo, puerto 9093)
-- ==========================================================
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'baneado') = 0,
    'ALTER TABLE users ADD COLUMN baneado BOOLEAN NOT NULL DEFAULT FALSE;',
    'SELECT "Columna baneado ya existe en users" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- La FK original de movimientos_recurrentes -> users se creó sin ON DELETE CASCADE (a
-- diferencia del resto de tablas hijas de users), así que eliminar una cuenta con algún
-- movimiento recurrente violaba la restricción de clave foránea. Se detecta por nombre real de
-- la restricción (MySQL la autogenera) y se sustituye por una con CASCADE, en dos pasos
-- (DROP y luego ADD con un nombre propio distinto): un único ALTER con DROP+ADD del mismo
-- nombre autogenerado falla con "Duplicate foreign key constraint name" en MySQL 8.
SET @fk_name = (SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'movimientos_recurrentes'
      AND REFERENCED_TABLE_NAME = 'users' AND DELETE_RULE <> 'CASCADE' LIMIT 1);
SET @sql = (SELECT IF(
    @fk_name IS NOT NULL,
    CONCAT('ALTER TABLE movimientos_recurrentes DROP FOREIGN KEY ', @fk_name, ';'),
    'SELECT "FK movimientos_recurrentes->users ya es CASCADE o ya se renombró" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
     WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'movimientos_recurrentes'
       AND CONSTRAINT_NAME = 'fk_recurrentes_user') = 0,
    'ALTER TABLE movimientos_recurrentes ADD CONSTRAINT fk_recurrentes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;',
    'SELECT "fk_recurrentes_user ya existe" as status;'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Mismo problema en cascada, un nivel más abajo: movimientos.cuenta_id, movimientos_recurrentes.cuenta_id
-- y transferencias.cuenta_origen_id/cuenta_destino_id referencian a cuentas(id) sin ON DELETE CASCADE.
-- Al borrar un usuario, la fila de "cuentas" se intenta borrar en cascada (por user_id) pero esas FKs
-- sin cascade lo bloqueaban con "Cannot delete or update a parent row" (así se detectó, probando el
-- borrado de cuenta real desde el panel de administración). Mismo patrón de 2 pasos que arriba para
-- cada una de las 4 relaciones.

-- movimientos.cuenta_id -> cuentas.id (ya tenía nombre propio: fk_movimientos_cuenta)
SET @needs_fix = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'movimientos'
      AND CONSTRAINT_NAME = 'fk_movimientos_cuenta' AND DELETE_RULE <> 'CASCADE');
SET @sql = (SELECT IF(@needs_fix > 0,
    'ALTER TABLE movimientos DROP FOREIGN KEY fk_movimientos_cuenta;',
    'SELECT "fk_movimientos_cuenta ya es CASCADE" as status;'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
     WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'movimientos' AND CONSTRAINT_NAME = 'fk_movimientos_cuenta') = 0,
    'ALTER TABLE movimientos ADD CONSTRAINT fk_movimientos_cuenta FOREIGN KEY (cuenta_id) REFERENCES cuentas(id) ON DELETE CASCADE;',
    'SELECT "fk_movimientos_cuenta ya existe" as status;'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- movimientos_recurrentes.cuenta_id -> cuentas.id (ya tenía nombre propio: fk_recurrentes_cuenta)
SET @needs_fix = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'movimientos_recurrentes'
      AND CONSTRAINT_NAME = 'fk_recurrentes_cuenta' AND DELETE_RULE <> 'CASCADE');
SET @sql = (SELECT IF(@needs_fix > 0,
    'ALTER TABLE movimientos_recurrentes DROP FOREIGN KEY fk_recurrentes_cuenta;',
    'SELECT "fk_recurrentes_cuenta ya es CASCADE" as status;'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
     WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'movimientos_recurrentes' AND CONSTRAINT_NAME = 'fk_recurrentes_cuenta') = 0,
    'ALTER TABLE movimientos_recurrentes ADD CONSTRAINT fk_recurrentes_cuenta FOREIGN KEY (cuenta_id) REFERENCES cuentas(id) ON DELETE CASCADE;',
    'SELECT "fk_recurrentes_cuenta ya existe" as status;'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- transferencias.cuenta_origen_id -> cuentas.id (nombre autogenerado, se detecta por columna)
SET @fk_name = (SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'transferencias'
      AND COLUMN_NAME = 'cuenta_origen_id' AND REFERENCED_TABLE_NAME = 'cuentas' LIMIT 1);
SET @needs_fix = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'transferencias'
      AND CONSTRAINT_NAME = @fk_name AND DELETE_RULE <> 'CASCADE');
SET @sql = (SELECT IF(@fk_name IS NOT NULL AND @needs_fix > 0,
    CONCAT('ALTER TABLE transferencias DROP FOREIGN KEY ', @fk_name, ';'),
    'SELECT "FK transferencias.cuenta_origen_id ya es CASCADE o ya se renombró" as status;'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
     WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'transferencias' AND CONSTRAINT_NAME = 'fk_transferencias_origen') = 0,
    'ALTER TABLE transferencias ADD CONSTRAINT fk_transferencias_origen FOREIGN KEY (cuenta_origen_id) REFERENCES cuentas(id) ON DELETE CASCADE;',
    'SELECT "fk_transferencias_origen ya existe" as status;'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- transferencias.cuenta_destino_id -> cuentas.id (nombre autogenerado, se detecta por columna)
SET @fk_name = (SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'transferencias'
      AND COLUMN_NAME = 'cuenta_destino_id' AND REFERENCED_TABLE_NAME = 'cuentas' LIMIT 1);
SET @needs_fix = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'transferencias'
      AND CONSTRAINT_NAME = @fk_name AND DELETE_RULE <> 'CASCADE');
SET @sql = (SELECT IF(@fk_name IS NOT NULL AND @needs_fix > 0,
    CONCAT('ALTER TABLE transferencias DROP FOREIGN KEY ', @fk_name, ';'),
    'SELECT "FK transferencias.cuenta_destino_id ya es CASCADE o ya se renombró" as status;'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
     WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'transferencias' AND CONSTRAINT_NAME = 'fk_transferencias_destino') = 0,
    'ALTER TABLE transferencias ADD CONSTRAINT fk_transferencias_destino FOREIGN KEY (cuenta_destino_id) REFERENCES cuentas(id) ON DELETE CASCADE;',
    'SELECT "fk_transferencias_destino ya existe" as status;'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ==========================================================
-- Categorías personalizadas por usuario + activar/desactivar categorías y subcategorías
-- ==========================================================

-- user_id NULL = categoría global (seeded, compartida); user_id informado = categoría propia,
-- solo visible para ese usuario. Las subcategorías de una categoría propia heredan su dueño a
-- través de categoria_id, no hace falta duplicar user_id en "subcategorias".
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'categorias' AND COLUMN_NAME = 'user_id') = 0,
    'ALTER TABLE categorias ADD COLUMN user_id BIGINT NULL, ADD CONSTRAINT fk_categorias_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;',
    'SELECT "Columna user_id ya existe en categorias" as status;'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'categorias_desactivadas') = 0,
    'CREATE TABLE categorias_desactivadas (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        user_id BIGINT NOT NULL,
        categoria_id BIGINT NOT NULL,
        UNIQUE KEY uq_categoria_desactivada (user_id, categoria_id),
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
        FOREIGN KEY (categoria_id) REFERENCES categorias(id) ON DELETE CASCADE
    );',
    'SELECT "Tabla categorias_desactivadas ya existe" as status;'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'subcategorias_desactivadas') = 0,
    'CREATE TABLE subcategorias_desactivadas (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        user_id BIGINT NOT NULL,
        subcategoria_id BIGINT NOT NULL,
        UNIQUE KEY uq_subcategoria_desactivada (user_id, subcategoria_id),
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
        FOREIGN KEY (subcategoria_id) REFERENCES subcategorias(id) ON DELETE CASCADE
    );',
    'SELECT "Tabla subcategorias_desactivadas ya existe" as status;'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Passkeys (WebAuthn): una fila por cada dispositivo/llavero que el usuario ha vinculado.
-- credential_id y public_key_cose se guardan en base64url. Al borrar el usuario se borran sus passkeys.
CREATE TABLE IF NOT EXISTS passkey_credentials (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    credential_id VARCHAR(512) NOT NULL UNIQUE,
    public_key_cose TEXT NOT NULL,
    signature_count BIGINT NOT NULL DEFAULT 0,
    nombre VARCHAR(100) NOT NULL,
    created_at DATETIME NOT NULL,
    last_used_at DATETIME NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
