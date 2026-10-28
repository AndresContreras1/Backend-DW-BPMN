# Guía técnica

Documento de referencia del backend `com.facimus.procesos`. Para instalar y ejecutar el proyecto, ver el [README](../README.md).

## 1. Stack

| Componente | Detalle |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot 4.1.0: `spring-boot-starter-webmvc`, `-data-jpa`, `-validation`, `-security`, `-flyway` |
| Autenticación | JWT con jjwt 0.12.6, firma HS256, contraseñas con BCrypt |
| Persistencia | Hibernate/JPA; H2 (perfil por defecto y pruebas) y PostgreSQL (`prod`) |
| Migraciones | Flyway (`flyway-database-postgresql` para PostgreSQL) |
| Mapeo | ModelMapper 3.2.0 |
| API docs | springdoc-openapi-starter-webmvc-ui 3.0.1 |
| Utilidades | Lombok, Spring Boot DevTools (opcional, solo desarrollo) |
| Pruebas | JUnit 5, Mockito, Spring Boot Test, Spring Security Test, ArchUnit 1.4.0 |
| Calidad | JaCoCo 0.8.13 y SonarCloud |

## 2. Inventario del código

Cifras contadas sobre `src/main/java` del estado actual:

| Elemento | Cantidad | Detalle |
|---|---|---|
| Entidades JPA | 17 | `gestion`: Empresa, Usuario, InvitacionUsuario, PermisoEstructura, Proceso, ProcesoCompartido, RolProceso, HistorialCambio. `modelado`: Pool, Lane, NodoFlujo (abstracta) con Actividad, Gateway y EventoMensaje, Arco, Mensaje, Correlacion |
| Enums | 9 | `gestion`: RolAcceso, EstadoProceso. `modelado`: TipoActividad, TipoGateway, TipoEventoMensaje, TipoParticipante, TipoDestinoExterno, PoliticaFalloNotificacion, PoliticaMensajeSinCaso |
| Controllers | 17 | 8 en `gestion` y 9 en `modelado` (incluye `ValidacionModeloController`) |
| Interfaces de servicio | 25 | Cada una con su clase `Impl`: 11 en `gestion`, 14 en `modelado` |
| Orquestadores | 5 | Empresa, Proceso, Pool, Lane y Correlacion (están entre las 25 interfaces) |
| Repositorios | 14 | 8 en `gestion` (incluido `EmpresaRepository`, raíz de la tenencia) y 6 en `modelado` |
| Eventos de dominio | 3 | `PoolMarcadoCajaNegraEvent`, `PoolParticipanteModificadoEvent`, `ProcesoPublicacionEvent` |
| Migraciones Flyway | 4 | `V1`, `V2`, `V5` (comunes) y `V3` (solo PostgreSQL) |

Los enums de `modelado` son `TipoActividad` (TAREA, USUARIO, SERVICIO, MANUAL, SCRIPT, REGLA_NEGOCIO), `TipoGateway` (EXCLUSIVO, PARALELO, INCLUSIVO), `TipoEventoMensaje` (THROW, CATCH_INICIO, CATCH_INTERMEDIO), `TipoParticipante` (EMPRESA, CLIENTE, PROVEEDOR, SISTEMA_EXTERNO), `TipoDestinoExterno` (CORREO, SERVICIO_WEB, COLA), `PoliticaFalloNotificacion` (CONTINUAR_FLUJO, DERIVAR_ACTIVIDAD_ERROR, FINALIZAR_PROCESO) y `PoliticaMensajeSinCaso` (DESCARTAR, INICIAR_CASO_NUEVO). `RolAcceso` es ADMINISTRADOR, EDITOR o SOLO_LECTURA, y `EstadoProceso` es BORRADOR o PUBLICADO.

## 3. Decisiones de arquitectura vigentes

### 3.1 Empaquetado modular y capas

El código se organiza por dominio (`gestion` y `modelado`) y, dentro de cada módulo, por capa: `controller` (con `dto`), `service`, `repository` y `model`. El flujo es `controller -> service -> repository`.

- Los controllers solo conocen servicios; nunca repositorios.
- Los servicios no conocen la capa web ni las clases de `controller`.
- Todo servicio es una interfaz `XxxService` con su implementación `XxxServiceImpl`.
- Los DTO son `record` y viven en `controller/dto`. Las entidades no salen por la API.
- Un servicio no inyecta el repositorio de otro dominio: pide los datos al servicio dueño. La única excepción es `NodoFlujoRepository`, usado por `NodoFlujoServiceImpl`, que centraliza las consultas de los tres tipos de nodo.
- No hay `@Transactional` en controllers, ni `System.out`, ni inyección por campo (solo constructor, con `@RequiredArgsConstructor`).
- `security` no depende de repositorios.
- `DatosDemoInitializer` es el único componente que usa repositorios saltándose los servicios, a propósito.

Estas reglas están verificadas por las pruebas ArchUnit (sección 11).

### 3.2 Multitenencia por columna

Toda entidad, salvo `Empresa`, extiende `EntidadEmpresa` y tiene la columna `empresa_id` (`nullable = false`, `updatable = false`). Los repositorios de esas entidades extienden `RepositorioTenant<T>`, que obliga a consultar con `findByIdAndEmpresaId` y `findAllByEmpresaId`. El `empresaId` nunca viene del cliente: sale del token JWT a través de `ApiPrincipal` (sección 4). Consultar un recurso de otra empresa responde 404, igual que si no existiera. Hay un índice por `empresa_id` en cada tabla (migración `V2`).

### 3.3 Herencia SINGLE_TABLE

`NodoFlujo` es abstracta y usa `InheritanceType.SINGLE_TABLE`: `Actividad`, `Gateway` y `EventoMensaje` comparten la tabla `nodos_flujo` con una columna discriminadora (`ACTIVIDAD`, `GATEWAY`, `EVENTO_MENSAJE`). Los arcos conectan cualquier nodo con cualquier otro del mismo pool. La relación entre un Message Catch y las actividades que usan sus datos vive en la tabla `evento_mensaje_actividades` (migración `V5`).

### 3.4 Enums como STRING

Todos los enums persistidos usan `@Enumerated(EnumType.STRING)`, nunca ordinal, para que reordenar o añadir valores no corrompa los datos. Una prueba de arquitectura lo exige.

### 3.5 Eliminación lógica

Las entidades eliminables tienen el campo `activo`: `Proceso`, `Usuario`, `RolProceso`, `ProcesoCompartido`, `Pool`, `Lane`, `NodoFlujo`, `Arco`, `Mensaje` y `Correlacion`. Eliminar significa poner `activo = false`; no se borran filas. Hay dos mecanismos:

- `Pool`, `Lane` y `Correlacion` usan `@SQLDelete` (convierte el `DELETE` en un `UPDATE ... SET activo = false`) y `@SQLRestriction("activo = true")` (oculta las filas inactivas).
- El resto filtra de forma explícita en los repositorios (métodos con `ActivoTrue` o predicados en la consulta) y los servicios cambian el campo.

Al eliminar una actividad, gateway o evento se desactivan también los arcos conectados. Eliminar un pool lo orquesta `PoolOrquestadorService`: elimina sus lanes en cascada y se rechaza si alguna tiene elementos activos. El nombre de un proceso o de un rol solo es único entre los activos (índices parciales de PostgreSQL en `V3` más validación en el servicio), así que se puede reutilizar el de uno dado de baja.

### 3.6 ModelMapper en la capa de servicio

Los métodos de consulta y de comando de cada servicio devuelven DTO de respuesta; el controller solo traduce HTTP. El mapeo entidad a DTO usa `ModelMapper` (registrado en `ModelMapperConfig`) dentro de los `Impl`, con la transacción aún abierta, de modo que las relaciones `LAZY` se resuelven sin `LazyInitializationException` (`spring.jpa.open-in-view=false`). Los DTO que necesitan composición propia se construyen a mano: `LoginResponse`, `ProcesoDetalleResponse`, `ProcesoDiagramaResponse` y `ProcesoCompartidoDetalleResponse`. Cada servicio expone además métodos que trabajan con la entidad (para que otros servicios compongan reglas de negocio) y métodos que trabajan con DTO (para el controller).

### 3.7 Orquestadores y eventos de dominio

Para evitar dependencias circulares entre servicios, hay dos mecanismos:

**Orquestadores** (transaccionales, dependen de varios servicios y ninguno depende de ellos):

| Orquestador | Caso de uso |
|---|---|
| `EmpresaOrquestadorService` | Registrar una empresa junto con su administrador inicial |
| `ProcesoOrquestadorService` | Crear un proceso junto con su pool inicial de tipo EMPRESA |
| `PoolOrquestadorService` | Eliminar un pool con sus lanes en cascada |
| `LaneOrquestadorService` | Crear o editar una lane resolviendo su rol de proceso |
| `CorrelacionOrquestadorService` | Definir o eliminar la correlación y sincronizar la clave del mensaje |

**Eventos de dominio** (síncronos, dentro de la misma transacción; si el listener lanza una excepción, el cambio se aborta antes de guardar):

| Evento | Lo publica | Lo escucha | Efecto |
|---|---|---|---|
| `ProcesoPublicacionEvent` | `ProcesoService.cambiarEstado`, al pasar a PUBLICADO | `ValidacionModeloService` | Impide publicar un modelo incoherente |
| `PoolMarcadoCajaNegraEvent` | `PoolService.editar` | `LaneService` | Impide marcar caja negra un pool con lanes |
| `PoolParticipanteModificadoEvent` | `PoolService.editar` | `MensajeService` | Impide cambiar tipo de participante o caja negra de un pool que es destino externo de mensajes activos |

### 3.8 ApiPrincipal

`ApiPrincipal` es un `record(usuarioId, empresaId, rol, email)` que representa al usuario autenticado. El filtro JWT lo coloca en el contexto de seguridad y los controllers lo reciben con `@AuthenticationPrincipal`. Es la única fuente del tenant y del rol en cada petición.

### 3.9 Validación de coherencia del modelo por API

`ValidacionModeloService` reúne todas las advertencias de un proceso sin lanzar excepción:

- `GET /procesos/{id}/validacion` devuelve `{ valido, advertencias[] }` para que el usuario vea el estado mientras modela.
- Publicar usa la misma lista y responde 409 si no está vacía.

Reglas comprobadas:

- Exactamente un pool de tipo EMPRESA, y que no sea caja negra.
- Un pool de caja negra no contiene lanes.
- Gateway divergente: al menos dos salidas; convergente (dos o más entradas): una salida.
- Arcos que salen de un gateway exclusivo o inclusivo con más de una salida: todos con condición.
- Message Catch de inicio sin arcos entrantes; Message Catch sin mensaje vinculado ni origen externo; Message Catch intermedio con clave de correlación.
- Mensajes: Message Throw asociado, receptor (Message Catch o destino externo), clave y correlación definidas, claves coherentes entre mensaje, Throw y Catch, sin mensajes ambiguos (mismo nombre y clave), y destinos externos hacia un pool SISTEMA_EXTERNO de caja negra con el destino documentado.

Además, cada operación de modelado aplica sus reglas al instante (409 o 400): nombres de nodo únicos por proceso, arcos solo dentro del mismo pool y sin lazos a sí mismos ni duplicados, condición solo en arcos de gateways exclusivos o inclusivos, no modelar en procesos inactivos, no mover un nodo con arcos a otro pool, mensajes entre pools distintos del mismo proceso, entre otras.

### 3.10 Consultas JPQL

Las consultas se derivan por nombre de método (`findByIdAndEmpresaId`, `...ActivoTrue`) y hay cuatro `@Query` JPQL para lo que no se expresa así: la búsqueda paginada de procesos con filtros opcionales (`ProcesoRepository`), el uso de roles por proceso (`LaneRepository`), mensajes por nodo (`MensajeRepository`) y la búsqueda de nodos por nombre sin distinguir mayúsculas (`NodoFlujoRepository`). Las lecturas que navegan relaciones declaran `@EntityGraph`.

### 3.11 Historial de cambios

`HistorialCambioService` y `AuditoriaModeladoService` registran en `HistorialCambio` (proceso, autor, fecha y descripción) los cambios del proceso, de sus roles, de lo que se comparte y del diagrama. Se consulta con `GET /procesos/{id}/historial` y dentro del detalle del proceso.

### 3.12 Procesos compartidos

Un administrador comparte un proceso con otra empresa y esta lo ve en solo lectura (`/procesos-compartidos`). La empresa invitada no puede modificar el diagrama; el propietario puede revocar el acceso. No se puede compartir con la propia empresa.

## 4. Seguridad (nivel backend)

- **JWT stateless**: `JwtAuthenticationFilter` lee `Authorization: Bearer <token>`, valida firma y expiración con `JwtService` y vuelve a cargar al usuario de la base en cada petición; si el usuario fue desactivado o ya no existe, la petición queda sin autenticar (401). El token lleva `usuarioId`, `empresaId`, `rol` y el correo como subject. Duración por defecto: 1800 segundos (`JWT_EXPIRATION_SECONDS`).
- **Secreto**: en `prod`, `JwtService` no arranca con un `JWT_SECRET` vacío o menor de 32 caracteres. Fuera de `prod`, un secreto vacío genera una clave aleatoria por arranque.
- **Sesión y CSRF**: sin sesión (`STATELESS`); el CSRF queda exento solo para `/api/v1/**` y `/h2-console/**`.
- **CORS**: `CorsConfig` permite los orígenes de `CORS_ALLOWED_ORIGINS`, los métodos GET, POST, PUT, PATCH y DELETE y las cabeceras `Authorization`, `Content-Type` y `Accept`.
- **Login**: el mismo mensaje y un tiempo de respuesta equivalente se dan si el correo no existe, está inactivo o la contraseña es incorrecta (siempre se ejecuta una comparación BCrypt). Los correos son únicos en todo el sistema, sin distinguir mayúsculas.
- **Roles de acceso** (`SecurityConfig`):
  - Públicas: `POST /auth/login`, `POST /empresas`, `POST /usuarios/invitaciones/*/aceptar`, además de Swagger, `/v3/api-docs`, `/h2-console` y `/error` (en `prod` Swagger y la consola H2 están apagados).
  - `GET` en `/api/v1/**`: cualquier usuario autenticado, salvo `/usuarios/**` y `/permisos-estructura` (solo ADMINISTRADOR). `GET /permisos-estructura/mio` es para cualquier autenticado.
  - `/usuarios/**`, `/permisos-estructura/**` (salvo `mio`), `/roles/**` en escritura, compartir y revocar procesos: ADMINISTRADOR.
  - `DELETE` de procesos, actividades, eventos de mensaje, arcos, gateways y mensajes: ADMINISTRADOR.
  - Resto de escrituras: ADMINISTRADOR o EDITOR. SOLO_LECTURA nunca escribe.
- **Permisos de estructura**: crear, editar y eliminar pools y lanes los decide `PermisoEstructuraService` por empresa y por rol (seis banderas: `crearPool`, `editarPool`, `eliminarPool`, `crearLane`, `editarLane`, `eliminarLane`). El ADMINISTRADOR siempre tiene todas y no se puede reducir; por defecto el EDITOR puede crear y editar pero no eliminar; SOLO_LECTURA no puede recibir ninguna. El cliente consulta los suyos con `GET /permisos-estructura/mio`. Sin permiso, la respuesta es 403.
- **Último administrador**: no se puede degradar ni desactivar al último administrador activo de una empresa (409).
- **Invitaciones**: token UUID de un solo uso con vigencia de 48 horas; no se puede invitar a un correo ya registrado ni duplicar una invitación pendiente.
- **Respuestas de error seguras**: sin trazas ni mensajes internos (`server.error.include-*=never`) y sin exponer SQL ni nombres de restricciones.

## 5. Errores

Todos los errores usan `ProblemDetail` (RFC 7807) con `title`, `status`, `detail` e `instance`. `ApiExceptionHandler` los produce; para 401 y 403 en la cadena de seguridad lo hacen `JwtAuthEntryPoint` y `JwtAccessDeniedHandler`.

| Código | Título | Cuándo |
|---|---|---|
| 400 | Solicitud invalida | `SolicitudInvalidaException`: petición con campos incompatibles entre sí (p. ej. correlación, destino externo o políticas en un mensaje sin Message Throw) |
| 400 | Validación fallida | Falla Bean Validation; el detalle lista `campo: mensaje` |
| 400 | Parámetro inválido | Tipo de parámetro incorrecto o `pagina`/`tamano` fuera de rango |
| 400 | JSON inválido | Cuerpo ausente o malformado |
| 401 | No autenticado | Token ausente, inválido o expirado; credenciales incorrectas en el login |
| 403 | Sin permisos | Rol sin acceso al recurso o sin permiso de estructura |
| 404 | Recurso no encontrado | `RecursoNoEncontradoException`: no existe o pertenece a otra empresa |
| 409 | Regla de negocio violada | `ReglaNegocioException`: nombre duplicado, publicar un modelo incoherente, borrar algo en uso, último administrador, etc. |
| 409 | Conflicto de datos | `DataIntegrityViolationException`; el mensaje es genérico y el detalle va solo al log |
| 500 | Error interno | Cualquier excepción no prevista; el detalle va solo al log |

Las excepciones de dominio (`RecursoNoEncontradoException`, `ReglaNegocioException`, `SolicitudInvalidaException`) viven en `common/`. Los servicios no usan `ResponseStatusException`: la traducción a HTTP es responsabilidad de `ApiExceptionHandler`.

## 6. Paginación

Las listas paginadas (`GET /procesos`, `GET /roles/consulta`) usan los parámetros `pagina` (desde 0) y `tamano` (de 1 a 100; 10 por defecto) y devuelven `PageResponse<T>`:

```json
{ "content": [], "page": 0, "size": 10, "totalElements": 0, "totalPages": 0 }
```

Un valor fuera de rango responde 400. Los procesos se ordenan por fecha de modificación descendente y los roles por nombre. El resto de listados (usuarios, pools, lanes, nodos, arcos, mensajes, etc.) no se pagina.

## 7. Base de datos, Flyway y perfiles

| Perfil | Base | Esquema |
|---|---|---|
| por defecto | H2 en archivo `./data/procesos` | Hibernate `ddl-auto=update`, Flyway apagado |
| `test` | H2 en memoria (`testdb`) | Hibernate `create-drop`, Flyway apagado |
| `prod` | PostgreSQL (`DB_*`) | Flyway crea el esquema; Hibernate `validate` |

Migraciones (`src/main/resources/db/migration/`):

| Archivo | Contenido |
|---|---|
| `common/V1__esquema_inicial.sql` | Esquema completo |
| `common/V2__indices_por_empresa.sql` | Índices por `empresa_id` |
| `postgresql/V3__unicidad_entre_activos.sql` | Índices únicos parciales (nombre de proceso y de rol por empresa, solo activos); propios de PostgreSQL |
| `common/V5__eventos_mensaje_actividades.sql` | Tabla `evento_mensaje_actividades` |

En `prod`, Flyway carga `classpath:db/migration/common` y `classpath:db/migration/postgresql`, con `baseline-on-migrate=false`. `MigracionesTest` aplica las migraciones comunes sobre H2 en modo PostgreSQL y deja que Hibernate valide el esquema contra las entidades: si una entidad cambia sin migración, la prueba falla. Todo cambio de entidad que toque el esquema debe traer su migración nueva (nunca se edita una ya aplicada).

## 8. Datos de ejemplo

`DatosDemoInitializer` (`@Profile("default")`) carga, solo si no hay ninguna empresa, dos empresas, cuatro usuarios (uno por rol de acceso en la primera y un administrador en la segunda), roles de proceso y procesos de ejemplo. Credenciales y detalle en el [README](../README.md). No corre en `prod` ni en `test`.

## 9. Docker y Compose

- `Dockerfile` multi-etapa: `maven:3.9-eclipse-temurin-21` compila con `mvn -B clean package` (que ejecuta las pruebas) y `eclipse-temurin:21-jre` ejecuta el JAR con un usuario sin privilegios (`appuser`, UID 1001). Perfil `prod` por defecto, puerto 8080.
- `compose.yaml`: servicio `db` (PostgreSQL 16, con healthcheck y volumen `pgdata`) y servicio `backend` (perfil `prod`, espera a que la base esté sana). `DB_PASSWORD` y `JWT_SECRET` son obligatorias.
- `.env.example` documenta las variables; `.env` no se versiona.

## 10. CI/CD

Workflow `.github/workflows/ci.yml` (en `push` a `main` y en `pull_request`):

1. `build-and-test`: `mvn -B clean verify` en Ubuntu y Windows (JDK 21 Temurin), informe de pruebas, artefacto JaCoCo y verificación del JAR.
2. `architecture-guard`: ejecuta solo las pruebas de `com.facimus.procesos.arquitectura`; depende del job anterior.
3. `docker-build`: construye la imagen y la ejecuta brevemente; depende de los dos anteriores.
4. `sonarqube`: `mvn verify sonar:sonar` con `-Dsonar.qualitygate.wait=true` contra SonarCloud (`sonar.projectKey=AndresContreras1_Backend-DW-BPMN`). Usa los secretos `SONAR_TOKEN` y `SONAR_HOST_URL`. Se exceptúan de cobertura y análisis `**/dto/**`, `**/config/**` y `*Application.java`. No corre en pull requests de forks.

El repositorio no tiene un job de despliegue: el CI valida y construye, no publica la imagen.

## 11. Pruebas

`./mvnw verify` ejecuta **724 pruebas** (85 clases, 0 fallos). El inventario por paquete:

| Paquete de pruebas | Pruebas | Qué cubre |
|---|---|---|
| `arquitectura` | 45 | Reglas ArchUnit (ver abajo) |
| `gestion.service` | 119 | Servicios de gestión con Mockito, incluida la verificación del mapeo a DTO |
| `gestion.controller` | 71 | Controllers con `@WebMvcTest`: códigos HTTP, validación de requests, catálogo de errores |
| `gestion.repository` | 11 | `@DataJpaTest` de consultas de procesos, roles, historial, etc. |
| `modelado.service` | 252 | Servicios de modelado: reglas de pools, lanes, nodos, arcos, mensajes, correlaciones y validación del modelo |
| `modelado.controller` | 76 | Controllers de modelado |
| `modelado.repository` | 4 | Consultas de modelado |
| `modelado` (raíz) | 13 | Integración end-to-end de reglas de modelado |
| `security` | 110 | Autorización por rol, aislamiento entre empresas, permisos de estructura, JWT, respuestas seguras |
| `transaccionalidad` | 16 | Atomicidad de operaciones compuestas (registro de empresa, creación de proceso, eliminar pool, correlación), invariantes y proxies lazy |
| `common.api` | 1 | `ApiExceptionHandler` |
| `config` | 4 | `DatosDemoInitializer` |
| `migraciones` | 1 | Flyway contra las entidades |
| raíz | 1 | Carga del contexto de Spring |

Tipos: 37 clases con Mockito, 18 con `@WebMvcTest`, 17 con `@SpringBootTest`, 3 con `@DataJpaTest`. Los métodos de prueba siguen un nombre descriptivo de la forma `Sujeto_accion_resultado`.

**Reglas ArchUnit** (`arquitectura/`): capas controller/service/repository respetadas; los controllers no usan repositorios; los servicios no dependen de controllers ni de la capa web; los repositorios y las entidades no dependen de capas superiores; `security` no depende de repositorios; ningún servicio inyecta el repositorio de otro dominio (salvo `NodoFlujoRepository`) y no hay ciclos entre servicios; servicios como interfaz más `Impl` con sufijos de nombre correctos; DTO como `record` en `dto`; las entidades no salen por la API; todas las entidades salvo `Empresa` extienden `EntidadEmpresa` con `empresa_id` no actualizable; todo repositorio tenant extiende `RepositorioTenant`; los servicios consultan con `empresaId`; sin `HttpSession`; sin `@Transactional` en controllers; sin salida estándar; sin inyección por campo; `NodoFlujo` con `SINGLE_TABLE` y subtipos que lo extienden; enums como `STRING`.

**Cobertura (JaCoCo `check`, en `verify`)**: paquetes `*.service` con mínimo 70 % de líneas y 60 % de ramas; proyecto con mínimo 50 % de líneas; excluidos `dto`, `config` y `*Application`. Con el estado actual, todos los umbrales se cumplen.

Otros recursos: `postman/` (dos colecciones: la API completa y un flujo por historias de usuario) y `jmeter/` (planes de login, CRUD de procesos, CRUD de roles y modelado BPMN) para pruebas manuales y de carga; no forman parte de `verify`.

## 12. Guía para contribuir

**Flujo de ramas**

- `main` es la rama estable y no se trabaja directamente sobre ella.
- Cada cambio va en su propia rama: `feature/<tema>` para funcionalidad nueva y `fix/<tema>` para correcciones.
- Un cambio por pull request, con destino `main`. El PR debe pasar los cuatro jobs del CI.

**Commits**: prefijo convencional y descripción en español: `feat:`, `fix:`, `docs:`, `refactor:` (por ejemplo `docs: actualizar el README y la guía técnica`). Un commit debe contener un cambio coherente.

**Antes de abrir el PR**

1. `./mvnw verify` en verde (pruebas, ArchUnit y JaCoCo).
2. Si cambió una entidad, añadir la migración Flyway correspondiente.
3. Cada servicio nuevo es interfaz más `Impl`, devuelve DTO mediante ModelMapper y filtra por `empresaId`.
4. Cada endpoint nuevo lleva sus anotaciones OpenAPI, su regla en `SecurityConfig` si no cae en las generales y pruebas de controller y de servicio.
5. Actualizar el README y esta guía si cambian endpoints, variables o decisiones.

## 13. Lo que no se hace

- No se ejecutan procesos: no hay motor BPMN, instancias, tareas ni envío real de mensajes o notificaciones.
- No se importa ni se exporta XML BPMN.
- No hay refresh tokens ni revocación de tokens: `POST /auth/logout` solo confirma; el token vale hasta que expira (aunque desactivar al usuario sí lo invalida, porque se revisa en cada petición).
- No se envían correos (ni invitaciones ni notificaciones).
- No se recupera ni se cambia la contraseña por API.
- No se borran datos de forma física: todo es baja lógica.
- No hay control de versiones del diagrama: solo un historial de cambios y el estado BORRADOR/PUBLICADO (un proceso publicado no vuelve a borrador).
- No se despliega la imagen desde el CI.
- No hay frontend en este repositorio.

## 14. Limitaciones conocidas

- **La invitación entrega el token en la respuesta y no envía correo.** Quien invita debe hacer llegar el enlace o el token al colaborador por su cuenta. El texto "por correo" de la descripción OpenAPI del endpoint no corresponde a un envío real.
- **El contenido de los mensajes es texto libre.** El campo `contenido` de `Mensaje` y de `EventoMensaje` no se valida contra un esquema de datos; solo se comprueba que las claves de correlación coincidan.
- **La comprobación del último administrador no es atómica.** Se cuenta a los administradores activos y luego se guarda el cambio sin bloqueo; dos peticiones simultáneas podrían dejar a una empresa sin administrador.
- **El nombre único de proceso o rol depende de la validación del servicio en el perfil por defecto.** Los índices únicos parciales solo existen en PostgreSQL (`prod`); con H2 solo protege la comprobación previa del servicio, que tampoco es atómica ante peticiones simultáneas.
- **Los correos de usuario son únicos globalmente**, no por empresa; una persona no puede pertenecer a dos empresas con el mismo correo.
- **Las listas no paginadas** (usuarios, pools, lanes, nodos, arcos, mensajes) devuelven todo el conjunto; el diagrama completo se entrega en una sola respuesta.
- **Sin límite de intentos de inicio de sesión**: no hay bloqueo ni control de frecuencia.
- **Sin migración de la base H2 local**: el perfil por defecto usa `ddl-auto=update`; si cambia el modelo conviene borrar la carpeta `data/`.
- **Falta la migración `V4`**: la numeración de Flyway salta de `V3` a `V5` (no hay migración con ese número); las migraciones existentes se aplican en orden sin problema.
