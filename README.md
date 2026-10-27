# Facimus Procesos — Backend

Backend REST para la gestion de procesos BPMN en entornos multiempresa.

## Stack tecnologico

- Java 21
- Spring Boot 4.1
- Spring Security + JWT (stateless)
- Spring Data JPA / Hibernate
- H2 (desarrollo) / PostgreSQL (produccion)
- JaCoCo (cobertura) + ArchUnit (arquitectura)
- Docker (multi-stage build)
- GitHub Actions (CI/CD)

## Prerrequisitos

- JDK 21+
- Maven 3.9+ (o usar el wrapper incluido `./mvnw`)

## Ejecucion local

```bash
./mvnw spring-boot:run
```

La aplicacion inicia en `http://localhost:8080`. Se crea automaticamente una empresa demo con el usuario `admin@demo.com` / `admin123`.

## Variables de entorno

| Variable                | Descripcion                           | Default                    |
|-------------------------|---------------------------------------|----------------------------|
| `JWT_SECRET`            | Clave para firmar tokens JWT          | (generada aleatoriamente)  |
| `JWT_EXPIRATION_SECONDS`| Duracion del token en segundos        | `1800`                     |
| `CORS_ALLOWED_ORIGINS`  | Origenes permitidos (separados por ,) | `http://localhost:4200`    |
| `DB_HOST`               | Host de PostgreSQL (perfil prod)      | `localhost`                |
| `DB_PORT`               | Puerto de PostgreSQL (perfil prod)    | `5432`                     |
| `DB_NAME`               | Nombre de la base (perfil prod)       | `procesos`                 |
| `DB_USER`               | Usuario de la base (perfil prod)      | —                          |
| `DB_PASSWORD`           | Contrasena de la base (perfil prod)   | —                          |

## Endpoints principales

| Recurso        | Base path                              |
|----------------|----------------------------------------|
| Auth           | `POST /api/v1/auth/login`, `/logout`   |
| Empresas       | `POST /api/v1/empresas`                |
| Procesos       | `/api/v1/procesos`                     |
| Usuarios       | `/api/v1/usuarios`                     |
| Roles          | `/api/v1/roles`                        |
| Pools          | `/api/v1/procesos/{id}/pools`          |
| Lanes          | `/api/v1/pools/{id}/lanes`             |
| Actividades    | `/api/v1/lanes/{id}/actividades`       |
| Gateways       | `/api/v1/lanes/{id}/gateways`          |
| Arcos          | `/api/v1/arcos`                        |
| Mensajes       | `/api/v1/procesos/{id}/mensajes`       |
| Correlaciones  | `/api/v1/mensajes/{id}/correlacion`    |

Documentacion interactiva disponible en `/swagger-ui/index.html`.

## Tests

```bash
./mvnw verify
```

Ejecuta tests unitarios, de integracion y reglas de arquitectura (ArchUnit). JaCoCo exige un minimo del 50% de cobertura de linea.

## Docker

```bash
docker build -t facimus-procesos .
docker run -p 8080:8080 -e JWT_SECRET=mi_clave_secreta facimus-procesos
```

## Estructura del proyecto

```
src/main/java/com/facimus/procesos/
  common/       Kernel compartido (EntidadEmpresa, excepciones, paginacion)
  config/       Inicializadores, OpenAPI
  security/     JWT, filtros, CORS
  gestion/      Modulo de gestion (empresas, usuarios, procesos, roles, historial)
  modelado/     Modulo de modelado BPMN (pools, lanes, nodos, arcos, mensajes)
```

## Documentacion adicional

- [Guia tecnica](docs/guia-tecnica.md)
