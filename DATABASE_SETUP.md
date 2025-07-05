# Configuración de Base de Datos MySQL

## Requisitos Previos

1. **MySQL Server** instalado y ejecutándose
2. **Java 17** o superior
3. **Maven** instalado

## Configuración de MySQL

### 1. Instalar MySQL Server

#### Windows:
- Descargar MySQL Installer desde: https://dev.mysql.com/downloads/installer/
- Instalar MySQL Server y MySQL Workbench

#### macOS:
```bash
brew install mysql
brew services start mysql
```

#### Linux (Ubuntu/Debian):
```bash
sudo apt update
sudo apt install mysql-server
sudo systemctl start mysql
sudo systemctl enable mysql
```

### 2. Configurar MySQL

1. **Acceder a MySQL:**
```bash
mysql -u root -p
```

2. **Crear la base de datos:**
```sql
CREATE DATABASE finanzas CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

3. **Crear usuario (opcional):**
```sql
CREATE USER 'finanzas_user'@'localhost' IDENTIFIED BY 'tu_password';
GRANT ALL PRIVILEGES ON finanzas.* TO 'finanzas_user'@'localhost';
FLUSH PRIVILEGES;
```

### 3. Configurar la Aplicación

Editar `src/main/resources/application.properties`:

```properties
# Para usar el usuario root (por defecto)
spring.datasource.username=root
spring.datasource.password=tu_password_root

# O para usar un usuario específico
spring.datasource.username=finanzas_user
spring.datasource.password=tu_password
```

### 4. Ejecutar la Aplicación

```bash
mvn spring-boot:run
```

## Migración de Datos

La aplicación automáticamente migrará los datos existentes de los archivos JSON a la base de datos MySQL cuando se inicie por primera vez.

### Archivos que se migrarán:
- `data/users.json` → tabla `users`
- `data/movimientos.json` → tabla `movimientos`
- `data/meses_creados.json` → tabla `meses_manuales`

## Estructura de la Base de Datos

### Tabla `users`
- `id` (BIGINT, AUTO_INCREMENT, PRIMARY KEY)
- `username` (VARCHAR(255), UNIQUE, NOT NULL)
- `password` (VARCHAR(255), NOT NULL)

### Tabla `movimientos`
- `id` (BIGINT, AUTO_INCREMENT, PRIMARY KEY)
- `user_id` (BIGINT, FOREIGN KEY)
- `cantidad` (DOUBLE, NOT NULL)
- `ingreso` (BOOLEAN, NOT NULL)
- `asunto` (VARCHAR(255), NOT NULL)
- `fecha` (DATE, NOT NULL)
- `mes_asignado` (INT, NOT NULL)
- `anio_asignado` (INT, NOT NULL)

### Tabla `meses_manuales`
- `id` (BIGINT, AUTO_INCREMENT, PRIMARY KEY)
- `user_id` (BIGINT, FOREIGN KEY)
- `anio` (INT, NOT NULL)
- `mes` (INT, NOT NULL)

## Solución de Problemas

### Error de Conexión
- Verificar que MySQL esté ejecutándose
- Verificar credenciales en `application.properties`
- Verificar que la base de datos `finanzas` exista

### Error de Permisos
- Asegurar que el usuario tenga permisos en la base de datos
- Ejecutar: `GRANT ALL PRIVILEGES ON finanzas.* TO 'usuario'@'localhost';`

### Error de Puerto
- Verificar que MySQL esté en el puerto 3306 (por defecto)
- Si usa otro puerto, actualizar la URL en `application.properties`

## Backup y Restauración

### Backup
```bash
mysqldump -u root -p finanzas > backup_finanzas.sql
```

### Restauración
```bash
mysql -u root -p finanzas < backup_finanzas.sql
``` 