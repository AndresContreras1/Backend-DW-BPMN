# Facimus Procesos - Backend

API REST para el modelado de procesos de negocio con notación BPMN en un entorno multiempresa. Cada empresa registra a sus colaboradores, define roles de proceso y construye diagramas (pools, lanes, actividades, gateways, eventos de mensaje, arcos y mensajes entre participantes). El sistema valida la coherencia del modelo y permite publicarlo o compartirlo en solo lectura con otras empresas.

## Alcance

- **Modela y valida**: guarda la estructura del diagrama y comprueba sus reglas de coherencia (`GET /api/v1/procesos/{id}/validacion`; la publicación aplica las mismas reglas).
- **No ejecuta procesos**: no hay motor de ejecución, instancias, tareas asignadas ni envío real de mensajes. Los mensajes y las políticas de fallo son solo parte del modelo.
- **No incluye interfaz**: es solo el backend. El frontend consume la API y está fuera de este repositorio.

## Stack

| Componente | Versión |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.0 (Web MVC, Data JPA, Validation, Security) |
| JWT (jjwt) | 0.12.6 |
| Base de datos | H2 en archivo (desarrollo) / PostgreSQL 16 (producción) |
| Migraciones | Flyway (solo en el perfil `prod`) |
| Mapeo entidad a DTO | ModelMapper 3.2.0 |
| Documentación de la API | springdoc-openapi 3.0.1 (Swagger UI) |
| Pruebas | JUnit 5, Spring Boot Test, Spring Security Test, ArchUnit 1.4.0 |
| Calidad | JaCoCo 0.8.13, SonarCloud (sonar-maven-plugin 4.0.0.4121) |
| Contenedores | Docker (build multi-etapa) y Docker Compose |
| CI | GitHub Actions |

## Requisitos

- JDK 21.
- Maven: no hace falta instalarlo, el repositorio incluye el wrapper (`./mvnw`).
- Docker y Docker Compose (opcional, solo para la ejecución con PostgreSQL).

## Ejecución local (perfil por defecto)

```bash
./mvnw spring-boot:run
```

La API queda en `http://localhost:8080`. El perfil por defecto usa H2 en archivo (`./data/procesos`), crea el esquema con Hibernate (`ddl-auto=update`), deja Flyway apagado y expone la consola H2 en `/h2-console`. Si no se define `JWT_SECRET`, se genera una clave aleatoria en cada arranque y los tokens dejan de valer al reiniciar.

Con la base vacía, `DatosDemoInitializer` carga dos empresas de ejemplo. Los datos incluyen el proceso "Solicitud de vacaciones" completo (pool, lanes, actividades, gateway, arcos, mensaje a un sistema externo y correlación), un proceso en borrador y uno dado de baja lógica. La segunda empresa reutiliza el nombre del proceso para demostrar el aislamiento entre empresas.

| Empresa | Correo | Contraseña | Rol de acceso |
|---|---|---|---|
| Empresa Demo S.A.S. | `admin@demo.com` | `Admin-Demo-2026` | ADMINISTRADOR |
| Empresa Demo S.A.S. | `editor@demo.com` | `Editor-Demo-2026` | EDITOR |
| Empresa Demo S.A.S. | `lectura@demo.com` | `Lector-Demo-2026` | SOLO_LECTURA |
| Empresa Beta S.A.S. | `admin@beta.com` | `Admin-Beta-2026` | ADMINISTRADOR |

Estas cuentas existen **solo en el perfil por defecto**. El inicializador no corre en `prod` ni en `test`. Para borrar los datos locales basta con eliminar la carpeta `data/`.

## Ejecución con Docker Compose

`compose.yaml` levanta PostgreSQL 16 y el backend (perfil `prod`).

```bash
cp .env.example .env     # completar DB_PASSWORD y JWT_SECRET
docker compose up -d --build
```

Variables de `.env.example`:

| Variable | Descripción | Obligatoria |
|---|---|---|
| `DB_NAME` | Nombre de la base (por defecto `procesos`) | No |
| `DB_USER` | Usuario de la base (por defecto `procesos`) | No |
| `DB_PASSWORD` | Contraseña de la base | **Sí** |
| `JWT_SECRET` | Clave de firma de los JWT: mínimo 32 caracteres, aleatoria y propia de cada entorno | **Sí** |
| `CORS_ALLOWED_ORIGINS` | Orígenes permitidos, separados por coma (por defecto `http://localhost:4200`) | No |

`DB_PASSWORD` y `JWT_SECRET` son obligatorias: `docker compose` aborta si faltan, y en el perfil `prod` la aplicación no arranca con un `JWT_SECRET` vacío o de menos de 32 caracteres. En `prod` Swagger y `/v3/api-docs` quedan apagados, la consola H2 está desactivada y no se cargan datos de ejemplo: la primera empresa se crea con `POST /api/v1/empresas`.

El backend se publica en el puerto 8080; los datos de PostgreSQL persisten en el volumen `pgdata`.

## Perfiles

| Perfil | Base de datos | Esquema | Flyway | Notas |
|---|---|---|---|---|
| (por defecto) | H2 en archivo `./data` | Hibernate `update` | Apagado | Swagger y consola H2 activos; carga datos de ejemplo |
| `test` | H2 en memoria | Hibernate `create-drop` | Apagado | Lo usan las pruebas; `MigracionesTest` ejecuta Flyway aparte |
| `prod` | PostgreSQL | Flyway; Hibernate solo valida (`validate`) | Encendido | Swagger apagado; `JWT_SECRET` obligatorio |

## Variables de entorno

| Variable | Descripción | Valor por defecto |
|---|---|---|
| `JWT_SECRET` | Clave de firma de los JWT | Aleatoria en cada arranque (solo fuera de `prod`) |
| `JWT_EXPIRATION_SECONDS` | Duración del token en segundos | `1800` |
| `CORS_ALLOWED_ORIGINS` | Orígenes permitidos por CORS | `http://localhost:4200` |
| `DB_HOST` | Host de PostgreSQL (`prod`) | `localhost` |
| `DB_PORT` | Puerto de PostgreSQL (`prod`) | `5432` |
| `DB_NAME` | Nombre de la base (`prod`) | `procesos` |
| `DB_USER` | Usuario de la base (`prod`) | `procesos` |
| `DB_PASSWORD` | Contraseña de la base (`prod`) | vacía |
| `SPRING_PROFILES_ACTIVE` | Perfil activo | `prod` en la imagen Docker |

## API REST

Todas las rutas cuelgan de `/api/v1`. Salvo las públicas, requieren `Authorization: Bearer <token>`. Los errores usan `ProblemDetail` (ver la [guía técnica](docs/guia-tecnica.md)).

Reglas generales de autorización:

- Públicas: `POST /auth/login`, `POST /empresas` y `POST /usuarios/invitaciones/{token}/aceptar`.
- Cualquier usuario autenticado puede hacer `GET` en todo `/api/v1` salvo `/usuarios/**` y `/permisos-estructura` (que son solo del administrador). `GET /permisos-estructura/mio` es para cualquier usuario autenticado.
- Escrituras (`POST`, `PUT`, `PATCH`): ADMINISTRADOR o EDITOR, salvo las marcadas como "Admin".
- `DELETE`: ADMINISTRADOR, salvo pools y lanes, que dependen de los permisos de estructura (administrador siempre; editor solo si el administrador se lo concedió; solo lectura nunca). Crear o editar pools y lanes también consulta esos permisos.
- SOLO_LECTURA no puede escribir.

### Autenticación y empresa

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| POST | `/auth/login` | Público | Devuelve el JWT y los datos del usuario |
| POST | `/auth/logout` | Autenticado | Sin estado: el cliente descarta el token |
| POST | `/empresas` | Público | Registra una empresa y su administrador inicial |

### Usuarios e invitaciones (solo administrador)

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/usuarios?activo=` | Lista colaboradores (activos por defecto; `activo=false` lista los desactivados) |
| POST | `/usuarios` | Alta directa con contraseña |
| GET | `/usuarios/{id}` | Detalle |
| PATCH | `/usuarios/{id}` | Cambia rol de acceso y/o estado activo |
| DELETE | `/usuarios/{id}` | Desactiva al colaborador (baja lógica) |
| POST | `/usuarios/invitaciones` | Crea una invitación (token válido 48 horas) |
| POST | `/usuarios/invitaciones/{token}/aceptar` | Público: crea la cuenta con el token |

### Permisos de estructura

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/permisos-estructura` | Admin | Permisos de cada rol de acceso |
| GET | `/permisos-estructura/mio` | Autenticado | Permisos del rol del usuario actual |
| PUT | `/permisos-estructura/{rol}` | Admin | Define qué puede crear, editar o eliminar ese rol en pools y lanes |

### Roles de proceso

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/roles` | Autenticado | Lista los roles con los procesos donde están en uso |
| GET | `/roles/consulta?nombre=&pagina=&tamano=` | Autenticado | Búsqueda paginada por nombre |
| GET | `/roles/{id}` | Autenticado | Detalle |
| POST | `/roles` | Admin | Crea (nombre único por empresa) |
| PUT | `/roles/{id}` | Admin | Edita |
| DELETE | `/roles/{id}` | Admin | Baja lógica; se rechaza si está en uso en un proceso activo |

### Procesos

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/procesos?nombre=&estado=&categoria=&activo=&pagina=&tamano=` | Búsqueda paginada (`pagina` desde 0; `tamano` de 1 a 100, por defecto 10; orden por última modificación descendente) |
| POST | `/procesos` | Crea en BORRADOR con su pool inicial de tipo EMPRESA |
| GET | `/procesos/{id}` | Proceso con su historial |
| PUT | `/procesos/{id}` | Edita nombre, descripción y categoría |
| PATCH | `/procesos/{id}` | Cambia el estado; publicar exige que el modelo sea coherente |
| GET | `/procesos/{id}/diagrama` | Pools, lanes, actividades, gateways, eventos, arcos y mensajes en una respuesta |
| GET | `/procesos/{id}/historial` | Bitácora de cambios |
| GET | `/procesos/{id}/validacion` | Estado de coherencia del modelo: `{ valido, advertencias[] }` |
| DELETE | `/procesos/{id}` | Admin. Baja lógica |

### Procesos compartidos

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/procesos/{procesoId}/compartidos` | Autenticado | Empresas con las que se comparte el proceso (solo la propietaria) |
| POST | `/procesos/{procesoId}/compartidos` | Admin | Comparte en solo lectura con otra empresa |
| DELETE | `/procesos/{procesoId}/compartidos/{empresaInvitadaId}` | Admin | Revoca el acceso |
| GET | `/procesos-compartidos` | Autenticado | Procesos que otras empresas comparten con la actual |
| GET | `/procesos-compartidos/{procesoId}` | Autenticado | Detalle completo del diagrama compartido (solo lectura) |

### Modelado del diagrama

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/procesos/{procesoId}/pools` | Lista los pools |
| POST | `/procesos/{procesoId}/pools` | Crea un pool |
| GET / PUT / DELETE | `/pools/{id}` | Detalle, edición y baja (elimina sus lanes en cascada) |
| GET | `/pools/{poolId}/lanes` | Lista las lanes del pool |
| POST | `/pools/{poolId}/lanes` | Crea una lane (asociada a un rol de proceso) |
| PATCH | `/pools/{poolId}/lanes/orden` | Reordena las lanes (lista completa de ids) |
| GET / PUT / DELETE | `/lanes/{id}` | Detalle, edición y baja |
| GET / POST | `/lanes/{laneId}/actividades` | Lista y crea actividades |
| GET / PUT / DELETE | `/actividades/{id}` | Detalle, edición y baja |
| GET | `/actividades/{id}/impacto-eliminacion` | Advertencias previas a eliminar, sin borrar |
| GET / POST | `/lanes/{laneId}/gateways` | Lista y crea gateways |
| GET / PUT / DELETE | `/gateways/{id}` | Detalle, edición y baja |
| GET | `/gateways/{id}/impacto-eliminacion` | Advertencias previas a eliminar |
| GET / POST | `/lanes/{laneId}/eventos-mensaje` | Lista y crea eventos de mensaje (Throw, Catch inicio, Catch intermedio) |
| GET / PUT / DELETE | `/eventos-mensaje/{id}` | Detalle, edición y baja |
| GET | `/eventos-mensaje/{id}/impacto-eliminacion` | Advertencias previas a eliminar |
| GET | `/pools/{poolId}/arcos` | Lista los arcos del pool |
| POST | `/arcos` | Crea un arco entre dos nodos del mismo pool |
| GET / PUT / DELETE | `/arcos/{id}` | Detalle, edición y baja |
| GET | `/arcos/{id}/impacto-eliminacion` | Advertencias previas a eliminar |
| GET / POST | `/procesos/{procesoId}/mensajes` | Lista y crea mensajes entre dos pools |
| GET / PUT / DELETE | `/mensajes/{id}` | Detalle, edición y baja |
| PUT / GET / DELETE | `/mensajes/{mensajeId}/correlacion` | Define (upsert), consulta y elimina la correlación del mensaje |

## Swagger

Con el perfil por defecto: `http://localhost:8080/swagger-ui/index.html` (esquema OpenAPI en `/v3/api-docs`). Solo documenta las rutas `/api/**` y ofrece el botón "Authorize" para pegar el JWT. En `prod` está apagado.

Colecciones de Postman en `postman/` y planes de JMeter en `jmeter/` (login, CRUD de procesos, CRUD de roles y modelado BPMN) para probar la API a mano o bajo carga.

## Pruebas

```bash
./mvnw verify
```

Ejecuta 724 pruebas (unitarias con Mockito, de repositorio con `@DataJpaTest`, de controllers con MockMvc, de integración con seguridad real, de transaccionalidad, de migraciones y de arquitectura con ArchUnit) y después JaCoCo `check`. Umbrales del `pom.xml`:

- Paquetes `com.facimus.procesos.*.service`: cobertura de líneas mínima 70 % y de ramas 60 %.
- Proyecto completo: cobertura de líneas mínima 50 %.
- Se excluyen `**/dto/**`, `**/config/**` y la clase `*Application`.

El informe queda en `target/site/jacoco/`. El detalle del inventario está en la [guía técnica](docs/guia-tecnica.md).

## Integración continua

`.github/workflows/ci.yml` corre en cada `push` a `main` y en cada `pull_request`:

| Job | Qué hace |
|---|---|
| `build-and-test` | `mvn -B clean verify` en Ubuntu y Windows con JDK 21 (Temurin); publica los resultados de pruebas, el informe JaCoCo (Ubuntu) y comprueba que exista el JAR |
| `architecture-guard` | Ejecuta solo las pruebas del paquete `arquitectura`. Depende de `build-and-test` |
| `docker-build` | Construye la imagen, la ejecuta unos segundos e imprime sus logs (la construcción ya corre las pruebas con `mvn clean package`). Depende de los dos anteriores |
| `sonarqube` | `mvn verify sonar:sonar` contra SonarCloud (proyecto `AndresContreras1_Backend-DW-BPMN`) con `sonar.qualitygate.wait=true`: el job falla si el quality gate no se cumple. Usa los secretos `SONAR_TOKEN` y `SONAR_HOST_URL`; en pull requests desde forks se omite porque no reciben secretos |

## Estructura de paquetes

```
src/main/java/com/facimus/procesos/
  common/       Núcleo compartido: EntidadEmpresa, RepositorioTenant, excepciones de dominio,
                PageResponse, ApiExceptionHandler y eventos de dominio
  config/       ModelMapperConfig, OpenApiConfig y DatosDemoInitializer
  security/     JWT, filtro de autenticación, SecurityConfig, CORS, ApiPrincipal
  gestion/      Empresas, usuarios, invitaciones, procesos, roles de proceso, permisos de
                estructura, historial y procesos compartidos
  modelado/     Pools, lanes, nodos (actividades, gateways, eventos de mensaje), arcos,
                mensajes, correlaciones y validación del modelo
src/main/resources/db/migration/   Migraciones Flyway (common/ y postgresql/)
```

Cada módulo (`gestion`, `modelado`) tiene sus subpaquetes `controller` (con `dto`), `service` (interfaz + `Impl`), `repository` y `model`.

## Documentación adicional

- [Guía técnica](docs/guia-tecnica.md): arquitectura, seguridad, errores, pruebas, CI/CD, contribución y limitaciones.
- [Cobertura de historias de usuario](docs/cobertura-historias-usuario.md)
- [Guía de sustentación](docs/guia-sustentacion.md)
