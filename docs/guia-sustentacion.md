# Guía de sustentación — Facimus Procesos (Beta-back)

> Documento de estudio generado a partir de la lectura completa del repositorio
> `Facimus-Curiositatem/Beta-back` (rama `main`, commit `a4a1bb0`).
> Todo ejemplo de código aquí es **código real** del repositorio, con su ruta.

---

## 0. Antes de empezar: qué es (y qué NO es) este repositorio

**Beta-back es SOLO el backend.** Es una **API REST** escrita en **Java 21 + Spring Boot 4.1**.
No hay HTML, ni componentes, ni hooks, ni estilos CSS en este repositorio:

- La Entrega 1 usaba Thymeleaf (HTML renderizado en el servidor). En la **Entrega 2 se eliminó**
  por completo (commit `c6e3795 Migracion REST completa — Entrega 2`). El README es el plan de esa migración.
- El frontend planeado es una **SPA en Angular en otro repositorio** (evidencia:
  `cors.allowed-origins=${CORS_ALLOWED_ORIGINS:http://localhost:4200}` en
  `src/main/resources/application.properties` — el puerto 4200 es el de `ng serve` — y la sección 5.4
  de `docs/guia-tecnica.md`, que muestra una "VM 3 — Frontend Angular (nginx)").

Por eso, en las fases donde pides "Frontend" (componentes, props, hooks, estilos), te explico
**el contrato que el backend le exige a cualquier frontend** (qué debe enviar, qué recibe, cómo
maneja errores y el token), y te lo marco claramente. Si en la sustentación te preguntan por el
frontend, la respuesta correcta es: *"El backend es independiente del cliente; expone JSON sobre
HTTP. El frontend es otro proyecto que consume esta API."* Eso es justamente una ventaja
arquitectónica que debes saber defender (ver pregunta I-1 de la Fase 7).

**¿Qué hace el sistema?** Es un **Sistema de Gestión y Modelado de Procesos de negocio (BPMN),
multiempresa (multi-tenant)**:

1. Una empresa se registra (con su NIT) y automáticamente se crea su usuario **ADMINISTRADOR**.
2. El administrador crea colaboradores con rol **EDITOR** o **SOLO_LECTURA**.
3. Los usuarios crean **procesos** de negocio (ej. "Compras"), que empiezan en **BORRADOR** y luego se **PUBLICAN**.
4. Cada proceso se **modela** como un diagrama BPMN: **Pools** (participantes) → **Lanes** (carriles por rol)
   → **Actividades** y **Gateways** (nodos) → **Arcos** (flechas entre nodos) → **Mensajes** entre pools →
   **Correlaciones** de mensajes.
5. Todo cambio al proceso queda en una **bitácora** (`HistorialCambio`).
6. Cada empresa **solo ve sus propios datos** (aislamiento multi-tenant), garantizado por diseño y por tests.

### Mini-glosario BPMN (lo vas a necesitar)

| Término | Qué es | Entidad en el código |
|---|---|---|
| Pool | Un participante del proceso (la empresa, un cliente, un proveedor, un sistema externo) | `modelado/model/Pool.java` |
| Lane | Carril dentro de un pool; agrupa las tareas de un mismo **rol** | `modelado/model/Lane.java` |
| Rol de proceso | Una *función* (Analista, Supervisor), **no** una persona ni un permiso de login | `gestion/model/RolProceso.java` |
| Actividad | Una tarea | `modelado/model/Actividad.java` |
| Gateway | Rombo de decisión: EXCLUSIVO (uno u otro), PARALELO (todos), INCLUSIVO (uno o varios) | `modelado/model/Gateway.java` |
| Arco (sequence flow) | Flecha entre dos nodos **del mismo pool** | `modelado/model/Arco.java` |
| Mensaje (message flow) | Comunicación entre **pools distintos** (throw/catch) | `modelado/model/Mensaje.java` |
| Correlación | Criterio que dice a qué caso concreto pertenece un mensaje (ej. "número de orden") | `modelado/model/Correlacion.java` |
| Caja negra | Pool del que no se conoce su interior (ej. el cliente) | campo `Pool.cajaNegra` |

> Ojo con la ambigüedad de la palabra "rol": hay **dos** conceptos distintos.
> - `RolAcceso` (enum: ADMINISTRADOR, EDITOR, SOLO_LECTURA) = **permisos de seguridad** del usuario que inicia sesión.
> - `RolProceso` (entidad) = **función de negocio** dentro de un diagrama (nombra un Lane).
> Confundirlos en la sustentación es un error típico.

---

# FASE 1 — Comprensión total: mapa del proyecto

## 1.1 Tecnologías

| Capa | Tecnología | Versión | Dónde se declara |
|---|---|---|---|
| Lenguaje | Java | 21 | `pom.xml` → `<java.version>21</java.version>` |
| Framework | Spring Boot | 4.1.0 | `pom.xml` → `<parent> spring-boot-starter-parent` |
| Web | Spring Web MVC (servlets, Tomcat embebido) | (de Boot) | `spring-boot-starter-webmvc` |
| Persistencia | Spring Data JPA + Hibernate | (de Boot) | `spring-boot-starter-data-jpa` |
| Validación | Jakarta Bean Validation (Hibernate Validator) | (de Boot) | `spring-boot-starter-validation` |
| Seguridad | Spring Security | (de Boot) | `spring-boot-starter-security` |
| Tokens | JJWT (io.jsonwebtoken) | 0.12.6 | `jjwt-api`, `jjwt-impl`, `jjwt-jackson` |
| JSON | Jackson **3** (paquete `tools.jackson`) | (de Boot 4) | se ve en `JwtAuthEntryPoint.java`: `import tools.jackson.databind.json.JsonMapper;` |
| BD desarrollo | H2 (en archivo `./data/procesos`) | (de Boot) | `application.properties` |
| BD producción | PostgreSQL | (de Boot) | `application-prod.properties` |
| Documentación API | springdoc-openapi (Swagger UI) | 3.0.1 | `springdoc-openapi-starter-webmvc-ui` |
| Menos boilerplate | Lombok | (de Boot) | `@Getter @Setter @RequiredArgsConstructor` |
| Tests | JUnit 5, Mockito, MockMvc, Spring Security Test | (de Boot) | `spring-boot-starter-webmvc-test`, `spring-boot-starter-security-test` |
| Tests de arquitectura | ArchUnit | 1.4.0 | `archunit-junit5` |
| Cobertura | JaCoCo | 0.8.13 | plugin `jacoco-maven-plugin` (mínimo 50 % de líneas) |
| Calidad | SonarCloud | plugin 4.0.0.4121 | `sonar-maven-plugin` + job `sonarqube` del CI |
| Build | Maven (con wrapper `mvnw`) | 3.9 | `mvnw`, `.mvn/wrapper/` |
| Contenedor | Docker multi-stage | temurin 21 | `Dockerfile` |
| CI | GitHub Actions | — | `.github/workflows/ci.yml` |
| Pruebas manuales | Postman | — | `postman/Facimus-Procesos-API.postman_collection.json` |
| Pruebas de carga | JMeter | — | `jmeter/*.jmx` |

## 1.2 Patrón arquitectónico

No es MVC "clásico" (no hay Vista). Es una **arquitectura en capas (Layered Architecture)**
para una API REST, organizada **por módulo de dominio** ("package by feature" + capas dentro):

```
Controller (REST)  →  Service (reglas de negocio)  →  Repository (acceso a datos)  →  BD
      ↑↓ DTOs (records Request/Response)       ↑↓ Entidades JPA (model)
```

Patrones concretos que aparecen (los enseño en la Fase 2 con el código):

| Patrón | Dónde |
|---|---|
| **Layered architecture** | `controller/` → `service/` → `repository/` en `gestion` y `modelado` |
| **DTO (Data Transfer Object)** | `controller/dto/*Request.java`, `*Response.java` |
| **Static Factory Method** | `ProcesoResponse.of(proceso)`, `ApiPrincipal.of(usuario)`, `PageResponse.from(page)` |
| **Repository** | interfaces `*Repository` (Spring Data genera la implementación) |
| **Specification** (patrón de consulta combinable) | `gestion/repository/ProcesoSpecifications.java` |
| **Dependency Injection / IoC** | todos los constructores (`@RequiredArgsConstructor`) |
| **Chain of Responsibility** | la cadena de filtros de Spring Security + `JwtAuthenticationFilter` |
| **Template Method** | `JwtAuthenticationFilter extends OncePerRequestFilter` (sobrescribe `doFilterInternal`) y `ApiExceptionHandler extends ResponseEntityExceptionHandler` |
| **Proxy (AOP)** | `@Transactional` (Spring envuelve el service en un proxy que abre/cierra la transacción) |
| **Herencia de entidades (Single Table Inheritance)** | `NodoFlujo` ← `Actividad`, `Gateway` |
| **Layer Supertype** | `EntidadEmpresa` (`@MappedSuperclass`) — superclase común de todas las entidades con empresa |
| **Multi-tenancy por discriminador (columna `empresa_id`)** | `EntidadEmpresa` + `RepositorioTenant` |
| **Soft delete (baja lógica)** | campo `activo` en `Proceso`, `RolProceso`, `Usuario` |
| **Global Exception Handler / Problem Details (RFC 9457)** | `common/api/ApiExceptionHandler.java` |
| **Fitness functions (arquitectura verificada por tests)** | `src/test/.../arquitectura/*Test.java` (ArchUnit) |

## 1.3 Organización de carpetas

```
Beta-back/
├── pom.xml                         ← dependencias y plugins de build (Maven)
├── mvnw, mvnw.cmd, .mvn/           ← Maven Wrapper (no requiere Maven instalado)
├── Dockerfile, .dockerignore       ← imagen Docker multi-stage
├── .github/workflows/ci.yml        ← pipeline de CI (build, tests, ArchUnit, Docker, Sonar)
├── README.md                       ← plan/decisiones de la migración REST (Entrega 2)
├── docs/
│   ├── guia-tecnica.md             ← guía del equipo (algo desactualizada: habla de Thymeleaf)
│   └── cobertura-historias-usuario.md (sin commitear) ← auditoría HU-01..HU-28
├── postman/…postman_collection.json← colección para probar la API a mano
├── jmeter/*.jmx                    ← planes de prueba de carga (login, procesos, roles)
└── src/
    ├── main/
    │   ├── java/com/facimus/procesos/
    │   │   ├── ProcesosApplication.java     ← punto de entrada (main)
    │   │   ├── common/                      ← piezas transversales
    │   │   │   ├── EntidadEmpresa.java      ← superclase multi-tenant de las entidades
    │   │   │   ├── RepositorioTenant.java   ← repositorio base que obliga a filtrar por empresa
    │   │   │   ├── RecursoNoEncontradoException.java  (→ 404)
    │   │   │   ├── ReglaNegocioException.java         (→ 409)
    │   │   │   └── api/
    │   │   │       ├── ApiExceptionHandler.java       ← traduce excepciones a HTTP
    │   │   │       └── PageResponse.java              ← JSON de paginación
    │   │   ├── config/
    │   │   │   ├── DatosDemoInitializer.java ← crea empresa+admin demo si la BD está vacía
    │   │   │   └── OpenApiConfig.java        ← Swagger con esquema Bearer JWT
    │   │   ├── security/                     ← autenticación y autorización
    │   │   │   ├── SecurityConfig.java       ← reglas: quién puede llamar a qué
    │   │   │   ├── JwtService.java           ← genera/valida tokens
    │   │   │   ├── JwtAuthenticationFilter.java ← lee el header Authorization en cada petición
    │   │   │   ├── ApiPrincipal.java         ← "quién soy" (usuarioId, empresaId, rol, email)
    │   │   │   ├── JwtAuthEntryPoint.java    ← respuesta 401
    │   │   │   ├── JwtAccessDeniedHandler.java ← respuesta 403
    │   │   │   └── CorsConfig.java           ← permite al frontend (otro origen) llamar
    │   │   ├── gestion/                      ← MÓDULO 1: empresas, usuarios, procesos, roles
    │   │   │   ├── model/        (Empresa, Usuario, Proceso, HistorialCambio, RolProceso + enums)
    │   │   │   ├── repository/   (5 repos + ProcesoSpecifications)
    │   │   │   ├── service/      (5 services + dto/RolProcesoVista)
    │   │   │   └── controller/   (Auth, Empresa, Usuario, Proceso, RolProceso + dto/)
    │   │   └── modelado/                     ← MÓDULO 2: diagrama BPMN
    │   │       ├── model/        (Pool, Lane, NodoFlujo, Actividad, Gateway, Arco, Mensaje, Correlacion + enums)
    │   │       ├── repository/   (6 repos)
    │   │       ├── service/      (7 services)
    │   │       └── controller/   (7 controllers + dto/)
    │   └── resources/
    │       ├── application.properties       ← configuración por defecto (H2)
    │       └── application-prod.properties  ← perfil "prod" (PostgreSQL)
    └── test/java/com/facimus/procesos/
        ├── ProcesosApplicationTests.java    ← ¿arranca el contexto?
        ├── arquitectura/   (6 clases ArchUnit)
        ├── gestion/controller|service/      ← tests de controllers (MockMvc) y services (Mockito)
        ├── modelado/controller/             ← tests de controllers de modelado
        └── security/       (JWT, integración, autorización por rol, aislamiento entre empresas)
```

**Conteo:** 110 archivos Java de producción, 31 de test, 13 entidades, 4 enums, 12 services,
12 controllers, ~50 endpoints.

## 1.4 Flujo general de ejecución

### Arranque (una vez)

```
java -jar app.jar   (o ./mvnw spring-boot:run)
   │
   ▼
ProcesosApplication.main()  →  SpringApplication.run(...)
   │
   ├─ Lee application.properties (+ application-prod.properties si SPRING_PROFILES_ACTIVE=prod)
   ├─ @SpringBootApplication escanea com.facimus.procesos.** y crea los "beans":
   │     controllers, services, repositories (proxies generados), JwtService, SecurityConfig...
   ├─ Hibernate se conecta a la BD y con ddl-auto=update crea/ajusta las tablas
   ├─ Spring Security arma la cadena de filtros (SecurityFilterChain)
   ├─ Tomcat embebido escucha en el puerto 8080
   └─ DatosDemoInitializer.run(): si no hay empresas, crea "Empresa Demo S.A.S." + admin@demo.com
```

### Cada petición HTTP

```
Cliente (Angular / Postman / curl)
   │  HTTP + JSON, header "Authorization: Bearer <token>"
   ▼
Tomcat
   ▼
CorsFilter (orden -102)                     security/CorsConfig.java
   ▼
Spring Security FilterChain (orden -100)
   ├─ JwtAuthenticationFilter               security/JwtAuthenticationFilter.java
   │     valida token → carga usuario → pone ApiPrincipal en el SecurityContext
   └─ AuthorizationFilter                   reglas de security/SecurityConfig.java
         ¿no autenticado? → JwtAuthEntryPoint → 401
         ¿rol insuficiente? → JwtAccessDeniedHandler → 403
   ▼
DispatcherServlet (Spring MVC)
   ├─ busca el @RestController + método según URL y verbo
   ├─ Jackson convierte el JSON del body → record *Request
   ├─ @Validated ejecuta las anotaciones @NotBlank, @Email... → si fallan, 400
   └─ @AuthenticationPrincipal inyecta el ApiPrincipal
   ▼
Controller  → extrae empresaId del principal (NUNCA del body)
   ▼
Service (@Transactional)  → reglas de negocio; lanza ReglaNegocioException / RecursoNoEncontradoException
   ▼
Repository (Spring Data)  → SQL generado por Hibernate, siempre con "WHERE ... empresa_id = ?"
   ▼
BD (H2 / PostgreSQL)
   ▲
Entidad → Controller la convierte a record *Response → Jackson la vuelve JSON
   ▲
ResponseEntity con código HTTP (200/201/204) o, si hubo excepción,
ApiExceptionHandler → ProblemDetail (400/404/409/500)
```

## 1.5 Variables de entorno y configuración (sin secretos)

`src/main/resources/application.properties` (perfil por defecto = desarrollo):

| Propiedad | Valor | Propósito |
|---|---|---|
| `spring.datasource.url` | `jdbc:h2:file:./data/procesos;AUTO_SERVER=TRUE` | BD H2 guardada en archivo (persiste entre reinicios). `AUTO_SERVER` permite abrirla desde otra herramienta a la vez |
| `spring.jpa.hibernate.ddl-auto` | `update` | Hibernate crea/altera tablas según las entidades (no hay migraciones tipo Flyway) |
| `spring.jpa.open-in-view` | `false` | **Decisión consciente:** la sesión de Hibernate se cierra al terminar el service; evita consultas ocultas desde el controller |
| `hibernate.jdbc.time_zone` | `UTC` | fechas guardadas en UTC |
| `spring.h2.console.enabled` | `true` | consola web en `/h2-console` para ver la BD en desarrollo |
| `server.port` | `8080` | puerto HTTP |
| `jwt.secret` | `${JWT_SECRET:}` | clave para firmar tokens. **Vacía en dev** → `JwtService` genera una aleatoria (tokens mueren al reiniciar) |
| `jwt.expiration-seconds` | `${JWT_EXPIRATION_SECONDS:1800}` | vida del token: 30 min |
| `cors.allowed-origins` | `${CORS_ALLOWED_ORIGINS:http://localhost:4200}` | orígenes del frontend permitidos |
| `spring.autoconfigure.exclude` | `UserDetailsServiceAutoConfiguration` | evita que Spring cree un usuario "user" con contraseña impresa en consola: aquí la auth es por JWT propio |

`src/main/resources/application-prod.properties` (se activa con `SPRING_PROFILES_ACTIVE=prod`):

| Variable de entorno | Propósito |
|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME` | dónde está PostgreSQL (defaults `localhost`, `5432`, `procesos`) |
| `DB_USER`, `DB_PASSWORD` | credenciales de la BD (nunca en Git) |
| `JWT_SECRET` | **obligatoria en prod**: `jwt.secret=${JWT_SECRET}` no tiene default → si falta, la app no arranca (*fail fast*). Debe tener ≥ 32 bytes (HS256) o JJWT lanza `WeakKeyException` |
| (fijo) `spring.h2.console.enabled=false` | consola H2 apagada en producción |

Secretos del CI (GitHub → Settings → Secrets): `SONAR_TOKEN`, `SONAR_HOST_URL`, usados en el job `sonarqube`.

La sintaxis `${VAR:default}` significa: "usa la variable de entorno VAR; si no existe, usa default".

---

# FASE 2 — El proyecto explicado como a un estudiante (de menor a mayor dificultad)

Orden de estudio: **Nivel 1** build y arranque → **Nivel 2** modelo de datos → **Nivel 3** repositorios →
**Nivel 4** servicios → **Nivel 5** DTOs y controllers → **Nivel 6** manejo de errores →
**Nivel 7** seguridad → **Nivel 8** tests y CI/CD.

## Tabla resumen de carpetas

| Carpeta | ¿Para qué existe? | ¿Quién la usa? | Archivos críticos | ¿Qué pasaría si desapareciera? |
|---|---|---|---|---|
| raíz (`pom.xml`, `mvnw`) | Definir cómo se compila y qué librerías hay | Maven, el IDE, el CI, Docker | `pom.xml` | No compila nada: no hay dependencias |
| `src/main/resources` | Configuración externa al código | Spring Boot al arrancar | `application.properties` | Sin BD configurada, sin `jwt.*` → `JwtService` falla al crearse; la app no arranca |
| `common/` | Código compartido por ambos módulos | Todas las entidades, repos y services | `EntidadEmpresa`, `RepositorioTenant`, `ApiExceptionHandler` | Se rompe el multi-tenant y los errores saldrían como HTML/500 genérico |
| `config/` | Beans de configuración y datos semilla | Spring al arrancar | `DatosDemoInitializer` | No habría usuario inicial ni Swagger con botón "Authorize" (la app sí funcionaría) |
| `security/` | Autenticación (¿quién eres?) y autorización (¿qué puedes hacer?) | Spring Security en cada petición; controllers vía `ApiPrincipal` | `SecurityConfig`, `JwtService`, `JwtAuthenticationFilter` | Spring Boot activaría una seguridad por defecto (login básico) y la API quedaría inutilizable para el frontend; y los controllers no tendrían `empresaId` |
| `gestion/` | Módulo de administración: empresas, usuarios, procesos, roles, historial | Frontend (endpoints), `modelado` (usa `Proceso`, `RolProceso`) | `ProcesoService`, `UsuarioService`, `SecurityConfig` depende de `UsuarioService` | No hay login ni procesos: el sistema no tiene sentido |
| `modelado/` | Módulo del diagrama BPMN | Frontend (editor de diagramas); `ProcesoService` crea el Pool inicial; `RolProcesoService` consulta Lanes | `NodoFlujo`, `ArcoService`, `PoolService` | Se podrían crear procesos pero no modelarlos; además `ProcesoService` no compilaría (usa `PoolRepository`) |
| `*/model` | Entidades JPA = tablas | Repositories, services | `Proceso`, `NodoFlujo` | No hay tablas |
| `*/repository` | Acceso a datos | Services | `ProcesoRepository`, `NodoFlujoRepository` | Los services no pueden leer/escribir |
| `*/service` | Reglas de negocio + transacciones | Controllers, filtro JWT | `ProcesoService`, `ArcoService` | Controllers tendrían que hablar con repos (lo prohíbe ArchUnit) |
| `*/controller` | Endpoints HTTP | Clientes HTTP | `ProcesoController`, `AuthController` | La API no expone nada |
| `*/controller/dto` | Contratos JSON de entrada/salida | Controllers, Jackson | `LoginRequest/Response`, `ProcesoResponse` | Habría que exponer entidades (inseguro: se filtraría `passwordHash`) |
| `src/test` | Pruebas automáticas | Maven (`mvn test`), CI | `arquitectura/*`, `security/*IntegracionTest` | Nada protege las reglas; cualquier cambio podría romper el aislamiento sin que nadie lo note |
| `.github/workflows` | CI | GitHub Actions | `ci.yml` | No hay verificación automática en cada push/PR |
| `postman/`, `jmeter/` | Pruebas manuales y de carga | Personas del equipo | colección Postman | No afecta al código; se pierde evidencia de pruebas |
| `docs/` | Documentación | Equipo, evaluadores | `guia-tecnica.md` | No afecta al código |

---

## NIVEL 1 — Build y arranque

### `pom.xml` (Project Object Model de Maven)

- **Responsabilidad:** declarar identidad del proyecto (`com.facimus:procesos:0.0.1-SNAPSHOT`), versión de Java,
  dependencias y plugins.
- **Cómo se ejecuta:** no se "ejecuta"; Maven lo lee con `./mvnw clean verify`.
- **Claves para la sustentación:**
  - `<parent>spring-boot-starter-parent 4.1.0` → hereda versiones compatibles de todas las librerías de Spring
    (por eso muchas dependencias no llevan `<version>`). Esto se llama **gestión de dependencias (BOM)**.
  - Los **starters** (`spring-boot-starter-webmvc`, `-data-jpa`, `-validation`, `-security`) son "paquetes de
    dependencias" que traen todo lo necesario para una funcionalidad y activan su **autoconfiguración**.
  - `<scope>runtime</scope>` (h2, postgresql, jjwt-impl, jjwt-jackson): se necesitan al ejecutar, no para compilar.
    El código solo usa la API (`jjwt-api`), nunca la implementación: buena práctica de desacoplamiento.
  - `<scope>test</scope>`: solo existen en los tests (ArchUnit, MockMvc).
  - `maven-compiler-plugin` con `annotationProcessorPaths` → Lombok: genera getters/setters/constructores
    **en tiempo de compilación**.
  - `jacoco-maven-plugin`: `prepare-agent` instrumenta los tests, `report` genera `target/site/jacoco/`,
    `check` **falla el build si la cobertura de líneas < 50 %** (hoy está en 87,2 %).
  - `sonar.*` properties: configuran el análisis en SonarCloud.

### `mvnw` / `mvnw.cmd` / `.mvn/wrapper/maven-wrapper.properties`
Maven Wrapper: descarga la versión correcta de Maven la primera vez. Así cualquier persona (o el CI) compila
igual sin instalar Maven. Si desapareciera, habría que tener Maven instalado.

### `ProcesosApplication.java`

```java
@SpringBootApplication
public class ProcesosApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProcesosApplication.class, args);
    }
}
```

- **Responsabilidad:** punto de entrada. `@SpringBootApplication` = `@Configuration` + `@EnableAutoConfiguration`
  + `@ComponentScan`.
  - `@ComponentScan`: busca clases con `@Component`, `@Service`, `@RestController`, `@Configuration`...
    **en su paquete y subpaquetes** (`com.facimus.procesos.**`). Por eso está en la raíz del paquete.
  - `@EnableAutoConfiguration`: como hay H2 en el classpath, configura un DataSource; como hay Spring MVC,
    levanta Tomcat; etc.
- **Qué pasaría si desapareciera:** no hay `main`, no arranca nada.

### `src/main/resources/application*.properties`
Explicados en la sección 1.5. Participan **solo en el arranque**. Los valores se inyectan con `@Value`
(ej. `JwtService`, `CorsConfig`).

### `config/DatosDemoInitializer.java` — datos semilla

```java
@Component
@RequiredArgsConstructor
public class DatosDemoInitializer implements CommandLineRunner {
    ...
    @Override
    public void run(String... args) {
        if (empresaRepository.count() > 0) {   // 1. solo si la BD está vacía
            return;
        }
        Empresa empresa = new Empresa();        // 2. crea empresa demo
        ...
        empresa = empresaRepository.save(empresa);
        Usuario admin = new Usuario();          // 3. crea admin con contraseña cifrada BCrypt
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD_DEMO));
        admin.setRolAcceso(RolAcceso.ADMINISTRADOR);
        usuarioRepository.save(admin);
    }
}
```

- `CommandLineRunner`: interfaz de Spring Boot; su `run` se ejecuta **una vez, justo después de arrancar**.
- **Importa:** `EmpresaRepository`, `UsuarioRepository`, `PasswordEncoder` (bean de `SecurityConfig`).
- **Quién la usa:** Spring automáticamente; los tests de integración (`SeguridadIntegracionTest`,
  `AutorizacionPorRolTest`) inician sesión con `admin@demo.com / admin123` gracias a esta clase.
- **Riesgo:** no está restringida a un perfil (`@Profile("!prod")`), así que en producción con BD vacía también
  crearía ese admin con contraseña conocida (ver Fase 8).
- Nota: aquí usa repositorios directamente (no un service). ArchUnit solo prohíbe eso a controllers y a `security`.

### `config/OpenApiConfig.java` — Swagger
Crea un bean `OpenAPI` con título, versión y un **esquema de seguridad `bearerAuth` (HTTP bearer, formato JWT)**.
Resultado: en `http://localhost:8080/swagger-ui.html` aparece el botón **Authorize** para pegar el token.
Los endpoints públicos llevan `@SecurityRequirements()` vacío (login y registro) para que Swagger no pida token.

---

## NIVEL 2 — El modelo de datos (entidades JPA)

### Conceptos primero
- **ORM (Object-Relational Mapping):** mapear clases Java ↔ tablas. **JPA** es la especificación (anotaciones
  `jakarta.persistence.*`); **Hibernate** es la implementación que genera el SQL.
- `@Entity` = esta clase es una tabla. `@Table(name="procesos")` = nombre de la tabla.
- `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)` = clave primaria autoincremental de la BD.
- `@Column(nullable=false, unique=true)` = restricciones NOT NULL / UNIQUE en el DDL.
- `@ManyToOne` + `@JoinColumn(name="x_id")` = clave foránea. **Por defecto `@ManyToOne` es EAGER** (carga el
  objeto relacionado inmediatamente). Esto es importante: como `open-in-view=false`, si fuera LAZY, al convertir
  la entidad a DTO en el controller (fuera de la transacción) daría `LazyInitializationException`. Con EAGER
  funciona, a costa de más consultas (ver Fase 8, rendimiento).
- `@Enumerated(EnumType.STRING)` = guarda el enum como texto (`'BORRADOR'`), no como número. Si se usara
  ORDINAL y alguien reordenara el enum, todos los datos cambiarían de significado. ArchUnit lo prohíbe
  (`HerenciaJpaTest`).
- `@Lob` = objeto grande (texto largo): `Proceso.descripcion`, `Mensaje.contenido`.
- Lombok: `@Getter @Setter @NoArgsConstructor` generan getters, setters y el constructor vacío que JPA exige.

### `common/EntidadEmpresa.java` — la pieza clave del multi-tenant (explicada junto con `Empresa`)

```java
@Getter @Setter
@MappedSuperclass                       // no es tabla: sus campos se COPIAN a cada tabla hija
public abstract class EntidadEmpresa {
    @ManyToOne(optional = false)
    @JoinColumn(name = "empresa_id", nullable = false, updatable = false)
    protected Empresa empresa;
}
```

Línea por línea:
1. `@MappedSuperclass`: no crea tabla propia. Cada entidad hija (`procesos`, `usuarios`, `pools`...) recibe
   la columna `empresa_id`.
2. `abstract`: no se puede instanciar sola.
3. `@ManyToOne(optional = false)`: muchas filas pertenecen a una empresa; obligatoria.
4. `nullable = false`: en BD es NOT NULL → **ningún dato puede existir sin dueño**.
5. `updatable = false`: Hibernate **nunca** incluye `empresa_id` en un UPDATE → un registro **no puede
   "mudarse" de empresa**, ni por error ni por ataque.

`Empresa` es la única entidad que **no** extiende `EntidadEmpresa`: es la raíz del tenant. Tiene `nit` con
`unique = true` (regla HU-01 reforzada también a nivel BD).

Test que lo protege: `arquitectura/MultitenenciaTest.java` ("Toda @Entity excepto Empresa debe extender
EntidadEmpresa" y "EntidadEmpresa tiene @JoinColumn con updatable=false").

### Entidades del módulo `gestion/model`

| Entidad | Tabla | Campos importantes | Reglas que expresa |
|---|---|---|---|
| `Empresa` | `empresas` | `nombre`, `nit` (UNIQUE), `correoContacto`, `fechaRegistro` | raíz del tenant |
| `Usuario` | `usuarios` | `nombre`, `email`, `passwordHash`, `rolAcceso` (enum STRING), `activo` | `UNIQUE(empresa_id, email)`: el correo es único **dentro** de una empresa |
| `Proceso` | `procesos` | `nombre`, `descripcion` (@Lob), `categoria`, `estado` (default BORRADOR), `activo` (default true), `fechaCreacion`, `fechaModificacion` | baja lógica con `activo` |
| `HistorialCambio` | `historial_cambios` | `proceso` (FK), `autor` (FK a Usuario), `fechaCambio`, `descripcionCambio` | bitácora **solo INSERT** |
| `RolProceso` | `roles_proceso` | `nombre`, `descripcion`, `activo` | baja lógica (HU-19) |
| `RolAcceso` (enum) | — | ADMINISTRADOR, EDITOR, SOLO_LECTURA | permisos de seguridad |
| `EstadoProceso` (enum) | — | BORRADOR, PUBLICADO | ciclo de vida |

Nota: se guarda `passwordHash`, **nunca** la contraseña. El hash es BCrypt (`$2a$10$...`), irreversible y con sal.

### Entidades del módulo `modelado/model` (el diagrama BPMN)

```
Proceso 1 ──< Pool 1 ──< Lane >── 1 RolProceso
                 │         │
                 │         └──< NodoFlujo (abstracta, SINGLE_TABLE "nodos_flujo")
                 │                 ├── Actividad  (tipo_nodo='ACTIVIDAD', descripcion)
                 │                 └── Gateway    (tipo_nodo='GATEWAY', tipo_gateway)
                 │
                 └──< Arco (origen: NodoFlujo, destino: NodoFlujo, pool redundante)
Proceso 1 ──< Mensaje (poolOrigen, poolDestino) 1 ── 0..1 Correlacion
```

#### `NodoFlujo.java` + `Actividad.java` + `Gateway.java` — herencia JPA (explicar juntos)

```java
@Entity
@Table(name = "nodos_flujo")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)   // una sola tabla para toda la jerarquía
@DiscriminatorColumn(name = "tipo_nodo")                 // columna que dice qué subtipo es cada fila
public abstract class NodoFlujo extends EntidadEmpresa {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String nombre;
    @Column(name = "posicion_x", nullable = false) private int posicionX;   // coordenadas en el lienzo
    @Column(name = "posicion_y", nullable = false) private int posicionY;
    @ManyToOne(optional = false) @JoinColumn(name = "lane_id", nullable = false) private Lane lane;
}

@Entity @DiscriminatorValue("ACTIVIDAD")
public class Actividad extends NodoFlujo { @Column private String descripcion; }

@Entity @DiscriminatorValue("GATEWAY")
public class Gateway extends NodoFlujo {
    // Sin nullable = false: con SINGLE_TABLE las actividades comparten esta columna y no tienen tipo.
    @Enumerated(EnumType.STRING) @Column(name = "tipo_gateway") private TipoGateway tipoGateway;
}
```

**¿Por qué herencia?** Porque un **Arco** conecta "nodos", y un nodo puede ser Actividad o Gateway. Con una
superclase, `Arco.origen` y `Arco.destino` son simplemente `NodoFlujo` (polimorfismo).

**¿Por qué SINGLE_TABLE y no JOINED o TABLE_PER_CLASS?** (pregunta segura en la sustentación)

| Estrategia | Tablas | Ventaja | Desventaja |
|---|---|---|---|
| **SINGLE_TABLE (elegida)** | 1 (`nodos_flujo`) | sin JOINs, consultas polimórficas rápidas, FK simple desde `arcos` | columnas de subtipos deben ser nullable (`tipo_gateway`, `descripcion`) |
| JOINED | 3 | normalizado, permite NOT NULL por subtipo | JOIN en cada lectura |
| TABLE_PER_CLASS | 2 | tablas separadas | FK polimórfica desde `arcos` imposible sin UNION |

El comentario del código lo justifica: *"SINGLE_TABLE porque solo hay dos subtipos con pocos campos propios;
evita joins innecesarios."* La desventaja se compensa validando en el DTO: `GatewayRequest` exige
`@NotNull tipoGateway`. Test: `HerenciaJpaTest`.

#### `Arco.java`
Tiene `origen` y `destino` (`NodoFlujo`) y un `pool` **redundante**: *"Redundante respecto a origen/destino: sirve
para validar que ambos comparten el mismo pool."* (desnormalización consciente para facilitar
`findAllByPoolIdAndEmpresaId`).

#### `Mensaje.java` y `Correlacion.java`
`Mensaje` une **dos pools distintos** (`poolOrigen`, `poolDestino`) y pertenece a un `Proceso`.
`Correlacion` es `@OneToOne` con `Mensaje` y `@JoinColumn(unique = true)`: **como máximo una correlación por mensaje**.

#### `Pool.java`, `Lane.java`
`Pool` tiene `tipoParticipante` (EMPRESA, CLIENTE, PROVEEDOR, SISTEMA_EXTERNO), `cajaNegra`, `orden`.
`Lane` tiene `orden` y un `RolProceso` obligatorio — es el **puente entre los dos módulos** (modelado → gestion).

---

## NIVEL 3 — Repositorios (acceso a datos)

### Concepto: Spring Data JPA
Tú escribes **solo la interfaz**; Spring genera la implementación al arrancar (un *proxy*). Hay 3 formas de
consultar en este proyecto:

1. **Métodos heredados** de `JpaRepository`: `save`, `delete`, `findAll`, `count`...
2. **Derived queries (consultas derivadas del nombre del método)**: Spring lee el nombre y genera el JPQL.
3. **Specifications** (Criteria API): filtros dinámicos combinables.

### `common/RepositorioTenant.java` — el contrato multi-tenant

```java
@NoRepositoryBean   // "no crees un repositorio para esta interfaz; es solo una base"
public interface RepositorioTenant<T extends EntidadEmpresa> extends JpaRepository<T, Long> {
    Optional<T> findByIdAndEmpresaId(Long id, Long empresaId);
    List<T> findAllByEmpresaId(Long empresaId);
    boolean existsByIdAndEmpresaId(Long id, Long empresaId);
}
```

- Genéricos: `T extends EntidadEmpresa` → solo sirve para entidades con empresa.
- `findByIdAndEmpresaId(5, 1)` genera:
  `select ... from procesos p where p.id = ? and p.empresa_id = ?`
  Si el proceso 5 es de la empresa 2, **devuelve vacío** → el service lanza 404. Así se previene **IDOR**
  (*Insecure Direct Object Reference*: cambiar el id en la URL para ver datos ajenos).
- Todos los repos (excepto `EmpresaRepository`) extienden esto. Tests: `RepositorioTenantTest` y
  `AislamientoTenantTest` (este último **prohíbe** que un service llame a `findById`, `existsById`, `deleteById`,
  `getReferenceById`, `findAllById` o `findAll()` sin Specification sobre un `RepositorioTenant`).

### Derived queries: cómo leer los nombres (con ejemplos reales)

| Método (archivo) | SQL aproximado |
|---|---|
| `existsByNit(nit)` (`EmpresaRepository`) | `select count(*)>0 from empresas where nit=?` |
| `existsByEmpresaIdAndNombreIgnoreCaseAndActivoTrue(empresaId, nombre)` (`ProcesoRepository`) | `... where empresa_id=? and upper(nombre)=upper(?) and activo=true` |
| `findAllByProcesoIdAndEmpresaIdOrderByFechaCambioDesc` (`HistorialCambioRepository`) | `... where proceso_id=? and empresa_id=? order by fecha_cambio desc` |
| `countByRolProcesoIdAndEmpresaId` (`LaneRepository`) | `select count(*) from lanes where rol_proceso_id=? and empresa_id=?` |
| `existsByNombreIgnoreCaseAndLane_Pool_ProcesoIdAndEmpresaId` (`NodoFlujoRepository`) | ver abajo |

Desglose del más complejo:
```
exists                     → devuelve boolean
By                         → empieza el WHERE
NombreIgnoreCase           → upper(n.nombre) = upper(?1)
And
Lane_Pool_ProcesoId        → navega n.lane.pool.proceso.id = ?2   (el "_" separa niveles de la ruta)
And
EmpresaId                  → n.empresa.id = ?3
```
→ "¿Existe ya un nodo con ese nombre en **cualquier lane de cualquier pool del mismo proceso**?" (HU-08:
nombre único por proceso). Genera JOINs `nodos_flujo → lanes → pools`.

### `UsuarioRepository.findByEmail` — la única excepción documentada
```java
/** Login-only: el formulario de inicio de sesion no conoce todavia el empresaId ... */
Optional<Usuario> findByEmail(String email);
```
En el login aún no sabemos la empresa. **Pero** el email solo es único por empresa → si existe en dos empresas,
esta consulta devuelve 2 filas y Spring lanza `IncorrectResultSizeDataAccessException` → **HTTP 500**.
**Lo verifiqué ejecutándolo** (ver Fase 8, bug B-1). Pregunta probable en la sustentación.

### `ProcesoSpecifications.java` — filtros dinámicos (HU-07), línea por línea

```java
public static Specification<Proceso> conFiltros(Long empresaId, String nombre, EstadoProceso estado,
        String categoria) {
    return (root, query, cb) -> {                                     // (1)
        var predicado = cb.and(
                cb.equal(root.get("empresa").get("id"), empresaId),   // (2) SIEMPRE por empresa
                cb.isTrue(root.get("activo")));                       // (3) SIEMPRE solo activos
        if (StringUtils.hasText(nombre)) {                            // (4) filtro opcional
            predicado = cb.and(predicado,
                cb.like(cb.lower(root.get("nombre")), "%" + nombre.toLowerCase() + "%"));
        }
        if (estado != null) {
            predicado = cb.and(predicado, cb.equal(root.get("estado"), estado));
        }
        if (StringUtils.hasText(categoria)) {
            predicado = cb.and(predicado, cb.equal(root.get("categoria"), categoria));
        }
        return predicado;                                             // (5)
    };
}
```
1. Una `Specification` es una **lambda** que recibe `root` (la entidad, "FROM procesos"), `query` y `cb`
   (CriteriaBuilder, fábrica de condiciones).
2. y 3. Condiciones obligatorias: tenant y baja lógica.
4. Cada filtro solo se agrega si el usuario lo envió → evita escribir 8 métodos distintos para las
   combinaciones posibles de 3 filtros.
5. Se pasa a `procesoRepository.findAll(spec, pageable)` (de `JpaSpecificationExecutor`), que además
   pagina y ordena.

¿Hay inyección SQL? **No**: la Criteria API usa parámetros enlazados (`?`), el texto nunca se concatena al SQL.

---

## NIVEL 4 — Servicios (reglas de negocio)

### Conceptos
- `@Service`: bean de Spring con lógica de negocio.
- `@RequiredArgsConstructor` (Lombok): genera un constructor con todos los campos `final` → Spring inyecta las
  dependencias por **constructor** (la forma recomendada: campos inmutables y fáciles de testear con mocks).
- `@Transactional`: todo el método es **una transacción**: si algo lanza una excepción no controlada
  (`RuntimeException`), **se revierte todo** (rollback). Spring lo implementa con un **proxy** que envuelve el bean.
- Firma común: **todo método recibe `empresaId` como primer parámetro**. El service nunca lo inventa: se lo pasa
  el controller desde el token.
- Excepciones de dominio: `RecursoNoEncontradoException` (→ 404) y `ReglaNegocioException` (→ 409). Ambas son
  `RuntimeException` (no obligan a `throws` y disparan rollback).

### `gestion/service/ProcesoService.java` — el service más importante (línea por línea de `crear`)

```java
@Transactional
public Proceso crear(Long empresaId, Long usuarioId, String nombre, String descripcion, String categoria) {
    // 1. Regla HU-04: nombre único (ignorando mayúsculas) entre procesos ACTIVOS de la empresa
    if (procesoRepository.existsByEmpresaIdAndNombreIgnoreCaseAndActivoTrue(empresaId, nombre)) {
        throw new ReglaNegocioException("Ya existe un proceso activo con el nombre \"" + nombre + "\" en esta empresa.");
    }
    // 2. Carga empresa y autor (el autor, acotado a la empresa)
    Empresa empresa = empresaRepository.findById(empresaId)
            .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
    Usuario autor = usuarioRepository.findByIdAndEmpresaId(usuarioId, empresaId)
            .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));

    // 3. Construye el proceso: BORRADOR, activo, fechas
    LocalDateTime ahora = LocalDateTime.now();
    Proceso proceso = new Proceso();
    proceso.setEmpresa(empresa);
    ...
    proceso.setEstado(EstadoProceso.BORRADOR);
    proceso = procesoRepository.save(proceso);          // INSERT INTO procesos → obtiene id

    // 4. Regla HU-21: todo proceso nace con un Pool de la propia empresa
    Pool poolInicial = new Pool();
    poolInicial.setNombre(empresa.getNombre());
    poolInicial.setTipoParticipante(TipoParticipante.EMPRESA);
    poolInicial.setOrden(0);
    poolRepository.save(poolInicial);                   // INSERT INTO pools

    // 5. Trazabilidad
    historialCambioService.registrar(proceso, autor, "Proceso creado.");   // INSERT INTO historial_cambios
    return proceso;
}
```
Si el paso 5 fallara, gracias a `@Transactional` **tampoco quedarían** el proceso ni el pool (atomicidad: la "A"
de ACID).

Otros métodos de `ProcesoService`:
- `editarDatos`: valida nombre único **solo si cambió** (`!proceso.getNombre().equalsIgnoreCase(nombre)`),
  actualiza y registra "Proceso editado.".
- `cambiarEstado`: **máquina de estados** simple:
  - mismo estado → no hace nada (idempotente);
  - PUBLICADO → BORRADOR → `ReglaNegocioException("Un proceso publicado no puede volver a borrador.")`;
  - BORRADOR → PUBLICADO → guarda y registra "Proceso publicado.".
- `eliminarLogico`: `setActivo(false)` + historial. **No borra filas** (HU-06).
- `buscar`: usa `ProcesoSpecifications` + `Pageable`.
- `obtener`: `findByIdAndEmpresaIdAndActivoTrue` → un proceso eliminado lógicamente da 404.

### `UsuarioService.java` — alta, cambios y autenticación
```java
public Usuario autenticar(String email, String password) {
    Usuario usuario = usuarioRepository.findByEmail(email)
            .filter(Usuario::isActivo)                                   // inactivo = como si no existiera
            .orElseThrow(() -> new ReglaNegocioException(CREDENCIALES_INVALIDAS));
    if (!passwordEncoder.matches(password, usuario.getPasswordHash())) { // BCrypt compara con el hash
        throw new ReglaNegocioException(CREDENCIALES_INVALIDAS);
    }
    return usuario;
}
```
- **Mismo mensaje** ("Correo o contrasena incorrectos.") si el correo no existe o la clave es mala → no permite
  **enumerar usuarios** (HU-03).
- `passwordEncoder.matches` re-calcula BCrypt con la sal guardada dentro del hash y compara.
- `crearColaborador`: correo único por empresa, cifra con `passwordEncoder.encode`.
- `actualizar(empresaId, id, rolAcceso, activo)`: actualización **parcial** (solo lo no nulo) → encaja con PATCH.
- `desactivar`: baja lógica (`activo=false`) → conserva el historial del que es autor.
- `obtener`: lo usa también el **filtro JWT** en cada petición.

### `EmpresaService.registrar` (HU-01)
En una sola transacción: valida NIT único → crea `Empresa` → crea `Usuario` ADMINISTRADOR con clave cifrada.

### `HistorialCambioService`
`registrar` (solo INSERT) y `listarPorProceso` (orden descendente por fecha). Nunca edita ni borra: es una
bitácora de auditoría.

### `RolProcesoService` (HU-17 a HU-20)
- `validarNombreUnico`: trae todos los roles activos y compara en memoria (funciona, pero ver Fase 8).
- `eliminar`: si algún `Lane` usa el rol, lanza error **listando los procesos** que lo usan:
  `lane.getPool().getProceso().getNombre()` (navegación de objetos gracias a EAGER). Si no, baja lógica.
- `listarConUso`: devuelve `RolProcesoVista(rol, usos, enUso)` (un record del paquete `service/dto`) para que el
  frontend sepa si mostrar el botón "eliminar".

### Services de `modelado` — reglas BPMN

| Service | Reglas de negocio que implementa |
|---|---|
| `PoolService` | `orden` = número de pools existentes; no se elimina un pool si alguna de sus lanes tiene nodos; al eliminar borra sus lanes vacías |
| `LaneService` | exige un `RolProceso` de la misma empresa; `orden` automático; no se elimina una lane con nodos |
| `ActividadService` / `GatewayService` | nombre único **por proceso**; al eliminar, borra antes los arcos entrantes y salientes (**cascada manual**) para no violar FKs; `obtener` filtra por tipo con `.filter(Actividad.class::isInstance).map(Actividad.class::cast)` porque el repo es polimórfico |
| `ArcoService` | ver abajo |
| `MensajeService` | pool origen ≠ destino; al eliminar un mensaje borra antes su correlación |
| `CorrelacionService` | `definir` = **upsert** (si existe la actualiza, si no la crea) → encaja con PUT idempotente |

#### `ArcoService.crear` — el método con más reglas (línea por línea)
```java
if (origenId.equals(destinoId))                                  // R1: no hay auto-bucles
    throw new ReglaNegocioException("Un arco no puede tener el mismo nodo como origen y destino.");
NodoFlujo origen = nodoFlujoRepository.findByIdAndEmpresaId(origenId, empresaId)   // R2: existen y son de mi empresa
        .orElseThrow(() -> new RecursoNoEncontradoException("Nodo de origen no encontrado."));
NodoFlujo destino = ...;
Pool poolOrigen = origen.getLane().getPool();
Pool poolDestino = destino.getLane().getPool();
if (!poolOrigen.getId().equals(poolDestino.getId()))             // R3: BPMN: sequence flow no cruza pools
    throw new ReglaNegocioException("El origen y el destino de un arco deben pertenecer al mismo pool.");
if (arcoRepository.existsByOrigenIdAndDestinoIdAndEmpresaId(origenId, destinoId, empresaId))  // R4: sin duplicados
    throw new ReglaNegocioException("Ya existe un arco entre estos dos nodos.");
if (destino instanceof Gateway gatewayDestino                    // R5: pattern matching de Java 16+
        && (gatewayDestino.getTipoGateway() == TipoGateway.EXCLUSIVO
            || gatewayDestino.getTipoGateway() == TipoGateway.INCLUSIVO)
        && !StringUtils.hasText(condicion))
    throw new ReglaNegocioException("Un arco hacia un gateway exclusivo o inclusivo requiere condicion.");
Arco arco = new Arco(); ... arco.setPool(poolOrigen); ...
return arcoRepository.save(arco);
```
`destino instanceof Gateway gatewayDestino` es **pattern matching for instanceof**: comprueba el tipo y, si
coincide, crea la variable ya casteada.

---

## NIVEL 5 — DTOs y Controllers (la capa HTTP)

### DTOs como `record` de Java
```java
public record ProcesoRequest(
        @NotBlank(message = "El nombre es obligatorio.") String nombre,
        @NotBlank(message = "La descripcion es obligatoria.") String descripcion,
        @NotBlank(message = "La categoria es obligatoria.") String categoria) {
}
```
- Un `record` es una clase **inmutable** con constructor, getters (`nombre()`), `equals`, `hashCode` y
  `toString` generados. Ideal para datos que viajan.
- Las anotaciones `@NotBlank`, `@Email`, `@Size(min = 6)`, `@NotNull` son **Bean Validation**: se ejecutan
  cuando el controller marca el parámetro con `@Validated`.
- Validación a nivel de objeto: `ActualizarUsuarioRequest` usa
  `@AssertTrue(message = "Debe enviar al menos rolAcceso o activo.") isActualizacionPresente()`.
- **¿Por qué no devolver la entidad directamente?**
  1. Seguridad: `Usuario` tiene `passwordHash`; `UsuarioResponse` no.
  2. Evitar ciclos/relaciones enormes en el JSON (`Lane → Pool → Proceso → Empresa`...). `LaneResponse` solo
     manda `poolId`, `rolProcesoId`, `rolProcesoNombre`.
  3. Estabilidad del contrato: puedo cambiar la BD sin romper el frontend.
  4. Evitar **mass assignment**: el cliente no puede mandar `empresaId`, `activo` o `estado` en el cuerpo de
     creación porque el record no tiene esos campos. `AislamientoTenantTest.requests_sin_empresa` **prohíbe**
     que cualquier `*Request` tenga un campo `empresa`/`empresaId`.
- **Response con static factory:** `ProcesoResponse.of(Proceso p)` convierte entidad → DTO. Es un "mapper"
  manual (no se usa MapStruct).

### Controllers — anatomía con `ProcesoController`

```java
@RestController                          // (1) cada método devuelve datos (JSON), no vistas
@RequestMapping("/api/v1/procesos")      // (2) prefijo común + versionado
@RequiredArgsConstructor
public class ProcesoController {
    private static final int TAMANO_PAGINA = 10;
    private final ProcesoService procesoService;
    private final HistorialCambioService historialCambioService;

    @PostMapping                                                  // (3) POST /api/v1/procesos
    public ResponseEntity<ProcesoResponse> crear(
            @Validated @RequestBody ProcesoRequest request,       // (4) JSON → record + validación
            @AuthenticationPrincipal ApiPrincipal principal) {    // (5) identidad desde el token
        Long empresaId = principal.empresaId();
        Long usuarioId = principal.usuarioId();
        Proceso proceso = procesoService.crear(empresaId, usuarioId, request.nombre(), request.descripcion(),
                request.categoria());                             // (6) delega TODO al service
        return ResponseEntity.created(URI.create("/api/v1/procesos/" + proceso.getId()))  // (7) 201 + Location
                .body(ProcesoResponse.of(proceso));               // (8) entidad → DTO
    }
```
1. `@RestController` = `@Controller` + `@ResponseBody`.
2. Versionado en la URL (`/v1`) → en el futuro puede existir `/v2` sin romper clientes.
3. Verbo + ruta = "endpoint".
4. `@RequestBody`: Jackson deserializa el JSON. `@Validated`: si falla una regla → 400 antes de entrar.
5. `@AuthenticationPrincipal`: Spring saca el `principal` del `SecurityContext` que llenó el filtro JWT.
6. El controller **no tiene lógica de negocio** (ArchUnit: "Los controllers no acceden directamente a repositorios").
7. REST semántico: **201 Created** + header `Location` con la URL del nuevo recurso.
8. Nunca sale una entidad.

Otros detalles de `ProcesoController`:
- `listar`: parámetros opcionales `@RequestParam(required = false)` (`?nombre=&estado=&categoria=`),
  `pagina` con `@Min(0)`, tamaño fijo 10, orden `fechaModificacion DESC`, y respuesta `PageResponse`.
- `detalle`: devuelve `ProcesoDetalleResponse(proceso, historial)`: DTO compuesto.
- `PUT /{id}`: reemplaza datos (nombre, descripción, categoría). `PATCH /{id}`: **cambio parcial** (solo `estado`).
- `GET /{id}/historial`: primero llama `procesoService.obtener` (valida pertenencia → 404 si es de otra empresa)
  y luego lista.
- `DELETE /{id}`: **204 No Content**.

### `PageResponse.java`
```java
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    public static <T> PageResponse<T> from(Page<T> pagina) { ... }
}
```
No se expone el `Page` de Spring (su JSON es enorme e inestable entre versiones); se publica un contrato propio
(README §14).

### Diseño de rutas REST en `modelado` (recursos anidados)
Los recursos que **se crean dentro de un padre** usan ruta anidada; los que se consultan/editan por id usan ruta plana:
```
POST /api/v1/procesos/{procesoId}/pools     crear pool en un proceso
GET  /api/v1/procesos/{procesoId}/pools     listar pools de un proceso
GET  /api/v1/pools/{id}                     detalle
PUT  /api/v1/pools/{id}                     editar
DELETE /api/v1/pools/{id}                   eliminar
```
Mismo patrón para lanes (`/pools/{poolId}/lanes`), actividades y gateways (`/lanes/{laneId}/...`), arcos
(`/pools/{poolId}/arcos`, pero `POST /arcos` plano porque se define por `origenId`/`destinoId`), mensajes
(`/procesos/{procesoId}/mensajes`) y correlación (`/mensajes/{mensajeId}/correlacion` con PUT/GET: recurso
singular, "upsert").

### `AuthController` y `EmpresaController`
- `POST /api/v1/empresas` (público): registro HU-01 → 201 + `EmpresaResponse`.
- `POST /api/v1/auth/login` (público):
  ```java
  try {
      usuario = usuarioService.autenticar(request.email(), request.password());
  } catch (ReglaNegocioException e) {
      throw new BadCredentialsException(e.getMessage());   // convierte 409 → 401
  }
  String token = jwtService.generarToken(ApiPrincipal.of(usuario));
  return ResponseEntity.ok(new LoginResponse(token, "Bearer", jwtService.getExpirationSeconds(),
          UsuarioResponse.of(usuario)));
  ```
  Traducción de excepción: un error de negocio (409) no es semánticamente correcto para "credenciales malas";
  lo correcto es **401 Unauthorized**. `BadCredentialsException` es una `AuthenticationException` y el
  `ApiExceptionHandler` la mapea a 401.
- `POST /api/v1/auth/logout` → 204. *"Sin estado en el servidor: cerrar sesión es que el cliente descarte su
  token."* (el servidor no guarda sesiones).

---

## NIVEL 6 — Manejo centralizado de errores

### `common/api/ApiExceptionHandler.java`
- `@RestControllerAdvice`: un "interceptor" global de excepciones lanzadas por **cualquier** controller.
- Extiende `ResponseEntityExceptionHandler` (clase base de Spring) y sobrescribe los errores estándar de MVC.
- Devuelve **`ProblemDetail`** (estándar **RFC 9457**, "Problem Details for HTTP APIs"): JSON con `type`,
  `title`, `status`, `detail`, `instance`, content-type `application/problem+json`.

| Excepción | Quién la lanza | HTTP | `title` |
|---|---|---|---|
| `MethodArgumentNotValidException` | `@Validated` en un `@RequestBody` | 400 | "Validación fallida" (detalle: `campo: mensaje; campo2: mensaje`) |
| `HandlerMethodValidationException` | `@Min` en `@RequestParam` | 400 | "Parámetro inválido" |
| `TypeMismatchException` | `/procesos/abc` (id no numérico) o `estado=OTRO` | 400 | "Parámetro inválido" |
| `HttpMessageNotReadableException` | JSON mal formado | 400 | "JSON inválido" |
| `AuthenticationException` | login fallido | 401 | "No autenticado" |
| `AccessDeniedException` | (si llegara al controller) | 403 | "Sin permisos" |
| `RecursoNoEncontradoException` | services (`orElseThrow`) | 404 | "Recurso no encontrado" |
| `ReglaNegocioException` | services (reglas) | 409 Conflict | "Regla de negocio violada" |
| `Exception` (cualquier otra) | bugs | 500 | "Error interno" — **se loguea** el stack trace pero **no se le muestra** al cliente |

Ejemplo real de respuesta (lo obtuve al ejecutar el bug B-1):
```json
{"detail":"Ocurrió un error inesperado. Intenta nuevamente más tarde.",
 "instance":"/api/v1/auth/login","status":500,"title":"Error interno"}
```

Por qué 404 y no 403 cuando el recurso es de otra empresa: *"o pertenece a otra empresa, lo cual para efectos de
aislamiento es indistinguible de 'no existe'"* (Javadoc de `RecursoNoEncontradoException`). Responder 403
confirmaría que el id existe.

---

## NIVEL 7 — Seguridad (lo más difícil; estúdialo con calma)

### Conceptos
- **Autenticación** = ¿quién eres? (login → token). **Autorización** = ¿qué puedes hacer? (rol).
- **JWT (JSON Web Token)** = `header.payload.firma` en Base64URL. El payload lleva *claims* (datos). La firma
  (HMAC-SHA256 con una clave secreta) impide modificarlo: si alguien cambia `empresaId` de 1 a 2, la firma no
  coincide y el token se rechaza (test: `JwtServiceTest` "Cambiar la empresa dentro del token invalida la firma").
  **El payload NO está cifrado**, solo firmado: cualquiera puede leerlo (no poner secretos ahí).
- **Stateless** = el servidor no guarda sesión; cada petición trae el token. Escala horizontalmente (varias
  instancias sin compartir memoria).
- **Bearer** = "el portador de este token tiene acceso": header `Authorization: Bearer eyJhbGciOi...`.

### `security/JwtService.java`
```java
public JwtService(@Value("${jwt.secret}") String secret,
        @Value("${jwt.expiration-seconds}") long expirationSeconds) {
    if (secret.isBlank()) {
        log.warn("JWT_SECRET no definido: se usa una clave aleatoria y los tokens se invalidan al reiniciar.");
        this.key = Jwts.SIG.HS256.key().build();                         // clave aleatoria (dev)
    } else {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); // clave fija (prod)
    }
    this.expirationSeconds = expirationSeconds;
}

public String generarToken(ApiPrincipal principal) {
    Date ahora = new Date();
    return Jwts.builder()
            .subject(principal.email())                        // "sub"
            .claim("usuarioId", principal.usuarioId())
            .claim("empresaId", principal.empresaId())
            .claim("rol", principal.rol().name())
            .issuedAt(ahora)                                   // "iat"
            .expiration(new Date(ahora.getTime() + expirationSeconds * 1000))   // "exp" (30 min)
            .signWith(key)                                     // firma HS256
            .compact();                                        // → "xxxxx.yyyyy.zzzzz"
}

public Optional<Claims> validar(String token) {
    try {
        return Optional.of(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload());
    } catch (JwtException | IllegalArgumentException e) {      // firma mala, expirado, basura
        return Optional.empty();
    }
}
```
`validar` nunca lanza excepción: devuelve `Optional` vacío. Así el filtro decide sin try/catch.

### `security/ApiPrincipal.java`
```java
/** Identidad del usuario autenticado. El tenant sale de aqui, nunca del request. */
public record ApiPrincipal(Long usuarioId, Long empresaId, RolAcceso rol, String email) {
    public static ApiPrincipal of(Usuario usuario) { ... }
    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority(rol.name()));   // "ADMINISTRADOR", "EDITOR"...
    }
}
```
Es el objeto que recibe cada controller con `@AuthenticationPrincipal`. `authorities()` traduce el rol al formato
que entiende Spring Security.

### `security/JwtAuthenticationFilter.java` — línea por línea
```java
public class JwtAuthenticationFilter extends OncePerRequestFilter {      // se ejecuta 1 vez por petición
    private static final String PREFIJO = "Bearer ";
    ...
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);        // (1)
        if (header != null && header.startsWith(PREFIJO)) {                  // (2)
            jwtService.validar(header.substring(PREFIJO.length()))           // (3) Optional<Claims>
                    .flatMap(this::usuarioVigente)                           // (4) Optional<ApiPrincipal>
                    .ifPresent(principal -> autenticar(principal, request)); // (5)
        }
        filterChain.doFilter(request, response);                             // (6) SIEMPRE continúa
    }

    private Optional<ApiPrincipal> usuarioVigente(Claims claims) {
        Long empresaId = claims.get(JwtService.CLAIM_EMPRESA_ID, Long.class);
        Long usuarioId = claims.get(JwtService.CLAIM_USUARIO_ID, Long.class);
        if (empresaId == null || usuarioId == null) return Optional.empty();
        try {
            Usuario usuario = usuarioService.obtener(empresaId, usuarioId);  // (7) consulta a BD
            return usuario.isActivo() ? Optional.of(ApiPrincipal.of(usuario)) : Optional.empty();
        } catch (RecursoNoEncontradoException e) {
            return Optional.empty();
        }
    }

    private void autenticar(ApiPrincipal principal, HttpServletRequest request) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()); // (8)
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);                     // (9)
    }
}
```
1. Lee `Authorization`.
2. Solo actúa si empieza por `"Bearer "`.
3. Quita el prefijo y valida firma + expiración.
4. `flatMap`: si el token es válido, verifica que el usuario **siga existiendo y activo**.
5. Si todo está bien, autentica.
6. **Nunca bloquea** por sí mismo: si no autenticó, la petición sigue "anónima" y es `SecurityConfig` quien
   decide si eso es aceptable (login/registro sí; lo demás → 401). Esto es **separación de responsabilidades**.
7. **Decisión de diseño importante:** consulta la BD en cada petición. Ventaja: si el admin **desactiva** a un
   usuario, su token deja de servir **inmediatamente** (test: "El token de un usuario desactivado deja de servir:
   401"), y el rol usado es el **actual** de la BD, no el del token. Costo: 1 consulta por petición.
8. Crea el objeto `Authentication` con el principal y sus permisos (password `null`: ya se verificó con el token).
9. Lo guarda en el `SecurityContextHolder` (almacenamiento por hilo, *ThreadLocal*) → de ahí lo toman las reglas
   de autorización y `@AuthenticationPrincipal`.

Nótese: no tiene `@Component`. *"Sin @Bean a proposito: como bean, Spring Boot tambien lo registraria como filtro
del servlet"* (se ejecutaría dos veces: una fuera y otra dentro de la cadena de Security).

### `security/SecurityConfig.java` — la matriz de permisos
```java
return http
    .csrf(AbstractHttpConfigurer::disable)                    // (a)
    .headers(headers -> headers.frameOptions(frame -> frame.disable()))   // (b)
    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))  // (c)
    .authorizeHttpRequests(requests -> requests               // (d) SE EVALÚAN EN ORDEN: gana la PRIMERA que coincide
        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
        .requestMatchers(HttpMethod.POST, "/api/v1/empresas").permitAll()
        .requestMatchers("/h2-console/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/error").permitAll()
        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()
        .requestMatchers("/api/v1/usuarios/**").hasAuthority(ADMINISTRADOR)        // incluso GET
        .requestMatchers(HttpMethod.GET, "/api/v1/**").authenticated()             // cualquier rol consulta
        .requestMatchers("/api/v1/roles/**").hasAuthority(ADMINISTRADOR)           // POST/PUT/DELETE roles
        .requestMatchers(HttpMethod.DELETE, "/api/v1/procesos/**", "/api/v1/actividades/**",
                "/api/v1/arcos/**", "/api/v1/gateways/**", "/api/v1/pools/**",
                "/api/v1/lanes/**", "/api/v1/mensajes/**").hasAuthority(ADMINISTRADOR)
        .requestMatchers("/api/v1/**").hasAnyAuthority(ADMINISTRADOR, EDITOR)     // resto de escrituras
        .anyRequest().authenticated());
```
(a) **CSRF desactivado**: CSRF ataca aplicaciones que se autentican con **cookies** (el navegador las envía solo).
Aquí el token va en un header que el navegador **no** agrega automáticamente → CSRF no aplica.
(b) Permite iframes (lo necesita la consola H2).
(c) STATELESS: Spring no crea `HttpSession`.
(d) **El orden importa.** Ejemplo: `GET /api/v1/usuarios` coincide primero con la regla de usuarios → solo admin.
`GET /api/v1/roles` coincide primero con "GET /api/v1/**" → cualquier autenticado.

Matriz resultante:

| Operación | ADMINISTRADOR | EDITOR | SOLO_LECTURA | Anónimo |
|---|:-:|:-:|:-:|:-:|
| Registrar empresa / login | ✅ | ✅ | ✅ | ✅ |
| Cualquier GET (excepto usuarios) | ✅ | ✅ | ✅ | 401 |
| Todo `/usuarios/**` | ✅ | 403 | 403 | 401 |
| Crear/editar/eliminar roles de proceso | ✅ | 403 | 403 | 401 |
| Crear/editar procesos, pools, lanes, actividades, gateways, arcos, mensajes, correlación | ✅ | ✅ | 403 | 401 |
| DELETE de procesos y elementos del modelo | ✅ | 403 | 403 | 401 |

Test que valida la matriz con tokens reales: `security/AutorizacionPorRolTest.java` (parametrizado con
`@CsvSource`).

`reglasComunes` es `static` y **se reutiliza** en `SeguridadControllersTestAutoConfiguration` (tests) → los tests
de controllers usan **exactamente** las mismas reglas que producción (evita duplicar la matriz).

`passwordEncoder()` expone `BCryptPasswordEncoder` como bean (usado por `UsuarioService`, `EmpresaService`,
`DatosDemoInitializer`).

### `JwtAuthEntryPoint` (401) y `JwtAccessDeniedHandler` (403)
Cuando Spring Security rechaza **antes** de llegar al controller, el `@RestControllerAdvice` no participa (no
estamos en MVC todavía). Estas dos clases escriben a mano un `ProblemDetail` JSON en la respuesta con
`JsonMapper` (Jackson 3) → **mismo formato de error en toda la API**.

### `CorsConfig.java`
**CORS** (*Cross-Origin Resource Sharing*): el navegador bloquea que una página en `http://localhost:4200`
(Angular) llame a `http://localhost:8080` (otro origen = otro puerto) **a menos que** el servidor responda con
cabeceras `Access-Control-Allow-Origin`. Antes de peticiones "no simples" (con `Authorization` o JSON) el
navegador manda un **preflight** `OPTIONS`.
- Orígenes: `cors.allowed-origins` (configurable por entorno).
- Headers permitidos: `Authorization`, `Content-Type`, `Accept`. Métodos: GET, POST, PUT, PATCH, DELETE.
- `bean.setOrder(-102)`: *"Antes que la cadena de Spring Security (orden -100): si no, el preflight llega sin
  cabeceras CORS."* (el preflight no trae token y Security lo rechazaría con 401).
- Postman/curl **no** aplican CORS (es una protección del navegador).

---

## NIVEL 8 — Tests, CI/CD y Docker

### Pirámide de tests del proyecto (resultado real: **221 tests, 0 fallos**, cobertura **87,2 % líneas**, 59 % ramas)

| Tipo | Herramienta | Archivos | Qué prueba |
|---|---|---|---|
| Unitarios de service | JUnit 5 + **Mockito** (`@Mock`, `@InjectMocks`) | `gestion/service/*Test` | reglas de negocio aisladas, sin BD |
| Slice de controller | `@WebMvcTest` + **MockMvc** + `@MockitoBean` | `gestion/controller/*Test`, `modelado/controller/*Test` | códigos HTTP, JSON, validaciones, `Location`, 401/403 |
| Integración | `@SpringBootTest` + `@AutoConfigureMockMvc` + H2 en memoria | `security/SeguridadIntegracionTest`, `AutorizacionPorRolTest`, `AislamientoEmpresasIntegracionTest` | la app completa: login real, tokens reales, empresa A no ve datos de B |
| Unitario de JWT | JUnit | `security/JwtServiceTest` | firma, expiración, clave distinta, basura |
| Arquitectura | **ArchUnit** | `arquitectura/*` (20 reglas) | capas, paquetes, multi-tenant, sin HttpSession, enums STRING |
| Humo | `@SpringBootTest` | `ProcesosApplicationTests` | el contexto arranca |

Piezas de apoyo:
- `ApiPrincipalRequestPostProcessor.principal(RolAcceso.EDITOR)`: en tests de controller simula un usuario
  autenticado sin generar token real.
- `SeguridadControllersTestAutoConfiguration` + archivo
  `src/test/resources/META-INF/spring/...AutoConfigureMockMvc.imports`: registra esa configuración de seguridad
  en los `@WebMvcTest` (donde `SecurityConfig` no se carga) para que los tests también verifiquen 401/403.

Ejemplo de regla ArchUnit (`AislamientoTenantTest`):
```java
noFields()
    .that().areDeclaredInClassesThat().haveSimpleNameEndingWith("Request")
    .should().haveNameMatching("empresa(Id)?")
    .because("README §10: la empresa sale del ApiPrincipal, nunca del request")
    .check(clases);
```
Las decisiones de arquitectura **no son un documento que se puede olvidar**: si alguien las rompe, **el build falla**.

### `.github/workflows/ci.yml`
```
push (main, develop, entrega-1-backend) / PR (main, develop)
  ├─ build-and-test  [ubuntu + windows en paralelo]  mvn clean verify → tests + JaCoCo check
  │     └─ publica reporte de tests y el reporte JaCoCo como artefacto
  ├─ architecture-guard  (needs build-and-test)  solo tests de com.facimus.procesos.arquitectura.**
  ├─ docker-build  (needs los dos anteriores)  docker build + arrancar el contenedor 10 s y ver logs
  └─ sonarqube  (needs build-and-test, solo en push)  mvn verify sonar:sonar → SonarCloud
```

### `Dockerfile` (multi-stage)
```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build     # etapa 1: imagen grande con Maven + JDK
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B               # descarga dependencias → capa cacheada (si pom no cambia)
COPY src/ src/
RUN mvn -B clean package && cp target/*.jar app.jar   # compila y corre tests

FROM eclipse-temurin:21-jre                    # etapa 2: solo JRE (más pequeña, menos superficie de ataque)
RUN useradd -r -u 1001 appuser
USER appuser                                   # no corre como root (seguridad)
WORKDIR /app
COPY --from=build --chown=appuser:appuser /build/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```
`.dockerignore` evita copiar `.git`, `target/`, `data/`, etc. al contexto de build.

### Postman y JMeter
- Postman: colección con variable `baseUrl`, y scripts que **guardan el token** del login en `{{token}}`; la
  colección usa auth Bearer automáticamente.
- JMeter: planes de carga `plan-login.jmx` (registro → login → logout con aserciones 201/200/204),
  `plan-procesos-crud.jmx`, `plan-roles-crud.jmx` (usuarios concurrentes).

---

# FASE 3 — Flujo completo de acciones reales del usuario

Recorrido de punta a punta:
**el usuario registra su empresa → inicia sesión → consulta sus procesos → crea un proceso → se guarda en la BD →
el frontend actualiza la interfaz.** Los pasos del cliente (Angular) se marcan como *[cliente]* porque ese
código no está en este repositorio.

## 3.1 Registro de empresa (HU-01)

```
[cliente] Formulario "Registrar empresa"
   │  POST /api/v1/empresas
   │  { "nombreEmpresa":"Acme", "nit":"900-1", "correoContacto":"c@acme.com",
   │    "nombreAdmin":"Ana", "emailAdmin":"ana@acme.com", "passwordAdmin":"secreta1" }
   ▼
CorsFilter                                  security/CorsConfig.java
   ▼
JwtAuthenticationFilter                     (sin header Authorization → no hace nada)
   ▼
AuthorizationFilter                         SecurityConfig: POST /api/v1/empresas → permitAll ✅
   ▼
DispatcherServlet → EmpresaController.registrar     gestion/controller/EmpresaController.java
   │  Jackson → RegistroEmpresaRequest; @Validated: @NotBlank, @Email, @Size(min=6)
   │  (si falla → ApiExceptionHandler.handleMethodArgumentNotValid → 400)
   ▼
EmpresaService.registrar  (@Transactional)          gestion/service/EmpresaService.java
   │  existsByNit? → sí: ReglaNegocioException → 409
   │  empresaRepository.save(empresa)                → INSERT INTO empresas
   │  passwordEncoder.encode("secreta1")             → "$2a$10$..." (BCrypt)
   │  usuarioRepository.save(admin ADMINISTRADOR)    → INSERT INTO usuarios
   │  COMMIT
   ▼
EmpresaController → 201 Created, Location: /api/v1/empresas/7, body EmpresaResponse
```

## 3.2 Inicio de sesión (HU-03)

```
[cliente] Formulario login
   │  POST /api/v1/auth/login  {"email":"ana@acme.com","password":"secreta1"}
   ▼
CorsFilter → JwtAuthenticationFilter (nada) → AuthorizationFilter (permitAll)
   ▼
AuthController.login                         gestion/controller/AuthController.java
   │  @Validated LoginRequest (@NotBlank)
   ▼
UsuarioService.autenticar                    gestion/service/UsuarioService.java
   │  usuarioRepository.findByEmail(...)     SELECT * FROM usuarios WHERE email=?
   │  .filter(activo)  +  passwordEncoder.matches(clave, hash)
   │  ✗ → ReglaNegocioException → AuthController la convierte en BadCredentialsException
   │      → ApiExceptionHandler → 401 ProblemDetail
   ▼
ApiPrincipal.of(usuario)                     security/ApiPrincipal.java
   ▼
JwtService.generarToken                      security/JwtService.java
   │  claims: sub=email, usuarioId, empresaId, rol, iat, exp(+1800 s); firma HS256
   ▼
200 OK
{ "accessToken":"eyJhbGciOiJIUzI1NiJ9...", "tokenType":"Bearer", "expiresIn":1800,
  "usuario":{"id":3,"nombre":"Ana","email":"ana@acme.com","rolAcceso":"ADMINISTRADOR","activo":true} }
   ▼
[cliente] guarda el token (memoria/sessionStorage) y el usuario (para mostrar menú según rol);
          redirige al "dashboard"
```

## 3.3 Entrar al dashboard: listar procesos (HU-07)

```
[cliente] GET /api/v1/procesos?nombre=comp&estado=BORRADOR&pagina=0
          Authorization: Bearer eyJ...
   ▼
JwtAuthenticationFilter
   │ JwtService.validar(token) → Claims (firma OK, no expirado)
   │ UsuarioService.obtener(empresaId, usuarioId) → SELECT usuario (¿sigue activo?)
   │ SecurityContextHolder ← Authentication(ApiPrincipal, [ADMINISTRADOR])
   ▼
AuthorizationFilter: GET /api/v1/** → authenticated ✅
   ▼
ProcesoController.listar
   │ empresaId = principal.empresaId()   ← del TOKEN, no de la URL
   │ PageRequest.of(0, 10, Sort.by("fechaModificacion").descending())
   ▼
ProcesoService.buscar → ProcesoSpecifications.conFiltros(empresaId, "comp", BORRADOR, null)
   ▼
ProcesoRepository.findAll(spec, pageable)
   │ SELECT ... FROM procesos WHERE empresa_id=? AND activo=true
   │   AND lower(nombre) LIKE '%comp%' AND estado='BORRADOR'
   │   ORDER BY fecha_modificacion DESC LIMIT 10 OFFSET 0
   │ + SELECT count(*) ... (para totalElements)
   ▼
Page<Proceso> → .map(ProcesoResponse::of) → PageResponse.from(...)
   ▼
200 {"content":[...],"page":0,"size":10,"totalElements":1,"totalPages":1}
   ▼
[cliente] pinta la tabla y los botones de paginación
```

## 3.4 Crear un registro: nuevo proceso (HU-04) — el flujo principal para exponer

```
App (cliente)
 ↓  [cliente] botón "Nuevo proceso" → formulario → POST /api/v1/procesos
 ↓           {"nombre":"Compras","descripcion":"Proceso de compras","categoria":"Operativo"}
 ↓           Authorization: Bearer eyJ...
Tomcat (puerto 8080)
 ↓
CorsFilter .................................. security/CorsConfig.java
 ↓
JwtAuthenticationFilter ..................... security/JwtAuthenticationFilter.java
 ↓   └─ JwtService.validar ................. security/JwtService.java
 ↓   └─ UsuarioService.obtener ............. gestion/service/UsuarioService.java → UsuarioRepository
 ↓   └─ ApiPrincipal en SecurityContext .... security/ApiPrincipal.java
AuthorizationFilter ......................... security/SecurityConfig.java
 ↓   POST /api/v1/** → hasAnyAuthority(ADMINISTRADOR, EDITOR)   (SOLO_LECTURA → 403 JwtAccessDeniedHandler)
DispatcherServlet → "router" de Spring MVC (HandlerMapping: @PostMapping en /api/v1/procesos)
 ↓
ProcesoController.crear ..................... gestion/controller/ProcesoController.java
 ↓   Jackson: JSON → ProcesoRequest ........ gestion/controller/dto/ProcesoRequest.java
 ↓   @Validated: @NotBlank x3 (→ 400 si falla)
 ↓   @AuthenticationPrincipal → empresaId=1, usuarioId=3
ProcesoService.crear  [BEGIN TRANSACTION] ... gestion/service/ProcesoService.java
 ↓   ProcesoRepository.existsBy...NombreIgnoreCase...ActivoTrue  → SELECT (¿duplicado? → 409)
 ↓   EmpresaRepository.findById(1)                               → SELECT empresas
 ↓   UsuarioRepository.findByIdAndEmpresaId(3,1)                 → SELECT usuarios
 ↓   ProcesoRepository.save(proceso)                             → INSERT INTO procesos
 ↓   PoolRepository.save(poolInicial)                            → INSERT INTO pools
 ↓   HistorialCambioService.registrar                            → INSERT INTO historial_cambios
 ↓  [COMMIT]
Base de datos (H2 en dev / PostgreSQL en prod)
 ↑
ProcesoController: ProcesoResponse.of(proceso)
 ↑   ResponseEntity.created(URI "/api/v1/procesos/42")
Jackson: record → JSON
 ↑
HTTP 201 Created
Location: /api/v1/procesos/42
{"id":42,"nombre":"Compras","descripcion":"Proceso de compras","categoria":"Operativo",
 "estado":"BORRADOR","activo":true,"fechaCreacion":"2026-09-28T12:00:00","fechaModificacion":"..."}
 ↑
[cliente] agrega el proceso a la lista (o vuelve a pedir GET /api/v1/procesos) y navega a
          /procesos/42 para modelarlo → GET /api/v1/procesos/42/pools devuelve el pool inicial
```

## 3.5 Modelar: crear lane, actividades y un arco

```
1. GET  /api/v1/roles                              → elegir rol "Analista" (id 5) [crearlo antes si no existe: POST /roles, solo admin]
2. POST /api/v1/pools/{poolId}/lanes               {"nombre":"Compras","rolProcesoId":5}
      LaneController → LaneService.crear: pool y rol de mi empresa; orden = nº de lanes
3. POST /api/v1/lanes/{laneId}/actividades         {"nombre":"Solicitar","posicionX":100,"posicionY":50}
   POST /api/v1/lanes/{laneId}/gateways            {"nombre":"¿Aprobado?","tipoGateway":"EXCLUSIVO",...}
      ActividadService/GatewayService: nombre único en el proceso
4. POST /api/v1/arcos                              {"origenId":10,"destinoId":11,"etiqueta":"sí","condicion":"monto<1000"}
      ArcoService.crear: origen≠destino, mismo pool, no duplicado, condición obligatoria si destino es gateway EXCLUSIVO/INCLUSIVO
5. PATCH /api/v1/procesos/42                       {"estado":"PUBLICADO"}   → historial "Proceso publicado."
```

## 3.6 Flujos de error (tan importantes como el feliz)

**Usuario de la empresa B intenta ver el proceso 42 de la empresa A (IDOR):**
```
GET /api/v1/procesos/42 (token de B → empresaId=2)
→ ProcesoService.obtener(2, 42) → findByIdAndEmpresaIdAndActivoTrue(42, 2) → vacío
→ RecursoNoEncontradoException → ApiExceptionHandler → 404 "Proceso no encontrado."
```
(Probado en `AislamientoEmpresasIntegracionTest`.)

**Intento de engañar enviando `empresaId` en el cuerpo:** Jackson ignora el campo porque `ProcesoRequest` no lo
tiene; la empresa sale del token (test "Un empresaId de otra empresa en el cuerpo o en la URL se ignora: manda el token").

**Token vencido:** `JwtService.validar` → vacío → no se autentica → la regla exige autenticación →
`JwtAuthEntryPoint` → 401 → *[cliente]* redirige al login.

**SOLO_LECTURA hace POST:** autenticado pero sin autoridad → `JwtAccessDeniedHandler` → 403.

---

# FASE 4 — Desarrollo web desde cero, con este repositorio como ejemplo

## 4.1 Fundamentos que debes dominar

**Cliente-servidor.** El navegador (cliente) pide, el servidor responde. Aquí el servidor es Spring Boot/Tomcat en
el puerto 8080 y el cliente es una SPA Angular (u otro).

**HTTP.** Petición = método + URL + headers + cuerpo. Respuesta = código de estado + headers + cuerpo.

| Método | Semántica | Idempotente | Ejemplo real |
|---|---|:-:|---|
| GET | leer | sí | `GET /api/v1/procesos/42` |
| POST | crear | no | `POST /api/v1/procesos` → 201 + Location |
| PUT | reemplazar / definir | sí | `PUT /api/v1/procesos/42`, `PUT /api/v1/mensajes/9/correlacion` (upsert) |
| PATCH | modificar parcialmente | (depende) | `PATCH /api/v1/procesos/42 {"estado":"PUBLICADO"}`, `PATCH /api/v1/usuarios/3 {"activo":false}` |
| DELETE | eliminar | sí | `DELETE /api/v1/procesos/42` → 204 |

| Código | Significado | Dónde se produce aquí |
|---|---|---|
| 200 OK | éxito con cuerpo | GET, PUT, PATCH, login |
| 201 Created | recurso creado | todos los POST de creación (`ResponseEntity.created(...)`) |
| 204 No Content | éxito sin cuerpo | DELETE, logout |
| 400 Bad Request | datos inválidos | `ApiExceptionHandler` (validación, JSON, tipos) |
| 401 Unauthorized | no autenticado | `JwtAuthEntryPoint`, login fallido |
| 403 Forbidden | autenticado sin permiso | `JwtAccessDeniedHandler` |
| 404 Not Found | no existe (o es de otra empresa) | `RecursoNoEncontradoException` |
| 409 Conflict | viola regla de negocio | `ReglaNegocioException` |
| 500 Internal Server Error | bug | handler genérico `Exception` |

**REST** (*Representational State Transfer*): estilo de API donde todo es un **recurso** con URL (`/procesos/42`),
se opera con los verbos HTTP, **sin estado** entre peticiones, y la representación es JSON. Este proyecto lo
aplica con rigor: sustantivos en plural, verbos HTTP correctos, 201+Location, 204, versionado `/api/v1`,
errores RFC 9457, paginación propia.

**JSON.** Formato de texto de los datos. **Jackson** convierte JSON ⇄ objetos Java (serializar/deserializar).

## 4.2 Frontend (lo que el backend exige al cliente)

> **Aclaración honesta para la sustentación:** este repositorio no contiene frontend, componentes, props, hooks,
> ni estilos. Lo que sigue explica cada concepto y **cómo se conecta con el contrato real** de esta API. Si tu
> equipo tiene el repositorio del frontend, ese es el que se debe estudiar para ejemplos de componentes.

| Concepto frontend | Qué es | Cómo se relaciona con este backend |
|---|---|---|
| Estructura del proyecto | SPA (Single Page Application): una página que cambia su contenido con JS | Angular en `localhost:4200` (por eso el default de CORS). El backend solo entrega JSON |
| Componentes | Piezas reutilizables de UI (lista de procesos, formulario, lienzo BPMN) | Cada pantalla corresponde a un grupo de endpoints (ej. pantalla "Procesos" ↔ `ProcesoController`) |
| Estado | Datos que la UI recuerda (usuario logueado, token, lista actual) | El backend es **stateless**: el estado de sesión vive en el cliente (el token) |
| Props / inputs | Datos que un componente padre pasa a un hijo | Ej.: el lienzo pasa `laneId` a un "nodo" para que haga `POST /lanes/{laneId}/actividades` |
| Navegación (router) | Cambiar de vista sin recargar | Rutas del cliente (ej. `/procesos/42`) que llaman `GET /api/v1/procesos/42` |
| Hooks / ciclo de vida | Código que corre al montar un componente (React `useEffect`, Angular `ngOnInit`) | Al abrir "Procesos" se llama `GET /api/v1/procesos` |
| Renderizado | Dibujar el HTML a partir del estado | Server-side (Thymeleaf, Entrega 1) → **Client-side** (Entrega 2). Ver pregunta B-6 |
| Formularios | Capturar y validar entradas | Deben coincidir con los `*Request`. La validación del cliente es por UX; **la que manda es la del servidor** (`@NotBlank`, etc.) |
| Consumo de API | `fetch`/`HttpClient` | Enviar `Content-Type: application/json` y `Authorization: Bearer <token>` (idealmente con un *interceptor* HTTP que lo agregue a todas las peticiones) |
| Manejo de errores | Mostrar mensajes | Leer `ProblemDetail`: `title` y `detail`. Si 401 → borrar token e ir a login; si 403 → "sin permisos"; si 409 → mostrar `detail` ("Ya existe un proceso activo con el nombre...") |
| Paginación | Páginas de resultados | Usar `page`, `totalPages` de `PageResponse`; pedir `?pagina=n` |
| Estilos | CSS | No aplica al backend |
| Autorización en UI | Ocultar botones según rol | Usar `usuario.rolAcceso` del `LoginResponse`. **Ocultar un botón no es seguridad**: la seguridad real está en `SecurityConfig` |

Ejemplo de consumo (pseudocódigo de cliente que respeta el contrato):
```ts
const res = await fetch("http://localhost:8080/api/v1/procesos", {
  method: "POST",
  headers: { "Content-Type": "application/json", "Authorization": `Bearer ${token}` },
  body: JSON.stringify({ nombre, descripcion, categoria })
});
if (res.status === 201) { const proceso = await res.json(); /* agregar a la lista */ }
else { const problem = await res.json(); mostrarError(problem.detail); if (res.status === 401) irALogin(); }
```

## 4.3 Backend

| Concepto | Definición | Ejemplo real |
|---|---|---|
| Servidor | Proceso que escucha peticiones | Tomcat embebido, arrancado por `ProcesosApplication.main`, puerto `server.port=8080` |
| Rutas | URL + verbo → método | `@RequestMapping("/api/v1/procesos")` + `@GetMapping("/{id}")` en `ProcesoController` |
| Controladores | Reciben la petición, validan forma, delegan, arman respuesta | `ProcesoController.crear` |
| Servicios | Lógica de negocio + transacciones | `ProcesoService.crear` (nombre único, pool inicial, historial) |
| Modelos | Entidades mapeadas a tablas | `Proceso`, `NodoFlujo` |
| Repositorios | Acceso a datos | `ProcesoRepository extends RepositorioTenant<Proceso>, JpaSpecificationExecutor<Proceso>` |
| Middleware | Código que corre antes/después del controller para **todas** las peticiones | En Java se llaman **filtros**: `CorsFilter`, `JwtAuthenticationFilter`, filtros de Spring Security; y el `@RestControllerAdvice` como middleware de errores |
| Autenticación | Verificar identidad | Login con BCrypt → JWT; filtro que valida el JWT en cada petición |
| Autorización | Verificar permisos | `SecurityConfig.authorizeHttpRequests` (por rol) + filtro por `empresaId` en cada consulta (por tenant) |
| Validaciones | 3 niveles | (1) **Forma** en DTO: `@NotBlank`, `@Email`, `@Size`, `@NotNull`; (2) **Negocio** en service: nombre único, mismo pool, publicado→borrador prohibido; (3) **Integridad** en BD: `NOT NULL`, `UNIQUE(nit)`, `UNIQUE(empresa_id,email)`, FKs |
| Respuestas HTTP | `ResponseEntity` controla status, headers y body | `ResponseEntity.created(uri).body(dto)`, `ResponseEntity.noContent().build()`, `ResponseEntity.ok(dto)` |
| Inyección de dependencias | Spring crea los objetos y los pasa por constructor | `@RequiredArgsConstructor` + campos `private final` |

## 4.4 Base de datos

### Esquema (generado por Hibernate con `ddl-auto=update`)

```
empresas(id PK, nombre, nit UNIQUE, correo_contacto, fecha_registro)

usuarios(id PK, empresa_id FK→empresas, nombre, email, password_hash, rol_acceso, activo,
         UNIQUE(empresa_id, email))
procesos(id PK, empresa_id FK, nombre, descripcion LOB, categoria, estado, activo,
         fecha_creacion, fecha_modificacion)
historial_cambios(id PK, empresa_id FK, proceso_id FK→procesos, autor_id FK→usuarios,
                  fecha_cambio, descripcion_cambio)
roles_proceso(id PK, empresa_id FK, nombre, descripcion, activo)

pools(id PK, empresa_id FK, proceso_id FK→procesos, nombre, tipo_participante, caja_negra, orden)
lanes(id PK, empresa_id FK, pool_id FK→pools, rol_proceso_id FK→roles_proceso, nombre, orden)
nodos_flujo(id PK, empresa_id FK, lane_id FK→lanes, tipo_nodo /*ACTIVIDAD|GATEWAY*/,
            nombre, posicion_x, posicion_y, descripcion NULL, tipo_gateway NULL)
arcos(id PK, empresa_id FK, origen_id FK→nodos_flujo, destino_id FK→nodos_flujo,
      pool_id FK→pools, etiqueta, condicion)
mensajes(id PK, empresa_id FK, proceso_id FK→procesos, pool_origen_id FK→pools,
         pool_destino_id FK→pools, nombre, contenido LOB)
correlaciones(id PK, empresa_id FK, mensaje_id FK→mensajes UNIQUE, criterio)
```

### Relaciones (diagrama entidad-relación en ASCII)

```
                         ┌──────────┐
                         │ EMPRESA  │  (raíz del tenant; todas las tablas tienen empresa_id)
                         └────┬─────┘
          ┌───────────────┬───┴────────────┬──────────────────┐
          ▼ 1:N           ▼ 1:N            ▼ 1:N              ▼ 1:N
     ┌─────────┐    ┌──────────┐     ┌─────────────┐    (y todas las demás)
     │ USUARIO │    │ PROCESO  │     │ ROL_PROCESO │
     └────┬────┘    └──┬───┬───┘     └──────┬──────┘
          │ autor 1:N  │   │ 1:N            │ 1:N
          ▼            │   ▼                │
   ┌────────────────┐  │ ┌──────┐   1:N  ┌──▼───┐   1:N   ┌────────────┐
   │HISTORIAL_CAMBIO│◄─┘ │ POOL ├───────►│ LANE ├────────►│ NODO_FLUJO │ (Actividad | Gateway)
   └────────────────┘1:N └─┬──┬─┘        └──────┘         └──┬──────┬──┘
                           │  │ 1:N                    origen│      │destino
                           │  └──────────────►┌──────┐◄──────┘      │
                           │                  │ ARCO │◄─────────────┘
                           │ origen/destino   └──────┘
                           ▼
       PROCESO 1:N ──► ┌─────────┐ 1:0..1 ┌─────────────┐
                       │ MENSAJE ├───────►│ CORRELACION │
                       └─────────┘        └─────────────┘
```

### CRUD en este proyecto

| Operación | SQL | Cómo se hace aquí |
|---|---|---|
| Create | INSERT | `repository.save(nuevaEntidad)` (sin id → INSERT) |
| Read | SELECT | `findByIdAndEmpresaId`, derived queries, `findAll(spec, pageable)` |
| Update | UPDATE | cargar entidad → `setX(...)` → `repository.save(entidad)` (con id → UPDATE). Dentro de `@Transactional`, Hibernate detectaría el cambio igual (*dirty checking*); el `save` explícito lo deja claro |
| Delete | DELETE | físico en el modelo (`nodoFlujoRepository.delete`) — **lógico** (`activo=false`) en `Proceso`, `RolProceso`, `Usuario` |

### Cómo llegan los datos desde el frontend hasta la BD

```
Input del formulario (texto)
 → JSON en el body de la petición HTTP
 → Jackson → record ProcesoRequest (tipos Java: String, Long, enums)
 → Bean Validation (@NotBlank...)
 → Controller extrae campos + empresaId del token
 → Service crea/modifica la entidad Proceso (objeto Java)
 → Repository.save → Hibernate genera INSERT con parámetros (?) → JDBC → BD
 ← BD devuelve el id generado (IDENTITY) → Hibernate lo pone en proceso.id
 ← ProcesoResponse.of(proceso) → Jackson → JSON → HTTP 201
```

---

# FASE 5 — Dependencias y librerías

| Librería | ¿Para qué sirve? | ¿Dónde se usa? | Ejemplo en el proyecto |
|---|---|---|---|
| `spring-boot-starter-parent` | Versiones compatibles + configuración de plugins | `pom.xml` | `<version>4.1.0</version>` |
| `spring-boot-starter-webmvc` | Spring MVC + Tomcat embebido + Jackson | todos los controllers | `@RestController`, `@GetMapping`, `ResponseEntity` |
| `spring-boot-starter-data-jpa` | Spring Data JPA + Hibernate + pool de conexiones HikariCP | model, repository | `@Entity`, `JpaRepository`, `Specification` |
| `spring-boot-starter-validation` | Bean Validation (Hibernate Validator) | DTOs, controllers | `@NotBlank`, `@Email`, `@Validated`, `@Min` |
| `spring-boot-starter-security` | Filtros de seguridad, autorización, BCrypt | `security/` | `SecurityFilterChain`, `BCryptPasswordEncoder`, `@AuthenticationPrincipal` |
| `jjwt-api` (0.12.6) | API para crear/leer JWT | `JwtService` | `Jwts.builder()...signWith(key).compact()` |
| `jjwt-impl` (runtime) | Implementación interna de JJWT | (runtime) | — el código nunca la importa |
| `jjwt-jackson` (runtime) | JJWT serializa los claims con Jackson | (runtime) | — |
| `h2` (runtime) | BD embebida en Java (archivo o memoria) | dev y tests | `jdbc:h2:file:./data/procesos`, `jdbc:h2:mem:seguridad-it` |
| `postgresql` (runtime) | Driver JDBC de PostgreSQL | perfil prod | `application-prod.properties` |
| `springdoc-openapi-starter-webmvc-ui` (3.0.1) | Genera la especificación OpenAPI leyendo los controllers + Swagger UI | `OpenApiConfig`, `@SecurityRequirements` | `/swagger-ui.html`, `/v3/api-docs` |
| `spring-boot-devtools` (optional) | Reinicio automático al cambiar código en desarrollo | solo en local | — (se excluye del jar final) |
| `lombok` (optional) | Genera código repetitivo en compilación | entidades, services, controllers | `@Getter @Setter @NoArgsConstructor`, `@RequiredArgsConstructor` |
| `spring-boot-starter-webmvc-test` | JUnit 5, Mockito, AssertJ, MockMvc, `@WebMvcTest`, `@MockitoBean` | tests | `mockMvc.perform(post(...))...andExpect(status().isCreated())` |
| `spring-boot-starter-security-test` | Utilidades para simular autenticación | tests | `SecurityMockMvcRequestPostProcessors.authentication(...)` en `ApiPrincipalRequestPostProcessor` |
| `archunit-junit5` (1.4.0) | Tests que verifican la arquitectura | `arquitectura/` | `noClasses().that().resideInAPackage("..service..").should()...` |
| `jacoco-maven-plugin` (0.8.13) | Mide cobertura de tests | build | falla si cobertura de líneas < 50 % |
| `sonar-maven-plugin` | Envía análisis a SonarCloud (bugs, code smells, duplicación, cobertura) | CI | `mvn verify sonar:sonar` |
| `maven-compiler-plugin` | Compilar; aquí se registra Lombok como *annotation processor* | build | `annotationProcessorPaths` |
| `spring-boot-maven-plugin` | Empaqueta un **fat jar** ejecutable (`java -jar`) | build/Docker | excluye Lombok del jar |

### Librerías "no obvias", explicadas

- **Lombok:** no es magia en tiempo de ejecución. Es un *annotation processor*: durante `javac` inserta los métodos
  en el bytecode. `@RequiredArgsConstructor` crea `public ProcesoService(ProcesoRepository r, ...)` para los
  campos `final`. Por eso el IDE necesita el plugin de Lombok.
- **JJWT separada en api/impl/jackson:** el código compila **solo** contra la API. Si mañana cambian la
  implementación, el código no cambia. Es el principio de **depender de abstracciones**.
- **Jackson 3 (`tools.jackson.*`):** Spring Boot 4 migró de Jackson 2 (`com.fasterxml.jackson`) a Jackson 3,
  que cambió de paquete. Por eso `JwtAuthEntryPoint` importa `tools.jackson.databind.json.JsonMapper`.
- **HikariCP** (viene con data-jpa): mantiene un *pool* de conexiones abiertas a la BD para no abrir una por petición.
- **springdoc:** lee las anotaciones `@RestController`, `@RequestBody`, los records y las validaciones, y
  genera el documento OpenAPI 3 automáticamente. Es documentación **que no se desactualiza**.
- **ArchUnit:** analiza el **bytecode** compilado (no el texto) e importa las clases para verificar reglas de
  dependencia entre paquetes. Convierte las decisiones de arquitectura en tests.
- **H2 `AUTO_SERVER=TRUE`:** permite que un segundo proceso (ej. un cliente SQL) abra el mismo archivo de BD
  mientras la app corre.

---

# FASE 6 — Arquitectura completa

## 6.1 Diagrama de carpetas (por capas y módulos)

```
                     com.facimus.procesos
 ┌───────────────────────────────────────────────────────────────────┐
 │  security/   (transversal: quién eres y qué puedes)               │
 │  config/     (arranque: semilla, OpenAPI)                         │
 │  common/     (transversal: EntidadEmpresa, RepositorioTenant,     │
 │               excepciones, ApiExceptionHandler, PageResponse)     │
 ├───────────────────────────────┬───────────────────────────────────┤
 │  gestion/                     │  modelado/                        │
 │   controller/ + dto/          │   controller/ + dto/              │  ← capa HTTP
 │   service/    + dto/          │   service/                        │  ← capa negocio
 │   repository/                 │   repository/                     │  ← capa datos
 │   model/                      │   model/                          │  ← dominio
 └───────────────────────────────┴───────────────────────────────────┘
 Dependencias entre módulos (solo a nivel service/model):
   gestion.service.ProcesoService    ──usa──► modelado (Pool, PoolRepository)   (pool inicial)
   gestion.service.RolProcesoService ──usa──► modelado (LaneRepository)         (¿rol en uso?)
   modelado.model.Lane / Pool / Mensaje ──usa──► gestion.model (RolProceso, Proceso)
   modelado.service.* ──usa──► gestion.repository (ProcesoRepository, RolProcesoRepository)
```

Reglas de dependencia verificadas por ArchUnit (`EmpaquetadoTest`, `SeguridadArquitecturaTest`):
```
controller ──► service ──► repository ──► model
    ✗ controller ──► repository        ✗ service ──► controller
    ✗ repository ──► service/controller ✗ model ──► service/controller
    ✗ security ──► repository (pasa por UsuarioService)
    DTOs solo en controller.dto (excepto service.dto)
```

## 6.2 Diagrama de comunicación (despliegue)

```
┌───────────────────┐   HTTPS/HTTP + JSON    ┌──────────────────────────┐   JDBC (TCP 5432)   ┌──────────────┐
│  Navegador        │ ─────────────────────► │  procesos-back (Docker)  │ ──────────────────► │ PostgreSQL   │
│  SPA Angular      │ ◄───────────────────── │  Spring Boot :8080       │ ◄────────────────── │ (VM 1)       │
│  (VM 3, nginx:80) │  Authorization: Bearer │  (VM 2)                  │                     └──────────────┘
└───────────────────┘                        └──────────────────────────┘
     Postman / JMeter / Swagger UI también son clientes HTTP
```
(Topología planeada en `docs/guia-tecnica.md` §5.4.)

## 6.3 Flujo de datos (formas que toman los datos)

```
JSON (texto) ⇄ Record DTO (Request/Response) ⇄ Entidad JPA ⇄ Fila en tabla
      Jackson           Controller (of / campos)       Hibernate
```
Regla de oro: **las entidades nunca salen del backend; los DTOs nunca llegan a la BD.**

## 6.4 Ciclo de vida de una petición HTTP (detallado)

```
 1. Cliente arma la petición (método, URL, headers, body JSON)
 2. (Navegador, si es otro origen y no es "simple") → preflight OPTIONS → CorsFilter responde
 3. Tomcat recibe la conexión y asigna un hilo del pool de hilos
 4. CorsFilter (orden -102) agrega cabeceras Access-Control-*
 5. FilterChainProxy de Spring Security (orden -100):
      5a. JwtAuthenticationFilter: token → claims → usuario activo → SecurityContext
      5b. ExceptionTranslationFilter + AuthorizationFilter: aplica reglas de SecurityConfig
          → 401 (JwtAuthEntryPoint) / 403 (JwtAccessDeniedHandler) sin llegar a MVC
 6. DispatcherServlet: HandlerMapping encuentra el método del controller
 7. Argument resolvers: @PathVariable (conversión de tipo), @RequestParam, @RequestBody (Jackson),
    @AuthenticationPrincipal; validación (@Validated / @Min) → 400
 8. Controller → Service (proxy @Transactional abre transacción)
 9. Service → Repository (proxy Spring Data) → Hibernate → SQL parametrizado → JDBC (HikariCP) → BD
10. Commit (o rollback si hubo RuntimeException)
11. Controller mapea entidad → DTO y construye ResponseEntity (status + headers + body)
12. HttpMessageConverter (Jackson) serializa a JSON
13. Si en 7–11 hubo excepción → ApiExceptionHandler → ProblemDetail
14. La respuesta vuelve por los filtros; SecurityContext se limpia; el hilo vuelve al pool
```

## 6.5 De la interfaz a la BD (resumen para decir en 30 segundos)

> "El usuario llena un formulario en Angular; el cliente envía JSON por HTTP con su token JWT. El filtro JWT
> valida el token y carga la identidad —incluida la empresa—. Spring Security decide por rol si puede ejecutar
> la operación. El controller convierte el JSON en un DTO validado, toma la empresa **del token** y llama al
> service. El service aplica las reglas de negocio dentro de una transacción y usa repositorios que **siempre**
> filtran por empresa. Hibernate traduce a SQL parametrizado contra H2 o PostgreSQL. La entidad resultante se
> convierte a un DTO de respuesta y viaja como JSON con el código HTTP adecuado; si algo falla, un manejador
> global responde un ProblemDetail estándar."

---

# FASE 7 — Preguntas de sustentación con respuestas modelo (60 preguntas)

Consejo: responde siempre con **qué + por qué + dónde está en el código**. Nombrar el archivo da mucha credibilidad.

## Básicas

**B-1. ¿Qué hace este proyecto?**
Es el backend (API REST) de un sistema multiempresa para gestionar y modelar procesos de negocio con notación
BPMN. Permite registrar empresas, administrar usuarios con roles, crear procesos con ciclo de vida
BORRADOR→PUBLICADO y trazabilidad, y modelar el diagrama: pools, lanes, actividades, gateways, arcos, mensajes y
correlaciones. Cada empresa ve solo sus datos.

**B-2. ¿Por qué eligieron Spring Boot?**
Porque da, con autoconfiguración, todo lo que una API empresarial necesita: servidor embebido (Tomcat), acceso a
datos con JPA, validación, seguridad madura (Spring Security), transacciones declarativas y un ecosistema de
testing (MockMvc, `@SpringBootTest`). Además es estándar en la industria Java y encaja con el requisito de tipado
fuerte y arquitectura en capas.

**B-3. ¿Qué hace `ProcesosApplication`?**
Es el `main`. `@SpringBootApplication` activa el escaneo de componentes en `com.facimus.procesos.**`, la
autoconfiguración y la configuración. `SpringApplication.run` crea el contexto de Spring, conecta la BD, arma la
seguridad y levanta Tomcat en el puerto 8080.

**B-4. ¿Qué es una API REST y por qué la suya lo es?**
Una API donde los datos son recursos con URL, manipulados con verbos HTTP, sin estado en el servidor y
representados en JSON. La nuestra usa `/api/v1/procesos` (sustantivo, plural, versionado), POST→201+Location,
DELETE→204, PUT vs PATCH con su semántica, errores RFC 9457 y es stateless con JWT.

**B-5. ¿Qué es un DTO y por qué lo usan?**
Un objeto solo para transportar datos entre capas/sistemas. Usamos records `*Request` (entrada) y `*Response`
(salida). Evitan exponer entidades (por ejemplo `passwordHash` de `Usuario`), impiden que el cliente envíe campos
que no debe (como `empresaId` o `activo`) y desacoplan el contrato JSON del esquema de BD.

**B-6. ¿Por qué eliminaron Thymeleaf? ¿Diferencia entre renderizado en servidor y en cliente?**
Con Thymeleaf el servidor generaba HTML (server-side rendering) y dependía de `HttpSession`. En la Entrega 2 el
backend solo entrega JSON y un cliente SPA (Angular) renderiza en el navegador (client-side rendering). Así el
backend sirve a cualquier cliente (web, móvil, Postman), escala sin sesiones y los equipos de front/back trabajan
independientes. ArchUnit prohíbe hoy usar `HttpSession` (`AislamientoTenantTest`).

**B-7. ¿Qué es JPA y qué es Hibernate?**
JPA es la especificación Java para mapear objetos a tablas (anotaciones `@Entity`, `@ManyToOne`...). Hibernate es
la implementación que genera y ejecuta el SQL. Spring Data JPA agrega los repositorios que se implementan solos.

**B-8. ¿Qué base de datos usan y por qué dos?**
H2 en desarrollo y tests (embebida, cero instalación, en archivo `./data/procesos` o en memoria para tests) y
PostgreSQL en producción (perfil `prod`, `application-prod.properties`). Como el acceso es por JPA, el mismo código
funciona en ambas; solo cambian URL, driver y dialecto.

**B-9. ¿Qué es Lombok?**
Un procesador de anotaciones que genera getters, setters y constructores en compilación. `@RequiredArgsConstructor`
genera el constructor para la inyección de dependencias de los campos `final`.

**B-10. ¿Qué es un `record`?**
Una clase inmutable de Java (16+) que declara sus componentes y obtiene constructor, accessors, `equals`,
`hashCode` y `toString`. Todos los DTOs, `ApiPrincipal` y `PageResponse` son records.

**B-11. ¿Qué códigos HTTP devuelve la API?**
200 (lecturas/actualizaciones), 201 (creaciones con `Location`), 204 (borrados y logout), 400 (validación),
401 (sin token o token inválido), 403 (rol insuficiente), 404 (no existe o es de otra empresa), 409 (regla de
negocio), 500 (error inesperado, sin detalles internos).

**B-12. ¿Cómo se prueba la API sin frontend?**
Con Swagger UI (`/swagger-ui.html`, botón *Authorize* para pegar el Bearer token), con la colección Postman del
repositorio (guarda el token automáticamente tras el login) o con curl. Y automáticamente con 221 tests.

**B-13. ¿Cómo se ejecuta?**
`./mvnw spring-boot:run` (H2, usuario demo `admin@demo.com`), o con Docker:
`docker build -t facimus/procesos-back .` y `docker run -p 8080:8080 ...` con `SPRING_PROFILES_ACTIVE=prod` y las
variables `DB_*` y `JWT_SECRET` para PostgreSQL.

**B-14. ¿Qué es un JWT?**
Un token firmado con tres partes (header.payload.firma). Nuestro payload lleva `sub` (email), `usuarioId`,
`empresaId`, `rol`, `iat`, `exp`. Lo firma `JwtService` con HMAC-SHA256; si alguien lo altera la firma deja de
coincidir. No está cifrado: no lleva datos secretos.

**B-15. ¿Qué roles de acceso existen?**
`RolAcceso`: ADMINISTRADOR (todo, incluidos usuarios, roles de proceso y borrados), EDITOR (crear/editar procesos
y modelos), SOLO_LECTURA (solo GET). No confundir con `RolProceso`, que es una función de negocio en un Lane.

**B-16. ¿Qué significa "multiempresa" o multi-tenant?**
Varias empresas comparten la misma aplicación y la misma BD, pero cada una solo ve lo suyo. Lo implementamos con
una columna `empresa_id` en todas las tablas (`EntidadEmpresa`) y filtrando siempre por ella (`RepositorioTenant`).

**B-17. ¿Qué es un Pool, un Lane y un Gateway?**
Pool: participante del proceso (la empresa, un cliente...). Lane: carril dentro del pool asociado a un rol.
Gateway: punto de decisión (exclusivo, paralelo, inclusivo).

**B-18. ¿Qué es Maven y para qué el `mvnw`?**
Maven gestiona dependencias y el ciclo de build (compile, test, package, verify). `mvnw` es el wrapper: descarga la
versión correcta de Maven para que todos compilen igual.

## Intermedias

**I-1. ¿Por qué el frontend no está en este repositorio?**
Por separación de responsabilidades: el backend expone un contrato HTTP+JSON independiente del cliente. Se
despliegan, versionan y escalan por separado (VMs distintas en la topología de `docs/guia-tecnica.md`). El único
acoplamiento es la configuración CORS (`cors.allowed-origins`).

**I-2. ¿Por qué separar servicios de controladores?**
El controller solo traduce HTTP (JSON, códigos, headers); el service contiene la lógica y las transacciones. Así la
lógica se reutiliza (el filtro JWT usa `UsuarioService.obtener`), se prueba sin HTTP (`ProcesoServiceTest` con
Mockito) y se podría exponer por otro canal sin reescribirla. ArchUnit impide que un controller use repositorios.

**I-3. ¿Cómo manejan el estado?**
El servidor no guarda estado de sesión (`SessionCreationPolicy.STATELESS`). El estado de autenticación viaja en el
JWT y lo guarda el cliente. El estado persistente vive en la BD. Por petición, el `SecurityContextHolder` guarda la
identidad solo durante ese hilo.

**I-4. ¿Qué ventaja tiene esta arquitectura en capas y por módulos?**
Cohesión (todo lo de "modelado" junto), bajo acoplamiento entre capas, testeabilidad (cada capa con su tipo de
test), mantenibilidad (sabes dónde va cada cosa) y reglas verificables (ArchUnit). Si se quisiera, `modelado`
podría extraerse a un microservicio con poco esfuerzo.

**I-5. ¿Cómo garantizan que una empresa no vea los datos de otra?**
Cuatro capas de defensa: (1) la empresa sale del token firmado, nunca del request (`ApiPrincipal`); (2) todas las
entidades tienen `empresa_id` no nulo y no actualizable (`EntidadEmpresa`); (3) todos los repositorios exponen
consultas con `empresaId` (`RepositorioTenant`) y ArchUnit prohíbe `findById` y similares en services; (4) tests de
integración con dos empresas reales (`AislamientoEmpresasIntegracionTest`).

**I-6. ¿Por qué devuelven 404 y no 403 si el recurso es de otra empresa?**
Porque 403 revelaría que ese id existe. Para el aislamiento, "de otra empresa" es indistinguible de "no existe"
(Javadoc de `RecursoNoEncontradoException`).

**I-7. ¿Por qué `NodoFlujo` usa herencia SINGLE_TABLE?**
Actividad y Gateway comparten casi todo y un Arco debe apuntar a cualquiera de los dos. Una sola tabla con
discriminador `tipo_nodo` evita JOINs y permite una FK simple desde `arcos`. El costo (columnas nullable de subtipos)
se compensa validando en el DTO (`GatewayRequest` exige `tipoGateway`).

**I-8. ¿Por qué `EnumType.STRING`?**
Con ORDINAL se guarda la posición (0, 1, 2); si alguien reordena o inserta un valor, los datos cambian de
significado. STRING guarda el nombre. Está protegido por `HerenciaJpaTest`.

**I-9. ¿Qué hace `@Transactional`? Dé un ejemplo.**
Ejecuta el método en una transacción: todo o nada. En `ProcesoService.crear` se insertan el proceso, el pool
inicial y el historial; si falla el historial, se revierten los tres. Funciona con un proxy AOP de Spring y hace
rollback ante `RuntimeException` (nuestras excepciones de dominio lo son).

**I-10. ¿Por qué baja lógica en procesos, roles y usuarios?**
Para conservar integridad referencial y trazabilidad: el historial referencia al autor (Usuario) y al proceso; un
rol puede haber sido usado. Se marca `activo=false` y las consultas filtran `ActivoTrue`.

**I-11. ¿Cómo funciona la paginación?**
El controller crea `PageRequest.of(pagina, 10, Sort.by("fechaModificacion").descending())`; el repositorio hace
`LIMIT/OFFSET` y un `count`. Se responde con `PageResponse` (content, page, size, totalElements, totalPages), un
contrato propio en vez del `Page` de Spring.

**I-12. ¿Cómo funcionan los filtros de búsqueda de procesos?**
Con el patrón Specification (`ProcesoSpecifications.conFiltros`): condiciones fijas (empresa y activo) y
condiciones opcionales (nombre con LIKE sin distinguir mayúsculas, estado, categoría) que solo se agregan si llegan.

**I-13. ¿Qué es la inyección de dependencias?**
Spring crea los objetos (beans) y se los entrega a quien los necesita. Por ejemplo, `ProcesoController` declara
`private final ProcesoService procesoService;` y Spring lo pasa por el constructor generado por Lombok. Facilita
testear con mocks y cambiar implementaciones.

**I-14. ¿Por qué desactivaron CSRF?**
CSRF explota que el navegador envía cookies automáticamente. Aquí la autenticación va en el header
`Authorization`, que el navegador no agrega por sí mismo; sin cookies de sesión, CSRF no aplica.

**I-15. ¿Qué es CORS y por qué el filtro tiene orden -102?**
Mecanismo del navegador que bloquea llamadas entre orígenes distintos (4200 → 8080) salvo que el servidor lo
permita con cabeceras. El filtro CORS va antes que Spring Security (-100) porque el *preflight* OPTIONS no trae
token y, si Security lo procesara primero, respondería 401 sin cabeceras CORS.

**I-16. ¿Cómo manejan los errores?**
Centralizado en `ApiExceptionHandler` (`@RestControllerAdvice`), que traduce excepciones a `ProblemDetail`
(RFC 9457) con el código adecuado. Los errores de seguridad previos al controller los generan `JwtAuthEntryPoint`
(401) y `JwtAccessDeniedHandler` (403) con el mismo formato. Los 500 se registran en log sin exponer detalles.

**I-17. ¿Cómo se crea el pool inicial de un proceso?**
En `ProcesoService.crear`, dentro de la misma transacción: un `Pool` con el nombre de la empresa, tipo EMPRESA,
no caja negra y orden 0 (HU-21).

**I-18. ¿Qué tipos de tests tienen y cuántos?**
221 tests, todos pasando, 87,2 % de cobertura de líneas: unitarios de services (Mockito), de controllers
(`@WebMvcTest` + MockMvc), de integración (`@SpringBootTest` con H2 en memoria, tokens reales), de JWT y 20 reglas
de arquitectura con ArchUnit. JaCoCo exige mínimo 50 %.

**I-19. ¿Qué es ArchUnit y qué reglas verifican?**
Una librería que analiza el bytecode para verificar reglas de arquitectura como tests. Verificamos: ubicación de
controllers/services/repos/entidades/DTOs, que controllers no usen repositorios, que services no dependan de
controllers, que toda entidad extienda `EntidadEmpresa`, que todo repo extienda `RepositorioTenant`, que no se use
`HttpSession`, que ningún Request tenga `empresaId`, enums STRING y herencia SINGLE_TABLE.

**I-20. ¿Qué hace el pipeline de CI?**
En cada push/PR: compila y prueba en Ubuntu y Windows, publica reportes de tests y JaCoCo, corre por separado las
reglas de arquitectura, construye la imagen Docker y verifica que arranca, y en los push analiza con SonarCloud.

**I-21. ¿Por qué Docker multi-stage?**
La etapa 1 (Maven+JDK) compila y prueba; la etapa 2 solo tiene el JRE y el jar: imagen más pequeña y con menos
superficie de ataque. Además corre con un usuario no root (`appuser`) y cachea dependencias en una capa aparte.

## Difíciles

**D-1. ¿Qué ocurre si falla la API o la base de datos?**
Si una operación falla a mitad, `@Transactional` hace rollback: no quedan datos a medias. Cualquier excepción no
prevista llega al handler genérico, que responde 500 con un `ProblemDetail` genérico y registra el stack trace. Si
la BD está caída, HikariCP no obtiene conexión y la petición termina en 500. Lo que falta: health checks
(`/actuator/health`), reintentos y circuit breakers, y que el cliente muestre un mensaje amable ante 5xx.

**D-2. ¿Dónde se valida la información?**
En tres niveles: forma en los DTOs (`@NotBlank`, `@Email`, `@Size`, `@NotNull`, `@AssertTrue`, `@Min`); reglas de
negocio en los services (nombre único, mismo pool, publicado→borrador prohibido, condición obligatoria hacia
gateway exclusivo/inclusivo, rol en uso); integridad en la BD (NOT NULL, UNIQUE de NIT y de empresa+email, FKs).
La validación del frontend es solo de usabilidad.

**D-3. ¿Cómo escalarías este proyecto?**
Al ser stateless, se pueden correr N instancias detrás de un balanceador sin sesiones compartidas (solo necesitan
el mismo `JWT_SECRET`). Luego: migraciones con Flyway, índices compuestos por `empresa_id`, caché del usuario
autenticado para no consultar la BD en cada petición, réplicas de lectura de PostgreSQL, paginación configurable,
observabilidad con Actuator/Micrometer y, si crece mucho, separar `modelado` como servicio.

**D-4. ¿Qué problemas de seguridad identificas?**
(Ver Fase 8, S-1 a S-8.) Los principales: el usuario demo `admin@demo.com/admin123` se crearía también en
producción si la BD está vacía; no hay límite de intentos en el login; Swagger queda público en producción; la
contraseña mínima es de 6 caracteres; no existe revocación de tokens antes de que expiren (mitigado porque el filtro
comprueba que el usuario siga activo).

**D-5. ¿Qué optimizaciones propondrías?**
Consultas `count` en lugar de traer listas para calcular `orden` (`LaneService`, `PoolService`); evitar N+1 en
`RolProcesoService.listarConUso` con una sola consulta agrupada; relaciones `LAZY` + `@EntityGraph` o proyecciones
DTO; índices por `(empresa_id, ...)`; unicidad de nombre de rol con una consulta `exists` en vez de traer todos los
roles; cachear el usuario del token por unos segundos.

**D-6. Si alguien roba un token, ¿qué pasa? ¿Y el logout?**
Puede usarlo hasta que expire (30 min por defecto), porque JWT es stateless. El logout solo descarta el token en el
cliente. La mitigación existente: desactivar al usuario invalida sus tokens de inmediato, porque el filtro consulta
si sigue activo. Mejoras: tokens de acceso más cortos + refresh tokens, lista de revocación (denylist por `jti`) y
HTTPS obligatorio.

**D-7. ¿Por qué el filtro JWT consulta la BD en cada petición si el token ya trae los datos?**
Porque se decidió priorizar seguridad sobre rendimiento: así un usuario desactivado o al que le cambiaron el rol
pierde o ajusta sus permisos de inmediato, sin esperar a que expire el token. El costo es una consulta por clave
primaria, que se podría cachear.

**D-8. ¿Qué pasa si el mismo correo existe en dos empresas?**
Es un bug real que verifiqué: el registro lo permite, porque la unicidad es `UNIQUE(empresa_id, email)`, pero el
login usa `findByEmail`, que devuelve dos filas → `IncorrectResultSizeDataAccessException` → **HTTP 500**. Hay dos
soluciones: exigir email único global (restricción UNIQUE en `email` + validación en `EmpresaService` y
`UsuarioService`), o pedir un identificador de empresa (NIT) en el login y usar `findByEmpresaIdAndEmail`.

**D-9. ¿Por qué `spring.jpa.open-in-view=false` y cómo evitan `LazyInitializationException`?**
Open-in-view mantiene la sesión de Hibernate abierta hasta que se escribe la vista, lo que esconde consultas en la
capa web y retiene conexiones. Lo desactivamos. No hay `LazyInitializationException` porque las relaciones
`@ManyToOne` son EAGER por defecto, así que `ProcesoResponse.of`/`LaneResponse.of` en el controller tienen los
datos cargados. La mejora sería LAZY + `@EntityGraph` o mapear a DTO dentro del service.

**D-10. ¿Hay condiciones de carrera?**
Sí, en las validaciones *check-then-act*: dos peticiones simultáneas pueden pasar el `exists...Nombre...` y crear
dos procesos con el mismo nombre, porque no hay restricción UNIQUE en BD para eso. Pasa igual con `orden` de lanes
y pools (se calcula con `size()`). La solución es poner restricciones únicas en BD (índices únicos parciales en
PostgreSQL para `activo=true`) y capturar `DataIntegrityViolationException` → 409.

**D-11. ¿Qué pasa si no se define `JWT_SECRET`?**
En desarrollo (`jwt.secret=${JWT_SECRET:}`) `JwtService` genera una clave aleatoria y avisa en el log: los tokens
dejan de servir al reiniciar y no se pueden compartir entre instancias. En producción (`jwt.secret=${JWT_SECRET}`
sin default) la aplicación no arranca: *fail fast*. El secreto debe tener al menos 256 bits para HS256.

**D-12. ¿Son vulnerables a inyección SQL?**
No en el código actual: las derived queries y la Criteria API (`ProcesoSpecifications`) usan parámetros enlazados;
nunca se concatena texto del usuario en SQL. Un detalle menor: en el `LIKE` no se escapan `%` y `_`, así que un
usuario puede usar comodines en su búsqueda, pero eso no es una inyección.

**D-13. ¿Por qué `JwtAuthenticationFilter` no es un `@Component`?**
Porque Spring Boot registra automáticamente todo bean `Filter` en el contenedor de servlets, y además lo añadimos a
la cadena de Spring Security: se ejecutaría dos veces. Se instancia con `new` dentro de `SecurityConfig.filterChain`.

**D-14. ¿Cómo agregarías una entidad nueva, por ejemplo "Evento de inicio"?**
Crear `EventoInicio extends NodoFlujo` con `@DiscriminatorValue` en `modelado/model` (o una entidad que extienda
`EntidadEmpresa`), un repositorio que extienda `RepositorioTenant`, un service con métodos que reciban `empresaId`,
DTOs record en `controller/dto` sin campo `empresaId`, un controller en `/api/v1/...`, la regla en `SecurityConfig`
si cambia algún permiso, y tests. Si olvido algo, ArchUnit hace fallar el build.

**D-15. ¿Por qué `ddl-auto=update` es riesgoso en producción?**
Hibernate altera el esquema al arrancar, pero no borra ni renombra columnas, no migra datos, no versiona los cambios
y puede fallar a mitad. Lo correcto es usar Flyway o Liquibase con scripts versionados y `ddl-auto=validate`.

**D-16. ¿Qué historias de usuario no están completas?**
Según la auditoría `docs/cobertura-historias-usuario.md`: 13 completas, 13 parciales y 2 sin implementar. HU-23
(compartir procesos entre empresas) choca con el aislamiento estricto; HU-26 (notificaciones externas tipadas) no
existe. Entre las parciales: no se pueden reordenar lanes (HU-22), no se puede cambiar origen/destino de un arco
(HU-12) y no se valida el mínimo de arcos salientes de un gateway (HU-14).

**D-17. ¿Por qué el login convierte `ReglaNegocioException` en `BadCredentialsException`?**
Porque una regla de negocio se mapea a 409, pero unas credenciales inválidas deben responder 401. Además se usa el
mismo mensaje exista o no el correo, para no permitir enumerar usuarios.

**D-18. ¿Cómo prueban que el aislamiento funciona de verdad?**
Con un test de integración (`AislamientoEmpresasIntegracionTest`) que levanta la aplicación completa, crea dos
empresas con datos reales, inicia sesión con cada una y comprueba que los listados de A no muestran datos de B y
que un `empresaId` ajeno en el cuerpo o la URL se ignora. Además ArchUnit impide estructuralmente las consultas sin
empresa.

**D-19. ¿Qué es el problema N+1 y dónde aparece?**
Una consulta para traer N elementos más una consulta extra por cada uno. En `RolProcesoService.listarConUso` se
ejecuta un `count` por rol; en `PoolService.eliminar`, una consulta de nodos por lane; y la carga EAGER de
`@ManyToOne` hace consultas extra al mapear listas. Se soluciona con consultas agregadas (`GROUP BY`) o
`JOIN FETCH`.

**D-20. ¿Cómo implementarías HU-23 (compartir procesos entre empresas) sin romper el aislamiento?**
Con una tabla explícita de compartición (`proceso_compartido(proceso_id, empresa_duena_id, empresa_invitada_id,
permiso)`) y endpoints de solo lectura que consulten "mis procesos ∪ compartidos conmigo", sin relajar
`RepositorioTenant`. Sería una excepción documentada y testeada, igual que `findByEmail`.

**D-21. ¿Por qué el `pool` del Arco es redundante?**
Porque podría deducirse de `origen.lane.pool`, pero guardarlo permite listar los arcos de un pool con una consulta
simple (`findAllByPoolIdAndEmpresaId`) y deja explícita la regla "origen y destino en el mismo pool". Es una
desnormalización consciente.

---

# FASE 8 — Riesgos y decisiones técnicas (con evidencia)

## 8.1 Buenas prácticas presentes

| Práctica | Evidencia |
|---|---|
| Arquitectura en capas **verificada por tests** | `arquitectura/EmpaquetadoTest.java` (10 reglas) |
| Multi-tenant en profundidad | `EntidadEmpresa` (`updatable = false`), `RepositorioTenant`, `AislamientoTenantTest`, `AislamientoEmpresasIntegracionTest` |
| Tenant tomado del token, nunca del cliente | `ApiPrincipal`; regla ArchUnit `requests_sin_empresa` |
| Prevención de IDOR y de enumeración | 404 para recursos ajenos; mensaje de login genérico en `UsuarioService.CREDENCIALES_INVALIDAS` |
| Contraseñas con BCrypt | `SecurityConfig.passwordEncoder()`, `passwordHash` |
| API stateless con JWT firmado y expiración | `JwtService`, `SessionCreationPolicy.STATELESS` |
| Revocación efectiva al desactivar usuarios | `JwtAuthenticationFilter.usuarioVigente` + test "El token de un usuario desactivado deja de servir" |
| DTOs inmutables (records) y entidades nunca expuestas | `controller/dto/*` |
| Errores estándar RFC 9457 y uniformes (también en 401/403) | `ApiExceptionHandler`, `JwtAuthEntryPoint`, `JwtAccessDeniedHandler` |
| REST semántico | 201 + `Location` en todos los POST de creación, 204 en DELETE, PUT/PATCH diferenciados, `/api/v1` |
| Transacciones declarativas | `@Transactional` en todas las escrituras |
| Enums como STRING | `@Enumerated(EnumType.STRING)` + `HerenciaJpaTest` |
| `open-in-view=false` | `application.properties` |
| Secretos fuera del código; *fail fast* en prod | `${JWT_SECRET}`, `${DB_PASSWORD:}` |
| Inyección por constructor | `@RequiredArgsConstructor` + `final` |
| Tests abundantes | 221 tests, 87,2 % de líneas; misma matriz de seguridad en tests y prod (`SecurityConfig.reglasComunes`) |
| CI multiplataforma + Sonar + Docker smoke test | `.github/workflows/ci.yml` |
| Docker multi-stage y no root | `Dockerfile` |
| Documentación viva de la API | springdoc + `OpenApiConfig` |

## 8.2 Posibles bugs

| # | Bug | Evidencia | Consecuencia | Arreglo |
|---|---|---|---|---|
| B-1 | **Login con correo repetido en dos empresas → 500** (VERIFICADO ejecutándolo) | `Usuario`: `UNIQUE(empresa_id, email)`; `UsuarioRepository.findByEmail` devuelve `Optional` | `IncorrectResultSizeDataAccessException: 2 results` → `{"status":500,"title":"Error interno"}`; esa persona no puede iniciar sesión | email único global o login con NIT + `findByEmpresaIdAndEmail` |
| B-2 | Un mensaje puede unir pools de **otro proceso** | `MensajeService.crear` carga `poolOrigen`/`poolDestino` solo por empresa; nunca compara `pool.getProceso().getId()` con `procesoId` | modelo inconsistente | validar que ambos pools pertenezcan a `procesoId` |
| B-3 | Se puede asignar un **rol de proceso eliminado** a una lane | `LaneService.crear/editar` usa `rolProcesoRepository.findByIdAndEmpresaId` (sin `ActivoTrue`) | reaparecen roles dados de baja | usar `findByIdAndEmpresaIdAndActivoTrue` |
| B-4 | Un **proceso eliminado lógicamente** sigue siendo modelable | `PoolService.crear`, `MensajeService.crear/listarPorProceso`, `PoolService.listarPorProceso` usan `procesoRepository.findByIdAndEmpresaId`/`existsByIdAndEmpresaId` sin filtrar `activo` | se editan datos "borrados" | usar las variantes `...ActivoTrue` |
| B-5 | Editar una actividad o gateway **no revalida el nombre único** | `ActividadService.editar` y `GatewayService.editar` no llaman a `existsByNombreIgnoreCase...` | nombres duplicados vía PUT | repetir la validación excluyendo el propio id |
| B-6 | La regla "arco hacia gateway exclusivo/inclusivo requiere condición" **se puede evadir** | `ArcoService.editar` permite `condicion` vacía; `GatewayService.editar` permite cambiar a EXCLUSIVO sin revisar los arcos entrantes | modelo BPMN inválido | revalidar en ambas ediciones |
| B-7 | `orden` duplicado tras eliminar | `LaneService.crear`/`PoolService.crear`: `orden = lista.size()` | pools/lanes con el mismo orden | `max(orden)+1` o endpoint de reordenamiento |
| B-8 | La empresa puede **quedarse sin administrador** | `UsuarioService.actualizar/desactivar` no verifica si es el último ADMINISTRADOR (ni si se desactiva a sí mismo) | nadie puede gestionar usuarios ni roles | validar "al menos un admin activo" |
| B-9 | Unicidad por *check-then-act* sin respaldo en BD | nombres de proceso, rol y nodo | duplicados bajo concurrencia | restricciones únicas en BD |

## 8.3 Seguridad

| # | Riesgo | Evidencia | Recomendación |
|---|---|---|---|
| S-1 | **Admin demo con contraseña conocida en producción** si la BD está vacía | `DatosDemoInitializer` no tiene `@Profile` | `@Profile("!prod")` o desactivarlo por propiedad |
| S-2 | Sin límite de intentos de login (fuerza bruta) | `AuthController.login` | rate limiting / bloqueo temporal (Bucket4j, gateway) |
| S-3 | Enumeración por tiempo de respuesta | si el correo no existe no se ejecuta BCrypt (`UsuarioService.autenticar`) | comparar contra un hash ficticio para igualar tiempos |
| S-4 | Política de contraseña débil | `@Size(min = 6)` en `CrearUsuarioRequest` y `RegistroEmpresaRequest` | mínimo 8–12 y complejidad |
| S-5 | Swagger/OpenAPI públicos en prod | `permitAll` para `/swagger-ui/**`, `/v3/api-docs/**`; el propio springdoc lo advierte en el log de los tests | `springdoc.api-docs.enabled=false` en prod |
| S-6 | `frameOptions().disable()` global (solo lo necesitaba H2) | `SecurityConfig.reglasComunes` | `sameOrigin()` o desactivarlo solo en dev |
| S-7 | Tokens no revocables antes de expirar; sin refresh token | `AuthController.logout` no hace nada en servidor | expiración corta + refresh, o denylist por `jti` |
| S-8 | `allowCredentials(true)` sin usar cookies | `CorsConfig` | ponerlo en `false` (menos superficie) |
| S-9 | Registro público de empresas sin verificación de correo ni captcha | `POST /api/v1/empresas` es `permitAll` | verificación de correo y rate limit |

## 8.4 Problemas de rendimiento

| # | Problema | Evidencia |
|---|---|---|
| P-1 | 1 consulta a BD por cada petición autenticada | `JwtAuthenticationFilter.usuarioVigente` → `usuarioService.obtener` |
| P-2 | `@ManyToOne` EAGER en todo el modelo → JOINs/SELECT extra | todas las entidades (`Arco` carga origen → lane → pool → proceso → empresa) |
| P-3 | N+1 | `RolProcesoService.listarConUso` (un `count` por rol); `PoolService.eliminar` (una consulta de nodos por lane) |
| P-4 | Traer listas completas solo para contar o comparar | `LaneService.crear`/`PoolService.crear` (`.size()`), `RolProcesoService.validarNombreUnico` (todos los roles en memoria) |
| P-5 | Filtrado por tipo en memoria | `ActividadService.listarPorLane` y `GatewayService.listarPorLane` traen todos los nodos y filtran con `instanceof` (se podría filtrar por discriminador) |
| P-6 | Sin índices declarados en `empresa_id` y columnas de búsqueda | ninguna entidad usa `@Table(indexes=...)`; PostgreSQL no indexa FKs automáticamente |
| P-7 | Tamaño de página fijo (10) | `ProcesoController.TAMANO_PAGINA` |

## 8.5 Deuda técnica

| Deuda | Evidencia |
|---|---|
| Sin migraciones versionadas | `ddl-auto=update` en dev **y** en prod |
| Documentación desactualizada | `docs/guia-tecnica.md` habla de Thymeleaf, `HttpSession`, "30 tests" y "22 rutas Thymeleaf"; el README describe paquetes `rest/`, `mapper/` y `dto/request|response` que no existen en el código final; el Javadoc de `UsuarioRepository.findByEmail` menciona "la sesion" |
| README es un plan de migración, no una guía de uso | no explica cómo ejecutar, ni el usuario demo, ni Swagger |
| Código muerto (declarado, nunca llamado, verificado con grep) | `ProcesoService.publicar`, `UsuarioService.cambiarRolAcceso`, `EmpresaService.buscarPorNit`, `LaneRepository.existsByRolProcesoIdAndEmpresaId`, `MensajeRepository.findAllByPoolOrigenIdAndEmpresaId` y `...PoolDestino...`, `RepositorioTenant.findAllByEmpresaId` |
| Historial solo a nivel de proceso | cambios en pools, lanes, nodos o arcos no se registran en `HistorialCambio` |
| `@Lob String` en PostgreSQL | según la versión de Hibernate puede mapearse a `oid` (objeto grande) en vez de `text`; conviene verificarlo en prod o usar `@Column(columnDefinition="text")` |
| Excepción de ArchUnit no simétrica | `DatosDemoInitializer` (config) usa repositorios directamente |

## 8.6 Código duplicado

| Duplicación | Dónde |
|---|---|
| `ActividadService` y `GatewayService` son casi idénticos (crear/editar/eliminar con cascada de arcos/obtener/listar) | `modelado/service/` → se podría extraer un `NodoFlujoService` genérico |
| Búsqueda del autor repetida 4 veces | `ProcesoService`: `usuarioRepository.findByIdAndEmpresaId(...).orElseThrow(...)` en `crear`, `editarDatos`, `cambiarEstado`, `eliminarLogico` |
| `Long empresaId = principal.empresaId();` en cada método de cada controller | 12 controllers |
| Construcción manual de `RolProcesoVistaResponse` 3 veces | `RolProcesoController.crear/obtener/editar` |
| Mensaje "Ya existe un nodo con el nombre..." duplicado | `ActividadService.crear` y `GatewayService.crear` |
| Validación "¿existe el padre?" repetida (`if (!xRepository.existsByIdAndEmpresaId) throw ...`) | `listarPor*` en 5 services |

---

# FASE 9 — Plan de estudio

| Etapa | Qué leer / hacer | Tiempo | Al terminar debes saber... |
|---|---|---|---|
| **1. Contexto** | Esta guía §0 y Fase 1; `README.md` §1–§2 y §10–§16; `docs/cobertura-historias-usuario.md` | 1,5 h | explicar en 1 minuto qué hace el sistema, qué es BPMN, qué es multi-tenant y por qué no hay frontend aquí |
| **2. Ejecutarlo** | `./mvnw spring-boot:run`; abrir `http://localhost:8080/swagger-ui.html`; login con `admin@demo.com`/`admin123`; *Authorize*; crear un proceso; ver `/h2-console` (JDBC URL `jdbc:h2:file:./data/procesos`, usuario `sa`) | 1,5 h | mostrar en vivo login → token → crear proceso → ver las filas en `procesos`, `pools`, `historial_cambios` |
| **3. Modelo de datos** | `common/EntidadEmpresa`, `gestion/model/*`, `modelado/model/*` (en ese orden) | 2 h | dibujar el diagrama ER de memoria; defender SINGLE_TABLE, STRING, baja lógica y `updatable=false` |
| **4. Rutas y controllers** | Apéndice A (endpoints); `ProcesoController`, `AuthController`, `EmpresaController`, luego un controller de modelado (`LaneController`) y los DTOs | 2 h | para cualquier endpoint: verbo, ruta, request, response, código HTTP y quién puede llamarlo |
| **5. Services (negocio)** | `ProcesoService` → `UsuarioService` → `RolProcesoService` → `ArcoService` → resto de modelado | 3 h | enumerar las reglas de negocio de cada entidad y explicar `@Transactional` con el ejemplo del pool inicial |
| **6. Repositorios y consultas** | `RepositorioTenant`, `ProcesoRepository`, `ProcesoSpecifications`, `NodoFlujoRepository` | 1,5 h | traducir un nombre de derived query a SQL; explicar Specification y paginación |
| **7. Errores** | `ApiExceptionHandler`, las dos excepciones de `common/` | 45 min | decir qué código HTTP sale ante cada tipo de error y por qué 404 en lugar de 403 |
| **8. Seguridad** | `SecurityConfig` → `JwtService` → `JwtAuthenticationFilter` → `ApiPrincipal` → `JwtAuthEntryPoint`/`JwtAccessDeniedHandler` → `CorsConfig` | 3 h | recorrer la Fase 3.4 de memoria; explicar JWT, stateless, CSRF, CORS, orden de las reglas y la matriz de permisos |
| **9. Tests y calidad** | un test de cada tipo: `ProcesoServiceTest`, `ProcesoControllerTest`, `SeguridadIntegracionTest`, `AislamientoTenantTest`; `ci.yml`; `Dockerfile`; `./mvnw verify` y abrir `target/site/jacoco/index.html` | 2 h | explicar la pirámide de tests, qué protege ArchUnit y qué hace cada job del CI |
| **10. Defensa** | Fase 7 (responder en voz alta sin mirar) y Fase 8 (elegir 3 bugs y 3 mejoras para proponer por iniciativa propia) | 3 h | responder cualquier pregunta con "qué + por qué + archivo" |

**Total aproximado: 20 horas.** Si solo tienes una tarde (≈5 h), prioriza: etapa 1 → 2 → Fase 3.4 → etapa 8 → las
preguntas I-5, I-9, D-2, D-4, D-7 y D-8.

---

# Apéndice A — Todos los endpoints

| Método | Ruta | Controller | Request → Response | Código OK | Rol |
|---|---|---|---|---|---|
| POST | `/api/v1/empresas` | EmpresaController | `RegistroEmpresaRequest` → `EmpresaResponse` | 201 | público |
| POST | `/api/v1/auth/login` | AuthController | `LoginRequest` → `LoginResponse` | 200 | público |
| POST | `/api/v1/auth/logout` | AuthController | — | 204 | autenticado |
| GET | `/api/v1/usuarios` | UsuarioController | → `List<UsuarioResponse>` (activos) | 200 | ADMIN |
| POST | `/api/v1/usuarios` | UsuarioController | `CrearUsuarioRequest` → `UsuarioResponse` | 201 | ADMIN |
| GET | `/api/v1/usuarios/{id}` | UsuarioController | → `UsuarioResponse` | 200 | ADMIN |
| PATCH | `/api/v1/usuarios/{id}` | UsuarioController | `ActualizarUsuarioRequest` → `UsuarioResponse` | 200 | ADMIN |
| DELETE | `/api/v1/usuarios/{id}` | UsuarioController | (baja lógica) | 204 | ADMIN |
| GET | `/api/v1/procesos?nombre&estado&categoria&pagina` | ProcesoController | → `PageResponse<ProcesoResponse>` | 200 | todos |
| POST | `/api/v1/procesos` | ProcesoController | `ProcesoRequest` → `ProcesoResponse` | 201 | ADMIN, EDITOR |
| GET | `/api/v1/procesos/{id}` | ProcesoController | → `ProcesoDetalleResponse` (proceso + historial) | 200 | todos |
| PUT | `/api/v1/procesos/{id}` | ProcesoController | `EditarProcesoRequest` → `ProcesoResponse` | 200 | ADMIN, EDITOR |
| PATCH | `/api/v1/procesos/{id}` | ProcesoController | `CambiarEstadoProcesoRequest` → `ProcesoResponse` | 200 | ADMIN, EDITOR |
| GET | `/api/v1/procesos/{id}/historial` | ProcesoController | → `List<HistorialCambioResponse>` | 200 | todos |
| DELETE | `/api/v1/procesos/{id}` | ProcesoController | (baja lógica) | 204 | ADMIN |
| GET | `/api/v1/roles` | RolProcesoController | → `List<RolProcesoVistaResponse>` | 200 | todos |
| POST | `/api/v1/roles` | RolProcesoController | `RolProcesoRequest` → `RolProcesoVistaResponse` | 201 | ADMIN |
| GET | `/api/v1/roles/{id}` | RolProcesoController | → `RolProcesoVistaResponse` | 200 | todos |
| PUT | `/api/v1/roles/{id}` | RolProcesoController | `RolProcesoRequest` → `RolProcesoVistaResponse` | 200 | ADMIN |
| DELETE | `/api/v1/roles/{id}` | RolProcesoController | (baja lógica; 409 si está en uso) | 204 | ADMIN |
| GET | `/api/v1/procesos/{procesoId}/pools` | PoolController | → `List<PoolResponse>` | 200 | todos |
| POST | `/api/v1/procesos/{procesoId}/pools` | PoolController | `PoolRequest` → `PoolResponse` | 201 | ADMIN, EDITOR |
| GET | `/api/v1/pools/{id}` | PoolController | → `PoolResponse` | 200 | todos |
| PUT | `/api/v1/pools/{id}` | PoolController | `EditarPoolRequest` → `PoolResponse` | 200 | ADMIN, EDITOR |
| DELETE | `/api/v1/pools/{id}` | PoolController | — | 204 | ADMIN |
| GET | `/api/v1/pools/{poolId}/lanes` | LaneController | → `List<LaneResponse>` | 200 | todos |
| POST | `/api/v1/pools/{poolId}/lanes` | LaneController | `LaneRequest` → `LaneResponse` | 201 | ADMIN, EDITOR |
| GET | `/api/v1/lanes/{id}` | LaneController | → `LaneResponse` | 200 | todos |
| PUT | `/api/v1/lanes/{id}` | LaneController | `LaneRequest` → `LaneResponse` | 200 | ADMIN, EDITOR |
| DELETE | `/api/v1/lanes/{id}` | LaneController | — | 204 | ADMIN |
| GET | `/api/v1/lanes/{laneId}/actividades` | ActividadController | → `List<ActividadResponse>` | 200 | todos |
| POST | `/api/v1/lanes/{laneId}/actividades` | ActividadController | `ActividadRequest` → `ActividadResponse` | 201 | ADMIN, EDITOR |
| GET/PUT/DELETE | `/api/v1/actividades/{id}` | ActividadController | `ActividadRequest` → `ActividadResponse` | 200/200/204 | todos / A,E / ADMIN |
| GET | `/api/v1/lanes/{laneId}/gateways` | GatewayController | → `List<GatewayResponse>` | 200 | todos |
| POST | `/api/v1/lanes/{laneId}/gateways` | GatewayController | `GatewayRequest` → `GatewayResponse` | 201 | ADMIN, EDITOR |
| GET/PUT/DELETE | `/api/v1/gateways/{id}` | GatewayController | `GatewayRequest` → `GatewayResponse` | 200/200/204 | todos / A,E / ADMIN |
| POST | `/api/v1/arcos` | ArcoController | `ArcoRequest` → `ArcoResponse` | 201 | ADMIN, EDITOR |
| GET | `/api/v1/pools/{poolId}/arcos` | ArcoController | → `List<ArcoResponse>` | 200 | todos |
| GET/PUT/DELETE | `/api/v1/arcos/{id}` | ArcoController | `EditarArcoRequest` → `ArcoResponse` | 200/200/204 | todos / A,E / ADMIN |
| GET | `/api/v1/procesos/{procesoId}/mensajes` | MensajeController | → `List<MensajeResponse>` | 200 | todos |
| POST | `/api/v1/procesos/{procesoId}/mensajes` | MensajeController | `MensajeRequest` → `MensajeResponse` | 201 | ADMIN, EDITOR |
| GET/PUT/DELETE | `/api/v1/mensajes/{id}` | MensajeController | `EditarMensajeRequest` → `MensajeResponse` | 200/200/204 | todos / A,E / ADMIN |
| PUT | `/api/v1/mensajes/{mensajeId}/correlacion` | CorrelacionController | `CorrelacionRequest` → `CorrelacionResponse` (upsert) | 200 | ADMIN, EDITOR |
| GET | `/api/v1/mensajes/{mensajeId}/correlacion` | CorrelacionController | → `CorrelacionResponse` | 200 | todos |
| — | `/swagger-ui.html`, `/v3/api-docs`, `/h2-console` | springdoc / H2 | documentación y consola | — | público |

# Apéndice B — Frases clave para memorizar

1. *"La empresa sale del token, nunca del request."*
2. *"Un recurso de otra empresa es indistinguible de uno que no existe: por eso 404."*
3. *"El controller traduce HTTP; el service decide; el repositorio siempre filtra por empresa."*
4. *"Las reglas de arquitectura no son un documento: son tests que rompen el build."*
5. *"Stateless: el logout es que el cliente olvide el token; desactivar al usuario lo invalida de inmediato
   porque el filtro consulta si sigue activo."*
6. *"Validamos en tres niveles: forma en el DTO, negocio en el service, integridad en la base de datos."*
