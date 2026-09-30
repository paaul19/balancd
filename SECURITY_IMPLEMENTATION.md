# Implementación de Seguridad

## 🔐 Cifrado de Contraseñas

### **Tecnología Utilizada**
- **BCrypt**: Algoritmo de cifrado de contraseñas recomendado por Spring Security
- **Salt automático**: BCrypt genera automáticamente un salt único para cada contraseña
- **Factor de trabajo**: Configurado por defecto (10 rounds)

### **Características de Seguridad**

#### **1. Cifrado Automático**
- Las contraseñas se cifran automáticamente al registrar usuarios
- Las contraseñas se cifran al actualizar usuarios
- Verificación automática de si una contraseña ya está cifrada

#### **2. Migración Automática**
- Las contraseñas existentes en texto plano se migran automáticamente
- Se ejecuta al iniciar la aplicación
- No interrumpe el funcionamiento normal

#### **3. Verificación Segura**
- Las contraseñas se verifican usando `BCrypt.matches()`
- Nunca se comparan contraseñas en texto plano
- Protección contra ataques de timing

### **Servicios Implementados**

#### **PasswordService**
```java
// Cifrar contraseña
String encodedPassword = passwordService.encodePassword("miContraseña");

// Verificar contraseña
boolean isValid = passwordService.matches("miContraseña", encodedPassword);

// Verificar si ya está cifrada
boolean isEncoded = passwordService.isEncoded(password);
```

#### **PasswordMigrationService**
- Se ejecuta automáticamente al iniciar la aplicación
- Migra contraseñas existentes de texto plano a cifradas
- Registra el progreso en los logs

### **Base de Datos**

#### **Estructura Actualizada**
```sql
-- La columna password ahora puede almacenar contraseñas cifradas
ALTER TABLE users MODIFY COLUMN password VARCHAR(255) NOT NULL;
```

#### **Ejemplo de Contraseña Cifrada**
```
$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
```

### **Flujo de Seguridad**

#### **Registro de Usuario**
1. Usuario ingresa contraseña en texto plano
2. `UserService.saveUser()` detecta que no está cifrada
3. `PasswordService.encodePassword()` cifra la contraseña
4. Se guarda en la base de datos cifrada

#### **Autenticación**
1. Usuario ingresa contraseña en texto plano
2. `UserService.authenticateUser()` busca el usuario
3. `PasswordService.matches()` compara contraseñas
4. Retorna true/false según coincidencia

#### **Actualización de Usuario**
1. Si se proporciona nueva contraseña, se cifra automáticamente
2. Si no se proporciona, se preserva la contraseña existente
3. Se valida que el username no exista en otro usuario

### **Logs de Seguridad**

#### **Migración de Contraseñas**
```
Contraseña migrada para usuario: usuario1
Contraseña migrada para usuario: usuario2
Migración completada: 2 contraseñas cifradas
```

#### **Registro de Usuarios**
```
Usuario registrado: nuevoUsuario
Contraseña cifrada automáticamente
```

### **Comandos SQL Útiles**

#### **Verificar Contraseñas Cifradas**
```sql
SELECT username, 
       CASE 
           WHEN password LIKE '$2a$%' THEN 'CIFRADA'
           ELSE 'TEXTO_PLANO'
       END as estado_contraseña
FROM users;
```

#### **Contar Usuarios por Estado de Contraseña**
```sql
SELECT 
    CASE 
        WHEN password LIKE '$2a$%' THEN 'CIFRADAS'
        ELSE 'TEXTO_PLANO'
    END as tipo,
    COUNT(*) as total
FROM users 
GROUP BY tipo;
```

### **Próximas Mejoras de Seguridad**

1. **Validación de Contraseñas**
   - Longitud mínima
   - Complejidad requerida
   - Caracteres especiales

2. **Rate Limiting**
   - Límite de intentos de login
   - Bloqueo temporal de cuentas

3. **Auditoría**
   - Logs de intentos de login
   - Registro de cambios de contraseña

4. **Autenticación de Dos Factores**
   - Códigos SMS/Email
   - Aplicaciones autenticadoras

### **Configuración de Spring Security**

La aplicación usa una configuración mínima de Spring Security:
- Deshabilita la autenticación automática
- Permite acceso a recursos estáticos
- Mantiene la autenticación personalizada existente 