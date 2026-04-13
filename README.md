# Backend - TD Inmobiliaria

Este repositorio contiene el núcleo del sistema TD Inmobiliaria, una plataforma basada en **microservicios** desarrollada con **Java 21** y **Spring Boot 3.5**. El sistema emplea una arquitectura distribuida para gestionar de forma eficiente el ciclo de vida inmobiliario, desde la publicación de propiedades hasta la gestión de cierres y recibos de pago.

## Estructura de Microservicios

El ecosistema se divide en **9 servicios independientes** que se comunican entre sí y se registran dinámicamente:

```text
Backend/
├── access-control-service    # Gestión de roles, permisos granulares y caché (Caffeine)
├── api-gateway               # Punto de entrada, enrutamiento dinámico y filtro JWT
├── identity-service          # Autenticación, JWT, ciclo de vida de credenciales y auditoría
├── notification-service      # Motor de mensajería (Email) para credenciales y alertas
├── operation-service         # Gestión de cierres de venta y recibos de pago (MinIO)
├── property-service          # Inventario, imágenes, documentos legales y políticas de acceso
├── service-registry          # Servidor de descubrimiento (Netflix Eureka)
├── user-service              # Perfiles de personas (Agentes, Clientes, Dueños) y Favoritos
└── visit-calendar-service    # Agenda, gestión de visitas y flujo de reasignación
```

---

## Detalle de los Microservicios

### 1. **service-registry** (Puerto: 8761)
Servidor **Eureka** que permite el registro y localización de servicios sin necesidad de hardcodear direcciones IP, facilitando el escalamiento.

### 2. **api-gateway** (Puerto: 8080)
Basado en **Spring Cloud Gateway**. Implementa un `AuthenticationFilter` que valida los tokens JWT contra el servicio de identidad y propaga el contexto del usuario (`X-Auth-User-Id`, `X-Auth-Roles`) a los servicios internos.

### 3. **identity-service** (Puerto: 8081)
Gestiona la seguridad centralizada.
- Generación de Access Tokens y Refresh Tokens.
- Control de contraseñas temporales y cambios obligatorios.
- **Auditoría AOP**: Registra cada inicio de sesión y cambio de credenciales.

### 4. **access-control-service** (Puerto: 8082)
Maneja la lógica de RBAC (Control de Acceso Basado en Roles).
- Define catálogos de permisos (Recurso + Acción + Scope).
- Utiliza **Caffeine Cache** para optimizar la validación de permisos en tiempo real.

### 5. **notification-service** (Puerto: 8083)
Encargado del envío de correos electrónicos mediante SMTP.
- Notifica credenciales a nuevos usuarios.
- Alertas de solicitudes de visita y reasignaciones.

### 6. **user-service** (Puerto: 8084)
Almacena la información extendida de los usuarios.
- Diferenciación por tipo: **Admin, Employee (Agent), Owner, Interested Client**.
- Gestión de **Favoritos** con historial de acciones (Added/Removed).
- Lógica de baja lógica para clientes inactivos.

### 7. **property-service** (Puerto: 8085)
El corazón del inventario.
- CRUD de propiedades con historial de precios y cambios de estado.
- **Gestión Multimedia**: Integración con **MinIO** para subida de imágenes y contratos mediante URLs prefirmadas.
- Políticas de acceso específicas por propiedad.

### 8. **visit-calendar-service** (Puerto: 8086)
Gestiona la interacción cliente-agente.
- Sistema de solicitudes de visita con detección de conflictos de horario.
- **Flujo de Reasignación**: Permite a un agente solicitar que otro tome su visita, con sistema de aceptación/rechazo.

### 9. **operation-service** (Puerto: 8087)
Gestiona el proceso de cierre y pagos.
- **Gestión de Recibos**: Almacenamiento seguro de comprobantes de pago en **MinIO**.
- Registro de operaciones comerciales vinculadas a propiedades y clientes.

---

## Tecnologías Principales

* **Java 21** (Runtime)
* **Spring Boot 3.5.13** & **Spring Cloud 2025.0.1**
* **MongoDB**: Base de datos principal para todos los servicios.
* **MinIO**: Almacenamiento de objetos (S3 compatible) para documentos y fotos.
* **JWT (io.jsonwebtoken)**: Para seguridad y stateless auth.
* **OpenFeign**: Comunicación inter-servicio síncrona.
* **Lombok**: Reducción de código boilerplate.

---

## Configuración y Ejecución

### Requisitos Previos
* **JDK 21** o superior.
* **Maven 3.9+**.
* **MongoDB** corriendo en `localhost:27017`.
* **MinIO** corriendo en `localhost:9000` (con buckets: `properties`, `property-images`, `documents`, `operations-payment-receipts`).
* **MailHog** (opcional para pruebas de correo) en puerto `1025`.

### Pasos para iniciar (Orden Recomendado)

1.  **Service Registry**:
    ```bash
    cd service-registry && mvn spring-boot:run
    ```
2.  **API Gateway**:
    ```bash
    cd api-gateway && mvn spring-boot:run
    ```
3.  **Servicios de Soporte**:
    * `identity-service`
    * `access-control-service`
    * `user-service`
4.  **Servicios de Negocio**:
    * `property-service`
    * `visit-calendar-service`
    * `operation-service`
    * `notification-service`

---

## Endpoints de Interés (vía Gateway)

| Servicio | Ruta Base | Acción Principal |
| :--- | :--- | :--- |
| **Auth** | `/auth/login` | Inicio de sesión y obtención de JWT |
| **Users** | `/persons` | Gestión de perfiles y roles |
| **Properties** | `/properties` | Búsqueda y registro de inmuebles |
| **Calendar** | `/api/calendar` | Visualización de agenda |
| **Operations** | `/api/operations` | Registro de cierres y recibos |

## Notas de Desarrollo

* **Seguridad**: Todos los servicios (excepto Auth y Eureka) requieren un token Bearer válido generado por el `identity-service`.
* **Carga de Archivos**: Las imágenes y documentos no viajan por el Gateway; se solicita una **URL prefirmada** al microservicio correspondiente y el cliente sube el archivo directamente a MinIO para optimizar el ancho de banda.
* **Auditoría**: Los servicios de `identity`, `property` y `user` mantienen logs de auditoría en colecciones de MongoDB para rastrear cambios críticos.