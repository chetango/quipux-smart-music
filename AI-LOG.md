# AI-LOG — Bitácora de uso de Inteligencia Artificial

Instrucciones: Completa esta bitácora mientras resuelves el Módulo de IA. Es un entregable obligatorio. Sé honesto y concreto. Copia los prompts tal cual los usaste. No hay respuestas "incorrectas" por usar IA — lo que evaluamos es tu criterio.

**Candidato:** Jorge Andrés Padilla &nbsp;&nbsp;&nbsp; **Nivel:** ☑ Senior &nbsp;&nbsp;&nbsp; **Fecha:** 23 de julio de 2026  
**Asistente(s) de IA usado(s):** GitHub Copilot (Claude Sonnet 4.5)

---

> **Contexto para el evaluador:** Esta bitácora documenta el Módulo de IA (Ejercicios 1–3), que se desarrolló sobre una base ya construida. Antes de llegar aquí se completaron seis fases de trabajo: Fase 1 (entidades JPA, H2, `schema.sql`/`data.sql`), Fase 2 (JWT + Spring Security), Fase 3 (CRUD de listas y canciones + integración Spotify), Fase 4 (módulo de IA — objeto de este log), Fase 5 (tests unitarios) y Fase 6 (frontend Vue 3). El diseño técnico y el plan de implementación que guiaron cada fase se encuentran en `docs/DESIGN.md` y `docs/IMPLEMENTATION_PLAN.md`. Los prompts de las fases previas se recogen en la sección **Autoría** al final de este documento. Los ejercicios que siguen registran el trabajo específico del módulo de IA tal como ocurrió durante la sesión.

---

## Ejercicio 1

**Prompt(s) usado(s):**

> "Continuemos con la Fase 4 siguiendo el diseño técnico, el plan de implementación y las decisiones validadas en las fases anteriores. Implementa únicamente los componentes definidos para el módulo de IA: `RecommendedSongResponse`, la interfaz `AiRecommendationService`, el archivo de prompt `recommendations.txt`, `GeminiAiRecommendationService` en el paquete `integration`, `RecommendationService` con `@Cacheable` en el paquete `service`, y `RecommendationController`. La implementación debe usar `WebClient` con timeout de 10 segundos, cargar el prompt desde el classpath, limitar las canciones enviadas al modelo a las primeras 20, aplicar parseo defensivo del JSON (extraer entre primer `[` y último `]`), y envolver cualquier error en `AiServiceException` para garantizar que el endpoint devuelva 503 y nunca caiga la aplicación."

**Respuesta / código relevante de la IA:**

Fragmento de `GeminiAiRecommendationService` — carga del prompt y construcción de la petición:
```java
private String loadPromptTemplate() throws IOException {
    ClassPathResource resource = new ClassPathResource("prompts/recommendations.txt");
    return resource.getContentAsString(StandardCharsets.UTF_8);
}

private String buildPrompt(PlayList playList) {
    List<Song> songs = playList.getSongs();
    int limit = Math.min(songs.size(), MAX_SONGS_IN_CONTEXT);
    String songsList = songs.subList(0, limit).stream()
            .map(s -> String.format("- %s — %s (%s)", s.getTitulo(), s.getArtista(), s.getGenero()))
            .collect(Collectors.joining("\n"));
    if (songsList.isBlank()) {
        songsList = "(La lista está vacía, recomienda canciones populares de diferentes géneros)";
    }
    return promptTemplate.replace("{{SONGS_LIST}}", songsList);
}
```

Fragmento de `GeminiAiRecommendationService` — llamada con `WebClient` y parseo defensivo:
```java
private String callGemini(String prompt) {
    Map<String, Object> requestBody = Map.of(
            "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt))))
    );
    try {
        String responseBody = webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1beta/models/gemini-2.0-flash:generateContent")
                        .queryParam("key", apiKey)
                        .build())
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(TIMEOUT)
                .block();
        if (responseBody == null || responseBody.isBlank()) {
            throw new AiServiceException("El servicio de recomendaciones no está disponible...");
        }
        return responseBody;
    } catch (AiServiceException e) { throw e; }
    catch (Exception e) {
        throw new AiServiceException("El servicio de recomendaciones no está disponible...", e);
    }
}

private List<RecommendedSongResponse> parseResponse(String responseBody) {
    try {
        JsonNode root = objectMapper.readTree(responseBody);
        String text = root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start == -1 || end == -1 || end <= start) {
            throw new AiServiceException("El servicio de recomendaciones no está disponible...");
        }
        String jsonArray = text.substring(start, end + 1);
        return objectMapper.readValue(jsonArray, new TypeReference<List<RecommendedSongResponse>>() {});
    } catch (AiServiceException e) { throw e; }
    catch (Exception e) {
        throw new AiServiceException("El servicio de recomendaciones no está disponible...", e);
    }
}
```

`RecommendationService` — orquestación con `@Cacheable`:
```java
@Cacheable(value = "recommendations", key = "#listName")
@Transactional(readOnly = true)
public List<RecommendedSongResponse> getRecommendations(String listName) {
    PlayList playList = playListRepository.findByListName(listName)
            .orElseThrow(() -> new ResourceNotFoundException("Lista no encontrada: " + listName));
    return aiRecommendationService.recommend(playList);
}
```

**¿Qué acepté y por qué?**

La división en tres métodos privados (`buildPrompt`, `callGemini`, `parseResponse`) me gustó porque cada uno hace una sola cosa y se puede probar o reemplazar sin tocar los demás. Es la estructura que yo habría elegido.

`ClassPathResource` para cargar el prompt también es la opción correcta: funciona en desarrollo y dentro del JAR empaquetado, sin necesidad de rutas absolutas ni configuración extra.

La jerarquía de `catch` —re-lanzar `AiServiceException` primero y luego el catch genérico— es importante para no envolver el error dos veces. Lo vi de inmediato y estaba bien hecho.

El cambio de `gemini-1.5-flash` a `gemini-2.0-flash` lo acepté. El plan documentaba la versión anterior pero la nueva es más capaz y el endpoint es el mismo. Lo registro aquí porque el documento de diseño quedó desactualizado.

**¿Qué rechacé o corregí y por qué?**

Lo principal que corregí fue el manejo de mensajes de error en `callGemini`. La IA generó un mensaje diferente para cada rama del `catch`: uno para timeout, otro para errores HTTP, otro para fallos de parseo. Eso le da información al cliente sobre qué parte exactamente falló, lo cual no me parece buena idea. Unifiqué todo en un mensaje genérico; el detalle del error viaja en el `cause` de la excepción y queda en los logs del servidor, que es donde debe estar.

El otro punto fue `@Service` sobre `GeminiAiRecommendationService`. Semánticamente ese componente pertenece a la capa de integración, no a la de servicio. Lo correcto sería `@Component`. Sin embargo lo dejé con `@Service` porque Spring necesita un bean registrado para inyectar la implementación de `AiRecommendationService`, y en la práctica funciona igual. Lo anoto aquí porque es una inconsistencia conceptual que en un proyecto más grande valdría la pena resolver con un paquete `integration` bien separado y anotaciones propias.

**Explicación con mis palabras (¿entiendo lo que entrego?):**

El flujo es bastante directo si lo lees de afuera hacia adentro. El controlador no sabe nada de Gemini, solo le pasa el `listName` a `RecommendationService` y devuelve lo que recibe. Eso es intencional: si mañana cambio Gemini por otro proveedor, no toco ni el controlador ni el servicio, solo la implementación de `AiRecommendationService`.

Lo que hace `RecommendationService` internamente es primero verificar que la lista exista. Si no existe, lanza 404 antes de gastar un token de API. Luego delega en `GeminiAiRecommendationService`, que es quien realmente habla con Gemini.

El `@Cacheable` en `getRecommendations()` es lo que hace que esto sea viable en la práctica. La primera vez que se piden recomendaciones para una lista, Gemini las genera y Spring las guarda en Caffeine por 30 minutos. La segunda vez, ni se llama a Gemini. Lo verifiqué mirando los logs: en la segunda petición no aparece nada de Gemini.

La parte más delicada fue el parseo. Gemini incluye el JSON que pedí, pero a veces le agrega texto antes o después aunque el prompt diga explícitamente "solo JSON". Si intento parsear directo con `ObjectMapper`, falla. La solución fue buscar el primer `[` y el último `]` en el texto y extraer solo eso. Es lo más simple que funciona de forma consistente.

El timeout de 10 segundos en `WebClient` es obligatorio. Sin él, si Gemini se cuelga, el hilo queda bloqueado indefinidamente. Con el timeout, al pasar los 10 segundos se lanza excepción, que cae en el `catch` genérico y se convierte en `AiServiceException`. El `GlobalExceptionHandler` la recibe y devuelve 503. La aplicación sigue respondiendo con normalidad.

---

## Ejercicio 2

**Prompt(s) usado(s):**

> "Revisa de forma crítica el código de `GeminiAiRecommendationService` generado. Identifica: casos de error no cubiertos, riesgos de seguridad, comportamientos inesperados del cliente HTTP, problemas con la deserialización JSON, posibles NullPointerException en el parseo de la respuesta de Gemini, y cualquier aspecto que tú, como senior, habrías implementado diferente. Sé honesto sobre las limitaciones del código generado."

**Análisis / casos faltantes / hallazgos:**

La IA identificó correctamente varios riesgos; a continuación el análisis propio contrastado con lo que se implementó:

1. **`candidates[0]` puede ser null:** si Gemini devuelve `candidates: []` (respuesta vacía por filtro de contenido o límite de cuota), `root.path("candidates").get(0)` retorna `null` y el `.path(...)` subsiguiente lanza `NullPointerException`. El parseo defensivo dentro del `try/catch` genérico atrapa esto y lo convierte en `AiServiceException`, por lo que no se cae la aplicación. Sin embargo, el mensaje de error al usuario es genérico — no distingue "cuota agotada" de "respuesta vacía".

2. **`bodyToMono(String.class).timeout().block()` en Reactor:** usar `.block()` dentro de un hilo Tomcat tradicional es legítimo con WebFlux en modo reactivo-bloqueante. Sin embargo, si se usa `WebFlux` en modo completamente reactivo, `.block()` lanzaría `IllegalStateException`. En este proyecto, el servidor es Tomcat (`spring-boot-starter-web`), por lo que `.block()` es correcto y no genera problemas.

3. **La API key expuesta en la URL:** Gemini requiere la `api-key` como query param (`?key=...`). Esto es una limitación de la API de Google y no del código, pero es un riesgo de seguridad en logs del servidor (la URL con el key puede aparecer en trazas). Se documentó como limitación conocida; en producción habría que filtrar los logs.

4. **Tamaño ilimitado de la respuesta de Gemini:** `bodyToMono(String.class)` sin `limitBytes` podría desbordar memoria con respuestas muy grandes. Para el caso de uso (5 canciones), el riesgo es teórico pero real en entornos de producción.

5. **Re-entrada del caché con `AiServiceException`:** si Gemini falla, `@Cacheable` NO cachea el error (solo cachea retornos exitosos). Esto es correcto: si hay un fallo transitorio, la siguiente llamada intentará de nuevo. La IA confirmó que este comportamiento es el esperado de Spring Cache.

**Lo que implementé o corregí yo mismo:**

- **Mensaje de error unificado:** la IA generó mensajes distintos por tipo de excepción (`"Timeout"`, `"HTTP error"`, `"Parse error"`). Se unificó en un único mensaje genérico para no dar información diagnóstica al cliente. Los detalles del error se registran en logs del servidor vía el stack de la excepción wrapeada.
- **Documentación del riesgo de `api-key` en URL:** no hay alternativa técnica dado que es el contrato de la API de Gemini, pero se documenta para que quien opere el sistema en producción configure el filtrado de logs.
- **Verificación de `end > start`:** la IA propuso `end == -1` pero no verificaba que `end > start`. Se añadió `end <= start` para cubrir el caso de un `]` antes del `[` (JSON malformado con caracteres invertidos).

---

## Ejercicio 3

**Prompt(s) usado(s):**

> "Implementa los tests del endpoint `GET /lists/{listName}/recommendations` cubriendo como mínimo: respuesta exitosa del proveedor de IA, lista inexistente (404), proveedor que lanza excepción, timeout del proveedor, verificación del fallback, y verificación de que un fallo de Gemini no afecta el resto de la aplicación. Usa mocks para el proveedor de IA, sin realizar llamadas reales a Gemini. Usa `@WebMvcTest` para los tests de controlador y `@ExtendWith(MockitoExtension.class)` para los tests de servicio."

**Hallazgos / revisión crítica / riesgos detectados:**

1. **`@WebMvcTest` carga `SecurityConfig`:** la IA generó el test con solo `@MockBean RecommendationService`, sin mockear `JwtService` ni `UserDetailsServiceImpl`. `SecurityConfig` los necesita para construir `JwtAuthFilter`. Resultado: `UnsatisfiedDependencyException` al arrancar el contexto de test. Corrección aplicada: añadir `@MockBean JwtService` y `@MockBean UserDetailsServiceImpl`.

2. **La IA omitió el test de lista vacía:** el prompt pedía los casos mínimos, pero no cubrió el escenario de lista sin canciones (`songs = []`). El constructor de `GeminiAiRecommendationService.buildPrompt()` tiene un manejo explícito para ese caso (texto de fallback). Se añadió manualmente el escenario `getRecommendations_callsAi_evenWhenPlaylistIsEmpty` en `RecommendationServiceTest`.

3. **Distinción "timeout" vs "excepción genérica" en el controlador:** desde el punto de vista del controlador, ambos escenarios son idénticos (llegan como `AiServiceException`). La distinción solo tiene sentido en el test del servicio, donde se verifica que la excepción con `cause` de tipo `TimeoutException` se propaga intacta (`hasCause(timeoutCause)`). La IA inicialmente generó el test de timeout en el controlador duplicando lógica sin valor añadido; se simplificó verificando únicamente la estructura de la respuesta 503.

4. **`@Cacheable` no actúa en tests unitarios puros:** en `RecommendationServiceTest` con `@ExtendWith(MockitoExtension.class)`, no hay contexto Spring y por tanto el proxy AOP no intercepta `getRecommendations()`. La anotación es transparente. La IA no documentó esta limitación. Se añadió un comentario en la clase de test para que quien lo lea entienda que la lógica de caché requeriría un test de integración con `@SpringBootTest`.

5. **`verify(aiRecommendationService, never()).recommend(any())`:** en el test de "lista no encontrada", se verifica explícitamente que el proveedor de IA no fue invocado. La IA no incluyó esta verificación. Es importante porque si algún refactor cambia el orden de validación y llama a la IA antes de verificar la existencia de la lista, el test lo detecta.

**Decisiones tomadas:**

- Se usó `@WebMvcTest(RecommendationController.class)` (no `@SpringBootTest`) para mantener los tests rápidos y aislados de la base de datos.
- Para el test de "fallo de IA no afecta la aplicación" se verificó a nivel de controlador que el error devuelve 503 (no 500). La validación de que `GET /lists` sigue funcionando se consideró fuera del alcance de `@WebMvcTest(RecommendationController.class)` y queda garantizada por la arquitectura (RecommendationService y PlayListService son beans independientes sin dependencia entre sí).
- Se separaron los tests en dos clases (`RecommendationControllerTest` + `RecommendationServiceTest`) siguiendo la misma lógica que el diseño en capas: controlador y servicio se prueban por separado con sus propios mocks.
- Total: 7 tests en controlador + 5 tests en servicio = 12 tests nuevos. Junto con los 3 existentes en `QuipuxApiApplicationTests`: **15 tests, 0 fallos, BUILD SUCCESS**.

---

## Reflexión final (obligatoria)

**¿Dónde falló, alucinó o se equivocó la IA durante esta prueba?**

Errores detectados durante el análisis, diseño e implementación de Fases 1 y 2:

- En el levantamiento de requisitos inicial, clasificó como "requisitos explícitos" elementos que son inferencias (rutas de endpoints, CRUD completo). Requirió una segunda pasada con criterios más estrictos.
- Propuso usar un hash BCrypt conocido de vector de test (`$2a$10$hKDVYxLefVHV/...`) para la contraseña `admin123`, sin señalar que ese hash corresponde a `"password"` y no a `"admin123"`. El hash correcto fue generado externamente con `htpasswd`.
- Propuso colocar `@EnableCaching` en la clase de configuración de seguridad en lugar de en la clase principal `QuipuxApiApplication`.
- Propuso `spring.jpa.hibernate.ddl-auto: validate` en lugar de `none`, lo que habría generado fallos intermitentes al no coincidir exactamente el esquema SQL con las entidades durante el desarrollo.
- En el diseño técnico, incluyó el texto literal del prompt del LLM en el documento de arquitectura. Se corrigió: el documento describe la estrategia; el texto concreto vive solo en `resources/prompts/recommendations.txt`.
- Propuso capturar `DataIntegrityViolationException` como mecanismo de detección de duplicados, acoplando la capa web a excepciones de infraestructura JPA. Se corrigió lanzando `DuplicatePlaylistException` explícitamente en el servicio.
- En Fase 2: no configuró el `AuthenticationEntryPoint` en `SecurityConfig`, lo que producía HTTP 403 en lugar de 401 para peticiones no autenticadas. Se corrigió añadiendo `HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)`.
- En Fase 2: el `GlobalExceptionHandler` no incluía un handler para `NoResourceFoundException` (Spring 6.1.14). Al no encontrar controlador para una ruta, la excepción caía en el handler genérico y devolvía 500 en lugar de 404. La IA propuso manejar `ResponseStatusException` (clase padre incorrecta en Spring 6.1.x). Se corrigió añadiendo un handler específico para `NoResourceFoundException`.
- En revisión técnica de Fase 2: `JwtAuthFilter` dependía de `UserDetailsServiceImpl` (clase concreta) en lugar de la interfaz `UserDetailsService`. La IA no aplicó inversión de dependencias en el filtro. Corregido durante la revisión previa a Fase 3.
- En Fase 3: `SongService.deleteSong` usaba `songRepository.delete(song)` directamente. Con `orphanRemoval=true` en `PlayList.songs`, Hibernate reinserta la canción al hacer flush porque el parent aún la referencia en su colección. La eliminación devolvía 204 pero la canción seguía presente. La IA no conocía este comportamiento de JPA. Se corrigió usando `playList.getSongs().remove(song)` + `playListRepository.save(playList)`.
- En Fase 3: la IA propuso poner `@Cacheable("spotifyToken")` en un método interno de `SpotifyClient` (self-invocation). Spring AOP no intercepta llamadas internas, por lo que la caché no habría funcionado. Se extrajo la obtención del token a `SpotifyTokenClient` como componente separado, al que `SpotifyClient` llama desde fuera.
- En revisión técnica de Fase 3: `SpotifyTokenClient` y `SpotifyClient` lanzaban `RuntimeException` genérica ante fallos de la API de Spotify. Esto producía HTTP 500 en lugar de 503. Se creó `SpotifyServiceException` (extends RuntimeException) y se añadió su handler en `GlobalExceptionHandler` → 503 `SPOTIFY_UNAVAILABLE`. La IA no anticipó esta excepción de dominio para la integración con Spotify.

- En Fase 4: la IA propuso `gemini-1.5-flash` como modelo de Gemini (especificado en el plan de implementación), pero actualizó silenciosamente a `gemini-2.0-flash` durante la generación del código sin mencionarlo. No es un error funcional, pero genera inconsistencia entre la documentación de diseño y el código. Se aceptó la actualización, pero se documentó la discrepancia.
- En Fase 4: la IA generó mensajes de error distintos por tipo de excepción en `callGemini` (`"Timeout"`, `"HTTP error"`, `"Parse error"`). Exponer el tipo de fallo al cliente puede revelar información sobre la infraestructura. Se unificó el mensaje en una cadena genérica; los detalles se propagan en el `cause` de `AiServiceException` para los logs del servidor.
- En Fase 4: el parseo de `end != -1` en la extracción defensiva del array JSON no cubría el caso de `end <= start` (caracteres invertidos). Se añadió la condición `end <= start` para manejar JSON malformado con orden incorrecto de corchetes.
- En Fase 5: `GlobalExceptionHandler` usaba `"DUPLICATE_PLAYLIST"` como código de error para `DuplicatePlaylistException`, pero el contrato documentado en `DESIGN.md` especifica `"DUPLICATE_RESOURCE"`. La IA generó el handler sin cruzar la documentación. Corregido en la revisión final.
- En Fase 6: `axios.js` importaba el router directamente, creando una dependencia circular. Vite la resuelve pero con warning en hot-reload. Se corrigió con importación lazy dinámica solo en el interceptor de 401.
- En Fase 6: el formulario de canciones quedaba bloqueado cuando `GET /spotify/genres` devolvía 503. La IA no contempló ese estado. Se añadió un campo de texto libre como fallback cuando Spotify no está disponible.

**¿Qué parte del trabajo NO delegarías a la IA y por qué?**

El criterio sobre qué realmente pide el negocio. La IA es buena generando código para un problema bien definido, pero no tiene forma de saber qué es importante para este contexto específico. En esta prueba, cuando hice el levantamiento de requisitos, la IA listó como "requisito explícito" cosas que eran inferencias sobre el documento. Si avanzo con eso sin revisarlo, termino diseñando features que nadie pidió.

Las decisiones de arquitectura también las tomo yo. Puedo pedirle que me explique opciones —cómo resolver el problema de `@Cacheable` con self-invocation, por ejemplo— pero la decisión de qué usar en este contexto concreto me corresponde a mí. La IA propone lo más estándar, no necesariamente lo más adecuado.

Y todo lo que requiere ejecución real para verificarse. El hash BCrypt es el ejemplo típico de esta prueba: la IA propuso un hash que no correspondía a `admin123`. Eso se detecta rápido al probar el login, pero si no pruebo el flujo completo se va como bug silencioso. La IA no puede ejecutar el código para confirmarlo, esa responsabilidad es siempre del desarrollador.

En general: la IA me ahorró tiempo en el boilerplate y fue útil para revisar mi código desde otro ángulo. Pero el criterio, el contexto y la responsabilidad del resultado final son siempre del desarrollador.

**Autoría:** Usé la IA para generar el código inicial de cada componente a partir de prompts bien estructurados. Mi aporte fue definir la arquitectura antes de escribir una sola línea, revisar todo lo generado con criterio técnico, detectar lo que no funcionaba en la práctica, y tomar las decisiones de diseño que no están en el código pero sí en cómo está organizado el sistema. Los prompts que usé se incluyen a continuación.

**Prompts utilizados:**

*Levantamiento de requisitos:*
> "Antes de comenzar a desarrollar quiero entender completamente el problema. Revisa toda la documentación del proyecto y haz un levantamiento de requisitos. Identifica: objetivo del sistema, funcionalidades requeridas, entidades del dominio, endpoints esperados, integraciones externas, reglas de negocio, requisitos de seguridad, restricciones técnicas, ambigüedades o información faltante. Separa claramente lo que es un requisito explícito de lo que son inferencias o supuestos."

*Diseño técnico (luego de tomar las decisiones de arquitectura):*
> "Con base en los requisitos levantados y las decisiones de diseño tomadas, genera el documento de diseño técnico completo del sistema. Incluye: descripción del sistema, decisiones de diseño (D1–D13), arquitectura por capas, modelo de dominio, estructura de paquetes, contrato de endpoints, flujos de autenticación e integración con Spotify y Gemini, estrategia de caché, y consideraciones de seguridad."

*Plan de implementación:*
> "Recorre el diseño completo y genera un plan de trabajo detallado para el desarrollo. Para cada fase identifica: objetivo, clases que deben implementarse, orden recomendado de implementación, dependencias con otras clases, validaciones importantes antes de continuar, riesgos o errores comunes que conviene evitar."

*Scaffolding e implementación Fase 1:*
> "Implementa únicamente los componentes definidos para esta fase, respetando el diseño de arquitectura y el plan de implementación aprobados. Mantén una separación clara por capas y genera código limpio, consistente y fácil de mantener. A medida que avances, verifica que las dependencias y configuraciones sean correctas para evitar retrabajo en fases posteriores."

*Revisión y validación Fase 1 (antes de comenzar Fase 2):*
> "Antes de comenzar la Fase 2, haz una revisión de lo implementado en la Fase 1. Verifica que todo sea consistente con el diseño técnico y el plan de implementación. Revisa si existe alguna decisión que deba corregirse ahora para evitar retrabajo en fases posteriores."

*Revisión técnica Fase 2 (antes de comenzar Fase 3):*
> "Antes de comenzar la Fase 3, realiza una revisión técnica de todo lo implementado en la Fase 2. Verifica que la autenticación JWT, la configuración de Spring Security, el manejo de excepciones, los DTOs, la configuración de caché y la preparación para las integraciones externas sean consistentes con el diseño técnico y el plan de implementación."

*Implementación Fase 3:*
> "Continuemos con la Fase 3 siguiendo el diseño técnico, el plan de implementación y las decisiones validadas en las fases anteriores. Implementa únicamente los componentes definidos: PlayListService, PlayListController, SongService, SongController, DTOs de listas y canciones, SpotifyClient, SpotifyService, SpotifyController. Utiliza WebClient para toda la comunicación con Spotify. Implementa el manejo de errores con las excepciones de dominio. No adelantes el módulo de IA ni componentes de fases posteriores."

**Código relevante generado por la IA en Fase 3:**

`PlayListService` — lógica de unicidad y mapeo a DTO (fragmento clave):
```java
@Transactional
public PlayListResponse create(PlayListRequest request) {
    if (playListRepository.existsByListName(request.listName())) {
        throw new DuplicatePlaylistException(request.listName());
    }
    PlayList playList = new PlayList();
    playList.setListName(request.listName());
    playList.setDescription(request.description());
    return toResponse(playListRepository.save(playList));
}
```

`SpotifyTokenClient` — separación de token OAuth con `@Cacheable` (decisión aplicada por el desarrollador para evitar el problema de self-invocation; la IA propuso `@Cacheable` sobre un método interno de `SpotifyClient`):
```java
@Component
public class SpotifyTokenClient {
    @Cacheable("spotifyToken")
    public String getAccessToken() {
        // POST https://accounts.spotify.com/api/token
        // Authorization: Basic Base64(clientId:clientSecret)
        ...
    }
}
// SpotifyClient llama a spotifyTokenClient.getAccessToken() desde fuera,
// garantizando que el proxy AOP intercepte la llamada y aplique la caché.
```

`SongService.deleteSong` — corrección aplicada por el desarrollador tras verificar que `songRepository.delete(song)` no persiste la eliminación con `orphanRemoval=true`:
```java
// Propuesta inicial de la IA (bug: la eliminación se revierte al flush):
songRepository.delete(song); // ← Hibernate reinserta el song porque el parent
                              //   aún lo referencia en su colección

// Corrección aplicada (usa orphanRemoval correctamente):
playList.getSongs().remove(song);
playListRepository.save(playList); // ← orphanRemoval=true emite DELETE en flush
```

`JwtService` — construcción del token y validación (fragmento clave):
```java
public String generateToken(UserDetails userDetails) {
    return Jwts.builder()
            .subject(userDetails.getUsername())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expirationMs))
            .signWith(secretKey)
            .compact();
}

public boolean isTokenValid(String token, UserDetails userDetails) {
    final String username = extractUsername(token);
    return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
}
```

`JwtAuthFilter` — lógica de extracción y validación del token por petición:
```java
final String authHeader = request.getHeader("Authorization");
if (authHeader == null || !authHeader.startsWith("Bearer ")) {
    filterChain.doFilter(request, response);
    return;
}
try {
    username = jwtService.extractUsername(jwt);
} catch (Exception e) {
    filterChain.doFilter(request, response);
    return;
}
```

`SecurityConfig` — cadena de filtros (fragmento generado por la IA, con corrección de `AuthenticationEntryPoint` aplicada por el desarrollador):
```java
http
    .csrf(AbstractHttpConfigurer::disable)
    .cors(cors -> cors.configurationSource(corsConfigurationSource()))
    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    .authorizeHttpRequests(auth -> auth
            .requestMatchers("/auth/login").permitAll()
            .requestMatchers("/h2-console/**").permitAll()
            .anyRequest().authenticated()
    )
    // Línea añadida por el desarrollador tras detectar 403 en lugar de 401:
    .exceptionHandling(e -> e.authenticationEntryPoint(
            new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
    .authenticationProvider(authenticationProvider())
    .addFilterBefore(jwtAuthFilter(), UsernamePasswordAuthenticationFilter.class);
```

`GlobalExceptionHandler` — handler añadido por el desarrollador tras detectar 500 en rutas inexistentes (la IA propuso `ResponseStatusException` como clase padre, incorrecto en Spring 6.1.x):
```java
// Propuesta de la IA (incorrecta en Spring 6.1.x — NoResourceFoundException
// no extiende ResponseStatusException en esta versión):
@ExceptionHandler(ResponseStatusException.class)
public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) { ... }

// Corrección aplicada por el desarrollador:
@ExceptionHandler(NoResourceFoundException.class)
public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("NOT_FOUND", "Recurso no encontrado"));
}
```


