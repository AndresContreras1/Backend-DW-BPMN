# Guía de sustentación — Facimus Procesos (backend)

Guía de estudio para sustentar oralmente el proyecto ante el profesor de Desarrollo Web. Cubre las dos entregas:

1. **Entrega 1:** backend Spring Boot + JPA organizado en capas.
2. **Entrega 2:** API REST lista para que la consuma un frontend Angular, empaquetada en Docker.

Todo lo que aparece aquí está verificado contra el código actual del repositorio. Cada fragmento de código trae su ruta (relativa a la raíz del repositorio) para que lo puedas abrir y mostrar durante la sustentación. Los documentos complementarios son `README.md`, `docs/guia-tecnica.md` y `docs/cobertura-historias-usuario.md`.

## Índice

1. [Qué hace el sistema y su alcance](#1-qué-hace-el-sistema-y-su-alcance)
2. [Mapa del repositorio](#2-mapa-del-repositorio)
3. [Arquitectura en capas y flujo de una petición](#3-arquitectura-en-capas-y-flujo-de-una-petición)
4. [Persistencia](#4-persistencia)
5. [Servicios y reglas de negocio](#5-servicios-y-reglas-de-negocio)
6. [API REST](#6-api-rest)
7. [Seguridad a nivel de backend](#7-seguridad-a-nivel-de-backend)
8. [Pruebas y calidad](#8-pruebas-y-calidad)
9. [Docker y compose](#9-docker-y-compose)
10. [Cómo se cumple cada historia de usuario](#10-cómo-se-cumple-cada-historia-de-usuario)
11. [Preguntas probables de sustentación](#11-preguntas-probables-de-sustentación)
12. [Glosario](#12-glosario)

---

## 1. Qué hace el sistema y su alcance

**Facimus Procesos** es una API REST multiempresa para **modelar y validar procesos de negocio en notación BPMN**. Una empresa se registra, crea usuarios con distintos roles, dibuja sus procesos (participantes, carriles, actividades, decisiones, flujos y mensajes entre participantes) y puede **publicar** un proceso solo si el modelo es coherente.

### Qué sí hace

- Registro de empresas y de su administrador inicial; alta de colaboradores por creación directa o por invitación con token de 48 horas.
- Inicio de sesión con JWT; tres roles de acceso: `ADMINISTRADOR`, `EDITOR`, `SOLO_LECTURA`.
- Ciclo de vida del proceso: crear (nace en `BORRADOR`), editar, publicar, eliminar de forma lógica, buscar con filtros y paginación, historial de cambios.
- Modelado BPMN: **pools** (participantes), **lanes** (carriles asociados a un rol de proceso), **actividades**, **gateways** (exclusivo, paralelo, inclusivo), **eventos de mensaje** (throw / catch), **arcos** (flujo de secuencia), **mensajes** entre pools y su **correlación**.
- **Validación del modelo** antes de publicar: devuelve la lista de advertencias que impiden la publicación.
- Roles de proceso (la función responsable: "Jefe inmediato", "Talento Humano"), permisos configurables para editar la estructura (pools y lanes) y compartir un proceso con otra empresa en solo lectura.

### Qué NO hace (alcance)

- **No ejecuta procesos.** No hay motor de workflow, ni instancias de proceso, ni tareas asignadas a personas. El sistema solo *describe* y *valida* el modelo. Por eso los "mensajes" y las "correlaciones" son metadatos del diagrama: un mensaje hacia un sistema externo solo documenta el destino; no se envía nada, no se guardan credenciales ni direcciones reales (`MensajeRequest.destinoExterno` lo dice explícitamente).
- **No envía correos.** La invitación de un colaborador genera un token y lo devuelve en la respuesta; no hay dependencia de correo en el `pom.xml`.
- **No incluye frontend.** Esta entrega es el backend; el comentario de `compose.yaml` indica que el frontend Angular se agregará como tercer servicio en la entrega siguiente. La API ya está preparada para él (CORS, JWT, errores en formato estándar, Swagger).

### Stack (verificado en `pom.xml`)

| Pieza | Versión / detalle |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.0 (`spring-boot-starter-parent`) |
| Web | `spring-boot-starter-webmvc` (Spring MVC) |
| Persistencia | `spring-boot-starter-data-jpa` (Hibernate), H2 (desarrollo y pruebas), PostgreSQL (producción) |
| Migraciones | `spring-boot-starter-flyway` + `flyway-database-postgresql` |
| Validación | `spring-boot-starter-validation` (Bean Validation) |
| Seguridad | `spring-boot-starter-security`, JJWT 0.12.6, BCrypt |
| Documentación | springdoc-openapi 3.0.1 (Swagger UI) |
| Mapeo | ModelMapper 3.2.0 |
| Boilerplate | Lombok |
| Pruebas | JUnit 5, Mockito, Spring Test, ArchUnit 1.4.0 |
| Calidad | JaCoCo 0.8.13, SonarCloud (sonar-maven-plugin 4.0.0.4121) |

> Dato útil: Spring Boot 4 usa Jackson 3, cuyos paquetes empiezan por `tools.jackson` (verás `tools.jackson.databind.json.JsonMapper` en `security/JwtAuthEntryPoint.java`).

---

## 2. Mapa del repositorio

```
.
├── pom.xml                      Dependencias, JaCoCo (umbrales), configuración de Sonar
├── Dockerfile                   Imagen multietapa (Maven -> JRE 21)
├── compose.yaml                 PostgreSQL 16 + backend
├── .env.example                 Plantilla de variables (DB_PASSWORD, JWT_SECRET, CORS...)
├── .github/workflows/ci.yml     Pipeline: build/test, ArchUnit, Docker, SonarCloud
├── docs/                        guia-tecnica.md, cobertura-historias-usuario.md, esta guía
├── postman/                     Colecciones de Postman (API y flujo de historias)
├── jmeter/                      Planes de carga (login, CRUD de procesos, roles, modelado)
└── src/
    ├── main/java/com/facimus/procesos/
    │   ├── ProcesosApplication.java
    │   ├── common/              Lo compartido entre módulos
    │   │   ├── EntidadEmpresa.java          @MappedSuperclass con empresa_id
    │   │   ├── RepositorioTenant.java       Contrato base de repositorios por empresa
    │   │   ├── *Exception.java              Excepciones de dominio (3)
    │   │   ├── api/ApiExceptionHandler.java @RestControllerAdvice
    │   │   ├── api/PageResponse.java        Respuesta paginada
    │   │   └── event/                       Eventos de dominio (3 records)
    │   ├── config/              DatosDemoInitializer, ModelMapperConfig, OpenApiConfig
    │   ├── security/            JWT, filtro, CORS, reglas de acceso
    │   ├── gestion/             Módulo 1: empresas, usuarios, procesos, roles, compartir
    │   │   ├── controller/ (+ dto/)   Entrada HTTP y DTOs Request/Response
    │   │   ├── service/    (+ dto/)   Interfaz + Impl, orquestadores
    │   │   ├── repository/            Spring Data JPA
    │   │   └── model/                 Entidades y enums
    │   └── modelado/            Módulo 2: pools, lanes, nodos, arcos, mensajes, validación
    │       └── (misma estructura: controller, service, repository, model)
    ├── main/resources/
    │   ├── application.properties         Perfil por defecto (desarrollo, H2 en archivo)
    │   ├── application-prod.properties    Perfil prod (PostgreSQL, Flyway, Swagger apagado)
    │   └── db/migration/{common,postgresql}/   Scripts Flyway
    └── test/java/com/facimus/procesos/
        ├── arquitectura/        Reglas ArchUnit
        ├── gestion/, modelado/  Pruebas de controller, service y repository
        ├── security/            Seguridad e integración
        ├── transaccionalidad/   Rollback, invariantes y proxies lazy
        ├── migraciones/         Flyway contra las entidades
        └── config/, common/     Inicializador de datos y manejador de errores
```

**Dos módulos por dominio** (`gestion` y `modelado`), y dentro de cada uno las capas `controller / service / repository / model`. Esto se llama *empaquetado modular por dominio* y lo protege la prueba `EmpaquetadoTest`.

- `gestion`: quién usa el sistema y el ciclo de vida del proceso (Empresa, Usuario, Invitación, Proceso, RolProceso, PermisoEstructura, ProcesoCompartido, HistorialCambio).
- `modelado`: el contenido del diagrama (Pool, Lane, NodoFlujo con sus tres subtipos, Arco, Mensaje, Correlacion) y la validación del modelo.

---

## 3. Arquitectura en capas y flujo de una petición

### 3.1 Las capas

```
HTTP ──► Controller ──► Service (interfaz + Impl) ──► Repository ──► Base de datos
            │                  │                           │
            └── DTOs ◄─────────┘                       Entidades (model)
```

| Capa | Paquete | Qué hace | Qué NO hace |
|---|---|---|---|
| **Controller** | `..controller` | Recibe la petición HTTP, valida el cuerpo (`@Validated`), obtiene la identidad (`@AuthenticationPrincipal`), llama al service y arma `ResponseEntity` con el código HTTP. | No tiene lógica de negocio, no toca repositorios, no conoce entidades JPA, no usa ModelMapper, no lleva `@Transactional`. |
| **Service** | `..service` | Reglas de negocio, transacciones (`@Transactional`), mapeo entidad → DTO. Interfaz `XService` + implementación `XServiceImpl`. | No conoce HTTP (ni anotaciones web ni servlets). |
| **Repository** | `..repository` | Consultas a la base con Spring Data JPA. | No conoce controllers ni services. |
| **Model** | `..model` | Entidades JPA y enums. | No depende de capas superiores. |
| **DTO** | `..controller.dto` | Records `*Request` (entrada) y `*Response` (salida). Son el contrato de la API. | No son entidades: la API nunca expone entidades JPA. |

Cada una de estas reglas está verificada por pruebas ArchUnit (sección 8).

### 3.2 Flujo real de punta a punta: crear un proceso

Petición del frontend:

```http
POST /api/v1/procesos
Authorization: Bearer <jwt>
Content-Type: application/json

{ "nombre": "Solicitud de vacaciones", "descripcion": "...", "categoria": "Talento Humano" }
```

**Paso 1 — Filtros de seguridad.** `JwtAuthenticationFilter` extrae el token, lo valida, comprueba que el usuario siga activo y deja un `ApiPrincipal(usuarioId, empresaId, rol, email)` en el contexto de seguridad. Luego `SecurityConfig` decide si ese rol puede hacer un `POST` a `/api/v1/**` (solo `ADMINISTRADOR` y `EDITOR`).

**Paso 2 — Controller** (`gestion/controller/ProcesoController.java`):

```java
@PostMapping
public ResponseEntity<ProcesoResponse> crear(@Validated @RequestBody ProcesoRequest request,
        @AuthenticationPrincipal ApiPrincipal principal) {
    ProcesoResponse proceso = procesoOrquestadorService.crearConPoolInicial(principal.empresaId(),
            principal.usuarioId(), request);
    return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/api/v1/procesos/{id}").buildAndExpand(proceso.id()).toUri())
            .body(proceso);
}
```

Fíjate en tres cosas: (a) `@Validated` dispara Bean Validation sobre el DTO; (b) **la empresa sale de `principal.empresaId()`, no del cuerpo**; (c) responde `201 Created` con cabecera `Location`.

**Paso 3 — DTO de entrada** (`gestion/controller/dto/ProcesoRequest.java`):

```java
public record ProcesoRequest(
        @NotBlank(message = "El nombre es obligatorio.") String nombre,
        @NotBlank(message = "La descripcion es obligatoria.") String descripcion,
        @NotBlank(message = "La categoria es obligatoria.") String categoria) {
}
```

Si falta un campo, Spring lanza `MethodArgumentNotValidException` antes de entrar al service y `ApiExceptionHandler` responde 400 (sección 6).

**Paso 4 — Orquestador** (`gestion/service/ProcesoOrquestadorServiceImpl.java`). Crear un proceso implica dos cosas: el proceso y su pool inicial (la propia empresa). Cada una vive en un service distinto, así que un orquestador las junta en **una sola transacción**:

```java
@Transactional
@Override
public Proceso crearConPoolInicial(Long empresaId, Long usuarioId, String nombre, String descripcion,
        String categoria) {
    Proceso proceso = procesoService.crear(empresaId, usuarioId, nombre, descripcion, categoria);
    poolService.crear(empresaId, proceso.getId(), proceso.getEmpresa().getNombre(),
            TipoParticipante.EMPRESA, false);
    return proceso;
}

@Transactional
@Override
public ProcesoResponse crearConPoolInicial(Long empresaId, Long usuarioId, ProcesoRequest request) {
    Proceso proceso = crearConPoolInicial(empresaId, usuarioId, request.nombre(), request.descripcion(),
            request.categoria());
    return modelMapper.map(proceso, ProcesoResponse.class);
}
```

Si `poolService.crear` falla, **todo se revierte**: no queda un proceso sin pool. Lo demuestra `transaccionalidad/CreacionProcesoTransaccionalTest`.

**Paso 5 — Service del proceso** (`gestion/service/ProcesoServiceImpl.java`):

```java
@Transactional
@Override
public Proceso crear(Long empresaId, Long usuarioId, String nombre, String descripcion, String categoria) {
    nombre = recortar(nombre);
    categoria = recortar(categoria);
    if (procesoRepository.existsByEmpresaIdAndNombreIgnoreCaseAndActivoTrue(empresaId, nombre)) {
        throw new ReglaNegocioException("Ya existe un proceso activo con el nombre \"" + nombre + "\" en esta empresa.");
    }
    Empresa empresa = empresaService.obtener(empresaId);
    Usuario autor = usuarioService.obtener(empresaId, usuarioId);
    ...
    proceso.setEstado(EstadoProceso.BORRADOR);
    proceso.setActivo(true);
    proceso = procesoRepository.save(proceso);
    historialCambioService.registrar(proceso, autor, "Proceso creado.");
    return proceso;
}
```

Aquí se aplica la regla de negocio (nombre único entre procesos activos de la empresa), se guarda y se deja rastro en el historial.

**Paso 6 — Repository** (`gestion/repository/ProcesoRepository.java`): `existsByEmpresaIdAndNombreIgnoreCaseAndActivoTrue(...)` es una *consulta derivada*: Spring Data genera el SQL a partir del nombre del método. Se lee: existe un proceso con `empresa.id = ?`, `nombre` igual ignorando mayúsculas y `activo = true`.

**Paso 7 — Respuesta.** El service convierte la entidad a `ProcesoResponse` con ModelMapper, el controller arma el `201` y el JSON que ve el frontend es un record sin ningún detalle de JPA:

```java
public record ProcesoResponse(Long id, String nombre, String descripcion, String categoria,
        EstadoProceso estado, boolean activo, LocalDateTime fechaCreacion, LocalDateTime fechaModificacion) {
}
```

### 3.3 Flujo de publicar un proceso

```http
PATCH /api/v1/procesos/42
{ "estado": "PUBLICADO" }
```

1. `ProcesoController.cambiarEstado` → `procesoService.cambiarEstado(empresaId, id, usuarioId, request)`.
2. `ProcesoServiceImpl.cambiarEstado` carga el proceso **con `empresaId`** (si es de otra empresa: 404), rechaza volver de `PUBLICADO` a `BORRADOR` y, si el nuevo estado es `PUBLICADO`, **publica un evento de dominio**:

```java
if (nuevoEstado == EstadoProceso.PUBLICADO) {
    eventPublisher.publishEvent(new ProcesoPublicacionEvent(empresaId, procesoId));
}
```

3. `ValidacionModeloServiceImpl` escucha ese evento (síncrono, misma transacción) y valida el modelo completo:

```java
@EventListener
public void alPublicarProceso(ProcesoPublicacionEvent evento) {
    ejecutarValidaciones(evento.empresaId(), evento.procesoId());
}
```

4. Si hay advertencias lanza `ReglaNegocioException` → la transacción se revierte, el proceso sigue en borrador y la API responde **409** con el detalle. Si todo está bien, el service guarda el estado y registra en el historial "Proceso publicado (estado: BORRADOR → PUBLICADO).".

¿Por qué un evento y no una llamada directa? Se explica en la sección 5.4.

### 3.4 Dos "niveles" de métodos en cada service

Casi todas las interfaces de service tienen **dos juegos de métodos**:

- **Nivel dominio**: reciben tipos simples y devuelven entidades (`Proceso crear(Long empresaId, Long usuarioId, String nombre, ...)`). Los usan *otros services* para componer reglas.
- **Nivel API**: reciben el `Request` y devuelven el `Response` (`ProcesoResponse editarDatos(..., EditarProcesoRequest request)`). Los usa el controller. Internamente llaman al de nivel dominio y mapean con ModelMapper.

Ejemplo de `gestion/service/ProcesoServiceImpl.java`:

```java
@Transactional
@Override
public ProcesoResponse editarDatos(Long empresaId, Long procesoId, Long usuarioId, EditarProcesoRequest request) {
    Proceso proceso = editarDatos(empresaId, procesoId, usuarioId, request.nombre(), request.descripcion(),
            request.categoria());
    return modelMapper.map(proceso, ProcesoResponse.class);
}
```

Así el controller queda delgado y el mapeo ocurre dentro de la transacción del service.

### 3.5 Interfaz + Impl

Cada service es una interfaz (`ProcesoService`) y una clase (`ProcesoServiceImpl`) anotada con `@Service`. Los controllers y los demás services dependen **de la interfaz**. Está exigido por `ReglasDeCapasTest` (todo `@Service` se llama `*ServiceImpl` e implementa la interfaz con el mismo nombre sin el sufijo). Ventajas, con ejemplos del propio repositorio:

- Las pruebas de controller sustituyen el service por un mock de la interfaz.
- Se puede cambiar la implementación sin tocar a quien la usa.
- Es el patrón de capas clásico que se estudia en el curso.

El costo es más archivos por concepto (dos por cada service).

### 3.6 Orquestadores: cómo se evitan los ciclos

Cuando el service A necesita al B y el B al A, Spring no puede crear los beans (dependencia circular). La solución del proyecto es **una dirección única de dependencias** y, cuando un caso de uso necesita dos services que ya dependen en otra dirección, se crea un **orquestador** que depende de ambos:

| Orquestador | Junta | Caso de uso |
|---|---|---|
| `EmpresaOrquestadorService` | `EmpresaService` + `UsuarioService` | Registrar empresa y su administrador (HU-01) |
| `ProcesoOrquestadorService` | `ProcesoService` + `PoolService` | Crear proceso con su pool inicial (HU-04) |
| `PoolOrquestadorService` | `PoolService` + `LaneService` + `MensajeService` | Eliminar un pool con sus lanes (HU-21) |
| `LaneOrquestadorService` | `LaneService` + `RolProcesoService` | Crear/editar lane resolviendo su rol de proceso |
| `CorrelacionOrquestadorService` | `MensajeService` + `CorrelacionService` | Definir/eliminar la correlación de un mensaje |

La prueba `SeparacionRepositoriosTest.servicios_sin_ciclos` verifica que no existan ciclos entre services.

### 3.7 Un service solo toca su repositorio

Un service **solo inyecta el repositorio de su propia entidad**; para datos de otro dominio llama al service dueño. Ejemplo: `ProcesoServiceImpl` no usa `EmpresaRepository`, llama a `empresaService.obtener(empresaId)`. La excepción documentada es `NodoFlujoRepository` (ver 4.3). Lo comprueba `SeparacionRepositoriosTest` generando una prueba por cada repositorio.

---

## 4. Persistencia

### 4.1 Entidades y relaciones

Trece entidades "simples" más `NodoFlujo` (abstracta) con sus tres subtipos concretos. Resumen de relaciones (todas `@ManyToOne` con `FetchType.LAZY`, salvo la `@ManyToMany` de eventos):

```
Empresa 1 ──< Usuario
Empresa 1 ──< Proceso 1 ──< Pool 1 ──< Lane >── 1 RolProceso
                  │            │         │
                  │            │         └──< NodoFlujo (Actividad | Gateway | EventoMensaje)
                  │            └──< Arco >── origen/destino: NodoFlujo
                  ├──< Mensaje (poolOrigen, poolDestino, eventoThrow, eventoCatch, actividadError)
                  │       └──< Correlacion
                  ├──< HistorialCambio (autor: Usuario)
                  └──< ProcesoCompartido >── empresaInvitada: Empresa
Empresa 1 ──< InvitacionUsuario
Empresa 1 ──< PermisoEstructura   (único por empresa + rol)
EventoMensaje >──< Actividad      (tabla evento_mensaje_actividades)
```

Una entidad típica (`gestion/model/Proceso.java`):

```java
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "procesos")
public class Proceso extends EntidadEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false, columnDefinition = "text")
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoProceso estado = EstadoProceso.BORRADOR;

    @Column(nullable = false)
    private boolean activo = true;
    ...
}
```

Decisiones de mapeo para poder defender:

- **`@Enumerated(EnumType.STRING)`** siempre (nunca `ORDINAL`): si alguien reordena el enum, los datos guardados no se corrompen. Lo exige `HerenciaJpaTest`.
- **`@ManyToOne(fetch = LAZY)`**: las relaciones no se cargan hasta que se usan, para no traer medio modelo en cada consulta.
- **Texto largo** con `columnDefinition = "text"` en vez de `@Lob`: en PostgreSQL `@Lob` guarda un OID (referencia a un objeto grande) y no un texto; el comentario está en `EventoMensaje`.
- **`GenerationType.IDENTITY`**: la base genera el id.
- **Lombok** (`@Getter`, `@Setter`, `@NoArgsConstructor`, `@RequiredArgsConstructor`) elimina el código repetitivo. Las dependencias de los services se inyectan **por constructor** (`@RequiredArgsConstructor` + campos `final`); `ReglasDeCapasTest` prohíbe la inyección por campo.

### 4.2 Multitenencia por `empresa_id`

Una sola base de datos y un solo esquema sirven a todas las empresas (*tenants*). Cada fila pertenece a una empresa por la columna `empresa_id`. Se implementa con una superclase (`common/EntidadEmpresa.java`):

```java
@Getter
@Setter
@MappedSuperclass
public abstract class EntidadEmpresa {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id", nullable = false, updatable = false)
    protected Empresa empresa;
}
```

- `@MappedSuperclass`: no es una tabla propia; sus campos se copian a la tabla de cada entidad hija.
- `nullable = false, updatable = false`: toda fila tiene empresa y **nunca se puede cambiar de empresa**.
- Todas las entidades extienden `EntidadEmpresa` **excepto `Empresa`**, que es la raíz (`MultitenenciaTest`).
- Existe un índice por `empresa_id` en cada tabla (`V2__indices_por_empresa.sql`) porque toda consulta filtra por él.

**Cómo se garantiza el aislamiento en las consultas.** Todo repositorio (excepto `EmpresaRepository`) extiende `common/RepositorioTenant.java`:

```java
@NoRepositoryBean
public interface RepositorioTenant<T extends EntidadEmpresa> extends JpaRepository<T, Long> {

    Optional<T> findByIdAndEmpresaId(Long id, Long empresaId);

    List<T> findAllByEmpresaId(Long empresaId);

    boolean existsByIdAndEmpresaId(Long id, Long empresaId);
}
```

Los services consultan con `findByIdAndEmpresaId(...)` y no con `findById(...)`. Si el id existe pero es de otra empresa, la consulta no devuelve nada y se lanza `RecursoNoEncontradoException` → **404**. Para que nadie use por accidente los métodos heredados sin filtro, `AislamientoTenantTest` prohíbe que un service llame a `findById`, `existsById`, `deleteById`, `getReferenceById`, `findAllById` o `findAll` (sin `Specification`) sobre un `RepositorioTenant`.

La única consulta que rompe la regla a propósito es el login (`UsuarioRepository.findByEmailIgnoreCase`), porque en el login todavía no se sabe a qué empresa pertenece quien entra; de ahí que el correo sea único en todo el sistema (`Usuario.email` es `unique`).

### 4.3 Herencia `SINGLE_TABLE` (`NodoFlujo`)

Un diagrama BPMN tiene nodos de varios tipos que comparten campos (nombre, posición, lane). Se modelan con herencia JPA (`modelado/model/NodoFlujo.java`):

```java
@Entity
@Table(name = "nodos_flujo")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "tipo_nodo")
public abstract class NodoFlujo extends EntidadEmpresa {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nombre;
    private int posicionX;
    private int posicionY;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "lane_id", nullable = false)
    private Lane lane;
    private boolean activo = true;
}
```

Los tres subtipos son `Actividad` (`@DiscriminatorValue("ACTIVIDAD")`), `Gateway` (`"GATEWAY"`) y `EventoMensaje` (`"EVENTO_MENSAJE"`).

- **SINGLE_TABLE** = una sola tabla `nodos_flujo` con la columna discriminadora `tipo_nodo` y las columnas de todos los subtipos (`tipo_gateway`, `tipo_actividad`, `contenido_evento`...). Las columnas propias de un subtipo son nulas en las filas de los demás, por eso no llevan `nullable = false` (comentario en `Gateway.java`); la regla "el gateway debe tener tipo" la impone el DTO con `@NotNull`.
- **Por qué esta estrategia:** los subtipos tienen pocos campos propios, así que una tabla evita *joins* en cada lectura del diagrama (comentario de `NodoFlujo`). El costo: columnas nulas y menos restricciones `NOT NULL` en la base. En `V1__esquema_inicial.sql` hay un `check` que exige `origen_externo` en los eventos de mensaje.
- **Consultas polimórficas:** `NodoFlujoRepository` devuelve actividades, gateways y eventos mezclados; los services filtran con `instanceof`. Por eso `NodoFlujoRepository` es la única excepción a "cada repositorio lo usa su service dueño": `NodoFlujoServiceImpl` centraliza esas consultas y los services de actividad, gateway y evento lo usan.
- `ActividadServiceImpl.obtener` muestra el patrón:

```java
return nodoFlujoService.buscar(empresaId, actividadId)
        .filter(nodo -> nodo.isActivo())
        .filter(Actividad.class::isInstance)
        .map(Actividad.class::cast)
        .orElseThrow(() -> new RecursoNoEncontradoException("Actividad no encontrada."));
```

### 4.4 Eliminación lógica

En este sistema casi nada se borra de verdad: se pone `activo = false`. Razones: conservar el historial y las referencias (un arco o un mensaje apunta a nodos y pools; borrar la fila rompería claves foráneas y trazabilidad) y poder consultar procesos dados de baja. Hay dos mecanismos, ambos reales en el código:

| Mecanismo | Entidades | Cómo funciona |
|---|---|---|
| **Manual** (campo `activo` + filtro en cada consulta/service) | `Proceso`, `NodoFlujo` (actividad, gateway, evento), `Arco`, `Mensaje`, `RolProceso`, `Usuario`, `ProcesoCompartido` | El service hace `setActivo(false)` y guarda. Los repositorios usan el sufijo `ActivoTrue` o los services filtran con `isActivo()`. |
| **Hibernate** (`@SQLDelete` + `@SQLRestriction`) | `Pool`, `Lane`, `Correlacion` | `repository.delete(x)` se convierte en `UPDATE ... SET activo = false`, y `@SQLRestriction("activo = true")` oculta automáticamente las filas inactivas en toda consulta. |

Fragmento de `modelado/model/Pool.java`:

```java
@Entity
@Table(name = "pools")
@SQLDelete(sql = "UPDATE pools SET activo = false WHERE id = ?")
@SQLRestriction("activo = true")
public class Pool extends EntidadEmpresa {
    ...
}
```

Detalles para no confundirse:

- Eliminar un **proceso** (`ProcesoServiceImpl.eliminarLogico`) lo marca inactivo y registra en el historial; deja de listarse por defecto (`activo=true` en la búsqueda) pero se puede consultar con `activo=false` y por detalle (`obtenerParaConsulta`). Un proceso inactivo **no admite modificaciones**: cada service del modelado llama a `validarProcesoActivo` y responde 409 "No se permite modelar en un proceso inactivo."
- Eliminar una **actividad** también desactiva los arcos conectados (`arcoService.desactivarPorNodo`).
- Eliminar un **rol de proceso** se rechaza si lo usa alguna lane de un proceso activo (409).
- Eliminar un **mensaje** desactiva su correlación.
- Unicidad entre activos: el nombre de un proceso o de un rol no se puede repetir *entre los activos* de la empresa. El service lo valida (409 con mensaje claro) y, en PostgreSQL, índices únicos parciales lo garantizan en la base (`db/migration/postgresql/V3__unicidad_entre_activos.sql`).

### 4.5 Consultas: derivadas y JPQL

**Consultas derivadas** (Spring Data las genera a partir del nombre). Ejemplos reales:

```java
// RolProcesoRepository
List<RolProceso> findAllByEmpresaIdAndActivoTrue(Long empresaId);
Page<RolProceso> findAllByEmpresaIdAndActivoTrueAndNombreContainingIgnoreCase(
        Long empresaId, String nombre, Pageable pageable);

// ArcoRepository  (navega relaciones con guion bajo: pool -> proceso -> id)
List<Arco> findAllByPool_ProcesoIdAndEmpresaId(Long procesoId, Long empresaId);

// InvitacionUsuarioRepository
boolean existsByEmpresaIdAndEmailIgnoreCaseAndUsadaFalseAndFechaExpiracionAfter(
        Long empresaId, String email, LocalDateTime fecha);
```

**Consultas JPQL con `@Query`** (las cuatro que hay en el proyecto):

1. Búsqueda de procesos con filtros opcionales y paginación (`gestion/repository/ProcesoRepository.java`):

```java
@Query("""
        SELECT p FROM Proceso p
        WHERE p.empresa.id = :empresaId
          AND p.activo = :activo
          AND LOWER(p.nombre) LIKE :patronNombre ESCAPE '!'
          AND (:filtrarEstado = false OR p.estado = :estado)
          AND (:filtrarCategoria = false OR LOWER(p.categoria) = :categoria)
        """)
Page<Proceso> buscarConFiltros(@Param("empresaId") Long empresaId, @Param("activo") boolean activo,
        @Param("patronNombre") String patronNombre, @Param("filtrarEstado") boolean filtrarEstado,
        @Param("estado") EstadoProceso estado, @Param("filtrarCategoria") boolean filtrarCategoria,
        @Param("categoria") String categoria, Pageable pageable);
```

   Por qué así: los filtros son opcionales, pero PostgreSQL no infiere el tipo de un parámetro `null`; por eso cada filtro llega como un booleano ("¿filtro?") más un valor de relleno nunca nulo. El `ESCAPE '!'` evita que el usuario use `%` o `_` como comodines: `ProcesoServiceImpl.patronDeBusqueda` los escapa. Siempre se acota por `empresa.id`.

2. Procesos activos donde se usa cada rol, en **una sola consulta agrupada** (`modelado/repository/LaneRepository.java`), para no hacer una consulta por rol (problema N+1):

```java
@Query("select l.rolProceso.id, l.pool.proceso.id, l.pool.proceso.nombre from Lane l "
        + "where l.empresa.id = :empresaId and l.rolProceso.id in :rolIds and l.pool.proceso.activo = true "
        + "group by l.rolProceso.id, l.pool.proceso.id, l.pool.proceso.nombre")
List<Object[]> procesosActivosPorRoles(@Param("rolIds") Collection<Long> rolIds,
        @Param("empresaId") Long empresaId);
```

3. Mensajes activos que usan un evento (`modelado/repository/MensajeRepository.java`):

```java
@Query("select m from Mensaje m where m.empresa.id = :empresaId and m.activo = true "
        + "and (m.eventoThrow.id = :eventoId or m.eventoCatch.id = :eventoId) order by m.nombre")
List<Mensaje> findActivosPorEvento(@Param("eventoId") Long eventoId, @Param("empresaId") Long empresaId);
```

4. Nodos activos con un nombre dado, de cualquier subtipo (`modelado/repository/NodoFlujoRepository.java`):

```java
@Query("select n from NodoFlujo n where lower(n.nombre) = lower(:nombre) "
        + "and n.lane.pool.proceso.id = :procesoId and n.empresa.id = :empresaId and n.activo = true")
List<NodoFlujo> buscarActivosPorNombre(@Param("nombre") String nombre, @Param("procesoId") Long procesoId,
        @Param("empresaId") Long empresaId);
```

JPQL consulta **entidades y atributos Java** (`Proceso p`, `p.empresa.id`), no tablas y columnas.

**`@EntityGraph`** (`@EntityGraph(attributePaths = "rolProceso")` en `LaneRepository`, `"autor"` en `HistorialCambioRepository`, etc.): como las relaciones son LAZY, estas anotaciones le piden a JPA que cargue una relación concreta **en la misma consulta**. En `ArcoRepository` el motivo está documentado: `ArcoService.editar` hace `instanceof` sobre origen y destino, y un proxy lazy sin inicializar nunca pasa un `instanceof` de subclase.

### 4.6 Paginación

El proyecto no expone el `Page` de Spring tal cual; define su propio contrato (`common/api/PageResponse.java`):

```java
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <T> PageResponse<T> from(Page<T> pagina) {
        return new PageResponse<>(pagina.getContent(), pagina.getNumber(), pagina.getSize(),
                pagina.getTotalElements(), pagina.getTotalPages());
    }
}
```

Uso en `ProcesoServiceImpl.consultarPagina`:

```java
Page<ProcesoResponse> procesos = buscar(empresaId, nombre, estado, categoria, activo,
        PageRequest.of(pagina, tamano, Sort.by("fechaModificacion").descending()))
        .map(p -> modelMapper.map(p, ProcesoResponse.class));
return PageResponse.from(procesos);
```

Parámetros de la petición: `pagina` (desde 0, mínimo 0) y `tamano` (por defecto 10, entre 1 y 100), validados con `@Min` / `@Max` en el controller. Los procesos se ordenan por `fechaModificacion` descendente; los roles (`GET /api/v1/roles/consulta`) por nombre sin distinguir mayúsculas y luego por id. Un contrato propio estable evita que un cambio interno de Spring altere el JSON que consume Angular.

### 4.7 Flyway y perfiles H2 / PostgreSQL

Hay dos perfiles de ejecución:

| | Perfil por defecto (desarrollo) | Perfil `prod` |
|---|---|---|
| Base | H2 en archivo `./data/procesos` | PostgreSQL (variables `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`) |
| Esquema | `ddl-auto=update` (Hibernate lo crea) | `ddl-auto=validate` (Hibernate solo valida) |
| Flyway | apagado (`spring.flyway.enabled=false`) | encendido, con `classpath:db/migration/common` + `classpath:db/migration/postgresql` |
| Consola H2 | `/h2-console` activa | desactivada |
| Swagger | activo | apagado |
| `JWT_SECRET` | opcional (clave aleatoria) | obligatorio, mínimo 32 caracteres |
| Datos de ejemplo | sí (`DatosDemoInitializer`) | no |

Pruebas: `src/test/resources/application-test.properties` usa H2 **en memoria** (`jdbc:h2:mem:testdb`) con `ddl-auto=create-drop`, para que ninguna prueba toque la base de desarrollo ni la de producción.

**Flyway** versiona el esquema en `src/main/resources/db/migration/`:

| Archivo | Contenido |
|---|---|
| `common/V1__esquema_inicial.sql` | Todas las tablas, claves foráneas y restricciones `check` |
| `common/V2__indices_por_empresa.sql` | Un índice por `empresa_id` en cada tabla |
| `postgresql/V3__unicidad_entre_activos.sql` | Índices únicos parciales (solo PostgreSQL) |
| `common/V5__eventos_mensaje_actividades.sql` | Tabla N:M entre eventos de mensaje y actividades |

Reglas: una migración aplicada **no se edita** (cambios nuevos van en un `V<n>` posterior; el propio `V1` lo dice en su cabecera) y la carpeta `postgresql/` solo se carga en prod porque H2 no entiende los índices parciales.

`migraciones/MigracionesTest` ejecuta las migraciones comunes sobre H2 en modo PostgreSQL con `ddl-auto=validate`: si alguien cambia una entidad y olvida su migración, **el contexto no arranca y la prueba falla**.

### 4.8 Inicializador de datos de ejemplo

`config/DatosDemoInitializer.java` es un `CommandLineRunner` marcado `@Profile("default")` y `@Transactional`:

```java
@Component
@Profile("default")
@Transactional
@RequiredArgsConstructor
public class DatosDemoInitializer implements CommandLineRunner {
    ...
    @Override
    public void run(String... args) {
        if (empresaRepository.count() > 0) {
            return;
        }
        Empresa demo = crearEmpresa("Empresa Demo S.A.S.", "900123456-1", "contacto@demo.com");
        ...
    }
}
```

- Solo corre en el perfil por defecto (ni prod ni test) y **solo si la base está vacía**, así reiniciar no duplica datos.
- Crea dos empresas independientes. La primera (Empresa Demo) trae un usuario por rol (`admin@demo.com`, `editor@demo.com`, `lectura@demo.com`), el proceso de referencia "Solicitud de vacaciones" completo (pools, lanes, actividades, gateway, arcos, mensaje a un sistema externo, correlación), un proceso en borrador y uno dado de baja. La segunda (Empresa Beta, `admin@beta.com`) reutiliza el nombre "Solicitud de vacaciones" para demostrar el aislamiento. Las contraseñas están en el propio archivo.
- Es **el único lugar que habla directo con repositorios saltándose los services** (la regla de capas lo permite con una excepción explícita para `config`). Es una excepción razonable: es código de arranque, no lógica de la API.

---

## 5. Servicios y reglas de negocio

### 5.1 Transacciones

- Todos los métodos que escriben llevan `@Transactional`; los de lectura, `@Transactional(readOnly = true)` (optimización y declaración de intención).
- Las transacciones **solo viven en el service** (nunca en el controller; `ReglasDeCapasTest.transactional_no_va_en_controllers`).
- Una `RuntimeException` (todas las excepciones de dominio lo son) revierte la transacción. Cuando un orquestador o un service llama a otro, **participan en la misma transacción** (propagación `REQUIRED` por defecto): por eso el rollback de "crear proceso + pool" es atómico.
- `spring.jpa.open-in-view=false`: la sesión de Hibernate se cierra al terminar el service. Consecuencia práctica: **todo lo que necesite relaciones lazy (incluido el mapeo a DTO) debe ocurrir dentro del service**.

### 5.2 Excepciones de dominio

| Excepción (`common/`) | Significado | HTTP |
|---|---|---|
| `RecursoNoEncontradoException` | No existe, **o pertenece a otra empresa** (indistinguible a propósito) | 404 |
| `ReglaNegocioException` | La operación viola una regla que depende del estado de los datos (NIT duplicado, arco entre pools distintos, rol en uso...) | 409 |
| `SolicitudInvalidaException` | La petición es incoherente por sí misma (combinación de campos sin sentido) | 400 |

### 5.3 Reglas de negocio por módulo

**Módulo `gestion`**

| Service | Reglas principales |
|---|---|
| `EmpresaServiceImpl` | NIT normalizado (sin espacios ni puntos, mayúsculas); no se registran dos empresas con el mismo NIT. |
| `UsuarioServiceImpl` | Correo único en todo el sistema. Contraseña con BCrypt. Nunca se degrada ni se desactiva al **último administrador activo** de la empresa. Login con tiempo de respuesta uniforme (sección 7). |
| `InvitacionUsuarioServiceImpl` | Token UUID, vigencia de 48 horas, un solo uso; no se invita a un correo ya registrado ni con invitación pendiente; al aceptar se crea el usuario. |
| `ProcesoServiceImpl` | Nombre único entre activos de la empresa (sin distinguir mayúsculas); nace en `BORRADOR`; un publicado no vuelve a borrador; publicar exige modelo válido; toda operación deja huella en el historial; el historial resume qué campos cambiaron. |
| `HistorialCambioServiceImpl` | Bitácora de solo inserción: nunca se edita ni se elimina. |
| `RolProcesoServiceImpl` | Nombre único entre roles activos; al renombrar un rol se renombran las lanes que lo usan; no se elimina un rol en uso en procesos activos. |
| `PermisoEstructuraServiceImpl` | Política por rol de qué estructura puede crear/editar/eliminar (pools y lanes). El administrador siempre puede todo; `SOLO_LECTURA` no puede recibir permisos de modificación; por defecto el editor puede crear y editar, pero no eliminar. |
| `ProcesoCompartidoServiceImpl` | Se comparte con otra empresa **siempre en solo lectura**; no se comparte con la propia empresa; revocar es una baja lógica; la empresa invitada solo ve el diagrama. |
| `ProcesoDiagramaServiceImpl` | Arma el diagrama completo (pools, lanes, actividades, gateways, eventos, arcos y mensajes) en una sola respuesta. |

**Módulo `modelado`**

| Service | Reglas principales |
|---|---|
| `PoolServiceImpl` | Un pool nuevo toma el siguiente `orden`; no se modela en un proceso inactivo; no se marca "caja negra" un pool que tiene lanes (vía evento); no se cambia el tipo o la caja negra si es destino externo de mensajes activos (vía evento). |
| `PoolOrquestadorServiceImpl` | Eliminar un pool exige que no tenga nodos activos ni mensajes activos; elimina sus lanes y luego el pool, en una transacción. |
| `LaneServiceImpl` | Un pool de caja negra no admite lanes; no se elimina una lane con elementos activos; reordenar exige la lista completa y sin duplicados de ids del pool. |
| `ActividadServiceImpl` / `GatewayServiceImpl` / `EventoMensajeServiceImpl` | Nombre de nodo único (sin mayúsculas) dentro del proceso; un pool de caja negra no admite nodos; mover un nodo a otra lane exige que sea del mismo proceso y, si cambia de pool, que no tenga arcos conectados (regla compartida en `ReglasMovimientoNodo`). |
| `ArcoServiceImpl` | Origen y destino distintos y **del mismo pool**; no hay dos arcos activos entre los mismos nodos; la **condición** solo es válida (y obligatoria) en salidas de gateways exclusivos o inclusivos, salvo gateway de convergencia; un Message Catch de inicio no admite arcos entrantes. |
| `MensajeServiceImpl` | Conecta dos pools distintos; debe tener Message Throw; Throw en el pool origen y Catch en el pool destino; el nombre y la clave de correlación deben coincidir entre mensaje y eventos; un mensaje a un sistema externo exige un pool `SISTEMA_EXTERNO` de caja negra, destino documentado y política de fallo. |
| `CorrelacionServiceImpl` | La correlación coincide con la clave de los eventos y no puede ser ambigua (mismo nombre + misma clave) dentro del proceso. |
| `ValidacionModeloServiceImpl` | Reúne **todas** las advertencias del modelo (ver abajo). |
| `AuditoriaModeladoServiceImpl` | Cada cambio del diagrama queda en el historial del proceso con el usuario autenticado. |

**Validación del modelo antes de publicar** (`ValidacionModeloServiceImpl.obtenerAdvertencias`): exactamente un pool de tipo `EMPRESA` y que no sea caja negra; los pools de caja negra sin lanes; gateway divergente con al menos dos salidas y convergente (2 o más entradas) con una salida; todos los arcos que salen de un gateway exclusivo/inclusivo con condición; Message Catch con mensaje vinculado u origen externo; Catch intermedio con clave de correlación; cada mensaje con Throw, receptor (Catch o destino externo), clave y correlación definida y sin ambigüedades; coherencia de claves entre mensaje y eventos; destinos externos bien formados. La misma lista alimenta dos usos: `GET /api/v1/procesos/{id}/validacion` (consulta, devuelve `{valido, advertencias}`) y la publicación (falla con 409 si la lista no está vacía).

### 5.4 Eventos de dominio

Tres records en `common/event/`, publicados con `ApplicationEventPublisher` y escuchados con `@EventListener`:

| Evento | Lo publica | Lo escucha | Para qué |
|---|---|---|---|
| `ProcesoPublicacionEvent` | `ProcesoServiceImpl.cambiarEstado` | `ValidacionModeloServiceImpl` | Validar el modelo al publicar |
| `PoolMarcadoCajaNegraEvent` | `PoolServiceImpl.editar` | `LaneServiceImpl` | Impedir caja negra si el pool tiene lanes |
| `PoolParticipanteModificadoEvent` | `PoolServiceImpl.editar` | `MensajeServiceImpl` | Impedir cambiar el pool si es destino externo de mensajes |

**Por qué existen:** si `ProcesoService` llamara directamente a `ValidacionModeloService`, habría un ciclo (`ProcesoService → ValidacionModeloService → PoolService/MensajeService → ProcesoService`). El evento invierte la dependencia: el publicador no conoce al oyente. Son **síncronos y dentro de la misma transacción**, así que si el oyente lanza una excepción, la operación entera se aborta antes de guardar nada. Y la regla se cumple **aunque alguien llame al service directamente** sin pasar por el controller (cubierto por `InvariantesDirectasEnServicioTest`).

### 5.5 ModelMapper en el service (¿por qué ahí?)

`config/ModelMapperConfig.java` registra un `AbstractConverter` explícito por cada pareja entidad → DTO de respuesta. Ejemplo:

```java
mapper.addConverter(new AbstractConverter<Lane, LaneResponse>() {
    @Override
    protected LaneResponse convert(Lane l) {
        return new LaneResponse(l.getId(), l.getNombre(), l.getOrden(),
                l.getPool().getId(), l.getRolProceso().getId(),
                l.getRolProceso().getNombre());
    }
});
```

Razones para mapear **dentro del service** y no en el controller:

1. **El controller solo conoce DTOs.** Si mapeara, tendría que manejar entidades JPA; `ReglasDeCapasTest.las_entidades_no_salen_por_la_api` prohíbe que un controller dependa de una entidad o de `ModelMapper`.
2. **Evita `LazyInitializationException`.** Los converters navegan relaciones lazy (`l.getRolProceso().getNombre()`). Con `open-in-view=false`, fuera de la transacción del service esa navegación fallaría; dentro, funciona. (Para las lecturas que devuelven entidades a otros services, los `@EntityGraph` del punto 4.5 cargan lo necesario.)
3. **El service es el que ya está en transacción** y conoce cómo armar el DTO (por ejemplo `RolProcesoVista` combina el rol con el conteo de usos).

Los DTOs que necesitan composición propia (`LoginResponse`, `ProcesoDetalleResponse`, `ProcesoDiagramaResponse`) se arman a mano en el service: no encajan en un solo converter.

> Nota para la defensa: algunos comentarios de Javadoc antiguos (por ejemplo en `common/EntidadEmpresa.java` y `config/ModelMapperConfig.java`) hablan de mapear "en los controllers". El código real y la regla ArchUnit mapean en el service; guíate por el código.

---

## 6. API REST

### 6.1 Convención de rutas

- Todo bajo `/api/v1` (versión en la URL).
- **Recursos en plural y en español**: `/procesos`, `/pools`, `/lanes`, `/actividades`, `/gateways`, `/eventos-mensaje`, `/arcos`, `/mensajes`, `/roles`, `/usuarios`.
- **Recurso hijo anidado al crear o listar** (el padre va en la ruta): `POST /procesos/{procesoId}/pools`, `GET /pools/{poolId}/lanes`, `POST /lanes/{laneId}/actividades`. **Recurso individual por su id directo**: `GET/PUT/DELETE /pools/{id}`.
- Verbos: `GET` consulta, `POST` crea, `PUT` edita los datos, `PATCH` cambios parciales (`PATCH /procesos/{id}` cambia el estado; `PATCH /usuarios/{id}` rol o activo; `PATCH /pools/{poolId}/lanes/orden` reordena), `DELETE` elimina (lógica).
- Subrecursos de consulta: `/procesos/{id}/diagrama`, `/procesos/{id}/historial`, `/procesos/{procesoId}/validacion`, `/actividades/{id}/impacto-eliminacion` (qué pasaría si se elimina, antes de hacerlo).

Mapa de endpoints por controller:

| Controller | Rutas |
|---|---|
| `AuthController` | `POST /auth/login`, `POST /auth/logout` |
| `EmpresaController` | `POST /empresas` |
| `UsuarioController` | `GET`, `POST /usuarios`; `GET`, `PATCH`, `DELETE /usuarios/{id}` |
| `InvitacionUsuarioController` | `POST /usuarios/invitaciones`, `POST /usuarios/invitaciones/{token}/aceptar` |
| `ProcesoController` | `GET`, `POST /procesos`; `GET`, `PUT`, `PATCH`, `DELETE /procesos/{id}`; `GET /procesos/{id}/diagrama`, `GET /procesos/{id}/historial` |
| `RolProcesoController` | `GET /roles`, `GET /roles/consulta` (paginado), `POST /roles`, `GET`, `PUT`, `DELETE /roles/{id}` |
| `PermisoEstructuraController` | `GET /permisos-estructura`, `GET /permisos-estructura/mio`, `PUT /permisos-estructura/{rol}` |
| `ProcesoCompartidoController` | `GET`, `POST /procesos/{procesoId}/compartidos`, `DELETE /procesos/{procesoId}/compartidos/{empresaInvitadaId}`, `GET /procesos-compartidos`, `GET /procesos-compartidos/{procesoId}` |
| `PoolController` | `GET`, `POST /procesos/{procesoId}/pools`; `GET`, `PUT`, `DELETE /pools/{id}` |
| `LaneController` | `GET`, `POST /pools/{poolId}/lanes`; `GET`, `PUT`, `DELETE /lanes/{id}`; `PATCH /pools/{poolId}/lanes/orden` |
| `ActividadController`, `GatewayController`, `EventoMensajeController` | `POST`, `GET /lanes/{laneId}/<recurso>`; `GET`, `PUT`, `DELETE /<recurso>/{id}`; `GET /<recurso>/{id}/impacto-eliminacion` |
| `ArcoController` | `POST /arcos`; `GET /pools/{poolId}/arcos`; `GET`, `PUT`, `DELETE /arcos/{id}`; `GET /arcos/{id}/impacto-eliminacion` |
| `MensajeController` | `GET`, `POST /procesos/{procesoId}/mensajes`; `GET`, `PUT`, `DELETE /mensajes/{id}` |
| `CorrelacionController` | `PUT`, `GET`, `DELETE /mensajes/{mensajeId}/correlacion` |
| `ValidacionModeloController` | `GET /procesos/{procesoId}/validacion` |

(Todas las rutas anteponen `/api/v1`.)

### 6.2 Códigos HTTP

| Código | Cuándo |
|---|---|
| **200 OK** | Consulta o edición exitosa (devuelve el recurso) |
| **201 Created** | Creación exitosa, con cabecera `Location` apuntando al recurso nuevo |
| **204 No Content** | Eliminación (lógica) o logout exitoso, sin cuerpo |
| **400 Bad Request** | Validación del cuerpo o de parámetros fallida, JSON malformado, tipo de parámetro inválido, `SolicitudInvalidaException` |
| **401 Unauthorized** | Token ausente, inválido o expirado; credenciales incorrectas |
| **403 Forbidden** | Token válido pero el rol (o el permiso de estructura) no alcanza |
| **404 Not Found** | El recurso no existe **o es de otra empresa** |
| **409 Conflict** | Regla de negocio violada o violación de restricción en la base (únicos, claves foráneas) |
| **500 Internal Server Error** | Error inesperado (mensaje genérico; el detalle va al log) |

### 6.3 Errores uniformes con `ProblemDetail`

Todos los errores salen en el formato estándar RFC 9457 (`application/problem+json`) usando `ProblemDetail` de Spring, con `status`, `title`, `detail` e `instance`. Se centralizan en `common/api/ApiExceptionHandler.java`, un `@RestControllerAdvice` que extiende `ResponseEntityExceptionHandler`:

```java
@ExceptionHandler(RecursoNoEncontradoException.class)
public ProblemDetail manejarNoEncontrado(RecursoNoEncontradoException ex, WebRequest req) {
    return construir(HttpStatus.NOT_FOUND, "Recurso no encontrado", ex.getMessage(), req);
}

@ExceptionHandler(ReglaNegocioException.class)
public ProblemDetail manejarReglaNegocio(ReglaNegocioException ex, WebRequest req) {
    return construir(HttpStatus.CONFLICT, "Regla de negocio violada", ex.getMessage(), req);
}

@Override
protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
        HttpHeaders headers, HttpStatusCode status, WebRequest req) {
    String detalle = ex.getBindingResult().getFieldErrors().stream()
            .map(err -> err.getField() + ": " + err.getDefaultMessage())
            .collect(Collectors.joining("; "));
    return ResponseEntity.badRequest()
            .body(construir(HttpStatus.BAD_REQUEST, "Validación fallida", detalle, req));
}
```

Ejemplo de respuesta:

```json
{
  "type": "about:blank",
  "title": "Validación fallida",
  "status": 400,
  "detail": "nombre: El nombre es obligatorio.",
  "instance": "/api/v1/procesos"
}
```

Detalles de seguridad en los errores:

- `DataIntegrityViolationException` responde un 409 **genérico** ("entra en conflicto con datos existentes"): nunca se expone el SQL ni el nombre de la restricción; el detalle va al log.
- `Exception` (último recurso) responde 500 genérico y registra el error con `Logger`.
- `application.properties` apaga además los detalles de error por defecto de Spring: `server.error.include-stacktrace=never`, `include-message=never`, `include-binding-errors=never`.
- Los 401 y 403 que decide Spring Security (antes de llegar a un controller) se producen con `JwtAuthEntryPoint` y `JwtAccessDeniedHandler`, que también escriben `ProblemDetail`, para que **toda la API hable el mismo formato**. Esto importa al frontend: un solo interceptor de errores en Angular.

### 6.4 Validación con Bean Validation

La validación está en el **DTO** (anotaciones sobre los componentes del record) y se activa con `@Validated @RequestBody` en el controller:

```java
public record RegistroEmpresaRequest(
        @NotBlank(message = "El nombre de la empresa es obligatorio.") String nombreEmpresa,
        @NotBlank(message = "El NIT es obligatorio.") String nit,
        @NotBlank(message = "El correo de contacto es obligatorio.")
        @Email(message = "El correo de contacto no es valido.") String correoContacto,
        ...
        @NotBlank(message = "La contrasena es obligatoria.")
        @Size(min = 8, message = "La contrasena debe tener al menos 8 caracteres.") String passwordAdmin) {
}
```

- Anotaciones usadas: `@NotBlank`, `@NotNull`, `@NotEmpty`, `@Email`, `@Size`, `@AssertTrue`, y `@Min`/`@Max` sobre parámetros de consulta.
- Validaciones cruzadas entre campos con `@AssertTrue` sobre un método del record: `ActualizarUsuarioRequest` exige enviar al menos `rolAcceso` o `activo`; `MensajeRequest` rechaza con 400 los campos avanzados si no hay `eventoThrowId`.
- **División de responsabilidades:** el DTO valida la *forma* (¿viene? ¿es un correo?) → 400. El service valida el *significado y el estado* (¿ya existe ese NIT?) → 404/409.
- Los DTOs **no traen `empresaId`**: lo garantiza `AislamientoTenantTest.requests_sin_empresa`.
- `ValidacionRequestsTest` prueba las validaciones de los DTOs.

### 6.5 Swagger / OpenAPI

- springdoc genera la documentación desde las anotaciones: `@Tag` por controller, `@Operation(summary, description)` por endpoint y `@ApiResponse` por código posible.
- `config/OpenApiConfig.java` define el título, la versión y un esquema de seguridad `bearerAuth` (HTTP bearer, formato JWT): en Swagger UI se puede pulsar "Authorize" y pegar el token.
- Rutas: `/swagger-ui.html` (interfaz) y `/v3/api-docs` (JSON). `springdoc.paths-to-match=/api/**` limita la documentación a la API REST.
- **Se apaga en producción** (`springdoc.api-docs.enabled=false`, `springdoc.swagger-ui.enabled=false` en `application-prod.properties`): publicaría el mapa completo de la API.
- `@SecurityRequirements()` vacío en login y registro de empresa indica que esos endpoints son públicos.

### 6.6 CORS (para Angular)

`security/CorsConfig.java` registra un `CorsFilter` con orígenes permitidos desde la propiedad `cors.allowed-origins` (variable `CORS_ALLOWED_ORIGINS`, por defecto `http://localhost:4200`), cabeceras `Authorization`, `Content-Type`, `Accept` y métodos `GET, POST, PUT, PATCH, DELETE`. El filtro tiene orden `-102`, antes de la cadena de Spring Security: si no, la petición *preflight* (`OPTIONS`) llegaría sin cabeceras CORS y el navegador la bloquearía.

---

## 7. Seguridad a nivel de backend

Solo lo necesario para entender el código; el detalle completo corresponde a la tercera entrega.

### 7.1 JWT y BCrypt

- **Login** (`POST /api/v1/auth/login`): `UsuarioServiceImpl.autenticar` busca el usuario por correo, compara la contraseña con **BCrypt** (`BCryptPasswordEncoder`, bean en `SecurityConfig`) y, si es correcta, `JwtService.generarToken` emite un JWT firmado (HS256) con el correo como `subject` y los claims `usuarioId`, `empresaId` y `rol`. Expira a los 1800 segundos por defecto (`jwt.expiration-seconds`).
- Las contraseñas **nunca se guardan en claro**: solo el hash (`Usuario.passwordHash`).
- **Secreto:** en el perfil `prod`, `JwtService` se niega a arrancar si `JWT_SECRET` falta o tiene menos de 32 caracteres. En desarrollo, sin secreto se usa una clave aleatoria (los tokens mueren al reiniciar).
- **Credenciales incorrectas:** responde un mensaje genérico ("Correo o contrasena incorrectos.") exista o no el correo, y **siempre ejecuta un `matches`**, contra un hash ficticio si el usuario no existe o está inactivo, para que el tiempo de respuesta no revele si el correo está registrado.
- **Logout** (`POST /auth/logout`) es *stateless*: devuelve 204 y el cliente descarta su token; el servidor no guarda sesiones (por eso no hay `HttpSession`, comprobado por `AislamientoTenantTest`).

### 7.2 El filtro y el `ApiPrincipal`

`security/JwtAuthenticationFilter.java` (extiende `OncePerRequestFilter`) se coloca antes de `UsernamePasswordAuthenticationFilter`:

```java
String header = request.getHeader(HttpHeaders.AUTHORIZATION);
if (header != null && header.startsWith(PREFIJO)) {
    jwtService.validar(header.substring(PREFIJO.length()))
            .flatMap(this::usuarioVigente)
            .ifPresent(principal -> autenticar(principal, request));
}
filterChain.doFilter(request, response);
```

- Valida firma y expiración. Además consulta al usuario (`usuarioService.obtener(empresaId, usuarioId)`) y exige que siga **activo**: desactivar a alguien lo deja sin acceso de inmediato, aunque su token no haya vencido.
- Si algo falla, **no autentica y deja pasar**; es `SecurityConfig` quien decide (401 si la ruta exige autenticación).
- La identidad queda en un record (`security/ApiPrincipal.java`):

```java
/** Identidad del usuario autenticado. El tenant sale de aqui, nunca del request. */
public record ApiPrincipal(Long usuarioId, Long empresaId, RolAcceso rol, String email) { ... }
```

Los controllers la reciben con `@AuthenticationPrincipal ApiPrincipal principal`.

### 7.3 Roles y reglas de acceso

`security/SecurityConfig.java` define la política (API sin sesión, `SessionCreationPolicy.STATELESS`). Los roles son *authorities* (`hasAuthority("ADMINISTRADOR")`). Resumen de las reglas, en el orden en que se evalúan:

| Regla | Quién |
|---|---|
| `POST /auth/login`, `POST /empresas`, `POST /usuarios/invitaciones/*/aceptar`, Swagger, `/h2-console`, `/error` | Público |
| `/usuarios/**` (cualquier método, incluido GET) | `ADMINISTRADOR` |
| `GET /permisos-estructura/mio` | Cualquier autenticado |
| `/permisos-estructura/**` | `ADMINISTRADOR` |
| `POST/DELETE` de compartir procesos | `ADMINISTRADOR` |
| Cualquier otro `GET /api/v1/**` | Cualquier autenticado (incluye `SOLO_LECTURA`) |
| `/roles/**` (escritura) | `ADMINISTRADOR` |
| `DELETE` de procesos, actividades, eventos, arcos, gateways, mensajes | `ADMINISTRADOR` |
| Resto de `POST/PUT/PATCH/DELETE /api/v1/**` | `ADMINISTRADOR` o `EDITOR` |
| `DELETE` de **pools y lanes** | No está en `SecurityConfig` a propósito: lo decide `PermisoEstructuraService` (administrador siempre; editor solo con permiso explícito; solo lectura nunca) |

Los permisos de estructura se evalúan en el controller con `permisoEstructuraService.validar(empresaId, rol, OperacionEstructura.X)`, que lanza `AccessDeniedException` → 403.

CSRF: se deja habilitado globalmente y solo se exime `/api/v1/**` y `/h2-console/**`, porque la API autentica con un token en la cabecera `Authorization` (no con cookies), así que el ataque CSRF no aplica.

### 7.4 Aislamiento entre empresas (IDOR)

**IDOR** (*Insecure Direct Object Reference*) es acceder a un recurso ajeno cambiando un id en la URL. Se evita en tres pasos encadenados:

1. La **empresa sale del token** (`ApiPrincipal.empresaId()`), nunca del cuerpo ni de la URL. Los `Request` no tienen campo `empresa`/`empresaId` (regla ArchUnit).
2. Todas las consultas puntuales usan `findByIdAndEmpresaId` (contrato `RepositorioTenant`, regla ArchUnit para los services).
3. Si el id es de otra empresa, el resultado es el mismo que si no existiera: **404**, sin revelar que el recurso existe.

`security/AislamientoEmpresasIntegracionTest` crea dos empresas con datos y verifica con el token de la empresa A que ningún recurso de la B es alcanzable (ni leer, ni editar, ni eliminar).

La excepción deliberada es el **proceso compartido**: la empresa invitada lee el diagrama de otra empresa solo por el camino explícito `GET /procesos-compartidos/{procesoId}`, que consulta por `empresaInvitadaId` y registro activo de `ProcesoCompartido`, y es de solo lectura.

---

## 8. Pruebas y calidad

### 8.1 Tipos de prueba (más de 600 métodos de prueba)

Ejecutar todo: `mvn clean verify` (corre pruebas, genera el informe de JaCoCo y aplica sus umbrales).

| Tipo | Anotaciones | Dónde | Qué comprueba |
|---|---|---|---|
| **Unitarias de service** | `@ExtendWith(MockitoExtension.class)`, `@Mock`, `@InjectMocks` | `gestion/service`, `modelado/service` | Reglas de negocio aisladas, con los repositorios y services vecinos simulados. Se separan por sufijo: `*ServiceTest` (reglas), `*ServiceMapeoTest` y `*ServiceResponseTest` (el mapeo a DTO). |
| **De controller** | `@WebMvcTest(XController.class)` + `MockMvc` | `gestion/controller`, `modelado/controller` | Rutas, códigos HTTP, validación del cuerpo y formato de error, con el service simulado. `security/SeguridadControllersTestAutoConfiguration` aplica en esas pruebas las mismas reglas de acceso y respuestas 401/403 que producción, y `ApiPrincipalRequestPostProcessor` inyecta un usuario autenticado. |
| **De repositorio** | `@DataJpaTest` | `ProcesoRepositoryTest`, `ConsultasGestionRepositoryTest`, `ConsultasModeladoRepositoryTest` | Las consultas JPQL y derivadas contra una H2 real (filtros, escape de `%`, aislamiento entre empresas). |
| **De integración** | `@SpringBootTest` (+ `@AutoConfigureMockMvc`) | `security/`, `transaccionalidad/`, `migraciones/`, `config/`, `modelado/ModeladoBrechasIntegracionTest` | Aplicación completa con H2 en memoria: seguridad extremo a extremo, **rollback transaccional**, invariantes del modelo, proxies lazy, migraciones Flyway, inicializador de datos. |
| **De arquitectura** | ArchUnit | `arquitectura/` | Las reglas de diseño (8.3). |

Convenciones: `@DisplayName` descriptivo (a menudo con la historia: "HU-04: crear proceso en BORRADOR"); nombres del estilo `Sujeto_accion_resultado` en repositorios y migraciones; en las pruebas de service, `assertThrows(ReglaNegocioException.class, ...)` más `verify(repo, never()).save(any())` para comprobar que, ante un error, no se guardó nada.

Pruebas de **transaccionalidad** destacadas: `CreacionProcesoTransaccionalTest` (si falla el pool, no queda el proceso), `RegistroEmpresaTransaccionalTest`, `EliminarPoolTransaccionalTest`, `CorrelacionMensajeTransaccionalTest`.

### 8.2 JaCoCo — umbrales reales

Configurados en `pom.xml`. El informe se genera en la fase `test` y la regla `check` se evalúa en `verify`. Se excluyen del cálculo `**/dto/**`, `**/config/**` y `**/*Application.class`.

| Alcance | Contador | Mínimo |
|---|---|---|
| Paquetes `com.facimus.procesos.*.service` | Líneas | **70 %** |
| Paquetes `com.facimus.procesos.*.service` | Ramas | **60 %** |
| Todo el proyecto (bundle) | Líneas | **50 %** |

Si no se cumplen, `mvn verify` falla y también el pipeline. La capa de service pide más porque ahí está la lógica de negocio.

### 8.3 ArchUnit — las reglas reales

Las pruebas de `src/test/java/com/facimus/procesos/arquitectura/` importan las clases compiladas y verifican la arquitectura como si fuera una prueba más.

**`EmpaquetadoTest`** (estructura):
1. Los `@RestController` viven en `..controller`.
2. Los `@Service` viven en `..service`.
3. Los repositorios viven en `..repository` (o `..common`).
4. Las `@Entity` viven en `..model`.
5. Los controllers no dependen de repositorios.
6. `modelado` no depende de los controllers de `gestion`.
7. Los services no dependen de controllers (salvo los DTO de `controller.dto`).
8. Los repositorios no dependen de controllers ni services.
9. El modelo no depende de services ni controllers.
10. Las clases `*Request` y `*Response` viven en `controller.dto` (o `common`).

**`ReglasDeCapasTest`** (patrón de capas y estilo):
1. Arquitectura en capas: Controller → Service → Repository (solo el service accede al repositorio; excepciones: `config` hacia repositorios y `security` hacia services).
2. Los controllers no dependen de entidades JPA ni de `ModelMapper`.
3. Todo `@Service` se llama `*ServiceImpl` e implementa la interfaz `*Service` homónima.
4. Las interfaces del paquete `service` terminan en `Service`.
5. Los `@RestController` terminan en `Controller`.
6. Los `Request` y `Response` son `record`.
7. No hay `@Transactional` en controllers.
8. El service no depende de anotaciones web, servlets ni `org.springframework.http`.
9. No se usa `System.out` ni `printStackTrace` (se usa `Logger`).
10. No hay inyección por campo (se usa constructor).

**`MultitenenciaTest`**: toda `@Entity` excepto `Empresa` extiende `EntidadEmpresa`; el campo `empresa` de `EntidadEmpresa` tiene `@JoinColumn(updatable = false)`.

**`RepositorioTenantTest`**: todo `*Repository` excepto `EmpresaRepository` extiende `RepositorioTenant`.

**`AislamientoTenantTest`**: los services no llaman `findById`/`existsById`/`deleteById`/`getReferenceById`/`findAllById`/`findAll` sobre un `RepositorioTenant`; ninguna clase usa `HttpSession`; ningún `*Request` tiene un campo `empresa` o `empresaId`.

**`HerenciaJpaTest`**: `NodoFlujo` usa `@Inheritance(SINGLE_TABLE)`; las entidades `Actividad*` y `Gateway*` extienden `NodoFlujo`; todos los `@Enumerated` usan `EnumType.STRING`.

**`SeguridadArquitecturaTest`**: el paquete `security` no accede a repositorios (pasa por los services).

**`SeparacionRepositoriosTest`**: (a) una prueba dinámica por cada repositorio: solo lo inyecta su service dueño (`XRepository` → `XServiceImpl`); (b) no hay ciclos de dependencia entre services.

En el pipeline estas pruebas corren también como un job aparte (ver 8.5): si alguien rompe la arquitectura, el job rojo lo dice.

### 8.4 SonarCloud

Configurado en el `pom.xml`:

- Servidor `https://sonarcloud.io`; el proyecto y la organización se definen con `sonar.projectKey` y `sonar.organization`.
- Cobertura tomada de JaCoCo (`sonar.coverage.jacoco.xmlReportPaths = target/site/jacoco/jacoco.xml`).
- Exclusiones: `**/dto/**`, `**/config/**` y `**/*Application.java`.

En CI el análisis se ejecuta con `mvn -B verify sonar:sonar ... -Dsonar.qualitygate.wait=true`: el job **falla si el *quality gate* no se cumple**. Según el comentario del workflow, el gate exige cobertura del código nuevo ≥ 80 %, sin bugs ni vulnerabilidades nuevas y duplicación ≤ 3 %. Sin `qualitygate.wait` el análisis solo se subiría y el job quedaría verde aunque el gate fallara.

### 8.5 Integración continua (`.github/workflows/ci.yml`)

Se dispara en `push` a `main` y en cada `pull_request`. Cuatro jobs:

| Job | Qué hace |
|---|---|
| **build-and-test** | En **ubuntu y windows** (matriz, `fail-fast: false`): JDK 21 Temurin con caché de Maven, `mvn -B clean verify` (compila, prueba y aplica los umbrales de JaCoCo), publica el resultado de las pruebas, sube el informe de JaCoCo (solo en ubuntu) y verifica que exista el `.jar`. |
| **architecture-guard** | Necesita build-and-test. Ejecuta solo `com.facimus.procesos.arquitectura.**` y publica un reporte "Architecture Rules" que **falla** si hay errores. |
| **docker-build** | Necesita los dos anteriores. Construye la imagen Docker, la ejecuta, espera 10 segundos, muestra los logs y la detiene. |
| **sonarqube** | Necesita build-and-test. Corre en `push` y en PR del propio repositorio (los PR desde forks no reciben secretos, así que se omite). Ejecuta `verify sonar:sonar` con `qualitygate.wait=true`. |

Así, antes de integrar un cambio se comprueba: compila y pasa las pruebas en dos sistemas operativos, cumple cobertura mínima, respeta la arquitectura, la imagen se construye y el análisis estático de SonarCloud pasa.

---

## 9. Docker y compose

### 9.1 Dockerfile (multietapa)

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src/ src/
RUN mvn -B clean package && cp target/*.jar app.jar

FROM eclipse-temurin:21-jre
RUN useradd -r -u 1001 appuser
USER appuser
WORKDIR /app
COPY --from=build --chown=appuser:appuser /build/app.jar app.jar
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- **Etapa 1 (build):** imagen con Maven y JDK 21 compila el proyecto. Se copia primero solo el `pom.xml` y se descargan las dependencias: así Docker **cachea esa capa** y no las vuelve a bajar mientras el `pom.xml` no cambie.
- **Etapa 2 (ejecución):** solo un JRE 21 y el `.jar`; la imagen final no lleva Maven ni el código fuente, queda más pequeña y con menos superficie de ataque.
- Corre como **usuario no root** (`appuser`, uid 1001).
- `SPRING_PROFILES_ACTIVE=prod` por defecto: la imagen arranca con PostgreSQL, Flyway y Swagger apagado.
- `.dockerignore` excluye `.git`, `.github`, `target/`, `data/` (la base H2 local), `*.md` y archivos de IDE, para que el contexto de build sea liviano y no se cuele nada local.

### 9.2 `compose.yaml`

Levanta la solución completa con un solo comando:

```bash
cp .env.example .env      # y completar DB_PASSWORD y JWT_SECRET
docker compose up -d --build
```

- **`db`** (`postgres:16`): base `procesos`, con un volumen `pgdata` para que los datos sobrevivan a reinicios, y un `healthcheck` con `pg_isready`.
- **`backend`** (se construye con el `Dockerfile`): `depends_on: db: condition: service_healthy`, es decir, **no arranca hasta que PostgreSQL esté listo**. Recibe por variables de entorno la conexión (`DB_HOST: db`: dentro de la red de compose el servicio se llama por su nombre), el `JWT_SECRET` y `CORS_ALLOWED_ORIGINS` (por defecto `http://localhost:4200`, el frontend Angular). Publica el puerto `8080:8080`.
- **Secretos fuera del código:** `DB_PASSWORD` y `JWT_SECRET` se declaran con `${VAR:?mensaje}`: si faltan, compose se niega a arrancar con un mensaje claro. El archivo `.env` no se versiona; solo se versiona `.env.example`, la plantilla.
- La base de datos **no publica puerto** al exterior: solo el backend puede alcanzarla.
- Al ser perfil `prod`, **Flyway crea el esquema** y Hibernate solo valida; no hay datos de ejemplo ni Swagger.
- El comentario del archivo indica que el frontend se agregará como tercer servicio en la entrega siguiente (por eso ya está lista la variable de CORS).

---

## 10. Cómo se cumple cada historia de usuario

El estado detallado (qué está completo y qué es parcial, con evidencia) vive en `docs/cobertura-historias-usuario.md`. Aquí solo el mapa de **dónde mirar** para cada historia.

| HU | Tema | Dónde está en el código |
|---|---|---|
| HU-01 | Registro de empresa | `EmpresaController` → `EmpresaOrquestadorService` (empresa + administrador en una transacción) |
| HU-02 | Registro de usuario en la empresa | `UsuarioController` (alta directa, solo administrador) e `InvitacionUsuarioController` (token de 48 h) |
| HU-03 | Inicio de sesión | `AuthController` → `UsuarioService.autenticar` + `JwtService` |
| HU-04 | Crear proceso | `ProcesoController.crear` → `ProcesoOrquestadorService` (proceso + pool inicial) |
| HU-05 | Editar proceso | `PUT /procesos/{id}` y `PATCH /procesos/{id}` (estado y publicación con validación) |
| HU-06 | Eliminar proceso | `DELETE /procesos/{id}`: baja lógica, solo administrador |
| HU-07 | Consultar procesos | `GET /procesos` (filtros + paginación), `/procesos/{id}`, `/diagrama`, `/historial` |
| HU-08 a HU-10 | Crear, editar y eliminar actividad | `ActividadController` / `ActividadService` (+ `impacto-eliminacion`) |
| HU-11 a HU-13 | Crear, editar y eliminar arco | `ArcoController` / `ArcoService` |
| HU-14 a HU-16 | Crear, editar y eliminar gateway | `GatewayController` / `GatewayService` |
| HU-17 a HU-20 | Roles de proceso: crear, editar, eliminar, consultar | `RolProcesoController` / `RolProcesoService` |
| HU-21, HU-22 | Pools y lanes | `PoolController` / `LaneController`, `PoolOrquestadorService`, `LaneOrquestadorService` |
| HU-23 | Compartir procesos entre empresas (solo lectura) | `ProcesoCompartidoController` / `ProcesoCompartidoService` |
| HU-24 | Permisos para modificar la estructura (pools y lanes) | `PermisoEstructuraController` / `PermisoEstructuraService` |
| HU-25 a HU-27 | Mensajes entre pools, destinos externos, eventos Message Throw/Catch | `MensajeController`, `EventoMensajeController`, `MensajeService`, `EventoMensajeService` |
| HU-28 | Correlación de mensajes | `CorrelacionController` / `CorrelacionOrquestadorService` |
| Transversal | Validación del modelo al publicar | `ValidacionModeloService` (+ `GET /procesos/{procesoId}/validacion`) |

Para la sustentación, el mensaje correcto es: "las historias de gestión y modelado están implementadas a nivel de API con sus reglas de negocio, y el documento de cobertura dice con honestidad dónde queda algo parcial". No afirmes que todo está al 100 % sin abrir ese documento.

---

## 11. Preguntas probables de sustentación

### Arquitectura y diseño

**1. ¿Por qué arquitectura en capas?**
Separa responsabilidades: el controller maneja HTTP, el service la lógica y las transacciones, el repository el acceso a datos y el model los datos. Cada capa se puede probar sola (mock del service en `@WebMvcTest`, mock del repository en las pruebas de service) y los cambios quedan localizados. Además está protegida por pruebas ArchUnit (`ReglasDeCapasTest`), así que no depende de la disciplina de cada quien.

**2. ¿Por qué cada service es interfaz + Impl?**
Porque quien lo usa depende de un contrato (`ProcesoService`) y no de la clase concreta: facilita sustituirlo por un mock en pruebas, permite cambiar la implementación sin tocar a los clientes y es el patrón estándar de la materia. El costo es duplicar archivos y que cualquier cambio de firma se haga en dos sitios. Hay una regla ArchUnit que obliga el patrón (`*ServiceImpl` implementa `*Service`).

**3. ¿Por qué ModelMapper está en el service y no en el controller?**
Porque el controller solo debe conocer DTOs (regla ArchUnit: no puede depender de entidades ni de `ModelMapper`) y porque el service es quien está dentro de la transacción: con `open-in-view=false` la sesión de Hibernate ya está cerrada cuando vuelve el controller, y los converters navegan relaciones lazy (por ejemplo `lane.getRolProceso().getNombre()`). Mapear dentro del service evita `LazyInitializationException`.

**4. ¿Por qué hay métodos con entidades y métodos con Request/Response en el mismo service?**
Los de nivel dominio (entidades y tipos simples) los reutilizan otros services para componer reglas; los de nivel API los usa el controller y devuelven el DTO ya mapeado. El de API llama al de dominio y agrega el mapeo. Así no se duplican reglas y el controller queda delgado.

**5. ¿Qué es un orquestador y por qué lo necesitan?**
Es un service que coordina varios services en una sola transacción cuando un caso de uso los cruza (por ejemplo, crear proceso + pool inicial). Existe para evitar dependencias circulares: si `ProcesoService` llamara a `PoolService` y `PoolService` a `ProcesoService`, Spring no podría construir los beans. La dirección de dependencias es única y el orquestador, que nadie más usa, une ambos lados. `SeparacionRepositoriosTest` verifica que no hay ciclos.

**6. ¿Por qué eventos de dominio en lugar de llamar directamente al service?**
Para romper ciclos y mantener la dependencia en un solo sentido. Al publicar un proceso, `ProcesoService` no conoce a `ValidacionModeloService`: publica `ProcesoPublicacionEvent` y quien valida lo escucha. Es síncrono y en la misma transacción, así que si la validación falla se revierte todo. Y la regla se cumple aunque alguien invoque el service sin pasar por el controller.

**7. ¿Por qué un service no inyecta el repositorio de otro dominio?**
Para que cada entidad tenga un único dueño de sus reglas e invariantes: si `ProcesoService` leyera `UsuarioRepository` directamente se saltaría las reglas de `UsuarioService`. La excepción es `NodoFlujoRepository`, que centraliza las consultas polimórficas de los tres subtipos de nodo; se concentra en `NodoFlujoServiceImpl`.

**8. ¿Por qué los controllers no tienen `@Transactional`?**
Porque la transacción debe cubrir una unidad de negocio, y esa unidad es el service (o el orquestador). Si estuviera en el controller, la lógica de transacciones se mezclaría con HTTP y sería fácil olvidarla. Lo exige una regla ArchUnit.

**9. ¿Por qué inyección por constructor y no por campo (`@Autowired`)?**
Los campos `final` garantizan que el objeto nunca queda a medias, se pueden crear en pruebas sin Spring (`@InjectMocks`) y el constructor hace evidentes las dependencias (y los ciclos). Con Lombok `@RequiredArgsConstructor` no cuesta código. Una regla ArchUnit prohíbe la inyección por campo.

### Persistencia

**10. ¿Qué es la multitenencia y cómo la implementaron?**
Varias empresas comparten una misma base y un mismo esquema; cada fila lleva `empresa_id`. Una superclase `@MappedSuperclass` (`EntidadEmpresa`) aporta esa columna (obligatoria y no actualizable) a todas las entidades menos `Empresa`, y el contrato `RepositorioTenant` obliga a consultar por `findByIdAndEmpresaId`. Alternativas: una base por empresa (más aislamiento y más operación) o un esquema por empresa; elegimos la más simple de operar para este alcance, con pruebas que protegen el aislamiento.

**11. ¿Por qué `empresaId` sale del token y no del request?**
Porque lo que viene en el request lo controla el cliente: si aceptáramos `empresaId` en el cuerpo o la URL, cualquiera podría pedir los datos de otra empresa. El `empresaId` va firmado dentro del JWT (que el cliente no puede modificar sin invalidar la firma) y se expone como `ApiPrincipal`. Ningún `*Request` tiene ese campo (regla ArchUnit).

**12. ¿Por qué 404 y no 403 cuando el recurso es de otra empresa?**
Un 403 le confirmaría al atacante que ese id existe y es de otro. Respondiendo 404, "no existe" y "no es tuyo" son indistinguibles, y no se filtra información. El 403 queda para cuando sabes quién es el usuario y su rol no alcanza (por ejemplo, un `SOLO_LECTURA` que intenta escribir).

**13. ¿Qué es un IDOR y cómo lo evitan?**
Acceso a un objeto ajeno cambiando su id en la petición. Se evita porque (1) la empresa sale del token, (2) todas las consultas por id incluyen la empresa, y (3) hay pruebas: `AislamientoTenantTest` (ArchUnit) y `AislamientoEmpresasIntegracionTest` (dos empresas reales intentando alcanzarse).

**14. ¿Por qué eliminación lógica y no `DELETE` físico?**
Para conservar la trazabilidad (el historial de un proceso, qué usuario hizo qué), mantener consistentes las referencias (arcos, mensajes y lanes apuntan a nodos y pools) y poder consultar lo dado de baja. Costo: hay que filtrar `activo = true` en cada consulta (o usar `@SQLRestriction` en `Pool`, `Lane` y `Correlacion`) y los índices únicos deben ser parciales (solo entre activos), como en `V3__unicidad_entre_activos.sql`.

**15. ¿Por qué dos mecanismos de baja lógica (manual y `@SQLDelete`)?**
`Pool`, `Lane` y `Correlacion` se eliminan con `repository.delete(...)`, que Hibernate convierte en `UPDATE ... SET activo = false`, y `@SQLRestriction` oculta las inactivas. En las demás entidades el service pone `activo = false` y filtra explícitamente porque necesitan consultar también los inactivos en casos concretos (por ejemplo, ver un proceso dado de baja con `obtenerParaConsulta`) y aplican reglas propias al desactivar (desactivar arcos conectados, registrar en el historial).

**16. ¿Por qué `SINGLE_TABLE` para `NodoFlujo`?**
Actividad, gateway y evento de mensaje comparten casi todos sus campos y tienen pocos propios, y el diagrama siempre se lee completo. Con una sola tabla no hay *joins* y las consultas polimórficas (`NodoFlujoRepository`) son simples. Desventajas: columnas nulas según el subtipo y no poder poner `NOT NULL` en las columnas propias (por eso `tipoGateway` se exige en el DTO con `@NotNull`). `JOINED` normalizaría mejor pero costaría joins en cada lectura.

**17. ¿Qué diferencia hay entre una consulta derivada y una `@Query` JPQL? ¿Cuándo usaron cada una?**
La derivada se infiere del nombre del método y sirve para filtros simples (`findAllByEmpresaIdAndActivoTrue`). La `@Query` se usa cuando la consulta es más compleja que un nombre legible: filtros opcionales con paginación (`buscarConFiltros`), agrupación en una sola consulta para evitar N+1 (`procesosActivosPorRoles`) u operadores `or` entre relaciones (`findActivosPorEvento`). JPQL trabaja con entidades y atributos, no con tablas.

**18. ¿Por qué los filtros opcionales de `buscarConFiltros` usan un booleano más un valor de relleno?**
PostgreSQL no puede inferir el tipo de un parámetro `null` dentro de la consulta (`:estado IS NULL OR ...` falla). Por eso cada filtro llega como `filtrarEstado` (boolean) y un valor no nulo de relleno, y la consulta hace `(:filtrarEstado = false OR p.estado = :estado)`. El patrón de nombre además escapa `%` y `_` para que el usuario no los use como comodines.

**19. ¿Por qué `FetchType.LAZY` y para qué sirven los `@EntityGraph`?**
LAZY evita traer relaciones que no se usan. Pero si una relación lazy se toca fuera de la transacción falla. Los `@EntityGraph` en el repositorio piden cargar una relación concreta en la misma consulta cuando el flujo lo necesita (por ejemplo `ArcoRepository` carga origen y destino porque el service hace `instanceof` sobre ellos, y un proxy sin inicializar no pasa ese `instanceof`).

**20. ¿Para qué Flyway si Hibernate puede crear el esquema?**
`ddl-auto=update` es cómodo en desarrollo pero no es seguro en producción: no deja historial, no controla el orden y puede hacer cambios inesperados. Flyway versiona el esquema con scripts (`V1`, `V2`...) reproducibles. En el perfil `prod` Flyway crea el esquema y Hibernate solo lo valida (`ddl-auto=validate`), y `MigracionesTest` hace fallar la compilación si una entidad cambia sin su migración.

**21. ¿Por qué H2 en desarrollo y PostgreSQL en producción?**
H2 embebida no exige instalar nada: el proyecto arranca con `mvn spring-boot:run` y trae datos de ejemplo. PostgreSQL es el motor real de producción. El riesgo es que se comporten distinto; se mitiga con `MigracionesTest` (H2 en modo PostgreSQL) y con scripts específicos de PostgreSQL aislados en su propia carpeta (`db/migration/postgresql`).

### API REST

**22. ¿Por qué `ProblemDetail`?**
Es el estándar de Spring (RFC 9457) para errores HTTP: todos los errores tienen la misma estructura (`title`, `status`, `detail`, `instance`) y el frontend necesita un solo manejador. Se centraliza en un `@RestControllerAdvice`, de modo que los controllers no tienen `try/catch`; las excepciones de dominio se traducen a 404/409/400 en un solo lugar.

**23. ¿Qué código devuelve cada caso (400, 401, 403, 404, 409)?**
400: el JSON o los datos están mal formados (validación). 401: no hay token válido. 403: hay token, pero el rol no alcanza. 404: no existe o es de otra empresa. 409: los datos son válidos pero chocan con una regla o con el estado (NIT repetido, publicar un modelo incompleto, eliminar un rol en uso). Distinguir 400 de 409: 400 es un problema de *forma*; 409 de *estado o regla*.

**24. ¿Dónde validan: en el DTO o en el service?**
En los dos, con propósitos distintos. El DTO valida la forma con Bean Validation (campo obligatorio, correo bien formado, longitud) y falla pronto con 400. El service valida lo que depende del estado y de otros datos (unicidad, existencia, relaciones entre entidades) y responde 404/409. Si solo se validara en el controller, un llamado directo al service se saltaría las reglas: por eso las reglas de negocio viven en el service.

**25. ¿Por qué paginar y por qué un `PageResponse` propio?**
Para no devolver listas enormes y dar al frontend `page`, `size`, `totalElements` y `totalPages`. Se usa un record propio en vez del JSON de `Page` de Spring para que el contrato de la API sea estable y no dependa de detalles internos del framework. El tamaño se limita a 1–100.

**26. ¿Por qué usan DTOs y no devuelven las entidades?**
Porque la entidad es el modelo de persistencia y la API es un contrato: exponer entidades filtraría campos internos (como `passwordHash`), acoplaría el JSON al esquema, y las relaciones lazy provocarían errores de serialización. Los DTO son `record`s inmutables. Además evita sobreenvío y subenvío de datos: el cliente solo manda lo que puede decidir (no la empresa, no el estado inicial).

**27. ¿Por qué los endpoints tienen `impacto-eliminacion`?**
Para que el frontend pueda avisar al usuario antes de eliminar ("el nodo X quedará sin salida", "se eliminarán los arcos conectados") con un `GET` que no cambia nada. Es una decisión de usabilidad: la eliminación es una baja lógica pero rompe la continuidad del flujo.

**28. ¿Cómo se documenta la API?**
Con springdoc: anotaciones `@Tag`, `@Operation` y `@ApiResponse` en los controllers generan el JSON OpenAPI (`/v3/api-docs`) y la interfaz Swagger UI (`/swagger-ui.html`), con un esquema `bearerAuth` para probar con el JWT. En producción se apaga para no publicar el mapa completo de la API.

**29. ¿Qué hace CORS y por qué un filtro con orden -102?**
El navegador bloquea las peticiones del frontend (por ejemplo `http://localhost:4200`) a otro origen salvo que el servidor lo autorice con cabeceras CORS. Antes de la petición real, el navegador manda un *preflight* `OPTIONS`; si la cadena de seguridad lo procesa primero, lo rechaza sin cabeceras. El filtro se registra con orden `-102`, antes de Spring Security (`-100`), y el origen permitido es configurable por variable de entorno.

### Seguridad

**30. ¿Cómo funciona el login con JWT?**
`POST /auth/login` valida correo y contraseña (BCrypt) y devuelve un token firmado con `usuarioId`, `empresaId` y `rol`. El cliente lo manda en `Authorization: Bearer <token>` en cada petición; `JwtAuthenticationFilter` valida firma y expiración, comprueba que el usuario siga activo y arma el `ApiPrincipal`. No hay sesión en el servidor (API *stateless*), por eso el logout solo significa que el cliente descarta el token.

**31. ¿Por qué BCrypt?**
Es un hash lento y con sal, pensado para contraseñas: aunque se filtre la base, no se recuperan las claves en claro y es costoso probarlas por fuerza bruta. Nunca se guarda ni se devuelve la contraseña (`passwordHash` no está en ningún DTO).

**32. ¿Por qué el login ejecuta `matches` aunque el correo no exista?**
Para que el tiempo de respuesta no delate si un correo está registrado: si no hubiera usuario se respondería más rápido que cuando hay que hacer el hash. Se compara contra un hash ficticio y el mensaje de error es el mismo en ambos casos.

**33. ¿Por qué el filtro consulta la base en cada petición si ya hay un token firmado?**
Porque un JWT firmado sigue siendo válido hasta que vence aunque el usuario ya no deba tener acceso. Consultando al usuario, desactivar a alguien (o degradarlo) surte efecto de inmediato. El costo es una consulta por petición; es un trade-off entre latencia y revocación inmediata.

**34. ¿Cómo se controlan los roles?**
Dos niveles: (1) por ruta y método en `SecurityConfig` (`hasAuthority`/`hasAnyAuthority`): por ejemplo `SOLO_LECTURA` solo hace `GET`, `EDITOR` crea y edita, `ADMINISTRADOR` elimina y administra usuarios. (2) La estructura (crear/editar/eliminar pools y lanes) es configurable por empresa y rol (`PermisoEstructura`), se valida en el controller con `PermisoEstructuraService`.

**35. ¿Por qué deshabilitan CSRF solo en `/api/v1/**`?**
CSRF es un ataque contra sesiones basadas en cookies que el navegador envía solo. Esta API autentica con un token en una cabecera que el navegador no agrega automáticamente, así que no es aplicable. No se desactiva globalmente: solo se exime la API (y la consola H2 de desarrollo).

### Pruebas, calidad y despliegue

**36. ¿Qué tipos de pruebas tienen y qué prueba cada una?**
Unitarias de service (Mockito, reglas de negocio), de controller (`@WebMvcTest`, rutas, códigos, validación y seguridad), de repositorio (`@DataJpaTest`, consultas), de integración (`@SpringBootTest`: seguridad, transacciones, migraciones) y de arquitectura (ArchUnit). Son más de 600 métodos de prueba.

**37. ¿Qué es ArchUnit y qué ganan con eso?**
Una librería que convierte reglas de arquitectura en pruebas JUnit: "el controller no depende de repositorios", "toda entidad menos Empresa extiende `EntidadEmpresa`". Sin ella, esas reglas serían un acuerdo verbal que se rompe; con ella, el pipeline falla si alguien las viola.

**38. ¿Cuáles son los umbrales de JaCoCo y por qué esos?**
Servicios: 70 % de líneas y 60 % de ramas; todo el proyecto: 50 % de líneas. Se exige más en `service` porque es donde vive la lógica de negocio. Se excluyen DTOs, configuración y la clase `Application` porque no tienen lógica propia que probar. Cobertura alta no garantiza buenas pruebas, pero un umbral evita que el código nuevo quede sin probar.

**39. ¿Qué hace el pipeline de CI?**
En cada push a `main` y cada PR: compila y ejecuta todas las pruebas en Ubuntu y Windows con `mvn verify` (que aplica JaCoCo), corre aparte las reglas de arquitectura, construye la imagen Docker y ejecuta el análisis de SonarCloud esperando su *quality gate*. Un cambio solo se integra si todo pasa.

**40. ¿Por qué un Dockerfile multietapa?**
La primera etapa necesita Maven y el JDK para compilar; la segunda solo necesita un JRE y el `.jar`. Separarlas deja una imagen final más pequeña, sin herramientas de compilación ni código fuente. Además copiar el `pom.xml` y descargar dependencias antes de copiar el código aprovecha el caché de capas, y la aplicación corre con un usuario no root.

**41. ¿Cómo manejan los secretos en Docker?**
No están en el código ni en la imagen: `compose.yaml` los lee de variables de entorno (`.env`, que no se versiona; solo se versiona `.env.example`). `DB_PASSWORD` y `JWT_SECRET` son obligatorios (`${VAR:?mensaje}`) y, además, `JwtService` se niega a arrancar en `prod` si el secreto tiene menos de 32 caracteres.

**42. ¿Por qué el backend espera a que la base esté `healthy`?**
Porque `depends_on` por sí solo solo espera a que el contenedor *inicie*, no a que PostgreSQL esté listo para recibir conexiones. El `healthcheck` con `pg_isready` y `condition: service_healthy` evitan que el backend falle al arrancar por no encontrar la base.

### Alcance y honestidad

**43. ¿El sistema ejecuta los procesos?**
No. Modela y valida: describe los procesos BPMN y comprueba que sean coherentes antes de publicarlos. No hay motor de ejecución, ni instancias de proceso, ni envío real de mensajes (un mensaje externo solo documenta el destino).

**44. ¿Qué limitaciones reconoces?**
- No se envían correos: la invitación devuelve el token en la respuesta.
- El logout no invalida el token en el servidor (el token vence a los 30 minutos por defecto; no hay refresh token).
- Los índices únicos parciales (nombre único entre activos) solo existen en PostgreSQL; en H2 la unicidad depende de la validación del service.
- El job de Docker del pipeline arranca la imagen sin base de datos y solo muestra los logs: comprueba que la imagen se construye, no que la aplicación completa funcione.
- El estado exacto de cada historia (qué está completo y qué es parcial) está en `docs/cobertura-historias-usuario.md`.

---

## 12. Glosario

| Término | Significado |
|---|---|
| **BPMN** | *Business Process Model and Notation*: notación estándar para dibujar procesos de negocio. |
| **Pool** | Participante de un proceso (la empresa, un cliente, un proveedor, un sistema externo). |
| **Lane** | Carril dentro de un pool; agrupa las actividades de un rol responsable. |
| **Caja negra** | Pool cuyo interior no se modela (no admite lanes ni nodos); típico de un sistema externo. |
| **Actividad** | Tarea del proceso (tarea, usuario, servicio, manual, script, regla de negocio). |
| **Gateway** | Punto de decisión o ramificación: exclusivo, paralelo o inclusivo. |
| **Divergente / convergente** | Gateway que reparte el flujo (varias salidas) / que lo une (varias entradas). |
| **Arco** | Flecha de secuencia entre dos nodos de un mismo pool. |
| **Message Throw / Catch** | Evento que envía un mensaje / evento que lo recibe. |
| **Correlación** | Criterio que relaciona un mensaje con el caso concreto del proceso (por ejemplo, el número de solicitud). |
| **Rol de proceso** | Función responsable de una lane (ej. "Jefe inmediato"); no es una persona ni un rol de acceso. |
| **Rol de acceso** | Permiso de la persona en el sistema: `ADMINISTRADOR`, `EDITOR`, `SOLO_LECTURA`. |
| **Multitenencia (tenant)** | Varias empresas comparten la misma aplicación y base, aisladas por `empresa_id`. |
| **IDOR** | Acceso a un recurso ajeno cambiando su id en la petición. |
| **Eliminación lógica** | Marcar un registro como inactivo (`activo = false`) en vez de borrarlo. |
| **Entidad** | Clase Java mapeada a una tabla (`@Entity`). |
| **DTO** | *Data Transfer Object*: objeto que define lo que entra y sale por la API; aquí, `record` `*Request` / `*Response`. |
| **Repository** | Interfaz de Spring Data que da acceso a la base sin escribir implementación. |
| **Consulta derivada** | Consulta generada por Spring Data a partir del nombre del método. |
| **JPQL** | Lenguaje de consultas de JPA: consulta entidades y atributos, no tablas. |
| **LAZY / EAGER** | Carga diferida / inmediata de una relación. |
| **`@EntityGraph`** | Indica qué relaciones cargar en una consulta concreta. |
| **N+1** | Problema de rendimiento: una consulta principal más una consulta por cada fila resultante. |
| **`@Transactional`** | Ejecuta el método dentro de una transacción; si hay excepción no controlada, se revierte. |
| **Orquestador** | Service que coordina varios services en una transacción, para evitar ciclos. |
| **Evento de dominio** | Mensaje interno (`ApplicationEventPublisher` + `@EventListener`) que desacopla a quien notifica de quien reacciona. |
| **Inyección de dependencias** | Spring entrega al objeto sus dependencias; aquí por constructor. |
| **ModelMapper** | Librería que convierte entidades en DTO; aquí con converters explícitos. |
| **Bean Validation** | Estándar de validación por anotaciones (`@NotBlank`, `@Email`, `@Size`...). |
| **`ProblemDetail`** | Formato estándar de error HTTP en JSON (RFC 9457). |
| **JWT** | *JSON Web Token*: token firmado que lleva la identidad (usuario, empresa, rol) y una fecha de expiración. |
| **BCrypt** | Algoritmo de hash lento con sal para contraseñas. |
| **Stateless** | Sin sesión en el servidor: cada petición trae todo lo necesario (el token). |
| **CORS** | Mecanismo del navegador para permitir que un frontend de otro origen consuma la API. |
| **Flyway** | Herramienta de migraciones: versiona el esquema con scripts `V<n>__descripcion.sql`. |
| **Perfil de Spring** | Conjunto de configuración activable (`default`, `prod`, `test`). |
| **ArchUnit** | Librería que prueba reglas de arquitectura como pruebas JUnit. |
| **JaCoCo** | Herramienta que mide qué porcentaje del código ejecutan las pruebas. |
| **SonarCloud / quality gate** | Análisis estático en la nube / condiciones que debe cumplir el código para aprobar. |
| **Multietapa (Docker)** | Dockerfile con varias etapas: compilar en una y copiar solo el resultado a la imagen final. |
| **Healthcheck** | Comprobación periódica de que un contenedor está listo. |
