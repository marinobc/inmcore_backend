# Backend - TD Inmobiliaria

Este repositorio contiene el backend del proyecto TD Inmobiliaria, implementado con una arquitectura de **microservicios** utilizando **Spring Boot** y **Java 21**. El sistema está diseñado para gestionar propiedades, usuarios, roles, autenticación, visitas y notificaciones de manera escalable y modular.

## Estructura del Proyecto

El backend está compuesto por **8 microservicios principales** y utilidades de soporte:

```
Backend/
├── access-control-service  # Servicio de gestión de roles y permisos
├── api-gateway             # Punto de entrada único con validación JWT
├── identity-service        # Servicio de autenticación y generación de tokens
├── notification-service    # Servicio de notificaciones por email
├── property-service        # Servicio de gestión de propiedades
├── service-registry        # Servicio de descubrimiento Eureka
├── user-service            # Servicio de gestión de información de personas/usuarios
├── visit-calendar-service  # Servicio de gestión de visitas y calendario
├── Utils/                  # Utilidades y scripts de soporte
│   ├── mongo bd.md         # Contiene el comando para instalar MongoDB
│   ├── mailhog.md          # Contiene el comando para instalar MailHog
│   ├── MongoDB Semilla.py  # Script para sembrar datos iniciales
│   └── services-launcher/  # Lanzador automatizado de servicios
│       └── run.py          # Script Python para iniciar todos los servicios
└── README.md
```

## Propósito de cada Microservicio

### 1. **service-registry** (Puerto: 8761)
**Servicio de Descubrimiento** - Eureka Server

**Función:** Permite que todos los microservicios se registren y se descubran entre sí dinámicamente.

### 2. **api-gateway** (Puerto: 8080)
**API Gateway** - Spring Cloud Gateway

**Función:** Actúa como proxy inverso, enruta las peticiones a los microservicios correspondientes y valida los tokens JWT antes de permitir el acceso.

### 3. **identity-service** (Puerto: 8081)
**Servicio de Identidad** - Gestión de Autenticación

**Función:** 
- Endpoint `POST /auth/login` para autenticación de usuarios
- Generación y validación de tokens JWT
- Gestión de sesiones y cambio de contraseña

### 4. **access-control-service** (Puerto: 8082)
**Servicio de Control de Acceso** - Roles y Permisos

**Función:**
- Gestión de roles (Administrador, Agente, Cliente, Propietario)
- Catálogo de permisos granulares por recurso/acción
- Asignación de roles a usuarios

### 5. **notification-service** (Puerto: 8083)
**Servicio de Notificaciones** - Emails

**Función:**
- Envío de credenciales temporales a nuevos usuarios
- Notificaciones por email
- Registro de logs de envío

### 6. **property-service** (Puerto: 8085)
**Servicio de Propiedades** - Gestión Inmobiliaria

**Función:**
- CRUD completo de propiedades
- Historial de precios y asignaciones
- Asignación de agentes a propiedades
- Control de acceso basado en permisos

### 7. **user-service** (Puerto: 8084)
**Servicio de Usuarios** - Gestión de Datos de Personas

**Función:**
- Gestión de perfiles: Administradores, Empleados, Propietarios y Clientes Interesados
- Registro y actualización de información personal
- Asociación con IDs de autenticación del identity-service
- Consulta de perfiles por tipo o ID

### 8. **visit-calendar-service** (Puerto: 8086)
**Servicio de Visitas** - Agenda y Calendario

**Función:**
- Gestión de solicitudes de visita a propiedades
- Creación de eventos de calendario
- Validación de conflictos de horarios
- Notificaciones de confirmación

## Tecnologías Utilizadas

- **Java 21 (Temurin)** - Versión LTS recomendada
- **Spring Boot 3.x** - Framework principal
- **Spring Cloud** - Para microservicios y descubrimiento
- **Maven** - Gestión de dependencias
- **MongoDB** - Base de datos NoSQL
- **JWT** - Autenticación basada en tokens
- **Python 3.12** - Scripts de utilidad

## Requisitos Previos

```bash
# Bash / Linux / macOS
java --version  # OpenJDK 21.0.10 o superior
mvn --version   # Apache Maven 3.8.7 o superior
python3 --version  # Python 3.12.3 o superior
docker --version  # Para MongoDB (opcional)

# Windows (CMD)
java --version
mvn --version
python --version
docker --version
```

## Configuración Inicial

### 1. Clonar el repositorio

```bash
# Bash / Linux / macOS
git clone https://github.com/OsquiMenacho28/Group-C_Droca-Inmob_Backend.git
cd Group-C_Droca-Inmob_Backend

# Windows (CMD)
git clone https://github.com/OsquiMenacho28/Group-C_Droca-Inmob_Backend.git
cd Group-C_Droca-Inmob_Backend
```

### 2. Configurar MongoDB

**Si tienes Docker:**
```bash
# El comando exacto está en Utils/mongo bd.md

# En Linux/macOS:
cat Utils/mongo\ bd.md

# En Windows (CMD):
type Utils\mongo bd.md
```

**Si no tienes Docker:**
Busca cómo instalar MongoDB en tu sistema operativo y, en caso de ser muy complicado, solicita ayuda al equipo.

### 3. Configurar MailHog (para pruebas de email)

**Si tienes Docker:**
```bash
# El comando exacto está en Utils/mailhog.md

# En Linux/macOS:
cat Utils/mailhog.md

# En Windows (CMD):
type Utils\mailhog.md
```

**Si no tienes Docker:**
MailHog es una herramienta para probar envíos de email en entorno de desarrollo. Puedes descargarlo desde https://github.com/mailhog/MailHog/releases o solicitar ayuda al equipo.

### 4. Sembrar datos iniciales

```bash
# Bash / Linux / macOS
cd Utils
pip install pymongo bcrypt
python3 "MongoDB Semilla.py"

# Windows (CMD)
cd Utils
pip install pymongo bcrypt
python "MongoDB Semilla.py"
```

## Usuarios de Prueba

### 👑 Master Admin
```
Email: admin@admin
Contraseña: admin
```

### 👤 Usuarios de Prueba (todos con contraseña: `password`)

| Rol | Email |
|-----|-------|
| **Administradores** | `admin1@admin`, `admin2@admin` |
| **Agentes** | `agent1@user`, `agent2@user` |
| **Propietarios** | `owner1@user`, `owner2@user` |
| **Clientes** | `client1@user`, `client2@user` |
| **Supervisor** | `supervisor1@user` |
| **Visualizador** | `viewer1@user` |

## Ejecución de los Microservicios

### Opción 1: Iniciar manualmente (respetar orden)

**Bash / Linux / macOS:**
```bash
# Terminal 1 - Service Registry (Eureka)
cd service-registry
mvn clean spring-boot:run

# Terminal 2 - API Gateway
cd ../api-gateway
mvn clean spring-boot:run

# Terminal 3 - Identity Service
cd ../identity-service
mvn clean spring-boot:run

# Terminal 4 - Access Control Service
cd ../access-control-service
mvn clean spring-boot:run

# Terminal 5 - Notification Service
cd ../notification-service
mvn clean spring-boot:run

# Terminal 6 - User Service
cd ../user-service
mvn clean spring-boot:run

# Terminal 7 - Property Service
cd ../property-service
mvn clean spring-boot:run

# Terminal 8 - Visit Calendar Service
cd ../visit-calendar-service
mvn clean spring-boot:run
```

**Windows (CMD):**
```cmd
REM Terminal 1 - Service Registry (Eureka)
cd service-registry
mvn clean spring-boot:run

REM Terminal 2 - API Gateway
cd ../api-gateway
mvn clean spring-boot:run

REM Terminal 3 - Identity Service
cd ../identity-service
mvn clean spring-boot:run

REM Terminal 4 - Access Control Service
cd ../access-control-service
mvn clean spring-boot:run

REM Terminal 5 - Notification Service
cd ../notification-service
mvn clean spring-boot:run

REM Terminal 6 - User Service
cd ../user-service
mvn clean spring-boot:run

REM Terminal 7 - Property Service
cd ../property-service
mvn clean spring-boot:run

REM Terminal 8 - Visit Calendar Service
cd ../visit-calendar-service
mvn clean spring-boot:run
```

### Opción 2: Usar el lanzador automático (requiere Python)

```bash
# Bash / Linux / macOS
cd Utils/services-launcher
python3 run.py

# Windows (CMD)
cd Utils/services-launcher
python run.py
```

Este script iniciará todos los servicios en el orden correcto automáticamente.

## Puertos y Endpoints

| Servicio | Puerto | URL Base | Descripción |
|----------|--------|----------|-------------|
| service-registry | 8761 | http://localhost:8761 | Dashboard Eureka |
| api-gateway | 8080 | http://localhost:8080 | Punto de entrada único |
| identity-service | 8081 | http://localhost:8081 | Autenticación |
| access-control-service | 8082 | http://localhost:8082 | Roles y permisos |
| notification-service | 8083 | http://localhost:8083 | Notificaciones |
| user-service | 8084 | http://localhost:8084 | Gestión de usuarios |
| property-service | 8085 | http://localhost:8085 | Gestión de propiedades |
| visit-calendar-service | 8086 | http://localhost:8086 | Visitas y calendario |
| MailHog UI | 8025 | http://localhost:8025 | Interfaz web de emails de prueba |

## Endpoints Principales del API Gateway

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| POST | `/auth/login` | Autenticación de usuario |
| POST | `/auth/change-password` | Cambio de contraseña |
| GET | `/users/me` | Obtener usuario actual |
| GET | `/roles` | Listar roles disponibles |
| GET | `/properties` | Listar propiedades |
| POST | `/properties` | Crear propiedad (Admin/Agente) |
| GET | `/persons` | Listar personas |
| GET | `/api/calendar/events` | Obtener eventos de calendario |
| POST | `/api/visits` | Solicitar visita |

## Extensiones Recomendadas para VS Code

- **[Extension Pack for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack)**: Soporte completo para Java
- **[Spring Boot Extension Pack](https://marketplace.visualstudio.com/items?itemName=vmware.vscode-boot-dev-pack)**: Herramientas para Spring Boot
- **[Python](https://marketplace.visualstudio.com/items?itemName=ms-python.python)**: Para scripts de utilidad
- **[REST Client](https://marketplace.visualstudio.com/items?itemName=humao.rest-client)**: Probar APIs directamente desde VS Code
- **[MongoDB for VS Code](https://marketplace.visualstudio.com/items?itemName=mongodb.mongodb-vscode)**: Explorar bases de datos MongoDB

## Notas Importantes

1. **Orden de inicio**: Siempre iniciar `service-registry` primero, luego `api-gateway`, y después los demás servicios
2. **MongoDB**: Asegúrate de que MongoDB esté corriendo antes de ejecutar cualquier servicio
3. **MailHog**: Útil para ver los emails enviados por el sistema en desarrollo sin necesidad de configurar un servidor SMTP real
4. **Tokens JWT**: El API Gateway valida todos los tokens, excepto endpoints públicos como `/auth/login`
5. **Logs**: Cada servicio genera logs en su propia consola; usa el lanzador para ver todos en una sola ventana
6. **Usuarios de prueba**: Los usuarios listados están disponibles después de ejecutar el script de semilla
7. **Property Service**: Requiere que los usuarios estén registrados en identity-service y user-service para asignar agentes
8. **Visit Calendar**: Las visitas requieren que existan propiedades y usuarios válidos
