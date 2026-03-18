# Backend - TD Inmobiliaria

Este repositorio contiene el backend del proyecto TD Inmobiliaria, implementado con una arquitectura de **microservicios** utilizando **Spring Boot** y **Java 21**. El sistema está diseñado para gestionar usuarios, roles, autenticación y notificaciones de manera escalable y modular.

## Estructura del Proyecto

El backend está compuesto por **6 microservicios principales** y utilidades de soporte:

```
Backend/
├── access-control-service  # Servicio de gestión de roles y permisos
├── api-gateway             # Punto de entrada único con validación JWT
├── identity-service        # Servicio de autenticación y generación de tokens
├── notification-service    # Servicio de notificaciones por email
├── service-registry        # Servicio de descubrimiento Eureka
├── user-service            # Servicio de gestión de información de personas/usuarios
├── Utils/                  # Utilidades y scripts de soporte
│   ├── mongo bd.md         # Contiene el comando para instalar MongoDB
│   ├── MongoDB Semilla.py  # Script para sembrar datos iniciales
│   └── services-launcher/  # Lanzador automatizado de servicios
│       └── run.py          # Script Python para iniciar todos los servicios
└── README.md
```

## Propósito de cada Microservicio

### 1. **service-registry** (Puerto: 8761)
**Servicio de Descubrimiento** - Eureka Server

```bash
git commit -m "feat(service-registry): inicializar servidor Eureka para registro y descubrimiento de microservicios"
```

**Función:** Permite que todos los microservicios se registren y se descubran entre sí dinámicamente.

### 2. **api-gateway** (Puerto: 8080)
**API Gateway** - Spring Cloud Gateway

```bash
git commit -m "feat(api-gateway): crear API Gateway como punto de entrada único con validación JWT integrada"
```

**Función:** Actúa como proxy inverso, enruta las peticiones a los microservicios correspondientes y valida los tokens JWT antes de permitir el acceso.

### 3. **identity-service** (Puerto: 8081)
**Servicio de Identidad** - Gestión de Autenticación

```bash
git commit -m "feat(identity-service): implementar servicio de autenticación con login JWT para clientes y agentes"
```

**Función:** 
- Endpoint `POST /auth/login` para autenticación de usuarios
- Generación y validación de tokens JWT
- Gestión de sesiones

### 4. **access-control-service** (Puerto: 8082)
**Servicio de Control de Acceso** - Roles y Permisos

```bash
git commit -m "feat(access-control): crear servicio de control de acceso con roles granulares (Administrador, Agente, Cliente)"
```

**Función:**
- Endpoint `PATCH /users/:id/role` para asignar roles
- Gestión de permisos granulares por recurso/acción
- Colección `roles` en MongoDB con permisos específicos

### 5. **notification-service** (Puerto: 8083)
**Servicio de Notificaciones** - Emails

```bash
git commit -m "feat(notification): implementar servicio de notificaciones para envío de credenciales por email"
```

**Función:**
- Envío de credenciales temporales a nuevos agentes
- Notificaciones por email
- Templates de correo electrónico
### 6. **user-service** (Puerto: 8084)
**Servicio de Usuarios** - Gestión de Datos de Personas

```bash
git commit -m "feat(user-service): implementar servicio de gestión de perfiles de personas (Admin, Empleado, Propietario, Cliente)"
```

**Función:**
- Gestión de perfiles de personas: Administradores, Empleados, Propietarios y Clientes Interesados.
- Registro y actualización de información personal.
- Asociación de usuarios con sus IDs de autenticación del `identity-service`.
- Consulta de perfiles por tipo o ID.

## Tecnologías Utilizadas

- **Java 21 (Temurin)** - Versión LTS recomendada
- **Spring Boot 3.x** - Framework principal
- **Spring Cloud** - Para microservicios y descubrimiento
- **Maven 3.8.7** - Gestión de dependencias
- **MongoDB** - Base de datos NoSQL
- **JWT** - Autenticación basada en tokens
- **Python 3.12** - Scripts de utilidad

## Requisitos Previos

```bash
# Versiones recomendadas
java --version  # OpenJDK 21.0.10 o superior
mvn --version   # Apache Maven 3.8.7 o superior
python3 --version  # Python 3.12.3 o superior
docker --version  # Para MongoDB (opcional)
```

## Configuración Inicial

### 1. Clonar el repositorio
```bash
git clone https://github.com/OsquiMenacho28/Group-C_Droca-Inmob_Backend.git
cd Backend
```

### 2. Configurar MongoDB

**Si tienes Docker:**
```bash
# El comando exacto está en Utils/mongo bd.md
cat Utils/mongo\ bd.md
```

**Si no tienes Docker:**
Busca cómo instalar MongoDB en tu sistema operativo y, en caso de ser muy complicado, solicita ayuda al equipo.

### 3. Sembrar datos iniciales
```bash
cd Utils
pip install pymongo bcrypt
python3 "MongoDB Semilla.py"
```

## Usuarios de Prueba

### 👑 Master Admin
```
Email:    admin@admin
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

```bash
# 1. Service Registry (Eureka)
cd service-registry
mvn clean spring-boot:run

# 2. API Gateway (nueva terminal)
cd ../api-gateway
mvn clean spring-boot:run

# 3. Identity Service
cd ../identity-service
mvn clean spring-boot:run

# 4. Access Control Service
cd ../access-control-service
mvn clean spring-boot:run

# 5. Notification Service
cd ../notification-service
mvn clean spring-boot:run

# 6. User Service
cd ../user-service
mvn clean spring-boot:run
```

### Opción 2: Usar el lanzador automático (requiere Python)

```bash
cd Utils/services-launcher
python3 run.py
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

## Extensiones Recomendadas para VS Code

- **[Extension Pack for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack)**: Soporte completo para Java
- **[Spring Boot Extension Pack](https://marketplace.visualstudio.com/items?itemName=vmware.vscode-boot-dev-pack)**: Herramientas para Spring Boot
- **[Python](https://marketplace.visualstudio.com/items?itemName=ms-python.python)**: Para scripts de utilidad
- **[REST Client](https://marketplace.visualstudio.com/items?itemName=humao.rest-client)**: Probar APIs directamente desde VS Code
- **[MongoDB for VS Code](https://marketplace.visualstudio.com/items?itemName=mongodb.mongodb-vscode)**: Explorar bases de datos MongoDB

## Notas Importantes

1. **Orden de inicio**: Siempre iniciar `service-registry` primero, luego `api-gateway`, y después los demás servicios
2. **MongoDB**: Si tienes Docker, usa el comando del archivo `mongo bd.md`
3. **Tokens JWT**: El API Gateway valida todos los tokens, excepto endpoints públicos como `/auth/login`
4. **Logs**: Cada servicio genera logs en su propia consola; usa el lanzador para ver todos en una sola ventana
5. **Usuarios de prueba**: Los usuarios listados están disponibles después de ejecutar el script de semilla