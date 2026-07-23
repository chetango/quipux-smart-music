# Quipux Smart Music

REST API para gestión de listas de reproducción con autenticación JWT, integración con Spotify y recomendaciones de IA mediante Gemini. Incluye frontend web en Vue 3.

## Tecnologías

**Backend**
- Java 21 — Eclipse Temurin
- Spring Boot 3.3.5 (Web, Security, Data JPA, Validation, Cache)
- H2 (base de datos en memoria)
- JWT (JJWT 0.12.6, firma HS512)
- Caffeine (caché en memoria)
- WebFlux / WebClient (integración HTTP con Spotify y Gemini)

**Frontend**
- Vue 3 + Vite
- Vue Router 4 (SPA con navigation guards)
- Pinia (state management)
- Axios (cliente HTTP con interceptores JWT)

---

## Requisitos previos

- JDK 21 (`JAVA_HOME` configurado a Java 21)
- Maven 3.9+
- Node.js 18+ y npm
- Variables de entorno para las integraciones externas (ver sección siguiente)

---

## Configuración de variables de entorno

El proyecto requiere las siguientes variables de entorno para las integraciones externas. **No incluir valores reales en el repositorio.**

| Variable de entorno | Descripción | Requerida para |
|---------------------|-------------|----------------|
| `SPOTIFY_CLIENT_ID` | ID de la aplicación en Spotify Developer Dashboard | `GET /spotify/genres` |
| `SPOTIFY_CLIENT_SECRET` | Secreto de la aplicación Spotify | `GET /spotify/genres` |
| `GEMINI_API_KEY` | Clave de la API de Gemini (Google AI Studio) | `GET /lists/{listName}/recommendations` |

### Cómo configurar las variables

**Linux / macOS — sesión de terminal:**
```bash
export SPOTIFY_CLIENT_ID=tu_client_id_aqui
export SPOTIFY_CLIENT_SECRET=tu_client_secret_aqui
export GEMINI_API_KEY=tu_gemini_api_key_aqui
```

**Variables permanentes (`~/.zshrc` o `~/.bashrc`):**
```bash
export SPOTIFY_CLIENT_ID=tu_client_id_aqui
export SPOTIFY_CLIENT_SECRET=tu_client_secret_aqui
export GEMINI_API_KEY=tu_gemini_api_key_aqui
```

**Windows — PowerShell:**
```powershell
$env:SPOTIFY_CLIENT_ID="tu_client_id_aqui"
$env:SPOTIFY_CLIENT_SECRET="tu_client_secret_aqui"
$env:GEMINI_API_KEY="tu_gemini_api_key_aqui"
```

> Si no se configuran las variables, la aplicación arranca con los valores `changeme` como fallback. Los endpoints que requieran esas APIs devolverán **503 Service Unavailable** al intentar usarlas.

### Cómo obtener las credenciales

- **Spotify:** crear una aplicación en [Spotify Developer Dashboard](https://developer.spotify.com/dashboard)
- **Gemini:** obtener una API key en [Google AI Studio](https://aistudio.google.com/app/apikey)

---

## Ejecutar la aplicación

### Backend

```bash
# Navegar al directorio del backend
cd backend

# Compilar y ejecutar
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # macOS solamente
mvn spring-boot:run
```

El servidor arranca en `http://localhost:8080`.

### Frontend

```bash
# Navegar al directorio del frontend
cd frontend

# Copiar el archivo de configuración de variables de entorno
cp .env.example .env
# Editar .env si el backend corre en un puerto diferente al 8080

# Instalar dependencias (solo la primera vez)
npm install

# Modo desarrollo
npm run dev
```

El frontend estará disponible en `http://localhost:5173`.

Para compilar para producción:
```bash
cd frontend && npm run build
# El resultado queda en frontend/dist/
```

---

## Ejecutar los tests

```bash
cd backend
mvn test
```

**Resultado esperado:** 15 tests, 0 fallos. Los tests NO realizan llamadas reales a Spotify ni a Gemini; el proveedor de IA está completamente mockeado.

---

## Endpoints disponibles

### Autenticación
| Método | Path | Auth | Descripción |
|--------|------|------|-------------|
| `POST` | `/auth/login` | No | Obtiene JWT. Body: `{ "username": "admin", "password": "admin123" }` |

### Listas de reproducción
| Método | Path | Auth | Descripción |
|--------|------|------|-------------|
| `GET` | `/lists` | JWT | Listar todas las listas |
| `POST` | `/lists` | JWT | Crear lista |
| `GET` | `/lists/{listName}` | JWT | Obtener lista por nombre |
| `PUT` | `/lists/{listName}` | JWT | Actualizar lista |
| `DELETE` | `/lists/{listName}` | JWT | Eliminar lista (y sus canciones) |

### Canciones
| Método | Path | Auth | Descripción |
|--------|------|------|-------------|
| `POST` | `/lists/{listName}/songs` | JWT | Agregar canción a una lista |
| `DELETE` | `/lists/{listName}/songs/{id}` | JWT | Eliminar canción de una lista |

### Integraciones externas
| Método | Path | Auth | Descripción |
|--------|------|------|-------------|
| `GET` | `/spotify/genres` | JWT | Géneros musicales disponibles en Spotify |
| `GET` | `/lists/{listName}/recommendations` | JWT | Recomendaciones de canciones mediante Gemini AI |

---

## Uso rápido

```bash
# 1. Login
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

# 2. Crear una lista
curl -X POST http://localhost:8080/lists \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"listName":"rock-clasico","description":"Lo mejor del rock clásico"}'

# 3. Agregar una canción
curl -X POST http://localhost:8080/lists/rock-clasico/songs \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"titulo":"Stairway to Heaven","artista":"Led Zeppelin","album":"Led Zeppelin IV","anno":"1971","genero":"rock"}'

# 4. Obtener recomendaciones de IA (requiere GEMINI_API_KEY configurada)
curl http://localhost:8080/lists/rock-clasico/recommendations \
  -H "Authorization: Bearer $TOKEN"
```

---

## Consola H2

Disponible en `http://localhost:8080/h2-console` durante el desarrollo.

- **JDBC URL:** `jdbc:h2:mem:quipuxdb`
- **Usuario:** `sa`
- **Contraseña:** *(vacía)*

---

## Autoría IA

Este proyecto fue desarrollado con asistencia de **GitHub Copilot (Claude Sonnet 4.5)** como parte de una prueba técnica senior. El uso de IA se documenta con detalle en `AI-LOG.md`.

La IA se usó como herramienta de generación de código y revisión. Las decisiones de arquitectura, el criterio sobre qué aceptar o rechazar, y la validación del comportamiento real del sistema fueron responsabilidad del desarrollador en todo momento.

**Lo que decidí yo:**
- Arquitectura por capas y separación de responsabilidades (D1–D13 en el documento de diseño)
- Qué requisitos eran explícitos vs. inferencias — la IA los mezclaba
- Usar `SpotifyTokenClient` separado para resolver el problema de self-invocation con `@Cacheable`
- Degradación controlada cuando Spotify no está disponible (campo de texto libre en el frontend)
- Timeout de 10s en Gemini, límite de 20 canciones en el prompt, caché de 30 min — decisiones de resiliencia y costo

**Errores que detecté y corregí** (detallados en `AI-LOG.md`):
- Hash BCrypt propuesto no correspondía a `admin123` — bug silencioso de autenticación
- `songRepository.delete(song)` con `orphanRemoval=true` no persistía la eliminación — Hibernate reinsertaba la canción al hacer flush
- `@Cacheable` en método interno de `SpotifyClient` (self-invocation) — la caché nunca habría funcionado
- `AuthenticationEntryPoint` faltante en `SecurityConfig` — devolvía 403 en lugar de 401
- `NoResourceFoundException` en Spring 6.1.x no extiende `ResponseStatusException` — rutas inexistentes devolvían 500
- `JwtAuthFilter` inyectaba clase concreta en lugar de interfaz `UserDetailsService`
- `SpotifyServiceException` no creada — errores de Spotify devolvían 500 en lugar de 503
- `@MockBean` faltante en `@WebMvcTest` — contexto de test no arrancaba por dependencias de `SecurityConfig`
