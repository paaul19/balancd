# Solución de Problemas de MySQL

## Error: "Public Key Retrieval is not allowed"

Este error es común en MySQL 8.0+ y se debe a configuraciones de seguridad más estrictas.

### Solución 1: Actualizar la URL de Conexión (Ya implementado)

La URL de conexión ya ha sido actualizada con los parámetros necesarios:
```
allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=utf8
```

### Solución 2: Configurar MySQL

Si el problema persiste, ejecuta estos comandos en MySQL:

```sql
-- Conectar como root
mysql -u root -p

-- Cambiar el método de autenticación del usuario
ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY 'tu_password';

-- O crear un nuevo usuario
CREATE USER 'finanzas_user'@'localhost' IDENTIFIED WITH mysql_native_password BY 'tu_password';
GRANT ALL PRIVILEGES ON finanzas.* TO 'finanzas_user'@'localhost';
FLUSH PRIVILEGES;
```

### Solución 3: Usar H2 Temporalmente

Si no puedes configurar MySQL inmediatamente, puedes usar H2:

```bash
# Ejecutar con perfil H2
mvn spring-boot:run -Dspring.profiles.active=h2
```

O agregar esta línea al final de `application.properties`:
```properties
spring.profiles.active=h2
```

## Verificar Instalación de MySQL

### 1. Verificar que MySQL esté ejecutándose

**Windows:**
```cmd
net start mysql
```

**macOS:**
```bash
brew services list | grep mysql
```

**Linux:**
```bash
sudo systemctl status mysql
```

### 2. Verificar conexión
```bash
mysql -u root -p
```

### 3. Crear la base de datos
```sql
CREATE DATABASE finanzas CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

## Configuración de Credenciales

### Opción 1: Usar root
En `application.properties`:
```properties
spring.datasource.username=root
spring.datasource.password=tu_password_root
```

### Opción 2: Crear usuario específico
```sql
CREATE USER 'finanzas_user'@'localhost' IDENTIFIED BY 'tu_password';
GRANT ALL PRIVILEGES ON finanzas.* TO 'finanzas_user'@'localhost';
FLUSH PRIVILEGES;
```

Luego en `application.properties`:
```properties
spring.datasource.username=finanzas_user
spring.datasource.password=tu_password
```

## Comandos Útiles

### Verificar tablas creadas
```sql
USE finanzas;
SHOW TABLES;
```

### Verificar datos migrados
```sql
SELECT * FROM users;
SELECT * FROM movimientos;
SELECT * FROM meses_manuales;
```

### Reiniciar MySQL (si es necesario)
**Windows:**
```cmd
net stop mysql
net start mysql
```

**macOS:**
```bash
brew services restart mysql
```

**Linux:**
```bash
sudo systemctl restart mysql
```

## Logs de Depuración

Para ver logs detallados, ejecuta:
```bash
mvn spring-boot:run -Ddebug=true
```

O agrega a `application.properties`:
```properties
logging.level.org.springframework.jdbc=DEBUG
logging.level.com.zaxxer.hikari=DEBUG
``` 