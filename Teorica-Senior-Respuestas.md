# Prueba Teórica — Desarrollador Java | SENIOR

**Nombre:** Jorge Andrés Padilla &nbsp;&nbsp;&nbsp; **Fecha:** 23 de julio de 2026

---

## Parte A — Java avanzado y JVM

### 1. ¿Cuál es el resultado y por qué?

**Respuesta: A. true 3**

En Java los arrays son objetos, incluso los de tipos primitivos. `int[]` extiende `Object`, así que asignarlo a una variable de tipo `Object` compila sin problema. `instanceof int[]` evalúa el tipo real del objeto en tiempo de ejecución, que sigue siendo `int[]`, por eso devuelve `true`. El cast explícito funciona sin excepción y `.length` retorna 3.

---

### 2. Sobre el modelo de memoria de la JVM, selecciona todas las verdaderas:

**Respuestas correctas: A, B, D**

- **A. Verdadera.** Los objetos viven en el heap; las referencias locales y los valores primitivos de métodos viven en el stack del hilo.
- **B. Verdadera.** El GC libera objetos que ya no son alcanzables desde ninguna raíz (GC roots).
- **C. Falsa.** `final` en una variable de referencia solo significa que no puedes reasignar la referencia. El objeto al que apunta puede ser perfectamente mutable. Para que un objeto sea inmutable tienes que diseñarlo así desde la clase: campos privados, sin setters, sin escape de referencias internas.
- **D. Verdadera.** Las variables `static` pertenecen a la clase y se almacenan en el metaspace (o PermGen en versiones anteriores a Java 8), compartidas por todas las instancias.

---

### 3. ¿Por qué String es inmutable en Java? Menciona al menos dos beneficios concretos de esa decisión de diseño.

String es inmutable por decisión de diseño desde la primera versión de Java. El compilador y la JVM confían en esa propiedad para varias optimizaciones.

**Dos beneficios concretos:**

**1. Thread safety sin costo.** Un `String` se puede pasar entre hilos sin sincronización. Como nadie puede cambiar su contenido, no hay condición de carrera. Si fuera mutable, cada acceso compartido requeriría locks.

**2. El String pool funciona gracias a esto.** La JVM mantiene un pool de instancias de String para reusar literales. Si dos variables apuntan al mismo `"hola"` del pool y `String` fuera mutable, cambiar una cambiaría la otra. La inmutabilidad hace que compartir instancias sea seguro.

Un tercero que vale mencionar: el `hashCode` puede calcularse una vez y cachearse dentro del objeto. Como los campos nunca cambian, el hash siempre será el mismo. Esto hace que `String` sea especialmente eficiente como clave de `HashMap`.

---

### 4. Explica la diferencia entre `==`, `equals()` y `hashCode()`. ¿Qué contrato debe cumplirse entre `equals()` y `hashCode()` y qué pasa si lo rompes en una clase que usas como clave de un HashMap?

**`==`** compara identidad: para objetos verifica si las dos referencias apuntan al mismo objeto en memoria. Para primitivos compara el valor directamente.

**`equals()`** compara igualdad lógica. Por defecto en `Object` es idéntico a `==`, pero se sobreescribe para comparar por contenido. Por ejemplo, dos instancias de `String` con el mismo texto son `equals()` aunque sean objetos distintos.

**`hashCode()`** devuelve un entero que representa al objeto. Es la base de las estructuras hash: `HashMap`, `HashSet`, etc. lo usan para decidir en qué bucket colocar o buscar el objeto.

**El contrato:** si `a.equals(b)` es `true`, entonces `a.hashCode()` debe ser igual a `b.hashCode()`. El inverso no es obligatorio: dos objetos pueden tener el mismo hash sin ser iguales (colisión).

**Si lo rompes en una clave de HashMap:** el mapa busca primero por bucket usando `hashCode()`. Si dos objetos son `equals()` pero tienen `hashCode()` diferentes, van a buckets distintos. Puedes meter un objeto como clave, luego buscar por un objeto "igual" y el `get()` te devuelve `null` porque ni siquiera mira el bucket correcto. El objeto queda atrapado en el mapa, inaccesible, y acumulas entradas duplicadas que no deberían existir. Es uno de los bugs más difíciles de detectar porque todo compila y arranca sin error.

---

## Parte B — Concurrencia

### 5. ¿Cuál de estas afirmaciones sobre concurrencia es correcta?

**Respuesta: B**

- **A. Falsa.** `synchronized` garantiza tanto visibilidad como exclusión mutua (solo un hilo a la vez ejecuta el bloque). `volatile` solo garantiza visibilidad: cuando un hilo escribe, el valor se propaga inmediatamente a la memoria principal y los demás hilos lo leen actualizado, pero no hay exclusión mutua.
- **B. Correcta.** `i++` no es atómica: son tres operaciones (leer, sumar, escribir). Con `volatile` dos hilos pueden leer el mismo valor, ambos sumar 1 y escribir el mismo resultado, perdiendo una actualización.
- **C. Falsa.** `HashMap` no es thread-safe. Con acceso concurrente puedes corrompir la estructura interna. En versiones antiguas de Java incluso podías entrar en un bucle infinito en `get()` por un cycle en la lista enlazada del bucket.
- **D. Falsa.** `ConcurrentHashMap` no bloquea todo el mapa. Usa bloqueo a nivel de bucket (o CAS en Java 8+), lo que permite alta concurrencia en escrituras en buckets distintos.

---

### 6. Tienes un contador compartido entre múltiples hilos que solo se incrementa. ¿Qué opción es la más adecuada y eficiente?

**Respuesta: B. AtomicInteger**

`AtomicInteger.incrementAndGet()` usa instrucciones CAS (compare-and-swap) del hardware. No adquiere ni libera locks, por lo que bajo contención alta es mucho más rápido que `synchronized`. `volatile int` no sirve porque `i++` sigue siendo no atómica aunque la variable sea volátil. `synchronized` funciona pero es más costoso. `Integer` normal directamente no sirve para compartir entre hilos.

---

### 7. Explica qué es un deadlock, da un ejemplo de cómo se produce y menciona una estrategia para prevenirlo.

Un deadlock ocurre cuando dos o más hilos se bloquean mutuamente, cada uno esperando un recurso que el otro tiene, y ninguno puede avanzar.

**Ejemplo típico — transferencia bancaria:**

```
Hilo 1: adquiere lock(CuentaA), espera lock(CuentaB)
Hilo 2: adquiere lock(CuentaB), espera lock(CuentaA)
```

Ambos hilos quedan suspendidos para siempre.

**Estrategia para prevenirlo:** establecer un orden global de adquisición de locks y respetarlo siempre. Si cada hilo siempre adquiere locks en orden ascendente por ID de cuenta, el escenario de arriba no puede ocurrir: el hilo que quiera transferir de B a A también intentará adquirir lock(A) primero. Otra opción es usar `tryLock(timeout)` y liberar los locks ya adquiridos si no se consiguen todos en el tiempo límite, para luego reintentar.

---

## Parte C — Diseño y arquitectura

### 8. ¿Qué patrón de diseño describe: "garantizar que una clase tenga una única instancia y proporcionar un punto de acceso global a ella"?

**Respuesta: B. Singleton**

---

### 9. Menciona los principios SOLID y explica con un ejemplo el principio de Inversión de Dependencias (D).

Los principios SOLID son:

- **S** — Single Responsibility: una clase, una razón para cambiar.
- **O** — Open/Closed: abierto para extensión, cerrado para modificación.
- **L** — Liskov Substitution: una subclase debe poder reemplazar a su clase base sin romper el comportamiento.
- **I** — Interface Segregation: mejor varias interfaces específicas que una grande y genérica.
- **D** — Dependency Inversion: los módulos de alto nivel no deben depender de los de bajo nivel; ambos deben depender de abstracciones.

**Ejemplo del principio D — tomado directamente de este proyecto:**

`RecommendationService` (módulo de alto nivel) no depende de `GeminiAiRecommendationService` (implementación concreta). Depende de la interfaz `AiRecommendationService`. Spring inyecta la implementación concreta en tiempo de ejecución.

El beneficio práctico: si mañana hay que cambiar Gemini por OpenAI o por un mock de tests, se crea una nueva clase que implemente `AiRecommendationService` y se cambia el bean inyectado. `RecommendationService` no se toca. Los tests unitarios mockeaban precisamente esta interfaz, sin saber nada de Gemini.

---

### 10. En una API REST que recibe alto tráfico, describe al menos tres estrategias para mejorar el rendimiento y la escalabilidad (a nivel de aplicación y/o datos).

**1. Caché en el resultado de operaciones costosas.** La estrategia más efectiva para reducir carga. Si una respuesta es calculable una vez y válida por un período, se cachea (Caffeine, Redis). En este proyecto se aplica exactamente así con las recomendaciones de IA: una llamada a Gemini cuesta tiempo y dinero; con caché de 30 minutos, el 99% de las peticiones se resuelven sin tocar la API externa.

**2. Índices de base de datos y evitar N+1.** El cuello de botella más común en APIs de alto tráfico no es el código Java sino la base de datos. Los índices en columnas de búsqueda frecuente (foreign keys, campos de filtro) cambian una consulta de full scan a index scan. El problema N+1 —una consulta por cada elemento de una lista— se elimina con `JOIN FETCH` o carga eager bien diseñada.

**3. Diseño stateless y escalado horizontal.** Una API que no guarda estado de sesión en el servidor puede replicarse en N instancias detrás de un load balancer sin coordinación entre instancias. JWT es exactamente para esto: el token lleva el estado del cliente y el servidor no necesita almacenar sesiones. Añadir instancias es trivial.

Como complemento: paginación en endpoints que devuelvan listas (no retornar 50.000 registros de golpe), timeouts en integraciones externas para no bloquear hilos, y connection pooling en la capa de base de datos para no abrir una conexión nueva en cada request.

---

## Parte D — Seguridad y transacciones

### 11. Sobre JWT (JSON Web Token), selecciona todas las verdaderas:

**Respuestas correctas: B, C, D**

- **A. Falsa.** El payload de un JWT estándar (JWS) está codificado en base64url, no cifrado. Cualquiera que tenga el token puede decodificarlo y leer el contenido. Para cifrado real existe JWE (JSON Web Encryption), que es distinto. En este proyecto se puede comprobarlo pegando cualquier token en jwt.io.
- **B. Verdadera.** La firma (usando HMAC o RSA) garantiza integridad: si alguien modifica el payload, la firma no coincide y el servidor rechaza el token.
- **C. Verdadera.** Como el payload es legible, poner contraseñas, números de tarjeta o información sensible ahí es un error de seguridad claro.
- **D. Verdadera.** JWT es stateless: el servidor no lleva registro de qué tokens están activos. Un token robado es válido hasta que expira. Para mitigarlo se usan tokens de corta duración, refresh tokens con revocación, o listas negras en Redis.

---

### 12. En el contexto de transacciones, ¿qué significan las propiedades ACID? Explica brevemente cada una.

- **Atomicidad:** la transacción es todo o nada. Si hay 5 pasos y el 3 falla, los 2 primeros se revierten. No queda la base de datos en un estado intermedio.
- **Consistencia:** la base de datos pasa de un estado válido a otro estado válido. Las constraints, reglas y relaciones de integridad se respetan antes y después de cada transacción committed.
- **Isolation (aislamiento):** las transacciones concurrentes no ven el trabajo en progreso de las demás. El grado exacto depende del nivel de aislamiento configurado, pero en ningún caso una transacción debe ver estados intermedios de otra.
- **Durabilidad:** una vez que la transacción hace commit, los datos persisten aunque el servidor se caiga inmediatamente después. El motor de base de datos garantiza que escribió en almacenamiento durable (write-ahead log, fsync, etc.).

---

### 13. ¿Qué problema previene un nivel de aislamiento más estricto y qué desventaja introduce? Menciona un fenómeno concreto.

Un nivel de aislamiento más estricto previene fenómenos de lectura anómalos: dirty reads, non-repeatable reads y phantom reads.

**Fenómeno concreto — phantom read:** en nivel READ COMMITTED, una transacción ejecuta `SELECT * FROM pedidos WHERE total > 1000` dos veces dentro de la misma unidad de trabajo. Entre las dos lecturas, otra transacción hace commit e inserta un pedido de $1500. La segunda lectura devuelve una fila que no existía en la primera. Mismo query, distinto resultado dentro de la misma transacción.

REPEATABLE READ evita que las filas ya leídas cambien entre lecturas, pero no impide que aparezcan filas nuevas (phantoms). SERIALIZABLE las previene todas porque ejecuta las transacciones como si fueran secuenciales.

**La desventaja** es rendimiento y concurrencia. A mayor aislamiento, más locking o más rollbacks por conflicto. SERIALIZABLE en un sistema de alto tráfico puede convertirse en un cuello de botella severo. La mayoría de sistemas usan READ COMMITTED o REPEATABLE READ y diseñan la lógica de negocio para tolerar los fenómenos residuales, o los manejan a nivel de aplicación cuando es necesario.

---

## Parte E — SQL avanzado

**Modelo de datos:**
```
PERSONA(id_persona PK, nombre, email, id_ciudad FK, id_pais FK)
CIUDAD(id_ciudad PK, nombre_ciudad)
PAIS(id_pais PK, nombre_pais)
```

---

### 14. Nombre, email, ciudad y país de personas fuera de Medellín y Bogotá con email que termine en `.com`

```sql
SELECT p.nombre,
       p.email,
       c.nombre_ciudad,
       pa.nombre_pais
FROM   PERSONA p
JOIN   CIUDAD c  ON p.id_ciudad = c.id_ciudad
JOIN   PAIS   pa ON p.id_pais   = pa.id_pais
WHERE  c.nombre_ciudad NOT IN ('Medellín', 'Bogotá')
  AND  p.email LIKE '%.com';
```

---

### 15. Por cada país, nombre del país y cantidad de ciudades registradas (incluyendo países con 0)

El modelo no tiene FK directa entre `CIUDAD` y `PAIS`, así que la relación ciudad-país existe solo a través de `PERSONA`. La consulta cuenta ciudades distintas en las que viven personas de cada país:

```sql
SELECT  pa.nombre_pais,
        COUNT(DISTINCT p.id_ciudad) AS cantidad_ciudades
FROM    PAIS pa
LEFT JOIN PERSONA p ON pa.id_pais = p.id_pais
GROUP BY pa.id_pais, pa.nombre_pais
ORDER BY pa.nombre_pais;
```

El `LEFT JOIN` garantiza que los países sin personas (y por tanto sin ciudades asociadas) aparezcan con `COUNT = 0`.

> **Nota:** si el modelo tuviera `id_pais FK` en `CIUDAD`, la consulta sería un `LEFT JOIN` directo entre `PAIS` y `CIUDAD`, más limpio y sin depender de que haya personas registradas. Con el modelo dado, es el único camino disponible.

---

### 16. Número de fila por país ordenado alfabéticamente por nombre (window function)

```sql
SELECT  p.nombre,
        pa.nombre_pais,
        ROW_NUMBER() OVER (
            PARTITION BY p.id_pais
            ORDER BY p.nombre ASC
        ) AS ranking
FROM    PERSONA p
JOIN    PAIS pa ON p.id_pais = pa.id_pais
ORDER BY pa.nombre_pais, ranking;
```

`PARTITION BY id_pais` reinicia el contador en cada país. `ORDER BY nombre ASC` dentro de la partición determina el orden alfabético para asignar los números. El `ORDER BY` externo es solo para presentación del resultado.
