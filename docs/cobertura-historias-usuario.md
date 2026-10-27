# Cobertura de historias de usuario — Facimus Procesos

Auditoría del backend (rama `main`, commit `a4a1bb0`) contra las 28 historias de usuario definidas en
[desarrolloweb.click/proyecto/hitoriasusuario](https://desarrolloweb.click/proyecto/hitoriasusuario/).

**Metodología:** cada historia se verificó leyendo directamente el código fuente actual (no memoria de
análisis anteriores) — controllers, servicios, modelos, repositorios y `SecurityConfig`. Cada hallazgo
cita archivo, clase y método concretos. Esta versión reemplaza una auditoría anterior más superficial:
varios huecos que antes parecían menores resultaron, tras leer el código línea por línea, ser más
importantes de lo reportado (por ejemplo: ningún módulo de `modelado` registra historial de cambios, y
los nodos/arcos se borran físicamente en vez de lógicamente).

**Leyenda:** ✅ Completo · 🟡 Parcial · ❌ No implementado

## Resumen general

| Estado | Cantidad |
|---|---|
| ✅ Completo | 4 |
| 🟡 Parcial | 21 |
| ❌ No implementado | 3 |

**Hallazgos transversales** (afectan a varias historias a la vez, se explican una sola vez aquí):

1. **El historial de cambios (`HistorialCambio`) solo existe para `Proceso`.** `HistorialCambioService.registrar()`
   (`src/main/java/com/facimus/procesos/gestion/service/HistorialCambioService.java`) solo lo invoca
   `ProcesoService`. Ningún servicio de `modelado` (actividades, arcos, gateways, pools, lanes, mensajes,
   correlación) ni `RolProcesoService` lo llama — el modelo `HistorialCambio` ni siquiera podría, porque
   su relación `proceso` es obligatoria (`@ManyToOne(optional = false)`). Esto hace que el criterio
   "queda registrado en el historial" falle sistemáticamente en 17 de las 28 historias.
2. **Borrado físico en el módulo `modelado`.** `Actividad`, `Gateway` y `Arco` (vía `NodoFlujo` y `Arco`,
   que solo extienden `EntidadEmpresa`) no tienen campo `activo` como sí lo tiene `Proceso`. Sus métodos
   `eliminar()` en `ActividadService`, `GatewayService` y `ArcoService` hacen `repository.delete(...)` —
   borrado físico de la fila, no baja lógica.
3. **Los permisos son reglas globales fijas en `SecurityConfig.java`**, no configurables por empresa ni
   por pool. Cumplen el espíritu de "quién puede hacer qué" pero no la idea de una matriz configurable.

---

## Gestión de empresas y usuarios

### HU-01 · Registro de empresa — ✅ Completo

**Qué es:** el alta de una empresa nueva en el sistema (multitenancy) junto con su primer usuario
administrador, que es quien podrá luego invitar/crear al resto de colaboradores.

**Dónde está:**
- Controller: [`EmpresaController.registrar()`](src/main/java/com/facimus/procesos/gestion/controller/EmpresaController.java) — `POST /api/v1/empresas` (público, `@SecurityRequirements()`)
- Service: [`EmpresaService.registrar()`](src/main/java/com/facimus/procesos/gestion/service/EmpresaService.java)
- DTO: [`RegistroEmpresaRequest`](src/main/java/com/facimus/procesos/gestion/controller/dto/RegistroEmpresaRequest.java)
- Modelos: [`Empresa.java`](src/main/java/com/facimus/procesos/gestion/model/Empresa.java) (raíz de la multitenancy, no extiende `EntidadEmpresa`), [`Usuario.java`](src/main/java/com/facimus/procesos/gestion/model/Usuario.java)

**Verificación:** NIT único (`existsByNit`, `@Column(unique=true)`), admin inicial creado en la misma
transacción, validación de campos con Bean Validation. Sin huecos.

---

### HU-02 · Registro de usuario en empresa — 🟡 Parcial

**Qué es:** que un administrador pueda dar de alta, editar el rol y desactivar colaboradores dentro de
su propia empresa, sin que eso afecte a los procesos que esos colaboradores ya crearon.

**Dónde está:**
- Controller: [`UsuarioController`](src/main/java/com/facimus/procesos/gestion/controller/UsuarioController.java) — `POST/GET/PATCH/DELETE /api/v1/usuarios` (todo restringido a `ADMINISTRADOR` en `SecurityConfig`)
- Service: [`UsuarioService`](src/main/java/com/facimus/procesos/gestion/service/UsuarioService.java) — `crearColaborador()`, `actualizar()`, `desactivar()`, `listarPorEmpresa()`
- DTOs: `CrearUsuarioRequest`, `ActualizarUsuarioRequest`

**Falta:** el criterio "invitar usuarios por correo electrónico" **no es un flujo de invitación real** —
el administrador define la contraseña directamente en `CrearUsuarioRequest.password`, sin token de
invitación ni correo enviado (no hay `MailSender`/`JavaMail` en todo el proyecto). Es alta directa, no
invitación. El resto de criterios (rol, empresa única, desactivar sin borrar, procesos de la empresa no
del usuario) sí se cumplen.

---

### HU-03 · Inicio de sesión — ✅ Completo

**Qué es:** el login con JWT: el usuario entrega correo/contraseña y recibe un token que lo identifica
(incluyendo su empresa) en cada petición futura, sin usar sesión de servidor.

**Dónde está:**
- Controller: [`AuthController`](src/main/java/com/facimus/procesos/gestion/controller/AuthController.java) — `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`
- Service: `UsuarioService.autenticar()`
- Seguridad: [`JwtService.java`](src/main/java/com/facimus/procesos/security/JwtService.java), [`JwtAuthenticationFilter.java`](src/main/java/com/facimus/procesos/security/JwtAuthenticationFilter.java), `SecurityConfig.passwordEncoder()` (BCrypt)

**Verificación:** contraseña con BCrypt, mensaje de error idéntico ("Correo o contraseña incorrectos")
exista o no el correo (mitiga enumeración), el JWT lleva `empresaId` y se revalida el usuario contra BD
en cada request. Sin huecos.

---

## Gestión de procesos

### HU-04 · Crear proceso — ✅ Completo

**Qué es:** el alta de un proceso de negocio nuevo dentro de la empresa, que nace vacío (en borrador)
pero con su "pool" inicial ya creado para poder empezar a modelarlo.

**Dónde está:**
- Controller: `ProcesoController.crear()` — `POST /api/v1/procesos`
- Service: [`ProcesoService.crear()`](src/main/java/com/facimus/procesos/gestion/service/ProcesoService.java)
- Modelos: [`Proceso.java`](src/main/java/com/facimus/procesos/gestion/model/Proceso.java), `EstadoProceso.java`

**Verificación:** nombre único por empresa (case-insensitive, entre procesos activos), estado inicial
`BORRADOR`, y creación automática de un `Pool` tipo `EMPRESA` asociado. Sin huecos.

---

### HU-05 · Editar proceso — 🟡 Parcial

**Qué es:** modificar los datos de un proceso existente (nombre, descripción, categoría) y su estado
(borrador/publicado), dejando rastro de quién y cuándo hizo el cambio.

**Dónde está:**
- Controller: `ProcesoController.editar()` (PUT, datos) y `ProcesoController.cambiarEstado()` (PATCH, estado)
- Service: `ProcesoService.editarDatos()` y `ProcesoService.cambiarEstado()`
- DTOs: `EditarProcesoRequest` (sin campo `estado`), `CambiarEstadoProcesoRequest` (solo `estado`)

**Falta:** el criterio oficial dice "se editan nombre, descripción, categoría **y estado**" como una
sola operación. En el código esto está **separado en dos endpoints** (`PUT` para datos, `PATCH` para
estado) — funcionalmente ambos cambios son posibles, pero no en una sola llamada, que es lo que pide el
criterio literalmente. Además, `ProcesoService.publicar()` existe pero no lo expone ningún controller
(código muerto). El resto (permisos, historial, validaciones) sí se cumple.

---

### HU-06 · Eliminar proceso — 🟡 Parcial

**Qué es:** dar de baja un proceso sin borrarlo físicamente de la base de datos, para poder auditar qué
existió aunque ya no esté en uso.

**Dónde está:**
- Controller: `ProcesoController.eliminar()` — `DELETE /api/v1/procesos/{id}`
- Service: `ProcesoService.eliminarLogico()`
- Filtros: [`ProcesoSpecifications.java`](src/main/java/com/facimus/procesos/gestion/repository/ProcesoSpecifications.java)

**Falta:** el criterio "un proceso inactivo deja de aparecer en el listado por defecto, **pero puede
consultarse con un filtro**" no se cumple. `ProcesoSpecifications.conFiltros()` fuerza
`cb.isTrue(root.get("activo"))` sin ningún parámetro para desactivarlo, y ni siquiera se puede acceder
por ID directo: `ProcesoService.obtener()` usa `findByIdAndEmpresaIdAndActivoTrue(...)`, así que un
proceso eliminado da 404 sin importar cómo se lo pida. Es baja lógica en base de datos, pero
**invisible por completo** vía API. El resto de criterios (confirmación es de UI, solo admin, historial)
se cumple.

---

### HU-07 · Consultar procesos — 🟡 Parcial

**Qué es:** listar los procesos de la empresa con búsqueda/filtros/paginación, y poder abrir uno para
ver su diagrama completo e historial.

**Dónde está:**
- Controller: `ProcesoController.listar()`, `.detalle()`, `.historial()`
- Service: `ProcesoService.buscar()`, `.obtener()`; `HistorialCambioService.listarPorProceso()`
- Elementos del diagrama (endpoints separados): `PoolController`, `LaneController`, `ActividadController`, `ArcoController`, `GatewayController`

**Falta:** "al abrir un proceso se visualiza su diagrama completo" no está resuelto en un solo endpoint.
`ProcesoController.detalle()` solo devuelve el proceso + su historial — **no incluye pools, lanes,
actividades, arcos ni gateways**. Cada uno vive en su propio endpoint anidado por su padre inmediato
(`GET /procesos/{id}/pools`, `GET /lanes/{id}/actividades`, etc.), así que el cliente necesita una
cascada de llamadas para reconstruir el diagrama; no hay un endpoint agregador tipo
`/procesos/{id}/diagrama`. Búsqueda, filtros, paginación y aislamiento por empresa sí están completos.

---

## Modelado del proceso: actividades, arcos y gateways

### HU-08 · Crear actividad — 🟡 Parcial

**Qué es:** agregar una tarea concreta (actividad) dentro de una lane de un proceso, con su posición en
el lienzo del diagrama.

**Dónde está:**
- Controller: [`ActividadController.crear()`](src/main/java/com/facimus/procesos/modelado/controller/ActividadController.java) — `POST /api/v1/lanes/{laneId}/actividades`
- Service: [`ActividadService.crear()`](src/main/java/com/facimus/procesos/modelado/service/ActividadService.java)
- Modelos: [`Actividad.java`](src/main/java/com/facimus/procesos/modelado/model/Actividad.java), [`NodoFlujo.java`](src/main/java/com/facimus/procesos/modelado/model/NodoFlujo.java) (superclase con herencia `SINGLE_TABLE`)

**Falta:** no existe el concepto de "tipo de actividad" en el dominio (`ActividadRequest` solo tiene
`nombre`, `descripcion`, `posicionX`, `posicionY` — ni un enum `TipoActividad` en todo el proyecto). Y,
por el hallazgo transversal 1, la creación no queda en el historial. Nombre único por proceso y posición
sí están implementados.

---

### HU-09 · Editar actividad — 🟡 Parcial

**Qué es:** modificar una actividad existente, incluyendo poder reasignarla a otra lane (y por tanto a
otro rol responsable).

**Dónde está:**
- Controller: `ActividadController.editar()` — `PUT /api/v1/actividades/{id}`
- Service: `ActividadService.editar()`

**Falta:** el método `editar()` **no recibe `laneId`** — no se puede reasignar la lane de una actividad
ya creada, solo nombre/descripción/posición. Sin historial (hallazgo transversal 1). Los arcos
conectados sí se conservan (no se tocan) y los permisos de edición sí están restringidos correctamente.

---

### HU-10 · Eliminar actividad — 🟡 Parcial

**Qué es:** borrar una actividad que ya no aplica, limpiando también las conexiones (arcos) que tenía.

**Dónde está:**
- Controller: `ActividadController.eliminar()` — `DELETE /api/v1/actividades/{id}`
- Service: `ActividadService.eliminar()`

**Falta:** elimina correctamente los arcos entrantes/salientes, pero es **borrado físico**
(`nodoFlujoRepository.delete(...)`), sin campo `activo`, sin historial, y sin ninguna advertencia sobre
elementos que queden desconectados. Solo administrador puede eliminar (correcto).

---

### HU-11 · Crear arco — 🟡 Parcial

**Qué es:** conectar dos nodos del diagrama (actividad o gateway) para representar el orden en que
ocurre el proceso.

**Dónde está:**
- Controller: [`ArcoController.crear()`](src/main/java/com/facimus/procesos/modelado/controller/ArcoController.java) — `POST /api/v1/arcos`
- Service: [`ArcoService.crear()`](src/main/java/com/facimus/procesos/modelado/service/ArcoService.java)
- Modelo: [`Arco.java`](src/main/java/com/facimus/procesos/modelado/model/Arco.java)

**Falta:** el criterio menciona "actividad, gateway **o evento**" como posibles extremos, pero el
dominio solo tiene dos subtipos de `NodoFlujo`: `Actividad` y `Gateway` — no existe el concepto de
"Evento" como nodo conectable por arcos. El resto (origen≠destino, mismo pool obligatorio, sin arcos
duplicados) está bien implementado.

---

### HU-12 · Editar arco — 🟡 Parcial *(hueco que ya existía y el fix de auditoría no corrigió)*

**Qué es:** modificar un arco ya creado, incluyendo poder cambiar a qué nodos conecta.

**Dónde está:**
- Controller: `ArcoController.editar()` — `PUT /api/v1/arcos/{id}`
- Service: `ArcoService.editar()`
- DTO: [`EditarArcoRequest`](src/main/java/com/facimus/procesos/modelado/controller/dto/EditarArcoRequest.java)

**Falta:** `EditarArcoRequest` solo tiene `etiqueta` y `condicion` — **no se puede cambiar origen ni
destino**, que es justo el criterio 1 de esta historia. Como consecuencia, tampoco se re-ejecutan las
validaciones de creación (mismo pool, duplicados, condición requerida) porque no hay nada nuevo que
validar. Este hueco ya se había detectado antes de la auditoría "H-01 a H-12" y sigue sin corregirse.

---

### HU-13 · Eliminar arco — 🟡 Parcial

**Qué es:** borrar un arco que ya no representa el flujo correcto.

**Dónde está:**
- Controller: `ArcoController.eliminar()` — `DELETE /api/v1/arcos/{id}`
- Service: `ArcoService.eliminar()`

**Falta:** borrado físico, sin historial, sin advertencia de nodos que queden sin entrada/salida. Solo
administrador puede eliminar (correcto).

---

### HU-14 · Crear gateway — 🟡 Parcial

**Qué es:** agregar un punto de decisión/ramificación (exclusivo, paralelo o inclusivo) al diagrama.

**Dónde está:**
- Controller: [`GatewayController.crear()`](src/main/java/com/facimus/procesos/modelado/controller/GatewayController.java) — `POST /api/v1/lanes/{laneId}/gateways`
- Service: [`GatewayService.crear()`](src/main/java/com/facimus/procesos/modelado/service/GatewayService.java)
- Modelo: [`Gateway.java`](src/main/java/com/facimus/procesos/modelado/model/Gateway.java), `TipoGateway.java` (`EXCLUSIVO`, `PARALELO`, `INCLUSIVO`)

**Falta:**
- No hay ninguna validación de "mínimo 2 arcos salientes" para un gateway de divergencia.
- La única validación de condición existente (`ArcoService.crear()`) exige condición cuando el gateway
  es el **destino** del arco (arco entrante), pero el criterio pide condición en los arcos **salientes**
  de un gateway exclusivo/inclusivo (gateway como origen). Es decir, la validación existe pero está
  aplicada en la dirección contraria a la que pide la historia.

---

### HU-15 · Editar gateway — 🟡 Parcial *(el más incompleto de este grupo)*

**Qué es:** cambiar el tipo de un gateway existente y ajustar las condiciones de sus arcos salientes en
consecuencia.

**Dónde está:**
- Controller: `GatewayController.editar()` — `PUT /api/v1/gateways/{id}`
- Service: `GatewayService.editar()`

**Falta:** `editar()` solo actualiza `nombre`, `tipoGateway`, `posicionX`, `posicionY` — **no interactúa
con `ArcoRepository` en absoluto**. Como consecuencia: no permite actualizar condiciones de arcos desde
este endpoint, no advierte si las condiciones no son mutuamente excluyentes al pasar a EXCLUSIVO, y
**no limpia las condiciones existentes al pasar a PARALELO** (quedan huérfanas en la base de datos).
Solo el criterio 1 (cambiar el tipo) se cumple.

---

### HU-16 · Eliminar gateway — 🟡 Parcial

**Qué es:** borrar un gateway que ya no se necesita.

**Dónde está:**
- Controller: `GatewayController.eliminar()` — `DELETE /api/v1/gateways/{id}`
- Service: `GatewayService.eliminar()`

**Falta:** mismo patrón que HU-10/HU-13: borrado físico, sin historial, sin advertencia de ramificación
rota. Elimina correctamente los arcos conectados. Solo administrador puede eliminar (correcto).

---

## Roles de proceso

### HU-17 · Crear rol de proceso — ✅ Completo

**Qué es:** definir una "función" (Analista, Supervisor, Auditor...) dentro de la empresa, que luego se
usará para nombrar lanes en cualquier proceso — no es un permiso de acceso, es un rol de negocio.

**Dónde está:**
- Controller: [`RolProcesoController.crear()`](src/main/java/com/facimus/procesos/gestion/controller/RolProcesoController.java) — `POST /api/v1/roles`
- Service: [`RolProcesoService.crear()`](src/main/java/com/facimus/procesos/gestion/service/RolProcesoService.java)
- Modelo: [`RolProceso.java`](src/main/java/com/facimus/procesos/gestion/model/RolProceso.java)

**Verificación:** nombre único por empresa, asociado a la empresa (no a un proceso específico), solo
administrador puede crear. Sin huecos.

---

### HU-18 · Editar rol de proceso — 🟡 Parcial

**Qué es:** cambiar el nombre/descripción de un rol de proceso, y que esos cambios se reflejen
automáticamente en todas las lanes que ya lo usan.

**Dónde está:**
- Controller: `RolProcesoController.editar()` — `PUT /api/v1/roles/{id}`
- Service: `RolProcesoService.editar()`

**Falta:** el criterio "el cambio queda registrado en el historial" no se cumple — mismo hallazgo
transversal 1, `HistorialCambio` solo admite `Proceso`. El resto funciona correctamente y, al ser una
relación por FK (no una copia del nombre), la propagación del nombre actualizado a las lanes es
automática sin necesidad de código extra.

---

### HU-19 · Eliminar rol de proceso — 🟡 Parcial

**Qué es:** dar de baja un rol de proceso, bloqueando la eliminación si todavía está en uso en algún
diagrama.

**Dónde está:**
- Controller: `RolProcesoController.eliminar()` — `DELETE /api/v1/roles/{id}`
- Service: `RolProcesoService.eliminar()`

**Verificación:** valida uso vía `LaneRepository`, si está en uso lanza excepción listando los nombres
de los procesos afectados, baja lógica (`activo=false`), solo administrador. **Falta:** igual que
HU-18, no hay registro en `HistorialCambio`.

---

### HU-20 · Consultar roles de proceso — 🟡 Parcial

**Qué es:** ver el listado de roles de proceso disponibles en la empresa, sabiendo cuáles están en uso
y por tanto no se pueden borrar.

**Dónde está:**
- Controller: `RolProcesoController.listar()` — `GET /api/v1/roles`; `.obtener()` — `GET /api/v1/roles/{id}`
- Service: `RolProcesoService.listarConUso()`, `.contarUsos()`
- DTO: `RolProcesoVistaResponse`

**Falta:** `listar()` no recibe ningún parámetro de búsqueda por nombre ni de paginación — devuelve
`List<...>` completa, a diferencia de `ProcesoController` que sí implementa ambos. Además, "para cada
rol se puede ver en qué procesos está siendo usado" solo se cumple parcialmente: el listado muestra un
**conteo** (`procesosQueLoUsan`), pero los **nombres** de esos procesos solo aparecen si falla un intento
de eliminación (mensaje de la excepción), no en la consulta normal.

---

## Pools y lanes

### HU-21 · Configurar pool por empresa — 🟡 Parcial

**Qué es:** el "pool" es el contenedor de más alto nivel del diagrama: representa a un participante
(la empresa dueña, un cliente, un proveedor o un sistema externo). Un proceso puede tener varios pools.

**Dónde está:**
- Modelo: [`Pool.java`](src/main/java/com/facimus/procesos/modelado/model/Pool.java)
- Controller: [`PoolController`](src/main/java/com/facimus/procesos/modelado/controller/PoolController.java) — `POST/GET/PUT/DELETE /api/v1/procesos/{procesoId}/pools`, `/api/v1/pools/{id}`
- Service: [`PoolService.java`](src/main/java/com/facimus/procesos/modelado/service/PoolService.java)
- Auto-creación: `ProcesoService.crear()` (líneas 59-66)

**Falta:**
- El campo `cajaNegra` existe y se guarda, pero **no se aplica en ningún lado**: se puede seguir
  agregando lanes y actividades a un pool marcado como caja negra sin ningún bloqueo.
- Cambios sobre pools no quedan en el historial (hallazgo transversal 1; `PoolService` no referencia
  `HistorialCambioService`).

Al crear un proceso ya se autogenera el pool empresarial, y se pueden crear pools adicionales
(cliente/proveedor/sistema externo) explícitamente. Permisos (editor/admin crean, solo admin borra) sí
están correctos.

---

### HU-22 · Diferenciar pool y lane (swimlane) — 🟡 Parcial

**Qué es:** las "lanes" son las divisiones internas de un pool — cada una representa el rol responsable
(vía `RolProceso`) de las actividades que contiene.

**Dónde está:**
- Modelo: [`Lane.java`](src/main/java/com/facimus/procesos/modelado/model/Lane.java)
- Controller: [`LaneController`](src/main/java/com/facimus/procesos/modelado/controller/LaneController.java) — `GET/POST/PUT/DELETE` bajo `/api/v1/pools/{poolId}/lanes`, `/api/v1/lanes/{id}`
- Service: [`LaneService.java`](src/main/java/com/facimus/procesos/modelado/service/LaneService.java)

**Falta:** no existe forma de **reordenar** lanes. El campo `orden` se asigna una única vez al crear
(`orden = tamaño de la lista actual`); `LaneService.editar()` solo toca `nombre` y `rolProceso`, nunca
`orden`. No hay endpoint, DTO ni lógica de reordenamiento en ningún punto del código. El resto
(asociación obligatoria a `RolProceso`, una actividad pertenece a exactamente una lane, no se puede
borrar una lane con actividades) está correctamente implementado.

---

### HU-23 · Compartir procesos entre pools (alcance y límites) — ❌ No implementado

**Qué es:** permitir que un proceso se marque como "compartido" para que otra empresa pueda consultarlo
(solo lectura), sin romper el aislamiento de datos entre empresas.

**Dónde debería estar y no está:** no existe ningún campo, entidad, servicio ni endpoint relacionado.
`Proceso.java` solo tiene `id, nombre, descripcion, categoria, estado, activo, fechaCreacion,
fechaModificacion` — nada de compartición o visibilidad entre empresas. `ProcesoService.obtener()` usa
`findByIdAndEmpresaIdAndActivoTrue`, es decir, el acceso está estrictamente limitado a la empresa
propietaria, sin excepción.

**Nota de diseño importante:** esta historia es estructuralmente opuesta al resto del sistema. Todo el
diseño de multitenancy (`EntidadEmpresa`, `RepositorioTenant`, y los tests de arquitectura/integración
que verifican aislamiento estricto entre empresas) está construido para que una empresa **nunca** pueda
ver datos de otra. Implementar HU-23 requiere una decisión consciente de cómo convive una excepción
controlada (solo lectura, solo procesos marcados explícitamente) con ese aislamiento, no es solo
"agregar un campo".

El único criterio con algo de código relacionado es el 5 ("los mensajes solo pueden dirigirse a pools
del mismo diagrama"): `MensajeService.crear()` busca los pools por `empresaId` pero **no valida que
ambos pools pertenezcan al mismo `proceso`**, así que en teoría se podría crear un mensaje cruzando
diagramas distintos de la misma empresa — un hueco menor relacionado, no una implementación real del
criterio de compartición entre empresas.

---

### HU-24 · Asociar roles y permisos a un pool — 🟡 Parcial

**Qué es:** controlar qué roles de proceso son asignables en un pool y qué roles de acceso (RBAC) pueden
modificar su estructura.

**Dónde está:**
- Seguridad: [`SecurityConfig.java`](src/main/java/com/facimus/procesos/security/SecurityConfig.java)
- Service: `LaneService.crear()`/`.editar()` (resuelven `RolProceso` con `findByIdAndEmpresaId`, scoped a la empresa)
- Service: `RolProcesoService.eliminar()` (bloquea borrar un rol en uso)

**Falta:** los permisos de quién puede crear/editar/eliminar pools y lanes son **reglas globales fijas
en código** (`SecurityConfig`: POST/PUT requieren ADMINISTRADOR o EDITOR, DELETE solo ADMINISTRADOR),
no una configuración por pool o por empresa como describe el criterio 2. No hay ninguna entidad de
"configuración de permisos por pool" en el proyecto. Sí se cumple: los roles de proceso asignables están
scoped a la empresa, `SOLO_LECTURA` puede ver pero no modificar, y no se puede eliminar un rol en uso sin
reasignarlo antes.

---

## Mensajes y colaboración entre pools

> **Hallazgo estructural que afecta a HU-25, HU-26 y HU-27 por igual:** `Mensaje` es una **entidad
> única y genérica** (`id, nombre, contenido, poolOrigen, poolDestino, proceso`) — no distingue entre
> "elemento que envía" (Throw) y "elemento que recibe" (Catch), no tiene variantes de inicio/intermedio,
> ni tipo de destino externo. Además, `Mensaje extends EntidadEmpresa`, **no** `extends NodoFlujo`, así
> que los mensajes están completamente fuera del grafo de `Arco`/`NodoFlujo` — no pueden tener "arcos
> entrantes" ni conectarse a actividades/gateways, porque el concepto no existe en ese modelo.

### HU-25 · Enviar mensaje entre procesos (Message Throw) — 🟡 Parcial

**Qué es:** modelar el envío de un mensaje desde un pool hacia otro pool del mismo proceso (BPMN:
"Message Throw").

**Dónde está:**
- Modelo: [`Mensaje.java`](src/main/java/com/facimus/procesos/modelado/model/Mensaje.java)
- Controller: [`MensajeController`](src/main/java/com/facimus/procesos/modelado/controller/MensajeController.java) — `POST /api/v1/procesos/{procesoId}/mensajes`, `PUT/DELETE/GET /api/v1/mensajes/{id}`
- Service: [`MensajeService.java`](src/main/java/com/facimus/procesos/modelado/service/MensajeService.java)

**Falta:** el `contenido` es un único texto libre (`@Lob String`), no una lista estructurada de campos
con tipo de dato. No hay validación de que exista un "Catch" correspondiente con el mismo nombre en el
pool destino (no puede haberla, dado el hallazgo estructural de arriba). Sin historial. **Sí** se valida
correctamente que origen y destino sean pools distintos (rechaza mensajes dentro del mismo pool).

---

### HU-26 · Envío de notificaciones externas — ❌ No implementado

**Qué es:** modelar el envío de una notificación desde el proceso hacia un sistema externo (correo,
servicio web, cola), documentando qué pasa si esa notificación falla.

**Dónde debería estar y no está:** `Pool.tipoParticipante` tiene el valor `SISTEMA_EXTERNO` y
`Pool.cajaNegra` existe como dato, pero no hay ningún campo de "tipo de destino" (correo/servicio
web/cola) ni de "comportamiento ante fallo" en ningún modelo del proyecto. Tampoco se impide agregar
actividades internas a un pool marcado como `SISTEMA_EXTERNO`/caja negra (mismo hueco que HU-21). Esta
historia reutilizaría `Mensaje`, pero ese modelo no tiene ninguno de los campos que pide.

---

### HU-27 · Recibir mensaje y activar proceso (Message Catch) — ❌ No implementado

**Qué es:** modelar la recepción de un mensaje, distinguiendo si es lo que arranca el proceso (inicio) o
si el proceso ya estaba corriendo y solo se pone en espera de él (intermedio).

**Dónde debería estar y no está:** mismo modelo genérico `Mensaje` que HU-25 — no hay campo para
distinguir inicio/intermedio, no hay relación `Mensaje → Actividad` para documentar qué actividades usan
los datos recibidos, no hay validación de correspondencia con un "Throw" del mismo nombre. El criterio
"un Message Catch de inicio no puede tener arcos entrantes" es **inaplicable** en el modelo actual: como
`Mensaje` no extiende `NodoFlujo`, no puede tener arcos ni entrantes ni salientes.

---

### HU-28 · Correlación de mensajes con instancias de proceso — 🟡 Parcial

**Qué es:** definir qué dato (número de radicado, NIT del cliente, etc.) permite saber a qué caso
concreto en ejecución pertenece un mensaje que llega.

**Dónde está:**
- Modelo: [`Correlacion.java`](src/main/java/com/facimus/procesos/modelado/model/Correlacion.java) — campo `criterio`, relación `@OneToOne` única y obligatoria con `Mensaje`
- Controller: [`CorrelacionController`](src/main/java/com/facimus/procesos/modelado/controller/CorrelacionController.java) — `PUT/GET /api/v1/mensajes/{mensajeId}/correlacion`
- Service: [`CorrelacionService.java`](src/main/java/com/facimus/procesos/modelado/service/CorrelacionService.java) — `definir()` (upsert), `obtener()`

**Falta:** no hay detección de ambigüedad (dos mensajes del mismo proceso con igual nombre y clave), no
hay advertencia para un "catch intermedio" sin clave definida (no puede haberla, porque esa distinción
no existe — ver HU-27), y no hay ninguna lógica sobre qué hacer si un mensaje no corresponde a ningún
caso en espera (eso es comportamiento de motor de ejecución, y este backend es solo de modelado/diseño,
no ejecuta procesos). El CRUD básico de la clave de correlación sí funciona correctamente.

---

## Conclusión

De las 28 historias, **solo 4 están completas al 100%** frente a sus criterios de aceptación oficiales
(HU-01, HU-03, HU-04, HU-17) — todas del módulo `gestion`, que es el más maduro del proyecto. El resto
tiene al menos un criterio sin cumplir, casi siempre por una de estas tres causas recurrentes:

1. **Historial de cambios limitado a `Proceso`** — afecta a 17 historias (todo `modelado` y roles de proceso).
2. **Borrado físico en vez de lógico** en actividades, arcos y gateways.
3. **El modelo de `Mensaje` es demasiado genérico** para las historias de mensajería BPMN (HU-25 a HU-28), que piden distinguir Throw/Catch, variantes de inicio/intermedio, y validaciones cruzadas entre ambos.

Las únicas dos ausencias totales de funcionalidad (no solo de validaciones finas) son **HU-23**
(compartir procesos entre empresas — contradice el diseño actual de aislamiento estricto) y **HU-26**
(notificaciones externas tipadas). **HU-27** también quedó en 0% tras esta revisión más rigurosa: la
distinción inicio/intermedio y la regla de "sin arcos entrantes" no tienen ningún soporte en el modelo
actual.
