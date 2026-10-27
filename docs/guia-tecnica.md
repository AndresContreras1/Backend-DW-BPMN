# Guia tecnica del backend — Sistema de Gestion de Procesos

Documento interno del equipo. Describe el estado actual del backend, las
decisiones vigentes, la infraestructura de CI/CD, el despliegue en Docker y
las guias que todo colaborador debe seguir para contribuir sin romper las
garantias que ya estan en pie.

Ultima actualizacion: 2026-10-02 (rama `refactor/modelmapper-y-separacion-repositorios`, PR #37).

---

## 1. Estado actual del proyecto

### Stack tecnologico

| Componente | Version |
|---|---|
| Java | 21 (Temurin) |
| Spring Boot | 4.1 |
| API | REST exclusivo (JSON + JWT stateless) |
| ORM | Hibernate (via Spring Data JPA) |
| BD desarrollo | H2 embebida en archivo (`./data/`) |
| BD produccion | PostgreSQL (via perfil `prod`) |
| Seguridad | Spring Security + JWT (Bearer token) |
| Cifrado de passwords | BCrypt |
| Tests de arquitectura | ArchUnit 1.4.0 |
| Cobertura | JaCoCo 0.8.13 |
| Analisis de calidad | SonarCloud |
| CI/CD | GitHub Actions |
| Contenedorizacion | Docker (multi-stage build) |
| Documentacion API | springdoc-openapi (Swagger UI) |
| Mapeo entidad-DTO | ModelMapper 3.2.0 |

### Entidades implementadas (17)

**Bloque gestion** (8): `Empresa`, `Usuario`, `Proceso`, `HistorialCambio`,
`RolProceso`, `InvitacionUsuario`, `PermisoEstructura`, `ProcesoCompartido`.

**Bloque modelado** (9): `Pool`, `Lane`, `NodoFlujo` (abstracta), `Actividad`,
`Gateway`, `EventoMensaje`, `Arco`, `Mensaje`, `Correlacion`.

**Enums** (9): `RolAcceso`, `EstadoProceso` (gestion); `TipoParticipante`,
`TipoGateway`, `TipoActividad`, `TipoEventoMensaje`, `TipoDestinoExterno`,
`PoliticaFalloNotificacion`, `PoliticaMensajeSinCaso` (modelado). Ademas,
`OperacionEstructura` vive en `gestion/service/` (no en `model/`): enumera las
operaciones que `PermisoEstructuraService` autoriza por rol (crear/editar/
eliminar pool o lane).

Todas las entidades excepto `Empresa` extienden `EntidadEmpresa`
(`@MappedSuperclass`) que aporta la columna `empresa_id` con
`updatable = false`.

### Servicios implementados (24)

**Bloque gestion** (11): `EmpresaService`, `UsuarioService`, `ProcesoService`,
`HistorialCambioService`, `RolProcesoService`, `InvitacionUsuarioService`,
`PermisoEstructuraService`, `ProcesoCompartidoService`,
`ProcesoDiagramaService`, y los orquestadores `EmpresaOrquestadorService`,
`ProcesoOrquestadorService`.

**Bloque modelado** (13): `PoolService`, `LaneService`, `ActividadService`,
`GatewayService`, `ArcoService`, `MensajeService`, `CorrelacionService`,
`EventoMensajeService`, `NodoFlujoService`, `AuditoriaModeladoService`,
`ValidacionModeloService`, y los orquestadores `PoolOrquestadorService`,
`CorrelacionOrquestadorService`.

Todos reciben `empresaId` del controlador (extraido del `ApiPrincipal`
autenticado) y lo usan para acotar cada operacion al tenant correcto. Sobre
los 4 orquestadores (resaltados arriba) ver la seccion 2.8.

### Controladores REST (16)

| Modulo | Controlador | Base path |
|---|---|---|
| Auth | `AuthController` | `/api/v1/auth` |
| Empresas | `EmpresaController` | `/api/v1/empresas` |
| Usuarios | `UsuarioController` | `/api/v1/usuarios` |
| Invitaciones | `InvitacionUsuarioController` | `/api/v1/usuarios/invitaciones` |
| Permisos de estructura | `PermisoEstructuraController` | `/api/v1/permisos-estructura` |
| Procesos | `ProcesoController` | `/api/v1/procesos` |
| Procesos compartidos | `ProcesoCompartidoController` | `/api/v1/procesos/{id}/compartidos`, `/api/v1/procesos-compartidos` |
| Roles | `RolProcesoController` | `/api/v1/roles` |
| Pools | `PoolController` | `/api/v1/procesos/{id}/pools`, `/api/v1/pools/{id}` |
| Lanes | `LaneController` | `/api/v1/pools/{id}/lanes`, `/api/v1/lanes/{id}` |
| Actividades | `ActividadController` | `/api/v1/lanes/{id}/actividades`, `/api/v1/actividades/{id}` |
| Gateways | `GatewayController` | `/api/v1/lanes/{id}/gateways`, `/api/v1/gateways/{id}` |
| Eventos de mensaje | `EventoMensajeController` | `/api/v1/lanes/{id}/eventos-mensaje`, `/api/v1/eventos-mensaje/{id}` |
| Arcos | `ArcoController` | `/api/v1/arcos`, `/api/v1/pools/{id}/arcos` |
| Mensajes | `MensajeController` | `/api/v1/procesos/{id}/mensajes`, `/api/v1/mensajes/{id}` |
| Correlaciones | `CorrelacionController` | `/api/v1/mensajes/{id}/correlacion` |

Documentacion interactiva disponible en `/swagger-ui/index.html`. Los 16
controllers tienen `@Tag` (clase) y `@Operation`/`@ApiResponse` (metodo) con
el DTO real o `ProblemDetail` segun el codigo de respuesta.

---

## 2. Decisiones de arquitectura vigentes

Estas decisiones estan protegidas por tests de ArchUnit. Si un PR las
viola, el CI falla automaticamente. No se deben cambiar sin un ADR nuevo
discutido con el equipo.

### 2.1 Empaquetado modular por dominio

```
com.facimus.procesos
├── common/       Clases transversales (EntidadEmpresa, excepciones, RepositorioTenant)
├── config/       Configuracion de Spring (OpenAPI, datos demo)
├── security/     JWT, filtros, CORS, ApiPrincipal
├── gestion/      Entidades de gestion del sistema
│   ├── model/
│   ├── repository/
│   ├── service/
│   └── controller/
└── modelado/     Entidades del modelado BPMN
    ├── model/
    ├── repository/
    ├── service/
    └── controller/
```

**Reglas verificadas por CI:**
- Los `@RestController` viven en paquetes `controller/`.
- Los `@Service` viven en paquetes `service/`.
- Los `@Entity` viven en paquetes `model/`.
- Los controllers no acceden directamente a repositorios (deben pasar por
  servicios).
- `modelado` no depende de `gestion.controller`.

### 2.2 Multi-tenencia por columna directa

- Toda `@Entity` excepto `Empresa` extiende `EntidadEmpresa`.
- `empresa_id` es `nullable = false, updatable = false`.
- Todo repositorio excepto `EmpresaRepository` extiende `RepositorioTenant<T>`,
  que expone `findByIdAndEmpresaId`, `findAllByEmpresaId` y
  `existsByIdAndEmpresaId`.
- **Toda query nueva debe filtrar por `empresaId`. Sin excepciones.** La unica
  excepcion documentada es `UsuarioRepository.findByEmail()` para el login.

### 2.3 Herencia JPA

- `NodoFlujo` es abstracta con `@Inheritance(SINGLE_TABLE)` y
  `@DiscriminatorColumn(name = "tipo_nodo")`.
- Tiene tres subtipos: `Actividad`, `Gateway` y `EventoMensaje` (Message
  Throw/Catch).
- `NodoFlujoRepository` es el unico repositorio compartido entre varios
  servicios (`ActividadService`, `GatewayService`, `EventoMensajeService`
  delegan en `NodoFlujoService` en vez de inyectarlo directamente): es la
  excepcion documentada a la regla "cada Service solo su propio Repository"
  (ver 2.7), justificada porque los tres subtipos comparten tabla.

### 2.4 Enums

Todos los campos `@Enumerated` usan `EnumType.STRING`. Nunca `ORDINAL`.
Agregar un valor al enum o reordenarlo no corrompe datos existentes.

### 2.5 Eliminacion logica

`Proceso` y `RolProceso` usan `boolean activo` desde el inicio del proyecto.
El mismo patron se extendio a todas las entidades de modelado: `Pool`,
`Lane`, `Actividad`, `Gateway`, `EventoMensaje`, `Arco` y `Mensaje` tambien
tienen `boolean activo` con filtrado explicito en cada query/servicio.
Nunca se hace `DELETE` fisico sobre ninguna de estas entidades.

Se evaluo adoptar `@SQLDelete`/`@Where` (soft delete declarativo de
Hibernate) y se descarto a proposito: cambiaria el filtrado de `activo` de
explicito (visible en cada query, igual que el filtrado por `empresaId`) a
implicito y global, lo que es mas dificil de auditar. La decision esta
documentada en `EntidadEmpresa.java`.

### 2.6 Seguridad

- Autenticacion stateless via JWT (Bearer token).
- Tres roles de acceso: `ADMINISTRADOR`, `EDITOR`, `SOLO_LECTURA`.
- `ApiPrincipal` encapsula la identidad del usuario autenticado y el
  `empresaId` del tenant.
- Errores de autenticacion y autorizacion responden con `ProblemDetail`
  (RFC 9457).

### 2.7 Mapeo entidad-DTO con ModelMapper

- `config/ModelMapperConfig.java` registra un bean `ModelMapper` con
  `AbstractConverter` explicitos solo para los DTOs que tienen campos
  derivados de una relacion (p. ej. `pool.getProceso().getId()` ->
  `procesoId`). Los DTOs que solo copian campos con el mismo nombre no
  tienen converter: ModelMapper los resuelve por coincidencia automatica.
- **El mapeo ocurre en los controllers**, no en los services, y es una
  decision deliberada: los services ya se llaman entre si con la entidad
  real (no con el DTO) para validar reglas de negocio cruzadas; mover el
  mapeo a la capa de servicio rompia esa composicion.
- Se probo eliminar los converters "triviales" confiando por completo en el
  mapeo automatico por nombre: rompio 24 tests, porque los DTO de respuesta
  son Java records (sin setters) y ModelMapper 3.2.0 no los resuelve solo
  por nombre cuando no hay un setter que rellenar. Por eso los converters
  explicitos se mantienen para esos casos.
- Las 20 relaciones `@ManyToOne`/`@OneToOne` del proyecto son
  `FetchType.LAZY` (ver `EntidadEmpresa.java` para el detalle). Como el
  mapeo sigue ocurriendo en el controller — fuera de la transaccion, con
  `spring.jpa.open-in-view=false` — acceder a una relacion no inicializada
  ahi lanzaria `LazyInitializationException`. La solucion no fue mover el
  mapeo a la capa de servicio (evaluado y descartado, ver arriba), sino
  declarar `@EntityGraph(attributePaths = {...})` en el metodo del
  repositorio que alimenta cada lectura que necesita esa relacion — p. ej.
  `LaneRepository.findByIdAndEmpresaId` carga `rolProceso` porque
  `LaneResponse` necesita su nombre. Cada repositorio con un
  `@EntityGraph` documenta en un comentario que relacion necesita y por
  que. Los accesos que solo leen el id de la relacion (la mayoria) no
  necesitan `@EntityGraph`: un proxy LAZY sin inicializar sigue
  respondiendo a `getId()` sin lanzar excepcion.

### 2.8 Orquestadores transaccionales y eventos de dominio

Varios casos de uso necesitan coordinar dos o mas servicios en una sola
operacion atomica (p. ej. crear un proceso y su pool inicial). Hacer esa
coordinacion llamando a dos servicios por separado desde el controller deja
cada llamada en su propia transaccion: si la segunda falla, la primera ya
quedo persistida. La solucion son 4 servicios `@Service` nuevos, cada uno
con un metodo `@Transactional` que envuelve la secuencia completa:

| Orquestador | Casos de uso | Depende de |
|---|---|---|
| `EmpresaOrquestadorService` | Registrar empresa + administrador inicial (HU-01) | `EmpresaService`, `UsuarioService` |
| `ProcesoOrquestadorService` | Crear proceso + pool inicial (HU-04) | `ProcesoService`, `PoolService` |
| `CorrelacionOrquestadorService` | Definir/eliminar correlacion + actualizar el mensaje (HU-28) | `MensajeService`, `CorrelacionService` |
| `PoolOrquestadorService` | Verificar + borrar lanes + eliminar el pool (HU-21) | `PoolService`, `LaneService` |

Como son clases nuevas que dependen de los services existentes (y ningun
service existente depende de ellas), no reintroducen los ciclos que
`SeparacionRepositoriosTest` prohibe.

Un caso distinto: hay reglas de negocio (validar el modelo BPMN antes de
publicar, o rechazar marcar un pool con lanes como caja negra) que deben
cumplirse aunque el Service se llame directamente, sin pasar por un
orquestador. Para esos dos casos se usan **eventos de dominio sincronos**
(`ApplicationEventPublisher` + `@EventListener`):

- `ProcesoService.cambiarEstado` publica `ProcesoPublicacionEvent` antes de
  guardar la transicion a `PUBLICADO`; `ValidacionModeloService` lo escucha.
- `PoolService.editar` publica `PoolMarcadoCajaNegraEvent` antes de guardar
  `cajaNegra = true`; `LaneService` lo escucha.

Un evento no es una dependencia de compilacion (quien publica no conoce a
quien escucha), y `@EventListener` de Spring se ejecuta de forma sincrona,
dentro de la misma transaccion del publicador: si el listener lanza una
excepcion, aborta la operacion antes de guardar nada.

Resultado: **0 usos de `@Lazy` en todo el proyecto**. `SeparacionRepositoriosTest`
tiene una regla `beFreeOfCycles()` que falla el build si se reintroduce un
ciclo entre servicios.

---

## 3. Suite de tests

### 3.1 Inventario

| Tipo | Clases | Tests | Que valida |
|---|---|---|---|
| Contexto | 1 | 1 | Spring Boot arranca correctamente |
| Arquitectura (ArchUnit) | 7 | 35 | Empaquetado, multi-tenencia, herencia JPA, seguridad, separacion de repositorios y 0 ciclos entre servicios |
| Controllers (gestion) | 8 | 53 | Endpoints REST, validaciones, permisos |
| Controllers (modelado) | 8 | 65 | Endpoints REST de modelado BPMN |
| Servicios (gestion) | 10 | 52 | Logica de negocio de gestion, incluye los orquestadores |
| Servicios (modelado) | 12 | 129 | Logica de negocio de modelado, incluye los orquestadores y los `@EventListener` |
| Seguridad e integracion | 4 | 92 | JWT, aislamiento de tenants, roles |
| Transaccionalidad (integracion, H2 real) | 7 | 8 | Rollback real de cada orquestador, cumplimiento de reglas de negocio llamando al Service directamente, y lectura de relaciones LAZY fuera de la transaccion (ProcesoCompartido, Arco) |
| **Total** | **57** | **435** | |

### 3.2 Ejecucion local

```bash
# Todos los tests
./mvnw test

# Solo tests de arquitectura
./mvnw test -Dtest="com.facimus.procesos.arquitectura.**"

# Solo tests de un servicio especifico
./mvnw test -Dtest="ArcoServiceTest"
```

El reporte de cobertura JaCoCo se genera en `target/site/jacoco/index.html`
despues de correr los tests.

### 3.3 Agregar tests nuevos

- Tests de arquitectura van en `src/test/java/com/facimus/procesos/arquitectura/`.
- Tests de controllers usan `@WebMvcTest` con mocks de servicios.
- Tests unitarios de servicio van en el paquete espejo del servicio bajo
  `src/test/`. Ejemplo: servicio en `modelado.service.ArcoService` → test en
  `modelado.service.ArcoServiceTest`.
- Usar `@ExtendWith(MockitoExtension.class)` con `@Mock` e `@InjectMocks`.
  No usar `@SpringBootTest` para tests unitarios — solo para tests de
  integracion que necesiten el contexto completo.

---

## 4. CI/CD con GitHub Actions

### 4.1 Pipeline actual (`.github/workflows/ci.yml`)

```
Push o PR a main/develop
│
├── Job 1: Build & Test (ubuntu + windows, en paralelo)
│   ├── Compilar con Maven
│   ├── Ejecutar los 435 tests
│   ├── Generar reporte de tests
│   ├── Subir reporte JaCoCo como artefacto
│   └── Empaquetar JAR
│
├── Job 2: Architecture Guard (despues de Job 1)
│   ├── Ejecutar los 35 tests de ArchUnit
│   └── Reporte separado de reglas de arquitectura
│
├── Job 3: Docker Build (despues de Jobs 1 y 2)
│   ├── Construir imagen Docker
│   └── Levantar contenedor y verificar que arranca
│
└── Job 4: SonarCloud (despues de Job 1, en push y en pull_request)
    ├── Analisis de calidad y cobertura (`mvn verify sonar:sonar`)
    └── Espera el resultado del quality gate (`-Dsonar.qualitygate.wait=true`):
        el job falla si el gate no se cumple
```

El job de SonarCloud corre tanto en `push` como en `pull_request`. Los PRs
abiertos desde un fork se omiten (GitHub no les entrega los secrets), por lo
que el analisis aplica a PRs de ramas del propio repositorio.

El quality gate es el predeterminado de SonarCloud ("Sonar way"), que evalua
solo el **codigo nuevo** del PR: cobertura >= 80%, duplicacion <= 3%, ratings
de fiabilidad/seguridad/mantenibilidad en A y 100% de security hotspots
revisados. Esto es distinto del minimo de JaCoCo configurado en el `pom.xml`
(50% de lineas sobre todo el proyecto, verificado en `mvn verify`).

### 4.2 Que bloquea un merge

Que un check bloquee el merge depende de que `main` tenga branch protection
con ese check marcado como obligatorio (Settings > Branches). Con la regla
activa, un PR no se puede mergear si:
- Falla cualquier test unitario o de integracion.
- Se viola alguna regla de arquitectura (ArchUnit).
- La imagen Docker no se construye correctamente.
- No se cumple el quality gate de SonarCloud (p. ej. cobertura de codigo
  nuevo por debajo del 80%): el job `SonarQube Analysis` falla.

Checks recomendados como obligatorios: `Build & Test (ubuntu-latest)`,
`Build & Test (windows-latest)`, `Architecture Rules`, `Docker Image` y
`SonarQube Analysis`. Sin branch protection, estos checks informan pero no
impiden el merge.

---

## 5. Despliegue con Docker

### 5.1 Imagen Docker

El `Dockerfile` usa build multi-stage:

```
Stage 1 — build (maven:3.9-eclipse-temurin-21)
  1. Copia pom.xml y descarga dependencias (capa cacheada)
  2. Copia codigo fuente
  3. Ejecuta mvn package (compila + corre tests)
  4. Si algun test falla, el build se detiene aqui

Stage 2 — runtime (eclipse-temurin:21-jre)
  1. Copia solo el JAR del stage anterior
  2. Corre como usuario no-root (appuser, uid 1001)
  3. Expone puerto 8080
```

**Construir la imagen localmente:**

```bash
docker build -t facimus/procesos-back .
```

### 5.2 Perfiles de Spring

| Perfil | BD | DatosDemoInitializer | Uso |
|---|---|---|---|
| (default) | H2 en archivo `./data/` | Activo | Desarrollo local |
| `prod` | PostgreSQL externo | Inactivo (`@Profile("!prod")`) | Contenedores Docker / VMs |

El perfil se activa con la variable de entorno `SPRING_PROFILES_ACTIVE=prod`.

### 5.3 Variables de entorno del perfil prod

| Variable | Default | Descripcion |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | (ninguno) | Debe ser `prod` para activar PostgreSQL |
| `DB_HOST` | `localhost` | IP o hostname de PostgreSQL |
| `DB_PORT` | `5432` | Puerto de PostgreSQL |
| `DB_NAME` | `procesos` | Nombre de la base de datos |
| `DB_USER` | `procesos` | Usuario de la base de datos |
| `DB_PASSWORD` | (vacio) | Contrasena de la base de datos |
| `JWT_SECRET` | (requerido) | Clave para firmar tokens JWT |

### 5.4 Topologia de despliegue

Tres servicios en contenedores Docker, cada uno en su propia VM.

```
VM 1 — Base de datos          VM 2 — Backend              VM 3 — Frontend
┌──────────────────┐          ┌──────────────────┐        ┌──────────────────┐
│  PostgreSQL 17   │          │  procesos-back   │        │  Angular (nginx) │
│  puerto 5432     │◄─────────│  puerto 8080     │◄───────│  puerto 80       │
│                  │   JDBC   │                  │  HTTP  │                  │
└──────────────────┘          └──────────────────┘        └──────────────────┘
```

### 5.5 Comandos de despliegue por VM

**VM 1 — Base de datos:**

```bash
docker run -d \
  --name procesos-db \
  --restart unless-stopped \
  -p 5432:5432 \
  -e POSTGRES_DB=procesos \
  -e POSTGRES_USER=procesos \
  -e POSTGRES_PASSWORD=<password-segura> \
  -v pgdata:/var/lib/postgresql/data \
  postgres:17
```

**VM 2 — Backend:**

```bash
docker run -d \
  --name procesos-back \
  --restart unless-stopped \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_HOST=<ip-vm-1> \
  -e DB_PORT=5432 \
  -e DB_NAME=procesos \
  -e DB_USER=procesos \
  -e DB_PASSWORD=<password-segura> \
  -e JWT_SECRET=<clave-segura> \
  facimus/procesos-back
```

**VM 3 — Frontend:**

```bash
docker run -d \
  --name procesos-front \
  --restart unless-stopped \
  -p 80:80 \
  facimus/procesos-front
```

### 5.6 Verificacion del despliegue

```bash
# Verificar que la BD responde
pg_isready -h <ip-vm-1> -p 5432

# Verificar que el backend responde
curl -s http://<ip-vm-2>:8080/swagger-ui/index.html | head -1

# Verificar login
curl -s -X POST http://<ip-vm-2>:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@demo.com","password":"admin123"}'
```

---

## 6. Guia para contribuir

### 6.1 Flujo de trabajo Git

1. Crear rama desde `main`: `feature/<descripcion>`, `fix/<descripcion>`.
2. Hacer commits modulares (un archivo por commit).
3. Abrir PR contra `main`.
4. Esperar que los 4 jobs del CI pasen en verde.
5. Merge con `--no-ff` para preservar el historial de la rama.

### 6.2 Agregar una entidad nueva

1. Crear la clase en el paquete `model/` del modulo correspondiente.
2. Si pertenece a una empresa, extender `EntidadEmpresa`. Si no lo haces,
   el test `MultitenenciaTest` fallara en CI.
3. Si tiene campos enum, mapearlos con `@Enumerated(EnumType.STRING)`. Si
   usas `ORDINAL`, el test `HerenciaJpaTest` fallara.
4. Crear el repositorio extendiendo `RepositorioTenant<T>`. Si no lo haces,
   el test `RepositorioTenantTest` fallara.
5. Crear el servicio en `service/`. Si lo pones en otro paquete, el test
   `EmpaquetadoTest` fallara.
6. Si necesita controlador, crearlo en `controller/`. Que solo dependa de
   servicios, no de repositorios directamente.
7. Agregar tests unitarios para el servicio y controller nuevos.

### 6.3 Cosas que no se deben hacer

- **No crear queries sin filtro `empresaId`** en ningun repositorio (excepto
  `EmpresaRepository`). Un tenant veria datos de otro.
- **No usar `@Enumerated(EnumType.ORDINAL)`**. Reordenar el enum corrompe
  datos existentes.
- **No acceder a repositorios desde controllers**. Toda la logica pasa por
  la capa de servicio.
- **No hacer DELETE fisico** sobre `Proceso` ni `RolProceso`. Usar
  `setActivo(false)`.
- **No hacer `git push --force`** a `main`.
- **No saltarse los hooks** con `--no-verify`.
