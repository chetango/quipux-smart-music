# Plan de Implementación — Quipux Senior API

**Basado en:** DESIGN.md v1.1  
**Fecha:** 23 de julio de 2026

Este documento detalla el orden de implementación incremental, las clases involucradas en cada fase, sus dependencias, las validaciones de cierre y los errores comunes a evitar. Cada fase debe quedar en estado funcional y validado antes de iniciar la siguiente.

---

## Índice

- [Fase 1 — Fundaciones del proyecto](#fase-1--fundaciones-del-proyecto)
- [Fase 2 — Autenticación JWT](#fase-2--autenticación-jwt)
- [Fase 3 — CRUD de listas y canciones + Spotify](#fase-3--crud-de-listas-y-canciones--spotify)
- [Fase 4 — Módulo de IA](#fase-4--módulo-de-ia)
- [Fase 5 — Pruebas unitarias](#fase-5--pruebas-unitarias)
- [Fase 6 — Frontend Vue 3](#fase-6--frontend-vue-3)
- [Dependencias entre fases](#dependencias-entre-fases)

---

## Fase 1 — Fundaciones del proyecto

**Objetivo:** Tener un proyecto Spring Boot arrancando correctamente con el esquema de base de datos creado, las entidades mapeadas y el usuario precargado disponible. Es la base sobre la que todo lo demás se construye.

**Estimación:** ~30 min

---

### Clases e archivos a implementar (en este orden)

#### 1. Inicialización del proyecto

- Usar Spring Initializr con las siguientes dependencias:
  - `Spring Web`
  - `Spring Security`
  - `Spring Data JPA`
  - `H2 Database`
  - `Validation`
  - `Spring Cache`
  - `Lombok`
- Dependencias adicionales a agregar manualmente en `pom.xml`:
  - `jjwt-api`, `jjwt-impl`, `jjwt-jackson` (JJWT para JWT)
  - `caffeine` + `spring-boot-starter-cache`

#### 2. `application.yml`

Configurar:
- H2: `spring.datasource.url=jdbc:h2:mem:quipuxdb`, modo `embedded`, consola H2 habilitada en dev
- JPA: `spring.jpa.hibernate.ddl-auto=none` (el DDL lo maneja `schema.sql`)
- `spring.sql.init.mode=always` para que Spring ejecute `schema.sql` y `data.sql`
- JWT: `app.jwt.secret` y `app.jwt.expiration-ms` (valores de desarrollo; en producción por env var)
- Puerto: `server.port=8080`

#### 3. `schema.sql` (en `src/main/resources/`)

Crear las tablas en orden de dependencia:

```
users → playlists → songs
```

Restricciones críticas:
- `users.username`: `UNIQUE NOT NULL`
- `playlists.list_name`: `UNIQUE NOT NULL`
- `songs.playlist_id`: `FOREIGN KEY REFERENCES playlists(id)`
- Todas las columnas `NOT NULL` marcadas en el modelo deben tener esa restricción en DDL

#### 4. `data.sql` (en `src/main/resources/`)

Insertar el usuario precargado. La contraseña debe ser un hash BCrypt válido (generado previamente, no en texto plano). Usar `INSERT INTO users (username, password) VALUES ('admin', '$2a$10$...')`.

#### 5. Entidades JPA (paquete `entity`)

Implementar en este orden (de menos a más dependencias):

| Clase | Anotaciones clave |
|---|---|
| `User` | `@Entity`, `@Table(name="users")`, `@Id`, `@GeneratedValue`, `@Column(unique=true)` |
| `PlayList` | `@Entity`, `@Table`, `@Column(name="list_name", unique=true)`, `@OneToMany(cascade=ALL, orphanRemoval=true)` |
| `Song` | `@Entity`, `@ManyToOne(optional=false)`, `@JoinColumn(name="playlist_id")` |

#### 6. Repositorios (paquete `repository`)

| Clase | Métodos personalizados necesarios |
|---|---|
| `UserRepository` | `Optional<User> findByUsername(String username)` |
| `PlayListRepository` | `Optional<PlayList> findByListName(String listName)`, `boolean existsByListName(String listName)` |
| `SongRepository` | Sin métodos personalizados en esta fase |

---

### Dependencias entre clases de esta fase

```
schema.sql ──────────────────────────────────────────► H2 crea las tablas
data.sql ────────────────────────────────────────────► H2 inserta el usuario
User (entity) ──────────────────────────────────────► UserRepository
PlayList (entity) ──────────────────────────────────► PlayListRepository
Song (entity) ──► depende de PlayList ───────────────► SongRepository
```

---

### Validaciones de cierre antes de continuar

- [ ] `mvn spring-boot:run` arranca sin errores
- [ ] H2 console (`/h2-console`) muestra las tablas `users`, `playlists`, `songs` correctamente creadas
- [ ] La tabla `users` contiene la fila del usuario precargado
- [ ] No hay errores de `SchemaExportException` ni `HibernateException` en el log
- [ ] El campo `list_name` tiene `UNIQUE` constraint visible en H2 console

---

### Errores comunes a evitar

- **`ddl-auto=create` en lugar de `none`:** si se usa `create` o `create-drop`, Spring ignorará `schema.sql` en algunos perfiles y creará el esquema desde las entidades, perdiendo las constraints explícitas del DDL. Mantener `none`.
- **`spring.sql.init.mode` ausente:** sin esta propiedad, `data.sql` y `schema.sql` no se ejecutan en Spring Boot 3.x con JPA. Es un error silencioso: la app arranca pero la BD queda vacía.
- **Hash BCrypt inválido en `data.sql`:** un hash mal formado hará que el login falle con `IllegalArgumentException` en BCrypt, no con 401. Verificar el hash con un generador online antes de insertarlo.
- **`@OneToMany` sin `orphanRemoval=true`:** si se omite, las canciones no se eliminarán al borrar la lista, dejando registros huérfanos con FK rota.
- **`@ManyToOne` sin `optional=false`:** permite que canciones sin lista pasen la validación JPA. Agregar `optional=false` para que la constraint NOT NULL sea detectada en tiempo de validación.

---

## Fase 2 — Autenticación JWT

**Objetivo:** Tener un sistema de login funcional que emita tokens JWT válidos, y un filtro de seguridad que proteja todos los endpoints excepto `POST /auth/login`. Al terminar esta fase, cualquier endpoint protegido debe devolver 401 sin token.

**Estimación:** ~30 min  
**Dependencia:** Fase 1 completa (entidad `User`, `UserRepository`, `application.yml`)

---

### Clases e archivos a implementar (en este orden)

#### 1. `JwtService` (paquete `security`)

Responsabilidades:
- `String generateToken(UserDetails userDetails)` — crea JWT firmado con `HS256`, subject = username, expiración desde `app.jwt.expiration-ms`
- `String extractUsername(String token)` — extrae el subject del payload
- `boolean isTokenValid(String token, UserDetails userDetails)` — verifica firma, expiración y que el username coincida

Implementación:
- Usar la librería JJWT (`io.jsonwebtoken`)
- El secreto se inyecta con `@Value("${app.jwt.secret}")` y debe convertirse a `SecretKey` con `Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))`
- El secreto debe tener al menos 256 bits (32 caracteres ASCII) para HS256; documentarlo en el README

#### 2. `UserDetailsServiceImpl` (paquete `service`)

Implementa `UserDetailsService` de Spring Security.

- `loadUserByUsername(String username)` — busca en `UserRepository`, lanza `UsernameNotFoundException` si no existe
- Mapea `User` → `org.springframework.security.core.userdetails.User` con lista de authorities vacía (sin roles en esta prueba)

> **Dependencia:** `UserRepository`

#### 3. `JwtAuthFilter` (paquete `security`)

Extiende `OncePerRequestFilter`.

- Extrae el header `Authorization`, valida que empiece con `Bearer `
- Llama a `JwtService.extractUsername()` y luego a `JwtService.isTokenValid()`
- Si el token es válido, construye `UsernamePasswordAuthenticationToken` y lo setea en `SecurityContextHolder`
- Si el token está ausente o es inválido, simplemente no establece contexto (Spring Security rechazará con 401 automáticamente)

> **Dependencia:** `JwtService`, `UserDetailsServiceImpl`

#### 4. `SecurityConfig` (paquete `config`)

- Deshabilitar CSRF (API stateless)
- Configurar CORS: permitir `http://localhost:5173` (Vite dev server) con métodos `GET, POST, PUT, DELETE, OPTIONS` y header `Authorization`
- `sessionManagement`: `STATELESS`
- Ruta pública: solo `POST /auth/login` y `GET /h2-console/**`
- Añadir `JwtAuthFilter` antes de `UsernamePasswordAuthenticationFilter`
- Bean `AuthenticationManager` expuesto para usarlo en `AuthController`
- Bean `PasswordEncoder` (BCryptPasswordEncoder)

> **Dependencia:** `JwtAuthFilter`, `UserDetailsServiceImpl`

#### 5. DTOs de autenticación (paquete `dto`)

| Clase | Campos |
|---|---|
| `LoginRequest` | `@NotBlank String username`, `@NotBlank String password` |
| `AuthResponse` | `String token` |

#### 6. `ErrorResponse` (paquete `dto/response`)

Clase compartida para todas las respuestas de error de la API:

| Campo | Tipo |
|---|---|
| `error` | `String` (código de error en SCREAMING_SNAKE_CASE) |
| `message` | `String` (descripción legible) |

Esta clase debe crearse aquí porque `GlobalExceptionHandler` la necesita desde esta fase.

#### 7. `GlobalExceptionHandler` (paquete `exception`) — versión inicial

Manejar en esta fase:
- `BadCredentialsException` → 401 `INVALID_CREDENTIALS`
- `MethodArgumentNotValidException` → 400 `VALIDATION_ERROR` (concatenar mensajes de campos)
- `Exception` (catch-all) → 500 `INTERNAL_ERROR` (log del error, mensaje genérico al cliente)

Todos retornan `ResponseEntity<ErrorResponse>`.

#### 8. Excepciones de dominio — base (paquete `exception`)

| Clase | Extiende | Descripción |
|---|---|---|
| `ResourceNotFoundException` | `RuntimeException` | Lanzada cuando no se encuentra una entidad por su identificador |
| `DuplicatePlaylistException` | `RuntimeException` | Lanzada cuando se intenta crear una lista con un `listName` ya existente |
| `AiServiceException` | `RuntimeException` | Lanzada ante cualquier fallo del proveedor IA |

Crearlas todas ahora para que estén disponibles en las fases siguientes, aunque sus handlers en `GlobalExceptionHandler` se añadan más adelante.

#### 9. `AuthController` (paquete `controller`)

- `POST /auth/login`: recibe `@Valid LoginRequest`, autentica con `AuthenticationManager`, llama a `JwtService.generateToken()`, retorna `200 AuthResponse`

> **Dependencia:** `AuthenticationManager`, `JwtService`, `UserDetailsServiceImpl`

---

### Dependencias entre clases de esta fase

```
UserRepository
    └──► UserDetailsServiceImpl
              └──► JwtAuthFilter ──► JwtService
                        └──► SecurityConfig
                                   └──► AuthController ──► JwtService
```

---

### Validaciones de cierre antes de continuar

- [ ] `POST /auth/login` con credenciales correctas → 200 con token JWT no vacío
- [ ] `POST /auth/login` con contraseña incorrecta → 401 con `ErrorResponse { error: "INVALID_CREDENTIALS" }`
- [ ] `POST /auth/login` con body vacío → 400 con `ErrorResponse { error: "VALIDATION_ERROR" }`
- [ ] `GET /lists` sin token → 401 (Spring Security rechaza antes de llegar al controlador)
- [ ] `GET /lists` con token válido → 403 o 404 (no 401; prueba que el filtro funciona aunque el endpoint no exista aún)
- [ ] Verificar en jwt.io que el token decodificado contiene `sub: "admin"` y la expiración correcta

---

### Errores comunes a evitar

- **Secreto JWT muy corto:** JJWT lanza `WeakKeyException` si el secreto tiene menos de 256 bits para HS256. Usar mínimo 32 caracteres en `app.jwt.secret`.
- **No deshabilitar CSRF:** en una API stateless con JWT, CSRF debe deshabilitarse explícitamente. Si se deja activo, `POST /auth/login` devolverá 403.
- **Headers CORS no configurados:** si el frontend (Vite en `localhost:5173`) no está en la lista de origins permitidos, las peticiones del navegador fallarán con CORS error antes de llegar a la autenticación.
- **`SecurityConfig` sin exponer `AuthenticationManager` como Bean:** `AuthController` necesita inyectarlo; si no está como `@Bean`, Spring falla con `NoSuchBeanDefinitionException`.
- **`JwtAuthFilter` registrado dos veces:** Spring Boot registra automáticamente los `@Component` de tipo `Filter`. Si además se añade explícitamente en `SecurityConfig.addFilterBefore()`, el filtro se ejecutará dos veces por request. La solución es no anotar `JwtAuthFilter` con `@Component` y registrarlo solo desde `SecurityConfig`.
- **H2 console bloqueada por Security:** la consola H2 usa frames; sin `frameOptions().disable()` en `SecurityConfig`, el navegador bloqueará el iframe con un error de seguridad.

---

## Fase 3 — CRUD de listas y canciones + Spotify

**Objetivo:** Tener los endpoints de gestión de listas y canciones completamente funcionales y protegidos por JWT, y exponer los géneros de Spotify. Al terminar esta fase, la API core está completa y puede exportarse la colección Postman.

**Estimación:** ~45 min  
**Dependencia:** Fase 2 completa (JWT funcional, `SecurityConfig`, `GlobalExceptionHandler` base)

---

### Clases e archivos a implementar (en este orden)

#### 1. DTOs de listas y canciones (paquete `dto`)

Implementar antes que los servicios; los servicios los retornan y los controladores los reciben.

| Clase | Campos | Validaciones |
|---|---|---|
| `PlayListRequest` | `String listName`, `String description` | `@NotBlank` en ambos |
| `PlayListResponse` | `String listName`, `String description`, `List<SongResponse> songs` | — |
| `SongRequest` | `String titulo`, `String artista`, `String album`, `String anno`, `String genero` | `@NotBlank` en todos |
| `SongResponse` | `Long id`, `String titulo`, `String artista`, `String album`, `String anno`, `String genero` | — |

> **Nota:** `anno` es `String` en todas las capas. No usar `int` ni `Integer`.

#### 2. `PlayListService` (paquete `service`)

Métodos a implementar en orden de complejidad:

| Método | Comportamiento |
|---|---|
| `List<PlayListResponse> findAll()` | Retorna todas las listas con sus canciones |
| `PlayListResponse findByListName(String listName)` | Lanza `ResourceNotFoundException` si no existe |
| `PlayListResponse create(PlayListRequest request)` | Verifica unicidad con `existsByListName()`; si ya existe lanza `DuplicatePlaylistException`; persiste y retorna |
| `PlayListResponse update(String listName, PlayListRequest request)` | Verifica existencia (404); si el nuevo nombre es diferente y ya existe, lanza `DuplicatePlaylistException`; actualiza y retorna |
| `void delete(String listName)` | Verifica existencia (404); elimina (cascade borrará las canciones) |

> **Nota sobre `DuplicatePlaylistException`:** verificar la unicidad del `listName` **antes** de intentar la persistencia (con `existsByListName()`), en lugar de capturar `DataIntegrityViolationException` post-persistencia. Esto hace el manejo de errores explícito y predecible.

> **Dependencia:** `PlayListRepository`, `ResourceNotFoundException`, `DuplicatePlaylistException`

#### 3. `PlayListController` (paquete `controller`)

| Endpoint | Método | Retorno |
|---|---|---|
| `GET /lists` | `findAll()` | `200 List<PlayListResponse>` |
| `POST /lists` | `create(@Valid @RequestBody PlayListRequest)` | `201 PlayListResponse` |
| `GET /lists/{listName}` | `findByListName(@PathVariable)` | `200 PlayListResponse` |
| `PUT /lists/{listName}` | `update(@PathVariable, @Valid @RequestBody)` | `200 PlayListResponse` |
| `DELETE /lists/{listName}` | `delete(@PathVariable)` | `204 No Content` |

> `@PathVariable String listName` recibe el valor ya decodificado por Spring (URL decoding automático).

#### 4. Ampliar `GlobalExceptionHandler` con los nuevos manejadores

Añadir:
- `ResourceNotFoundException` → 404 `NOT_FOUND`
- `DuplicatePlaylistException` → 409 `DUPLICATE_RESOURCE`

#### 5. `SongService` (paquete `service`)

| Método | Comportamiento |
|---|---|
| `SongResponse addSong(String listName, SongRequest request)` | Busca lista por `listName` (404 si no existe); crea `Song`, asocia la lista, persiste, retorna `SongResponse` |
| `void deleteSong(String listName, Long songId)` | Busca lista (404 si no existe); busca canción en la lista (404 si no está en esa lista); elimina |

> **Nota en `deleteSong`:** verificar que la canción pertenece a esa lista específica, no solo que existe en la BD. Usar la colección `playlist.getSongs()` o una query con ambos IDs para evitar eliminar canciones de otras listas.

> **Dependencia:** `PlayListRepository`, `SongRepository`, `ResourceNotFoundException`

#### 6. `SongController` (paquete `controller`)

| Endpoint | Método | Retorno |
|---|---|---|
| `POST /lists/{listName}/songs` | `addSong(@PathVariable, @Valid @RequestBody)` | `201 SongResponse` |
| `DELETE /lists/{listName}/songs/{id}` | `deleteSong(@PathVariable listName, @PathVariable Long id)` | `204 No Content` |

#### 7. `CacheConfig` (paquete `config`)

Configurar el `CacheManager` de Caffeine con dos cachés:

| Nombre de caché | TTL | Propósito |
|---|---|---|
| `spotifyToken` | 55 minutos | Token OAuth de Spotify |
| `recommendations` | 30 minutos | Respuestas del módulo IA por `listName` |

Anotar la clase con `@EnableCaching`.

#### 8. `SpotifyClient` (paquete `integration`)

Responsabilidades:
- `String getAccessToken()` — anotado con `@Cacheable("spotifyToken")`; realiza `POST https://accounts.spotify.com/api/token` con `Authorization: Basic Base64(clientId:clientSecret)` y body `grant_type=client_credentials`; retorna el `access_token`
- `List<String> fetchGenres()` — llama a `GET https://api.spotify.com/v1/recommendations/available-genre-seeds` con el token; retorna el array `genres`

Variables inyectadas:
- `@Value("${spotify.client-id}")` ← de env var `SPOTIFY_CLIENT_ID`
- `@Value("${spotify.client-secret}")` ← de env var `SPOTIFY_CLIENT_SECRET`

Usar `WebClient` (bean de `WebClientConfig`).

> **Riesgo R1:** `@Cacheable` en `getAccessToken()` funciona solo si la llamada viene desde fuera del bean. Si `fetchGenres()` llama a `getAccessToken()` directamente en la misma instancia, el proxy AOP no intercepta y la caché se salta. **Solución aplicada:** extraer la obtención del token a un componente `SpotifyTokenClient` separado con su propio `@Cacheable`; `SpotifyClient` inyecta `SpotifyTokenClient` y llama a `spotifyTokenClient.getAccessToken()` desde fuera, garantizando que el proxy intercepte.

#### 9. `WebClientConfig` (paquete `config`)

Bean `WebClient.Builder` sin configuración base adicional. Los tres clientes externos (`SpotifyTokenClient`, `SpotifyClient`, `GeminiAiRecommendationService`) usan `WebClient` (Spring WebFlux) para las llamadas HTTP. El timeout de Gemini se aplica directamente en la cadena reactiva (`.timeout(Duration.ofSeconds(10))`), no en el bean global.

#### 10. `SpotifyService` (paquete `service`)

- `List<String> getAvailableGenres()` — delega en `SpotifyClient.fetchGenres()`; cualquier excepción se propaga como HTTP 503 (puede capturarse en `GlobalExceptionHandler` o dejarse como `Exception` genérica → 500; para mayor claridad, crear `SpotifyServiceException` y mapear a 503 es preferible, pero queda a criterio de implementación)

#### 11. `SpotifyController` (paquete `controller`)

- `GET /spotify/genres` → `200 List<String>`

---

### Dependencias entre clases de esta fase

```
PlayListRequest/PlayListResponse/SongRequest/SongResponse
    └──► PlayListService ──► PlayListRepository
    │         └──► SongService ──► SongRepository
    └──► PlayListController ──► SongController
              └──► GlobalExceptionHandler (ampliado)

WebClientConfig (WebClient.Builder)
    └──► SpotifyTokenClient ──► CacheConfig (spotifyToken)
    └──► SpotifyClient ──► SpotifyTokenClient
              └──► SpotifyService
                        └──► SpotifyController
```

---

### Validaciones de cierre antes de continuar

- [ ] `POST /lists` crea lista → 201 con body correcto
- [ ] `POST /lists` con nombre duplicado → 409 con `error: "DUPLICATE_RESOURCE"`
- [ ] `GET /lists` → 200 con lista vacía `[]` o con elementos
- [ ] `GET /lists/{listName}` existente → 200; inexistente → 404
- [ ] `PUT /lists/{listName}` → 200 con datos actualizados
- [ ] `DELETE /lists/{listName}` → 204; segunda vez → 404
- [ ] `POST /lists/{listName}/songs` → 201; en lista inexistente → 404
- [ ] `DELETE /lists/{listName}/songs/{id}` → 204; con id de otra lista → 404
- [ ] `GET /spotify/genres` → 200 con lista de strings (requiere credenciales Spotify en env vars)
- [ ] Al borrar una lista con canciones, las canciones se eliminan en cascada (verificar en H2 console)
- [ ] Todos los endpoints devuelven 401 sin token

---

### Errores comunes a evitar

- **`anno` como `int` o `Integer`:** el modelo define `anno` como `String`. Usar `Integer` causará errores de deserialización con valores como `"circa 1990"` y divergencia con el contrato JSON.
- **Verificar unicidad post-persistencia en lugar de pre-persistencia:** capturar `DataIntegrityViolationException` de JPA es frágil porque Spring lo envuelve en otras excepciones según el contexto. Verificar con `existsByListName()` antes del `save()` es explícito y controlable.
- **`@Cacheable` en método de la misma clase (self-invocation):** ver Riesgo R1 en `SpotifyClient`. Spring AOP no intercepta llamadas internas; la solución más simple es que `fetchGenres()` no llame a `getAccessToken()` directamente sino a través del proxy.
- **Credenciales Spotify en Base64 mal construidas:** el header debe ser `Basic Base64("clientId:clientSecret")` exactamente. Un error frecuente es codificar en Base64 solo el `clientId` sin el separador `:` y el `clientSecret`.
- **Colección `songs` no inicializada en `PlayList`:** si `PlayList.songs` no se inicializa como `new ArrayList<>()` en la entidad, `addSong` lanzará `NullPointerException` al intentar `playlist.getSongs().add(song)`.
- **`DELETE /songs/{id}` sin verificar pertenencia a la lista:** un endpoint incorrecto podría borrar canciones de cualquier lista si solo valida que el `id` existe en la BD, sin verificar que `song.getPlaylist().getListName()` coincide con el `listName` del path.

---

## Fase 4 — Módulo de IA

**Objetivo:** Tener el endpoint `GET /lists/{listName}/recommendations` funcionando con integración real a Gemini, con caché, timeout, manejo de errores y fallback a 503. La arquitectura debe ser tal que el proveedor pueda cambiarse o mockearse sin modificar el controlador ni el servicio orquestador.

**Estimación:** ~40 min  
**Dependencia:** Fase 3 completa (`PlayListService` funcional, `CacheConfig` con caché `recommendations` configurada)

---

### Clases e archivos a implementar (en este orden)

#### 1. `RecommendedSongResponse` (paquete `dto/response`)

Campos: `String titulo`, `String artista`, `String genero`.

DTO de solo salida; no tiene anotaciones de validación.

#### 2. `AiRecommendationService` (paquete `service`) — interfaz

```
interface AiRecommendationService {
    List<RecommendedSongResponse> recommend(PlayList playlist);
}
```

Definir la interfaz antes de la implementación permite compilar `RecommendationService` independientemente de Gemini.

#### 3. `src/main/resources/prompts/recommendations.txt`

Crear el archivo con el prompt. El prompt debe:
- Describir el rol del modelo (experto en música)
- Incluir el marcador `{{SONGS_LIST}}` donde se insertarán las canciones
- Instruir al modelo a responder **únicamente** con un array JSON sin texto adicional
- Especificar el formato exacto de cada elemento: `{"titulo":"...","artista":"...","genero":"..."}`
- Pedir un número concreto de recomendaciones (5 es razonable)

El contenido concreto es una decisión de implementación; este plan no lo prescribe (ver DESIGN.md §D10).

#### 4. `GeminiAiRecommendationService` (paquete `integration`)

Implementa `AiRecommendationService`.

Pasos internos del método `recommend(PlayList playlist)`:

1. Cargar el template del prompt desde classpath (`ClassPathResource`)
2. Serializar las canciones de la lista a texto e interpolar en `{{SONGS_LIST}}`
3. Construir el request body para la API de Gemini
4. Ejecutar `POST` a `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key={apiKey}`
5. Usar `WebClient` con timeout reactivo de 10 segundos en la cadena: `.timeout(Duration.ofSeconds(10)).block()`
6. Extraer el texto de la respuesta de Gemini (navegar por `candidates[0].content.parts[0].text`)
7. Aplicar extracción defensiva del JSON: buscar el primer `[` y el último `]` en el texto
8. Parsear con `ObjectMapper` a `List<RecommendedSongResponse>`
9. Envolver todo en `try-catch(Exception e)` → lanzar `AiServiceException`

Variable inyectada:
- `@Value("${gemini.api-key}")` ← de env var `GEMINI_API_KEY`

> **Nota:** el timeout de 10 s se aplica como operador reactivo en la cadena `.timeout()` de `WebClient`, no en el bean global. Un timeout demasiado corto para Gemini (que puede tardar 3–8 s) provocará fallos innecesarios.

#### 5. Ampliar `GlobalExceptionHandler`

Añadir:
- `AiServiceException` → 503 `AI_UNAVAILABLE` con mensaje "El servicio de recomendaciones no está disponible en este momento. Inténtelo más tarde."

#### 6. `RecommendationService` (paquete `service`)

```
@Cacheable(value="recommendations", key="#listName")
List<RecommendedSongResponse> getRecommendations(String listName)
```

Pasos:
1. Buscar la lista con `PlayListRepository.findByListName(listName)` → lanzar `ResourceNotFoundException` si no existe
2. Delegar en `AiRecommendationService.recommend(playlist)`
3. Retornar el resultado (si la IA falla, la excepción se propaga; no capturar aquí)

> **Advertencia `@Cacheable`:** esta anotación solo funciona cuando `getRecommendations()` es invocado desde otra clase a través del proxy de Spring. No invocar este método desde dentro de `RecommendationService`. Ver DESIGN.md §9.3 nota de implementación.

> **Dependencia:** `PlayListRepository`, `AiRecommendationService`, `ResourceNotFoundException`

#### 7. `RecommendationController` (paquete `controller`)

- `GET /lists/{listName}/recommendations` → delega en `RecommendationService.getRecommendations(listName)` → `200 List<RecommendedSongResponse>`

---

### Dependencias entre clases de esta fase

```
recommendations.txt
    └──► GeminiAiRecommendationService (implementa AiRecommendationService)
              └──► RecommendationService (@Cacheable) ──► PlayListRepository
                        └──► RecommendationController
                                   └──► GlobalExceptionHandler (AiServiceException → 503)
```

---

### Validaciones de cierre antes de continuar

- [ ] `GET /lists/{listName}/recommendations` con lista existente y canciones → 200 con array de recomendaciones
- [ ] Segunda llamada inmediata al mismo endpoint → misma respuesta, sin nueva llamada a Gemini (verificar en logs que `GeminiAiRecommendationService.recommend()` no se invoca la segunda vez)
- [ ] `GET /lists/{listName}/recommendations` con lista inexistente → 404
- [ ] Sin token → 401
- [ ] Simular fallo de Gemini (apagar la conexión o usar API key inválida) → 503 con `ErrorResponse { error: "AI_UNAVAILABLE" }`
- [ ] Verificar en logs que el stacktrace de la excepción de Gemini no aparece en la respuesta HTTP

---

### Errores comunes a evitar

- **Timeout no configurado en `WebClient` para Gemini:** sin timeout, un cuelgue de Gemini bloqueará el hilo de forma indefinida. El timeout de 10 s es obligatorio para el requisito de resiliencia.
- **No aplicar extracción defensiva del JSON:** Gemini frecuentemente incluye texto explicativo o bloques markdown alrededor del JSON. Parsear directamente con `ObjectMapper` sin preprocesamiento fallará en esos casos.
- **Capturar `AiServiceException` en `RecommendationService`:** si el servicio captura y silencia la excepción (por ejemplo, retornando lista vacía), el `GlobalExceptionHandler` nunca la verá y el fallback 503 no funcionará. La excepción debe propagarse sin capturar.
- **`@Cacheable` en método privado o final:** Spring AOP no puede interceptar métodos privados ni finales. `getRecommendations()` debe ser `public` y no `final`.
- **Exponer la API key de Gemini en logs:** si se loguea el request body completo o la URL con query params, la clave `?key=GEMINI_API_KEY` quedará en los logs. Usar un nivel de log apropiado y enmascarar las claves si se habilita debug de HTTP.
- **`ClassPathResource` lanzando `FileNotFoundException`:** si la ruta al prompt en `recommendations.txt` no coincide exactamente (mayúsculas, separadores), la carga del archivo fallará en tiempo de ejecución. Verificar la ruta al arrancar (test de integración simple).

---

## Fase 5 — Pruebas unitarias

**Objetivo:** Tener cobertura de los escenarios críticos de negocio y del módulo IA, incluyendo el caso de fallo del proveedor IA con fallback a 503. Los tests deben pasar con `mvn test` sin conexión a Gemini ni a Spotify.

**Estimación:** ~35 min  
**Dependencia:** Fases 1–4 completas

---

### Clases e archivos a implementar (en este orden)

Implementar los tests de servicio antes que los de controlador, porque los tests de controlador dependen de los servicios y su comportamiento ya debe estar claro.

#### Tests de servicio (paquete `service` en `test/`)

##### `PlayListServiceTest`

| Escenario | Mock necesario | Resultado esperado |
|---|---|---|
| `findByListName` con nombre existente | `PlayListRepository.findByListName()` → `Optional.of(playlist)` | Retorna `PlayListResponse` correcto |
| `findByListName` con nombre inexistente | `PlayListRepository.findByListName()` → `Optional.empty()` | Lanza `ResourceNotFoundException` |
| `create` con nombre nuevo | `existsByListName()` → `false`, `save()` → entidad | Retorna `PlayListResponse` con datos correctos |
| `create` con nombre duplicado | `existsByListName()` → `true` | Lanza `DuplicatePlaylistException` |
| `update` en lista inexistente | `findByListName()` → `Optional.empty()` | Lanza `ResourceNotFoundException` |
| `delete` en lista existente | `findByListName()` → entidad | Llama a `repository.delete()` |
| `delete` en lista inexistente | `findByListName()` → `Optional.empty()` | Lanza `ResourceNotFoundException` |

##### `SongServiceTest`

| Escenario | Mock necesario | Resultado esperado |
|---|---|---|
| `addSong` en lista existente | `PlayListRepository.findByListName()` → playlist | Retorna `SongResponse` con `id` asignado |
| `addSong` en lista inexistente | `findByListName()` → `Optional.empty()` | Lanza `ResourceNotFoundException` |
| `deleteSong` canción existente en lista correcta | Playlist con la canción | Llama a eliminación |
| `deleteSong` canción inexistente en la lista | Playlist sin esa canción | Lanza `ResourceNotFoundException` |

##### `RecommendationServiceTest`

| Escenario | Mock necesario | Resultado esperado |
|---|---|---|
| Lista existente, IA responde OK | `PlayListRepository` → playlist; `AiRecommendationService` → lista de recomendaciones | Retorna lista de `RecommendedSongResponse` |
| Lista inexistente | `PlayListRepository.findByListName()` → `Optional.empty()` | Lanza `ResourceNotFoundException` |
| Lista existente, IA falla | `AiRecommendationService.recommend()` lanza `AiServiceException` | La excepción se **propaga** (no se captura) |

#### Tests de integración de controladores (paquete `controller` en `test/`)

Usar `@WebMvcTest` + `MockMvc`. Requieren configurar un JWT de prueba válido para los endpoints protegidos.

> **Configuración JWT en tests:** crear un helper `JwtTestUtils` o definir un token hardcodeado generado con la misma clave secreta que `application-test.yml`. Alternativamente, mockear `JwtService` y `UserDetailsService` con `@MockBean`.

##### `PlayListControllerTest`

| Escenario | Código esperado |
|---|---|
| `GET /lists` con JWT válido | 200 |
| `GET /lists` sin JWT | 401 |
| `POST /lists` body válido | 201 |
| `POST /lists` body inválido (`listName` en blanco) | 400 con `VALIDATION_ERROR` |
| `POST /lists` nombre duplicado (mock `DuplicatePlaylistException`) | 409 con `DUPLICATE_RESOURCE` |
| `GET /lists/{listName}` inexistente (mock `ResourceNotFoundException`) | 404 con `NOT_FOUND` |
| `DELETE /lists/{listName}` existente | 204 |

##### `SongControllerTest`

| Escenario | Código esperado |
|---|---|
| `POST /lists/{listName}/songs` body válido | 201 |
| `POST /lists/{listName}/songs` lista inexistente | 404 |
| `POST /lists/{listName}/songs` body inválido | 400 |
| `DELETE /lists/{listName}/songs/{id}` | 204 |
| `DELETE /lists/{listName}/songs/{id}` canción inexistente | 404 |

##### `RecommendationControllerTest` ← **núcleo del módulo IA**

| Escenario | Mock necesario | Código esperado |
|---|---|---|
| Lista existente, IA responde OK | `RecommendationService` → lista de recomendaciones | 200 con array JSON |
| Lista inexistente | `RecommendationService` lanza `ResourceNotFoundException` | 404 |
| Sin JWT | — | 401 |
| **IA falla (mock lanza `AiServiceException`)** | `RecommendationService` lanza `AiServiceException` | **503 con `{ error: "AI_UNAVAILABLE" }`** |

#### Test de integración de la capa de integración (paquete `integration` en `test/`)

##### `GeminiAiRecommendationServiceTest`

Usar `MockRestServiceServer` (de Spring Test) o `WireMock` para mockear las respuestas HTTP de Gemini.

| Escenario | Mock HTTP | Resultado esperado |
|---|---|---|
| Gemini responde JSON válido | 200 con array JSON correcto | Lista de `RecommendedSongResponse` correcta |
| Gemini responde con texto + JSON (markdown) | 200 con texto rodeando el JSON | Extracción defensiva funciona; retorna lista correcta |
| Gemini responde JSON malformado | 200 con texto no parseable | Lanza `AiServiceException` |
| Gemini responde timeout | Simular timeout de lectura | Lanza `AiServiceException` |
| Gemini responde 500 | HTTP 500 del mock | Lanza `AiServiceException` |

---

### Dependencias entre clases de esta fase

```
PlayListServiceTest ──► PlayListService (bajo test) + mocks de PlayListRepository
SongServiceTest ──► SongService + mocks
RecommendationServiceTest ──► RecommendationService + mock AiRecommendationService
PlayListControllerTest ──► @WebMvcTest(PlayListController) + @MockBean PlayListService
SongControllerTest ──► @WebMvcTest(SongController) + @MockBean SongService
RecommendationControllerTest ──► @WebMvcTest(RecommendationController) + @MockBean RecommendationService
GeminiAiRecommendationServiceTest ──► GeminiAiRecommendationService + MockRestServiceServer
```

---

### Validaciones de cierre antes de continuar

- [ ] `mvn test` pasa con todos los tests en verde sin conexión a internet
- [ ] El escenario de fallo de IA en `RecommendationControllerTest` produce exactamente 503 con `error: "AI_UNAVAILABLE"`
- [ ] Ningún test tiene `Thread.sleep()` ni depende de servicios externos reales
- [ ] La cobertura cubre al menos: todos los servicios de negocio, todos los controladores, y `GeminiAiRecommendationService`

---

### Errores comunes a evitar

- **`@SpringBootTest` en lugar de `@WebMvcTest` para tests de controlador:** `@SpringBootTest` levanta el contexto completo incluyendo H2, Security real y todas las dependencias; es mucho más lento y frágil para tests de controlador. Usar `@WebMvcTest` con `@MockBean` de los servicios.
- **JWT real en tests de controlador:** generar tokens reales en tests requiere la clave secreta en el perfil de test. La alternativa más simple es mockear `JwtService` con `@MockBean` y configurar `SecurityConfig` de test para deshabilitar el filtro JWT, o usar `@WithMockUser` de Spring Security Test.
- **No verificar que `AiServiceException` se propaga en `RecommendationServiceTest`:** el test debe afirmar con `assertThrows(AiServiceException.class, ...)` que la excepción no es capturada por el servicio.
- **Orden de mocks incorrecto en `RecommendationControllerTest`:** si se mockea `RecommendationService` pero no se configura `SecurityConfig` de test correctamente, el filtro JWT rechazará la petición con 401 antes de llegar al controlador, y el test fallará por el motivo equivocado.

---

## Fase 6 — Frontend Vue 3

**Objetivo:** Tener una interfaz web funcional que permita autenticarse, gestionar listas y canciones, y consultar recomendaciones de IA. Es la última fase de entrega visible.

**Estimación:** ~30 min  
**Dependencia:** Fase 3 completa (API con CORS configurado para `localhost:5173`)

---

### Clases e archivos a implementar (en este orden)

#### 1. Inicialización del proyecto

```bash
npm create vue@latest frontend
```

Seleccionar: Vue 3, sin TypeScript (o con TypeScript si es preferencia), Vue Router, Pinia (state management), sin testing en esta fase.

Instalar dependencias adicionales:
```bash
npm install axios
```

#### 2. Configuración de Axios (`src/api/axios.js`)

- Crear instancia de Axios con `baseURL: 'http://localhost:8080'`
- Añadir interceptor de request: leer el token de `localStorage` e inyectar `Authorization: Bearer <token>` en cada petición
- Añadir interceptor de response: si llega 401, redirigir a login y limpiar `localStorage`

#### 3. Store de autenticación (`src/stores/auth.js` con Pinia)

Estado:
- `token: null | string`
- `isAuthenticated: computed(() => !!token)`

Acciones:
- `login(username, password)` → llama a `POST /auth/login`, guarda token en estado y `localStorage`
- `logout()` → limpia estado y `localStorage`, redirige a login

#### 4. Router (`src/router/index.js`)

Rutas:
- `/login` → `LoginView` (pública)
- `/lists` → `ListsView` (requiere auth)
- `/lists/:listName` → `ListDetailView` (requiere auth)

Navigation guard: redirigir a `/login` si la ruta requiere auth y no hay token.

#### 5. Vistas

Implementar en orden de dependencia:

| Vista | Funcionalidades |
|---|---|
| `LoginView.vue` | Formulario usuario/contraseña; llama a `auth.login()`; redirige a `/lists` en éxito |
| `ListsView.vue` | Lista de todas las playlists (`GET /lists`); botón crear lista (modal o formulario inline); botón eliminar lista |
| `ListDetailView.vue` | Detalle de una lista con sus canciones; formulario para agregar canción (dropdown de géneros de `GET /spotify/genres`); botón eliminar canción; botón "Obtener recomendaciones" |

#### 6. Componentes reutilizables (opcionales pero recomendados)

- `SongCard.vue` o `SongRow.vue` — muestra los datos de una canción con botón de eliminar
- `RecommendationList.vue` — muestra el resultado de `/recommendations` o el mensaje de error 503

---

### Dependencias entre archivos de esta fase

```
axios.js (instancia + interceptores)
    └──► stores/auth.js ──► LoginView.vue
    └──► router/index.js ──► navigation guards
              └──► ListsView.vue ──► ListDetailView.vue
```

---

### Validaciones de cierre antes de continuar

- [ ] Login con credenciales correctas → redirige a `/lists` y muestra las listas
- [ ] Login con credenciales incorrectas → muestra mensaje de error, no redirige
- [ ] Refrescar la página en `/lists` → el token persiste desde `localStorage` y no redirige a login
- [ ] Crear lista → aparece en la lista sin recargar la página
- [ ] Eliminar lista → desaparece de la lista
- [ ] Agregar canción con género seleccionado del dropdown de Spotify → aparece en la lista de canciones
- [ ] Botón "Obtener recomendaciones" → muestra las canciones recomendadas
- [ ] Si Gemini falla → se muestra un mensaje de error amigable (no el JSON crudo del 503)
- [ ] Logout → limpia token, redirige a login, no permite volver con el botón "atrás" del navegador

---

### Errores comunes a evitar

- **Token en `sessionStorage` en lugar de `localStorage`:** `sessionStorage` se pierde al cerrar la pestaña. Para una mejor UX, usar `localStorage`. (En producción se evaluaría el riesgo de XSS, pero para esta prueba es aceptable.)
- **No manejar el 401 global en el interceptor de Axios:** si una petición recibe 401 (token expirado) y no hay interceptor que redirija al login, el usuario verá errores genéricos en lugar de ser redirigido automáticamente.
- **No usar `encodeURIComponent` en `listName` al construir URLs:** si el nombre de la lista tiene espacios (e.g., `"mis favoritas"`), Axios construirá la URL como `/lists/mis favoritas` que el navegador enviará sin codificar, causando errores 400 o 404. Usar siempre `encodeURIComponent(listName)` al construir el path.
- **CORS pre-flight bloqueado:** si `SecurityConfig` no permite el método `OPTIONS` o no incluye el header `Authorization` en los allowed headers, el navegador bloqueará todas las peticiones con preflight. Verificar con DevTools > Network > filter "OPTIONS".
- **Dropdown de géneros cargando en cada apertura del formulario:** llamar a `GET /spotify/genres` cada vez que se abre el formulario de agregar canción desperdicia el beneficio de la caché del backend. Cargar los géneros una sola vez al montar `ListDetailView` y guardar en estado local.

---

## Dependencias entre fases

```
Fase 1 (Fundaciones)
    │
    ▼
Fase 2 (JWT + Seguridad)
    │
    ▼
Fase 3 (CRUD + Spotify) ──────────────────────────────────────────────► Fase 6 (Frontend)
    │                                                                         ▲
    ▼                                                                         │
Fase 4 (Módulo IA) ──────────────────────────────────────────────────────────┘
    │
    ▼
Fase 5 (Tests) ← cubre Fases 1–4
```

- **Fase 6 puede iniciarse en paralelo con Fase 4** si ya existe la API de autenticación y CRUD (Fase 3). La integración del botón de recomendaciones se añade cuando Fase 4 esté lista.
- **Fase 5 es independiente** una vez que las fases 1–4 están implementadas; puede intercalarse entre fases si se prefiere TDD.

---

*Este plan debe consultarse al inicio de cada fase. Las validaciones de cierre son el criterio de "done" para cada una. Cualquier desviación respecto a lo descrito en DESIGN.md debe documentarse en AI-LOG.md.*
