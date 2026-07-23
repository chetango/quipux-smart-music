# Documento de Diseño Técnico — Quipux Senior API

**Versión:** 1.1  
**Fecha:** 23 de julio de 2026  
**Estado:** Borrador final

---

## Tabla de contenidos

1. [Objetivo de la solución](#1-objetivo-de-la-solución)
2. [Decisiones de arquitectura adoptadas](#2-decisiones-de-arquitectura-adoptadas)
3. [Arquitectura por capas](#3-arquitectura-por-capas)
4. [Estructura de paquetes](#4-estructura-de-paquetes)
5. [Modelo de dominio](#5-modelo-de-dominio)
6. [Contrato de endpoints](#6-contrato-de-endpoints)
7. [Flujo de autenticación](#7-flujo-de-autenticación)
8. [Flujo de integración con Spotify](#8-flujo-de-integración-con-spotify)
9. [Flujo del módulo de IA](#9-flujo-del-módulo-de-ia)
10. [Estrategia de manejo de errores](#10-estrategia-de-manejo-de-errores)
11. [Estrategia de pruebas](#11-estrategia-de-pruebas)
12. [Orden de implementación por fases](#12-orden-de-implementación-por-fases)
13. [Riesgos y consideraciones](#13-riesgos-y-consideraciones)

---

## 1. Objetivo de la solución

**Quipux Senior API** es una API REST desarrollada en Java con Spring Boot que permite a usuarios autenticados gestionar listas de reproducción musicales y sus canciones asociadas. El sistema integra la API de Spotify para la obtención de géneros musicales y expone un módulo de inteligencia artificial basado en el modelo Gemini de Google, que genera recomendaciones de canciones a partir del contenido de una lista existente. La solución incluye un frontend web construido con Vue 3 + Vite que consume la API.

El propósito principal es demostrar, en el contexto de una prueba técnica senior, la capacidad de diseñar y construir un sistema con múltiples integraciones externas, autenticación robusta, resiliencia ante fallos de terceros, y criterios de arquitectura profesional.

---

## 2. Decisiones de arquitectura adoptadas

| # | Decisión | Alternativas consideradas | Justificación |
|---|----------|--------------------------|---------------|
| D1 | **Spring Boot 3.x + Maven** como base del proyecto backend | Spring Boot 2.x, Quarkus | Spring Boot 3.x es el estándar actual del ecosistema Java empresarial. Maven provee reproducibilidad de builds y es compatible con los pipelines de CI/CD más extendidos. |
| D2 | **H2 en memoria** como base de datos | PostgreSQL, MySQL | Simplifica la configuración para la prueba técnica; no requiere infraestructura adicional y el esquema se recrea en cada inicio mediante `schema.sql` y `data.sql`. |
| D3 | **JWT (stateless)** para autenticación | Sesiones HTTP, OAuth2 completo | JWT elimina la necesidad de almacenar estado en servidor, es portable entre cliente web y Postman, y es el mecanismo pedido explícitamente en los requisitos. |
| D4 | **Sin registro público de usuarios** — usuario precargado vía `data.sql` | Registro abierto (POST /auth/register) | La prueba no requiere gestión de usuarios; un usuario precargado reduce superficie de ataque y scope de implementación, manteniendo el foco en las funcionalidades core. |
| D5 | **`listName` único + constraint DB, ID interno como PK de FK** | Usar `listName` como PK directa | El `listName` es el identificador semántico en URLs. Sin embargo, usar el ID numérico como clave foránea interna protege la integridad relacional frente a renombrados y es la práctica estándar de JPA. La unicidad se garantiza con `@Column(unique = true)`. |
| D6 | **Sin ownership de listas** | Asignar owner (userId) a cada lista | Los requisitos no especifican propiedad. Cualquier usuario autenticado puede crear, modificar y eliminar cualquier lista, simplificando el modelo de autorización. |
| D7 | **Spotify Client Credentials OAuth** para obtener géneros | API key fija, proxy propio | Client Credentials es el flujo correcto para comunicación servidor-servidor sin usuario final. El token obtenido se cachea con TTL de 55 minutos para evitar peticiones innecesarias. |
| D8 | **Gemini (Google)** como proveedor de IA | OpenAI GPT-4, Ollama local | Gemini Free Tier permite desarrollo sin coste. La integración se hace detrás de la interfaz `AiRecommendationService`, lo que hace el proveedor intercambiable sin modificar el resto del sistema. |
| D9 | **Interfaz `AiRecommendationService`** que desacopla el módulo IA | Llamada directa a Gemini en el controlador | El desacoplamiento permite mockear el proveedor en tests, cambiar de proveedor sin afectar controladores ni servicios de negocio, y cumple con el Principio de Inversión de Dependencias. |
| D10 | **Prompt versionado en `resources/prompts/recommendations.txt`** | Prompt hardcodeado en código, prompt en base de datos | Al residir en el repositorio, el prompt forma parte del historial de versiones y puede auditarse o modificarse con un simple commit, sin necesidad de redespliegue de configuración externa. |
| D11 | **Spring Cache + Caffeine** para caché en memoria | Redis, Ehcache | Para una aplicación de prueba de instancia única, Caffeine es suficiente, performante y no requiere infraestructura adicional. Se configuran dos cachés: `spotifyToken` (55 min) y `recommendations` (30 min). |
| D12 | **Vue 3 + Vite** como frontend | Angular, JSF | Vue 3 + Vite ofrece arranque rápido, tamaño de bundle reducido y DX adecuada para el tiempo de desarrollo disponible. Su arquitectura de componentes facilita la integración con la API REST. |
| D13 | **Credenciales externas solo por variables de entorno** | `application.properties` con valores reales, archivo `.env` commiteado | Evita la exposición accidental de secretos en el repositorio. Las variables `GEMINI_API_KEY`, `SPOTIFY_CLIENT_ID` y `SPOTIFY_CLIENT_SECRET` se leen vía `@Value` o `@ConfigurationProperties`. |

---

## 3. Arquitectura por capas

El sistema sigue una arquitectura en capas estricta. Cada capa tiene una responsabilidad única y solo se comunica con la capa inmediatamente inferior.

```
┌─────────────────────────────────────────────────────────┐
│                  CAPA DE PRESENTACIÓN                   │
│           (Controllers REST + DTOs de entrada/salida)   │
├─────────────────────────────────────────────────────────┤
│                    CAPA DE SERVICIO                     │
│         (Lógica de negocio + Orquestación IA)           │
├─────────────────────────────────────────────────────────┤
│                  CAPA DE PERSISTENCIA                   │
│             (Repositorios JPA + Entidades)              │
├───────────────────────────┬─────────────────────────────┤
│    CAPA DE INTEGRACIÓN    │   ASPECTOS TRANSVERSALES    │
│  (Spotify + Gemini AI)    │  (Seguridad, Caché, Errores)│
└───────────────────────────┴─────────────────────────────┘
```

### 3.1 Capa de Presentación

**Paquete:** `controller`

Es el punto de entrada de todas las peticiones HTTP. Sus responsabilidades son:

- Recibir y deserializar los cuerpos de petición (DTOs de request).
- Validar los datos de entrada mediante anotaciones de Bean Validation (`@Valid`).
- Delegar la lógica de negocio a los servicios correspondientes.
- Serializar y devolver las respuestas HTTP con los códigos de estado apropiados.
- No contiene lógica de negocio ni acceso directo a repositorios.

Controladores: `AuthController`, `PlayListController`, `SongController`, `SpotifyController`, `RecommendationController`.

### 3.2 Capa de Servicio

**Paquete:** `service`

Contiene toda la lógica de negocio de la aplicación. Sus responsabilidades son:

- Implementar las reglas de negocio (unicidad de `listName`, existencia de entidades, etc.).
- Coordinar llamadas a repositorios, integraciones externas y el módulo de IA.
- Manejar transacciones (`@Transactional`).
- Lanzar excepciones de dominio (`ResourceNotFoundException`, `DuplicatePlaylistException`, `AiServiceException`) ante condiciones de error.
- Construir los DTOs de respuesta a partir de las entidades.

Servicios: `UserDetailsServiceImpl`, `PlayListService`, `SongService`, `SpotifyService`, `RecommendationService`, `AiRecommendationService` (interfaz).

### 3.3 Capa de Persistencia

**Paquete:** `repository` + `entity`

Gestiona la interacción con la base de datos. Sus responsabilidades son:

- Definir las entidades JPA que mapean el modelo de dominio (`User`, `PlayList`, `Song`).
- Exponer repositorios Spring Data JPA con operaciones CRUD y queries derivadas.
- No contiene lógica de negocio; su única responsabilidad es la lectura y escritura de datos.

Repositorios: `UserRepository`, `PlayListRepository`, `SongRepository`.

La base de datos H2 se inicializa con `schema.sql` (DDL) y `data.sql` (DML con el usuario precargado) al arrancar la aplicación.

### 3.4 Capa de Integración

**Paquete:** `integration`

Encapsula la comunicación con servicios externos. Sus responsabilidades son:

- Gestionar el ciclo de vida del token OAuth de Spotify (obtención y renovación con caché de 55 minutos).
- Ejecutar las peticiones HTTP a la API de Spotify para obtener géneros musicales.
- Implementar `GeminiAiRecommendationService`, la implementación concreta de `AiRecommendationService`, que construye el prompt, llama a la API de Gemini, aplica timeout de 10 segundos y parsea la respuesta.
- Aislar cualquier cambio en contratos de APIs externas al interior de esta capa.

Clases: `SpotifyClient`, `SpotifyTokenClient`, `GeminiAiRecommendationService`.

### 3.5 Aspectos Transversales (Cross-cutting)

**Paquetes:** `config`, `security`, `exception`

Funcionalidades que afectan a todas las capas sin pertenecer a ninguna en particular:

- **Seguridad:** Filtro JWT (`JwtAuthFilter`), configuración de Spring Security (`SecurityConfig`), generación y validación de tokens JWT (`JwtService`).
- **Manejo de errores:** `GlobalExceptionHandler` (`@RestControllerAdvice`) que intercepta excepciones y produce respuestas de error consistentes.
- **Caché:** Configuración de Caffeine con los TTLs definidos para `spotifyToken` y `recommendations`.
- **Configuración general:** Bean `WebClient.Builder` para llamadas HTTP externas, propiedades de aplicación.

---

## 4. Estructura de paquetes

```
src/
└── main/
    ├── java/
    │   └── com/quipux/api/
    │       ├── QuipuxApiApplication.java            # Clase principal, punto de entrada Spring Boot
    │       │
    │       ├── config/                              # Configuración técnica de la aplicación
    │       │   ├── CacheConfig.java                 # Beans de Caffeine CacheManager con TTLs
    │       │   ├── SecurityConfig.java              # Cadena de filtros Spring Security, CORS, rutas públicas
        │   └── WebClientConfig.java             # Bean WebClient.Builder para llamadas HTTP externas
    │       │
    │       ├── controller/                          # Controladores REST — solo reciben y responden
    │       │   ├── AuthController.java              # POST /auth/login
    │       │   ├── PlayListController.java          # CRUD /lists y /lists/{listName}
    │       │   ├── SongController.java              # POST y DELETE /lists/{listName}/songs
    │       │   ├── SpotifyController.java           # GET /spotify/genres
    │       │   └── RecommendationController.java    # GET /lists/{listName}/recommendations
    │       │
    │       ├── dto/                                 # Objetos de transferencia de datos (request y response)
    │       │   ├── request/
    │       │   │   ├── LoginRequest.java            # Credenciales de login
    │       │   │   ├── PlayListRequest.java         # Crear/actualizar lista
    │       │   │   └── SongRequest.java             # Agregar canción a una lista
    │       │   └── response/
    │       │       ├── AuthResponse.java            # JWT token devuelto tras login
    │       │       ├── ErrorResponse.java           # Estructura uniforme de todas las respuestas de error
    │       │       ├── PlayListResponse.java        # Representación de lista con sus canciones
    │       │       ├── SongResponse.java            # Representación de una canción
    │       │       └── RecommendedSongResponse.java # titulo, artista, genero (módulo IA)
    │       │
    │       ├── entity/                              # Entidades JPA — mapeo objeto-relacional
    │       │   ├── User.java                        # Usuario del sistema (precargado)
    │       │   ├── PlayList.java                    # Lista de reproducción (listName único)
    │       │   └── Song.java                        # Canción asociada a una PlayList
    │       │
    │       ├── exception/                           # Jerarquía de excepciones + handler global
    │       │   ├── ResourceNotFoundException.java   # Recurso no encontrado → 404
    │       │   ├── DuplicatePlaylistException.java  # listName ya existe → 409
    │       │   ├── AiServiceException.java          # Fallo del proveedor IA → 503
    │       │   ├── SpotifyServiceException.java     # Fallo del cliente Spotify → 503
    │       │   └── GlobalExceptionHandler.java      # @RestControllerAdvice — mapeo excepción → HTTP
    │       │
    │       ├── integration/                         # Clientes de servicios externos
    │       │   ├── SpotifyClient.java               # Obtiene géneros de Spotify usando el token
    │       │   ├── SpotifyTokenClient.java          # Token OAuth Spotify con @Cacheable (TTL: 55 min)
    │       │   └── GeminiAiRecommendationService.java # Impl. de AiRecommendationService para Gemini
    │       │
    │       ├── repository/                          # Interfaces Spring Data JPA
    │       │   ├── UserRepository.java
    │       │   ├── PlayListRepository.java          # findByListName(String) para búsqueda por nombre
    │       │   └── SongRepository.java
    │       │
    │       ├── security/                            # Infraestructura de seguridad JWT
    │       │   ├── JwtService.java                  # Generación, firma y validación de tokens JWT
    │       │   └── JwtAuthFilter.java               # OncePerRequestFilter — extrae y valida JWT en cabecera
    │       │
    │       └── service/                             # Lógica de negocio de la aplicación
    │           ├── UserDetailsServiceImpl.java      # Carga usuario desde BD para Spring Security
    │           ├── PlayListService.java             # CRUD de listas, validación de unicidad
    │           ├── SongService.java                 # Agregar y eliminar canciones de una lista
    │           ├── SpotifyService.java              # Delega en SpotifyClient, aplica caché token
    │           ├── AiRecommendationService.java     # Interfaz — contrato del módulo IA
    │           └── RecommendationService.java       # Orquesta: busca lista → llama AI → devuelve DTOs
    │
    └── resources/
        ├── application.yml                          # Configuración general, propiedades de caché y JWT
        ├── schema.sql                               # DDL — creación de tablas (ejecutado al inicio)
        ├── data.sql                                 # DML — usuario precargado con contraseña hasheada
        └── prompts/
            └── recommendations.txt                 # Prompt versionado para el módulo de recomendaciones IA

src/
└── test/
    └── java/
        └── com/quipux/api/
            ├── controller/
            │   ├── PlayListControllerTest.java
            │   ├── SongControllerTest.java
            │   └── RecommendationControllerTest.java   # Incluye mock de fallo del proveedor IA
            ├── service/
            │   ├── PlayListServiceTest.java
            │   ├── SongServiceTest.java
            │   └── RecommendationServiceTest.java
            └── integration/
                └── GeminiAiRecommendationServiceTest.java
```

> **Nota de entrega:** de las clases de test listadas, se implementaron las correspondientes al módulo IA: `RecommendationControllerTest` (7 tests), `RecommendationServiceTest` (5 tests) y `QuipuxApiApplicationTests` (3 tests). Total: **15 tests, 0 fallos**. Los tests del CRUD base (`PlayListControllerTest`, `SongControllerTest`, etc.) no son requisito del Módulo IA y no se incluyeron en esta entrega.

---

## 5. Modelo de dominio

### 5.1 Entidad `User`

Representa al usuario del sistema. No existe registro público; se carga un único usuario mediante `data.sql` al arrancar la aplicación.

| Campo | Tipo Java | Tipo DB | Restricciones | Descripción |
|-------|-----------|---------|---------------|-------------|
| `id` | `Long` | `BIGINT` | PK, auto-generado | Identificador interno |
| `username` | `String` | `VARCHAR(100)` | NOT NULL, UNIQUE | Nombre de usuario para login |
| `password` | `String` | `VARCHAR(255)` | NOT NULL | Contraseña hasheada con BCrypt |

**Notas:**
- Spring Security carga este usuario a través de `UserDetailsServiceImpl` buscando por `username`.
- La contraseña almacenada es siempre el hash BCrypt, nunca el texto plano.
- No existe endpoint de registro; el usuario se crea exclusivamente en `data.sql`.

---

### 5.2 Entidad `PlayList`

Representa una lista de reproducción musical. El campo `listName` es único y actúa como identificador semántico en las URLs, aunque la clave primaria interna es el campo `id`.

| Campo | Tipo Java | Tipo DB | Restricciones | Descripción |
|-------|-----------|---------|---------------|-------------|
| `id` | `Long` | `BIGINT` | PK, auto-generado | Clave primaria interna usada en FK |
| `listName` | `String` | `VARCHAR(200)` | NOT NULL, UNIQUE | Nombre de la lista; identificador en URLs |
| `description` | `String` | `VARCHAR(500)` | NOT NULL | Descripción de la lista |
| `songs` | `List<Song>` | — | OneToMany (cascade ALL, orphanRemoval) | Canciones que pertenecen a esta lista |

**Restricciones:**
- `listName` tiene constraint `UNIQUE` a nivel de base de datos (`@Column(unique = true)`).
- El `id` es la clave foránea referenciada por `Song` (`playlist_id`), no `listName`.
- Al eliminar una lista, se eliminan en cascada todas sus canciones (`CascadeType.ALL`, `orphanRemoval = true`).
- Si se intenta crear una lista con un `listName` ya existente, `PlayListService` lanza `DuplicatePlaylistException` → HTTP 409.

---

### 5.3 Entidad `Song`

Representa una canción dentro de una lista de reproducción. El campo `anno` es de tipo `String` para acomodar valores como `"2023"` o `"circa 1990"` sin forzar conversiones numéricas.

| Campo | Tipo Java | Tipo DB | Restricciones | Descripción |
|-------|-----------|---------|---------------|-------------|
| `id` | `Long` | `BIGINT` | PK, auto-generado | Identificador interno de la canción |
| `titulo` | `String` | `VARCHAR(200)` | NOT NULL | Título de la canción |
| `artista` | `String` | `VARCHAR(200)` | NOT NULL | Nombre del artista o banda |
| `album` | `String` | `VARCHAR(200)` | NOT NULL | Nombre del álbum |
| `anno` | `String` | `VARCHAR(20)` | NOT NULL | Año de lanzamiento (**String**, no `int`) |
| `genero` | `String` | `VARCHAR(100)` | NOT NULL | Género musical (valor proveniente de Spotify) |
| `playList` | `PlayList` | `BIGINT` (FK) | ManyToOne, NOT NULL | Lista a la que pertenece la canción |

**Notas:**
- `anno` se declara como `String` deliberadamente. No se aplica validación de formato numérico.
- El `genero` debe provenir de los valores devueltos por `GET /spotify/genres`. No se valida en backend que el valor corresponda exactamente a uno de los géneros Spotify, pero es la convención de uso esperada.
- No existe endpoint de actualización de canciones individuales; solo se pueden agregar y eliminar.

---

### 5.4 Diagrama de relaciones

```
┌──────────┐          ┌────────────────┐           ┌────────────┐
│   User   │          │    PlayList    │   1..*    │    Song    │
│──────────│          │────────────────│───────────│────────────│
│ id (PK)  │          │ id (PK)        │           │ id (PK)    │
│ username │          │ listName (UQ)  │           │ titulo     │
│ password │          │ description    │           │ artista    │
└──────────┘          │ songs []       │◄──────────│ album      │
                      └────────────────┘  FK:      │ anno       │
                                          playlist_id│ genero   │
                                                    └────────────┘
```

> `User` y `PlayList` no tienen relación directa (sin ownership).

---

## 6. Contrato de endpoints

### 6.1 Autenticación

| Método | Path | Auth requerida | Request Body | Respuesta exitosa | Errores |
|--------|------|---------------|--------------|-------------------|---------|
| `POST` | `/auth/login` | No | `{ "username": "...", "password": "..." }` | **200** `{ "token": "<JWT>" }` | **401** credenciales inválidas |

> No existe `POST /auth/register`. El usuario se carga en `data.sql`.

---

### 6.2 Listas (`/lists`)

| Método | Path | Auth | Request Body | Respuesta exitosa | Errores |
|--------|------|------|--------------|-------------------|---------|
| `GET` | `/lists` | JWT | — | **200** `List<PlayListResponse>` | **401** |
| `POST` | `/lists` | JWT | `{ "listName": "...", "description": "..." }` | **201** `PlayListResponse` | **400**, **401**, **409** nombre duplicado |
| `GET` | `/lists/{listName}` | JWT | — | **200** `PlayListResponse` | **401**, **404** |
| `PUT` | `/lists/{listName}` | JWT | `{ "listName": "...", "description": "..." }` | **200** `PlayListResponse` | **400**, **401**, **404**, **409** |
| `DELETE` | `/lists/{listName}` | JWT | — | **204** sin cuerpo | **401**, **404** |

**Estructura `PlayListResponse`:**
```json
{
  "listName": "mis favoritas",
  "description": "Canciones que me gustan",
  "songs": [
    {
      "id": 1,
      "titulo": "Bohemian Rhapsody",
      "artista": "Queen",
      "album": "A Night at the Opera",
      "anno": "1975",
      "genero": "rock"
    }
  ]
}
```

---

### 6.3 Canciones (`/lists/{listName}/songs`)

| Método | Path | Auth | Request Body | Respuesta exitosa | Errores |
|--------|------|------|--------------|-------------------|---------|
| `POST` | `/lists/{listName}/songs` | JWT | `{ "titulo": "...", "artista": "...", "album": "...", "anno": "...", "genero": "..." }` | **201** `SongResponse` | **400**, **401**, **404** lista no encontrada |
| `DELETE` | `/lists/{listName}/songs/{id}` | JWT | — | **204** sin cuerpo | **401**, **404** lista o canción no encontrada |

> **Advertencia R3:** Si `listName` contiene espacios o caracteres especiales, el cliente debe aplicar URL encoding antes de incluirlo en el path (e.g., `mis%20favoritas`). Ver sección 13.

---

### 6.4 Spotify

| Método | Path | Auth | Request Body | Respuesta exitosa | Errores |
|--------|------|------|--------------|-------------------|---------|
| `GET` | `/spotify/genres` | JWT | — | **200** `List<String>` | **401**, **503** si Spotify no responde |

**Ejemplo de respuesta:**
```json
["acoustic", "afrobeat", "alt-rock", "ambient", "blues", "classical", "country", "dance",
 "electronic", "folk", "hip-hop", "jazz", "latin", "pop", "r-n-b", "reggae", "rock", "soul"]
```

---

### 6.5 Recomendaciones IA

| Método | Path | Auth | Request Body | Respuesta exitosa | Errores |
|--------|------|------|--------------|-------------------|---------|
| `GET` | `/lists/{listName}/recommendations` | JWT | — | **200** `List<RecommendedSongResponse>` | **401**, **404** lista no encontrada, **503** fallo IA |

**Estructura `RecommendedSongResponse`:**
```json
[
  { "titulo": "Stairway to Heaven", "artista": "Led Zeppelin", "genero": "rock" },
  { "titulo": "Hotel California",   "artista": "Eagles",        "genero": "rock" }
]
```

**Respuesta de fallback (503):**
```json
{
  "error": "AI_UNAVAILABLE",
  "message": "El servicio de recomendaciones no está disponible en este momento. Inténtelo más tarde."
}
```

---

## 7. Flujo de autenticación

### 7.1 Descripción paso a paso

1. **Arranque:** Spring Boot ejecuta `data.sql`, insertando el usuario precargado con hash BCrypt en la tabla `users`.
2. **Request de login:** El cliente envía `POST /auth/login` con `{ username, password }`. Es la única ruta pública del sistema.
3. **Validación de credenciales:** `AuthController` delega en `AuthenticationManager.authenticate()`. Spring Security invoca `UserDetailsServiceImpl.loadUserByUsername()` y compara la contraseña con BCrypt.
4. **Generación del token:** Si las credenciales son válidas, `JwtService.generateToken()` crea un JWT firmado con `HS256`, con `username` como subject y expiración configurable (default: 24 h).
5. **Respuesta:** HTTP 200 con `{ "token": "<JWT>" }`. El cliente almacena el token.
6. **Requests subsiguientes:** El cliente incluye `Authorization: Bearer <JWT>` en cada petición.
7. **Validación del filtro:** `JwtAuthFilter` intercepta cada request, extrae el token, llama a `JwtService.validateToken()` y, si es válido, establece el `SecurityContext`.
8. **Acceso o rechazo:** Spring Security permite continuar al controlador o responde 401 si el token está ausente, expirado o es inválido.

### 7.2 Diagrama de secuencia

```
Cliente               JwtAuthFilter        AuthController      AuthManager       JwtService        BD
  │                        │                    │                   │                │               │
  │── POST /auth/login ───►│                   │                   │                │               │
  │  (ruta pública)        │──── pass-through ►│                   │                │               │
  │                        │                   │── authenticate() ►│                │               │
  │                        │                   │                   │── loadUser() ──────────────────►│
  │                        │                   │                   │◄── UserDetails ─────────────────│
  │                        │                   │                   │── comparar BCrypt               │
  │                        │                   │◄── Auth ok ───────│                │               │
  │                        │                   │── generateToken() ────────────────►│               │
  │                        │                   │◄── JWT ────────────────────────────│               │
  │◄── 200 { token } ──────│◄── AuthResponse ──│                   │                │               │
  │                        │                   │                   │                │               │
  │── GET /lists ──────────►│                  │                   │                │               │
  │  Authorization: Bearer │── validateToken() ─────────────────────────────────────►│              │
  │                        │◄── valid ──────────────────────────────────────────────│               │
  │                        │── setAuthentication()                 │                │               │
  │                        │──────────────────►│                   │                │               │
  │◄── 200 [listas] ───────│◄── respuesta ─────│                   │                │               │
  │                        │                   │                   │                │               │
  │── GET /lists ──────────►│                  │                   │                │               │
  │  (token expirado)      │── validateToken() ─────────────────────────────────────►│              │
  │                        │◄── inválido ───────────────────────────────────────────│               │
  │◄── 401 Unauthorized ───│                   │                   │                │               │
```

---

## 8. Flujo de integración con Spotify

### 8.1 Configuración requerida

| Variable de entorno | Descripción |
|---------------------|-------------|
| `SPOTIFY_CLIENT_ID` | ID de la aplicación registrada en Spotify Developer Dashboard |
| `SPOTIFY_CLIENT_SECRET` | Secreto de la aplicación Spotify |

Se inyectan vía `@Value` en `SpotifyClient` y `SpotifyTokenClient`.

### 8.2 Endpoints Spotify utilizados

- **Obtención de token:** `POST https://accounts.spotify.com/api/token`  
  Header: `Authorization: Basic Base64(clientId:clientSecret)`  
  Body: `grant_type=client_credentials`

- **Obtención de géneros:** `GET https://api.spotify.com/v1/recommendations/available-genre-seeds`  
  Header: `Authorization: Bearer <access_token>`

### 8.3 Flujo paso a paso

1. El cliente autenticado envía `GET /spotify/genres` con su JWT.
2. `SpotifyController` delega en `SpotifyService.getAvailableGenres()`.
3. `SpotifyService` llama a `SpotifyClient.getGenres()`.
4. `SpotifyClient` llama a `spotifyTokenClient.getAccessToken()` del componente `SpotifyTokenClient`, anotado con `@Cacheable("spotifyToken")`. Al ser una invocación entre beans distintos, el proxy Spring AOP intercepta correctamente y la caché funciona.
5. **Caché hit (TTL: 55 min):** se devuelve el token cacheado sin llamada HTTP a Spotify.
6. **Caché miss:** se realiza `POST /api/token` con las credenciales en Base64. Spotify responde con `{ "access_token": "...", "expires_in": 3600 }`. El TTL de caché se fija en **55 minutos** (margen de 5 min respecto a la expiración real de Spotify).
7. Con el token vigente, `SpotifyClient` llama a `GET /recommendations/available-genre-seeds`.
8. La respuesta se mapea a `List<String>` y se devuelve al controlador.
9. `SpotifyController` responde con HTTP 200 y la lista de géneros.

> Si la llamada a Spotify falla por cualquier motivo, la excepción se propaga como HTTP 503.

---

## 9. Flujo del módulo de IA

### 9.1 Configuración requerida

| Variable de entorno | Descripción |
|---------------------|-------------|
| `GEMINI_API_KEY` | Clave de la API de Gemini (Google). Nunca en el repositorio. |

Se inyecta vía `@Value("${gemini.api-key}")` en `GeminiAiRecommendationService`.

### 9.2 Clases involucradas

```
RecommendationController
    → RecommendationService
        → AiRecommendationService (interfaz)
            → GeminiAiRecommendationService (implementación)
```

### 9.3 Flujo paso a paso

1. **Verificación de existencia:** `RecommendationService` busca la lista por `listName`. Si no existe → `ResourceNotFoundException` → 404.
2. **Consulta de caché:** el método `getRecommendations()` de `RecommendationService` está anotado con `@Cacheable(value="recommendations", key="#listName")`. Si hay resultado cacheado (TTL: 30 min), se devuelve directamente sin llamar a la IA.
   > **Nota de implementación:** `@Cacheable` solo intercepta llamadas que pasan por el proxy de Spring AOP. Invocar `getRecommendations()` desde otro método de la misma clase saltará la caché silenciosamente. Todos los usos deben hacerse desde clases externas (controladores u otros servicios).
3. **Carga del prompt:** `GeminiAiRecommendationService` carga el template desde `src/main/resources/prompts/recommendations.txt` (classpath, versionado en el repo). El template define la estrategia de prompting: incluye un marcador `{{SONGS_LIST}}` donde se interpolarán las canciones, y contiene una instrucción explícita de responder únicamente con un array JSON del tipo `[{"titulo":"...","artista":"...","genero":"..."}]`, sin texto adicional. El contenido concreto del prompt reside exclusivamente en el archivo, no en este documento.
4. **Interpolación:** las canciones de la lista (título, artista, género) se serializan a texto y se sustituye `{{SONGS_LIST}}`.
5. **Llamada a Gemini:** `POST` a `generativelanguage.googleapis.com` con el prompt construido y la API key. Timeout configurado en **10 segundos**.
6. **Parsing defensivo:** se extrae el fragmento JSON de la respuesta de Gemini (primer `[` al último `]`) antes de pasar al `ObjectMapper`. Esto mitiga el riesgo de texto adicional fuera del array.
7. **Fallback:** cualquier excepción durante los pasos 5–6 (timeout, error HTTP, JSON malformado) es capturada y convertida en `AiServiceException`. Esta excepción se deja propagar hasta `GlobalExceptionHandler` → HTTP 503. La aplicación nunca se cae.
8. **Almacenamiento en caché:** si la respuesta es válida, `@Cacheable` la almacena con clave `listName` y TTL de 30 minutos. Las llamadas subsiguientes al mismo `listName` no incurren en costo de API.
9. **Respuesta:** `RecommendationController` devuelve HTTP 200 con `List<RecommendedSongResponse>`.

### 9.4 Diagrama de flujo

```
GET /lists/{listName}/recommendations
            │
            ▼
   ┌─────────────────┐
   │ ¿Lista existe?  │── No ──► 404 Not Found
   └────────┬────────┘
            │ Sí
            ▼
   ┌─────────────────────┐
   │ ¿En caché (30 min)? │── Sí ──► 200 List<RecommendedSongResponse>
   └──────────┬──────────┘
              │ No
              ▼
   ┌──────────────────────┐
   │ Cargar prompt desde  │
   │ resources/prompts/   │
   │ recommendations.txt  │
   └──────────┬───────────┘
              ▼
   ┌──────────────────────┐
   │ Interpolar canciones │
   │ de la lista          │
   └──────────┬───────────┘
              ▼
   ┌──────────────────────┐
   │ POST a Gemini API    │
   │ timeout: 10 s        │
   └──────────┬───────────┘
              │
    ┌─────────▼──────────┐
    │ ¿Error / timeout / │── Sí ──► AiServiceException ──► 503 AI_UNAVAILABLE
    │ JSON inválido?     │
    └─────────┬──────────┘
              │ No
              ▼
   ┌──────────────────────┐
   │ Parsear JSON →       │
   │ List<Recommended     │
   │ SongResponse>        │
   └──────────┬───────────┘
              ▼
   ┌──────────────────────┐
   │ Guardar en caché     │
   │ key=listName 30 min  │
   └──────────┬───────────┘
              ▼
   200 List<RecommendedSongResponse>
```

---

## 10. Estrategia de manejo de errores

Todos los errores son interceptados por `GlobalExceptionHandler` (`@RestControllerAdvice`). Todas las respuestas de error devuelven una instancia de `ErrorResponse` (definida en `dto/response/`) con la siguiente estructura:

```json
{
  "error": "CODIGO_ERROR",
  "message": "Descripción legible del problema"
}
```

> **Principio de seguridad:** nunca se expone el stacktrace en la respuesta HTTP. Los detalles técnicos se registran únicamente en los logs del servidor.

| Excepción | Código HTTP | Campo `error` | Causa típica |
|-----------|-------------|---------------|--------------|
| `ResourceNotFoundException` | **404** | `NOT_FOUND` | Lista o canción buscada no existe en BD |
| `AiServiceException` | **503** | `AI_UNAVAILABLE` | Timeout, error HTTP o JSON malformado al llamar a Gemini |
| `BadCredentialsException` | **401** | `INVALID_CREDENTIALS` | Login con usuario o contraseña incorrectos |
| `DuplicatePlaylistException` | **409** | `DUPLICATE_RESOURCE` | `listName` ya existe; lanzada explícitamente por `PlayListService` |
| `SpotifyServiceException` | **503** | `SPOTIFY_UNAVAILABLE` | Timeout o error HTTP al llamar a la API de Spotify |
| `NoResourceFoundException` | **404** | `NOT_FOUND` | URL no coincide con ningún handler de Spring (rutas inexistentes) |
| `MethodArgumentNotValidException` | **400** | `VALIDATION_ERROR` | Bean Validation falló; `message` incluye el detalle por campo |
| `Exception` (genérica) | **500** | `INTERNAL_ERROR` | Cualquier excepción no anticipada; mensaje genérico al cliente |

**Ejemplo 400:**
```json
{ "error": "VALIDATION_ERROR", "message": "listName: no debe estar vacío; description: no debe estar vacío" }
```

**Ejemplo 409:**
```json
{ "error": "DUPLICATE_RESOURCE", "message": "Ya existe una lista con el nombre 'mis favoritas'" }
```

---

## 11. Estrategia de pruebas

El sistema utiliza **JUnit 5** con **Mockito**. Los tests de controladores usan `@WebMvcTest` (contexto web sin BD). Los tests de servicio son unitarios puros con mocks.

| Clase de test | Tipo | ¿Qué prueba? | Escenarios clave |
|---------------|------|--------------|------------------|
| `PlayListControllerTest` | `@WebMvcTest` | CRUD `/lists` con MockMvc | GET lista vacía; POST 201; POST nombre duplicado → 409; GET existente → 200; GET inexistente → 404; DELETE → 204; sin JWT → 401 |
| `SongControllerTest` | `@WebMvcTest` | `/lists/{listName}/songs` | POST agrega → 201; POST lista inexistente → 404; DELETE → 204; DELETE inexistente → 404; body inválido → 400 |
| `RecommendationControllerTest` | `@WebMvcTest` | `GET /lists/{listName}/recommendations` | IA ok → 200; lista inexistente → 404; **mock `AiServiceException` → 503 AI_UNAVAILABLE**; **mock timeout → 503**; sin JWT → 401 |
| `PlayListServiceTest` | Unitario | Lógica `PlayListService` | `findByListName` inexistente → `ResourceNotFoundException`; `create` duplicado → excepción; `update` retorna actualizado; `delete` llama repositorio |
| `SongServiceTest` | Unitario | Lógica `SongService` | `addSong` en lista existente; `addSong` lista inexistente → 404; `deleteSong` inexistente → 404 |
| `RecommendationServiceTest` | Unitario | Orquestación `RecommendationService` | Lista existente → llama `AiRecommendationService`; lista inexistente → `ResourceNotFoundException`; mock IA lanza `AiServiceException` → propaga sin ser tragada |
| `GeminiAiRecommendationServiceTest` | Unitario + mock HTTP | `GeminiAiRecommendationService` | JSON válido → parseo correcto; JSON malformado → `AiServiceException`; timeout → `AiServiceException`; respuesta 5xx → `AiServiceException` |

**Convenciones:**
- Mock del proveedor IA: `when(aiService.getRecommendations(any())).thenThrow(new AiServiceException(...))`.
- Tests de controlador usan tokens JWT de prueba generados con la misma clave secreta que `application-test.yml`.
- Perfil `test` (`@ActiveProfiles("test")`) con configuración H2 para tests de integración.

---

## 12. Orden de implementación por fases

### Fase 1 — Fundaciones del proyecto
**Estimación:** ~30 min | **Dependencias:** ninguna

- [ ] Spring Initializr: Web, Security, Data JPA, H2, Validation, Cache, Lombok
- [ ] `application.yml` con propiedades de H2, JWT, caché y puerto
- [ ] Entidades JPA: `User`, `PlayList`, `Song` con anotaciones y relaciones
- [ ] `schema.sql` (DDL) y `data.sql` (usuario precargado con hash BCrypt)
- [ ] Repositorios Spring Data JPA
- [ ] Verificar arranque y creación correcta del esquema H2

---

### Fase 2 — Autenticación JWT
**Estimación:** ~30 min | **Dependencias:** Fase 1

- [ ] `JwtService`: generación, firma y validación con HS256
- [ ] `JwtAuthFilter`: `OncePerRequestFilter` que extrae y valida el token
- [ ] `UserDetailsServiceImpl`: carga usuario desde BD
- [ ] `SecurityConfig`: cadena de filtros, rutas públicas (`POST /auth/login`), CORS
- [ ] `AuthController`: `POST /auth/login`
- [ ] `GlobalExceptionHandler`: manejadores de `BadCredentialsException`, `MethodArgumentNotValidException`, `Exception` genérica
- [ ] Verificar con Postman: login → token; endpoint protegido sin token → 401

---

### Fase 3 — CRUD de listas y canciones + Spotify
**Estimación:** ~45 min | **Dependencias:** Fase 2

- [ ] `PlayListService`: CRUD completo con `ResourceNotFoundException`; lanzar `DuplicatePlaylistException` cuando `listName` ya existe
- [ ] `PlayListController`: 5 endpoints CRUD de listas
- [ ] `SongService`: agregar y eliminar canciones
- [ ] `SongController`: `POST` y `DELETE` de canciones
- [ ] Agregar `ResourceNotFoundException` y `DuplicatePlaylistException` a `GlobalExceptionHandler`
- [ ] `SpotifyClient`: token Client Credentials + `GET /recommendations/available-genre-seeds` con caché Caffeine (55 min)
- [ ] `SpotifyService` y `SpotifyController`
- [ ] `CacheConfig`: `CacheManager` Caffeine con cachés `spotifyToken` y `recommendations`
- [ ] Exportar colección Postman

---

### Fase 4 — Módulo de IA
**Estimación:** ~40 min | **Dependencias:** Fase 3

- [ ] Interfaz `AiRecommendationService`
- [ ] `src/main/resources/prompts/recommendations.txt` con el prompt versionado
- [ ] `GeminiAiRecommendationService`: carga prompt, interpola, llama Gemini (timeout 10 s), parsea con extracción defensiva de JSON, lanza `AiServiceException` ante cualquier fallo
- [ ] `RecommendationService`: verifica lista + `@Cacheable("recommendations")` TTL 30 min
- [ ] `RecommendationController`: `GET /lists/{listName}/recommendations`
- [ ] Agregar `AiServiceException` a `GlobalExceptionHandler` → 503
- [ ] Verificar con Postman: recomendaciones OK; segunda llamada desde caché; 404 en lista inexistente; 401 sin token

---

### Fase 5 — Pruebas unitarias
**Estimación:** ~35 min | **Dependencias:** Fases 1–4

- [ ] `PlayListServiceTest`, `SongServiceTest`, `RecommendationServiceTest`
- [ ] `PlayListControllerTest`, `SongControllerTest`
- [ ] `RecommendationControllerTest` (escenario de fallo IA → 503)
- [ ] `GeminiAiRecommendationServiceTest` (respuesta válida, JSON malformado, timeout)
- [ ] `mvn test` — todos los tests en verde

---

### Fase 6 — Frontend Vue 3 + documentación final
**Estimación:** ~30 min | **Dependencias:** Fases 1–3 (API con CORS)

- [ ] `npm create vue@latest` — Vue 3 + Vite
- [ ] Vista de login → almacena JWT
- [ ] Interceptor Axios: `Authorization: Bearer <token>` en cada request
- [ ] Vista de listas: GET /lists, crear lista, eliminar lista
- [ ] Vista de detalle: canciones, agregar canción (dropdown de géneros de Spotify), botón "Recomendar"
- [ ] `AI-LOG.md` completado (prompts, revisión crítica, decisiones)
- [ ] `README.md`: instrucciones de configuración, variables de entorno, sección "Autoría IA"

---

## 13. Riesgos y consideraciones

| ID | Riesgo | Probabilidad | Impacto | Mitigación |
|----|--------|-------------|---------|------------|
| R1 | **Expiración del token Spotify:** el token expira a los 3600 s. Si el TTL de caché coincide exactamente, existe una ventana en que el token cacheado ya caducó en Spotify pero aún no en caché local. | Media | Medio | TTL de caché fijado en **55 minutos** (margen de 5 min). Se recomienda además capturar respuestas 401 de Spotify e invalidar la caché manualmente (`cacheManager.getCache("spotifyToken").clear()`) antes de reintentar. |
| R2 | **Fragilidad del parsing de respuestas de Gemini:** los LLM pueden devolver texto adicional fuera del JSON esperado (explicaciones, bloques markdown ` ```json `), JSON con campos faltantes o respuestas vacías, causando fallos en `ObjectMapper`. | Alta | Alto | El prompt incluye instrucción explícita de devolver **únicamente** el array JSON. `GeminiAiRecommendationService` aplica extracción defensiva (primer `[` → último `]`) antes de parsear. Cualquier `JsonProcessingException` se convierte en `AiServiceException` → 503, nunca en crash. |
| R3 | **`listName` con espacios o caracteres especiales en path variable:** URLs como `/lists/mis favoritas` son inválidas; Spring Boot puede devolver 400 o 404 inesperado. | Media | Medio | El frontend aplica `encodeURIComponent(listName)` al construir cada URL. El README y la colección Postman documentan este comportamiento. Como mejora futura, usar ID numérico en las URLs de listas elimina completamente esta ambigüedad. |

---

*Este documento es la referencia técnica del proyecto Quipux Senior API. Cualquier decisión de implementación no contemplada aquí debe resolverse priorizando simplicidad, seguridad y coherencia con las decisiones de la sección 2.*
