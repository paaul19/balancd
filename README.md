# balanc*d
**balanc*d** es una aplicación web completa y segura para la gestión de finanzas personales. Permite a los usuarios registrar, categorizar y analizar sus ingresos y gastos por meses, con funcionalidades avanzadas como movimientos recurrentes, búsqueda avanzada y cifrado de datos sensibles.

## 🚀 Características Principales

### 🔐 Autenticación y Seguridad
- Registro de usuarios con verificación de email
- Login seguro con contraseñas cifradas (BCrypt)
- Recuperación de contraseña vía email
- Cifrado AES-256 de datos financieros
- Acceso protegido con contraseña adicional
- Verificación obligatoria de email para activar cuentas

### 💰 Gestión Financiera
- Registro de ingresos y gastos detallados
- Categorización (Comida, Transporte, Ocio, etc.)
- Organización por meses (asignación manual)
- Edición y eliminación de movimientos
- Balance automático mensual

### 🔄 Movimientos Recurrentes
- Configuración de pagos automáticos
- Fechas de inicio/fin configurables
- Activación y pausa de movimientos
- Procesamiento programado
- CRUD completo para movimientos recurrentes

### 🔍 Búsqueda y Filtros
- Búsqueda por descripción
- Filtros por tipo y período
- Búsqueda avanzada por múltiples criterios
- Navegación por meses con vista de balance

### 📊 Análisis y Reportes
- Balance total y mensual
- Estadísticas por categoría
- Historial completo de movimientos

### 🎨 Interfaz de Usuario
- Responsive (desktop y móvil)
- Tema claro/oscuro configurable
- UI intuitiva con tutorial incluido
- Navegación fluida y notificaciones

## 🛠️ Stack Tecnológico

### Backend
- Java 17
- Spring Boot 3.2.2
- Spring Security
- Spring Data JPA
- MySQL 8.0
- Maven

### Frontend
- Thymeleaf
- HTML5, CSS3, JavaScript
- Bootstrap

### Servicios
- SendGrid (envío de emails)
- JWT (tokens)
- BCrypt (cifrado de contraseñas)
- AES-256 (cifrado de datos sensibles)

### DevOps
- Docker
- Docker Compose (opcional)
- Logback (logging)
- Spring Boot Actuator (monitoreo)

## 📁 Estructura del Proyecto (Resumen)
balancd/
├── src/
│ ├── main/java/... # Código Java estructurado por capas
│ ├── main/resources/ # Propiedades, plantillas, recursos estáticos
├── logs/ # Archivos de log
├── data/ # Datos legacy
├── Dockerfile # Configuración Docker
├── pom.xml # Dependencias
└── README.md # Este archivo

## 🔐 Seguridad

### Cifrado
- Contraseñas: BCrypt con salt automático
- Datos financieros: Cifrados con AES-256
- Claves y tokens: Se gestionan vía variables de entorno

### Autenticación y Autorización
- Email obligatorio para activar cuenta
- Recuperación segura de contraseña
- Protección CSRF activa
- Acceso controlado y rutas protegidas por roles
