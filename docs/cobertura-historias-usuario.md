# Cobertura de las historias de usuario

Matriz de cobertura de las 28 historias de usuario (HU-01 a HU-28) frente al backend actual (Spring Boot 4.1, Java 21, paquete `com.facimus.procesos`). Cada fila se verificó leyendo el código real (controllers, servicios, modelos, repositorios, DTOs, `SecurityConfig`, `ValidacionModeloServiceImpl`, migraciones y pruebas), no a partir de documentación previa.

**Alcance del sistema.** El sistema **modela y valida** procesos BPMN; no los ejecuta: no hay instancias en curso, no se envían correos ni mensajes reales y no se guardan credenciales de integración. Todos los elementos se modelan y se validan, ninguno se ejecuta.

## Leyenda

| Marca | Significado |
|---|---|
| ✅ | Cumple: el backend implementa el criterio completo. |
| 🟡 | Parcial: el backend cubre una parte; la fila explica qué falta. |
| ❌ | Falta: el criterio no está implementado. |
| UI | Lo resuelve el frontend (dibujo, confirmación, ocultar opciones). La fila indica qué expone el backend para ello. |

## Resumen por estado

Se evaluaron **146 criterios de aceptación** de las 28 historias.

| Estado | Criterios | Historias afectadas |
|---|---|---|
| ✅ Cumple | 121 | El backend implementa el criterio completo. |
| 🟡 Parcial | 8 | HU-02, HU-03, HU-18, HU-19, HU-24, HU-25, HU-26, HU-27 (una fila cada una). |
| ❌ Falta | 1 | HU-15 (advertencia de condiciones no mutuamente excluyentes). |
| UI | 16 | Dibujo, confirmación previa y ocultar opciones: lo resuelve el frontend con lo que expone el backend. |

Por historia: **19 historias** tienen todos sus criterios en ✅ o UI; **8** tienen al menos un criterio 🟡 y **1** (HU-15) tiene un criterio ❌.

Distribución por bloque:

| Bloque | Historias | ✅ | 🟡 | ❌ | UI |
|---|---|---|---|---|---|
| Gestión de empresas y usuarios | HU-01 a HU-03 | 13 | 2 | 0 | 0 |
| Gestión de procesos | HU-04 a HU-07 | 18 | 0 | 0 | 3 |
| Modelado: actividades, arcos y gateways | HU-08 a HU-16 | 36 | 0 | 1 | 8 |
| Roles de proceso | HU-17 a HU-20 | 17 | 2 | 0 | 1 |
| Pools y lanes | HU-21 a HU-24 | 19 | 1 | 0 | 3 |
| Mensajes y colaboración entre pools | HU-25 a HU-28 | 18 | 3 | 0 | 1 |
| **Total** | **28 historias** | **121** | **8** | **1** | **16** |

## Hallazgos transversales

1. **Aislamiento por empresa.** El `empresaId` nunca viaja en el request: sale del JWT (`ApiPrincipal`, construido en [JwtAuthenticationFilter][]) y todas las consultas puntuales pasan por `findByIdAndEmpresaId` (contrato [RepositorioTenant][]). Un recurso de otra empresa responde 404. Pruebas: [AislamientoEmpresasIntegracionTest][], [AislamientoTenantTest][], [MultitenenciaTest][], [RepositorioTenantTest][].
2. **Autorización por rol de acceso.** La matriz vive en [SecurityConfig][]: cualquier usuario autenticado consulta (`GET`); administrador y editor modifican; solo el administrador elimina procesos, actividades, arcos, gateways, eventos y mensajes, gestiona usuarios y roles de proceso, y comparte procesos. Para pools y lanes la decisión final la toma [PermisoEstructuraServiceImpl][] (HU-24). Prueba parametrizada: [AutorizacionPorRolTest][] › `Autorizacion_peticionSegunRol_devuelveEstadoDeLaMatriz`.
3. **Historial de cambios.** `HistorialCambio` es una bitácora de solo inserción ([HistorialCambioServiceImpl][]) con autor y fecha. La escriben [ProcesoServiceImpl][] (datos, estado, baja, compartición) y [AuditoriaModeladoServiceImpl][] (pools, lanes, actividades, gateways, eventos, arcos, mensajes y correlación). Consulta: `GET /api/v1/procesos/{id}/historial` y el detalle del proceso.
4. **Eliminación lógica.** Ninguna entidad de negocio se borra físicamente: `Proceso`, `RolProceso`, `NodoFlujo` (actividades, gateways, eventos), `Arco`, `Mensaje`, `ProcesoCompartido` y `Usuario` usan el campo `activo`; [Pool][] y [Lane][] y `Correlacion` lo hacen con `@SQLDelete` + `@SQLRestriction`, de modo que `repository.delete(...)` ejecuta un `UPDATE activo = false`. Un proceso dado de baja sigue siendo consultable (detalle, diagrama, historial) pero no admite modelado.
5. **Borrador libre, publicación estricta.** Un proceso en `BORRADOR` admite un diagrama incompleto. `PATCH /api/v1/procesos/{id}` a `PUBLICADO` publica `ProcesoPublicacionEvent`, que [ValidacionModeloServiceImpl][] escucha dentro de la misma transacción y aborta con 409 si hay advertencias. Las mismas advertencias se consultan sin publicar con `GET /api/v1/procesos/{id}/validacion`. Un proceso publicado no vuelve a borrador.
6. **Advertencias previas a eliminar.** Cada elemento del diagrama expone `GET .../impacto-eliminacion` (actividades, arcos, gateways, eventos de mensaje) con `requiereConfirmacion`, `rompeContinuidad` y la lista de advertencias; el frontend lo usa para pedir confirmación.
7. **Errores uniformes.** [ApiExceptionHandler][] responde `ProblemDetail`: 400 (validación o JSON inválido), 401, 403, 404, 409 (regla de negocio o integridad de datos). No se exponen trazas ni SQL ([RespuestasSegurasTest][]).
8. **Esquema.** En desarrollo (H2) Hibernate crea el esquema; en producción (PostgreSQL) lo crea Flyway: [V1__esquema_inicial][], [V2__indices_por_empresa][], [V3__unicidad_entre_activos][] (solo PostgreSQL) y [V5__eventos_mensaje_actividades][]. [MigracionesTest][] comprueba que el esquema coincide con las entidades.

## Gestión de empresas y usuarios

### HU-01 · Registro de empresa

**Qué modela.** La empresa es la raíz de todo: cada elemento del sistema cuelga de una empresa, y es la que aparece como pool propietario en los diagramas. Registrarla crea también su administrador inicial.

**Dónde está.** `POST /api/v1/empresas` (público) → [EmpresaController][] → [EmpresaOrquestadorServiceImpl][].registrarConAdministrador (transaccional) → [EmpresaServiceImpl][].registrar + [UsuarioServiceImpl][].crearColaborador.

| # | Criterio | Estado | Evidencia |
|---|---|---|---|
| 1 | Se solicitan nombre, NIT y correo de contacto | ✅ | [RegistroEmpresaRequest][] (`@NotBlank`, `@Email`), más nombre, correo y contraseña del administrador. Test: [EmpresaControllerTest][] › `registrar_exitoso`. |
| 2 | El NIT es único en el sistema | ✅ | [EmpresaServiceImpl][].registrar normaliza el NIT (sin espacios ni puntos, mayúsculas) y rechaza duplicados con 409; además `empresas.nit` es `unique` en [V1__esquema_inicial][]. Tests: [EmpresaServiceTest][] › `registrar_nit_duplicado_lanza_excepcion`, `EmpresaServiceImpl_registrar_nitDuplicadoConOtroFormato_lanzaExcepcion`. |
| 3 | Se genera un administrador inicial con las credenciales del representante | ✅ | [EmpresaOrquestadorServiceImpl][] crea el usuario con rol `ADMINISTRADOR`; si falla, se revierte la empresa. Tests: [EmpresaOrquestadorServiceTest][], [RegistroEmpresaTransaccionalTest][] › `registrarConAdministrador_revierte_la_empresa_si_falla_el_administrador`. |
| 4 | La empresa queda como entidad independiente con su propio espacio de procesos | ✅ | `Empresa` es la raíz de la tenencia; el resto de entidades extiende [EntidadEmpresa][]. Tests: [MultitenenciaTest][], [AislamientoEmpresasIntegracionTest][] › `Aislamiento_listadosGenerales_soloMuestranLaEmpresaDelToken`. |
| 5 | Los campos obligatorios se validan antes de crear y los errores se muestran al usuario | ✅ | Bean Validation en el DTO; [ApiExceptionHandler][] responde 400 con `campo: mensaje`. Mostrarlos es del frontend. Tests: [EmpresaControllerTest][] › `registrar_validacion_falla`, [ValidacionRequestsTest][]. |

Nota (dos empresas con procesos del mismo nombre): la unicidad del nombre se acota por empresa. Prueba: [ProcesoRepositoryTest][] › `ProcesoRepository_buscarConFiltros_noMuestraProcesosDeOtraEmpresa`.

### HU-02 · Registro de usuario en empresa

**Qué modela.** Colaboradores de la empresa con un **rol de acceso** (administrador, editor, solo lectura), que define qué puede hacer cada uno en la aplicación y no aparece en el diagrama. No se confunde con el rol de proceso (lane).

**Dónde está.** `GET/POST /api/v1/usuarios`, `GET/PATCH/DELETE /api/v1/usuarios/{id}` → [UsuarioController][] → [UsuarioServiceImpl][]. Invitaciones: `POST /api/v1/usuarios/invitaciones` y `POST /api/v1/usuarios/invitaciones/{token}/aceptar` (público) → [InvitacionUsuarioController][] → [InvitacionUsuarioServiceImpl][]. Todo `/usuarios/**` es solo administrador.

| # | Criterio | Estado | Evidencia |
|---|---|---|---|
| 1 | Se puede invitar a usuarios por correo electrónico | 🟡 | La invitación se crea y se acepta (token UUID, vigencia de 48 horas, un solo uso, rechaza correos ya registrados o con invitación vigente), pero **no se envía ningún correo**: el token se devuelve al administrador en [InvitacionUsuarioResponse][] para que se lo entregue a la persona invitada. Tests: [InvitacionUsuarioServiceTest][], [InvitacionUsuarioControllerTest][]. |
| 2 | Se puede asignar un rol de acceso | ✅ | `rolAcceso` obligatorio al crear el colaborador y al invitar ([CrearUsuarioRequest][], [InvitacionUsuarioRequest][]). Test: [UsuarioControllerTest][] › `crear_colaborador`. |
| 3 | Roles disponibles: administrador, editor y solo lectura | ✅ | Enum [RolAcceso][] (`ADMINISTRADOR`, `EDITOR`, `SOLO_LECTURA`). |
| 4 | Se puede cambiar el rol y desactivar a un usuario sin borrar su historial | ✅ | `PATCH /usuarios/{id}` (rol y/o `activo`) y `DELETE /usuarios/{id}` (baja lógica). El usuario no se borra, por lo que `HistorialCambio.autor` sigue intacto. Protección del último administrador activo: ver *Limitaciones* (sin bloqueo de fila). Tests: [UsuarioControllerTest][] › `cambiar_rol`, `desactivar_usuario`; [UsuarioServiceTest][] › `actualizar_rol_y_estado`, `UsuarioServiceImpl_desactivar_ultimoAdministrador_lanzaReglaNegocio`. |
| 5 | Los procesos pertenecen a la empresa, no al usuario; si el usuario se desactiva, sus procesos siguen disponibles | ✅ | [Proceso][] extiende [EntidadEmpresa][] y no guarda al creador (el autor solo consta en el historial). Desactivar un usuario no toca procesos; su token deja de valer en [JwtAuthenticationFilter][]. Test: [SeguridadIntegracionTest][] › `Seguridad_endpointProtegido_usuarioDesactivado_devuelve401`. |

### HU-03 · Inicio de sesión

**Qué modela.** El inicio de sesión determina la empresa (pool propietario) de todo lo que el usuario ve: el JWT lleva `empresaId` y rol, y toda consulta posterior se filtra por ellos.

**Dónde está.** `POST /api/v1/auth/login` y `POST /api/v1/auth/logout` → [AuthController][] → [UsuarioServiceImpl][].autenticar; token en [JwtService][]; filtro en [JwtAuthenticationFilter][]; contraseñas con `BCryptPasswordEncoder` ([SecurityConfig][]).

| # | Criterio | Estado | Evidencia |
|---|---|---|---|
| 1 | Se validan correo y contraseña | ✅ | [LoginRequest][] (`@NotBlank`, `@Email`) y comparación BCrypt. Tests: [AuthControllerTest][] › `AuthController_login_camposVacios_devuelve400`, [ValidacionRequestsTest][] › `LoginRequest_correoSinFormatoDeEmail_esInvalido`. |
| 2 | La contraseña se almacena cifrada | ✅ | Solo se guarda `usuarios.password_hash` (BCrypt). Tests: [UsuarioServiceTest][] › `crearColaborador_exitoso`, [DatosDemoInitializerTest][] › `DatosDemoInitializer_run_noGuardaContrasenasEnTextoPlano`. |
| 3 | Tras iniciar sesión solo se listan procesos, roles y usuarios de la empresa propia | ✅ | Tenant tomado del token. Test: [AislamientoEmpresasIntegracionTest][] › `Aislamiento_listadosGenerales_soloMuestranLaEmpresaDelToken`, `Aislamiento_empresaIdEnElRequest_seIgnora`. |
| 4 | Un intento fallido muestra un mensaje genérico sin revelar si el correo existe | ✅ | Siempre 401 con el mismo texto; si el correo no existe o está inactivo se ejecuta igualmente un `matches` contra un hash ficticio para igualar el tiempo de respuesta. Tests: [SeguridadIntegracionTest][] › `Seguridad_login_claveIncorrecta_devuelve401`, [UsuarioServiceTest][] › `UsuarioServiceImpl_autenticar_correoInexistente_ejecutaMatchesContraHashFicticio`. |
| 5 | Existe la opción de cerrar sesión | 🟡 | `POST /auth/logout` responde 204 pero es *stateless*: no invalida el token, el cliente debe descartarlo y el token sigue siendo válido hasta expirar (`jwt.expiration-seconds`, 1800 s por defecto). Tests: [AuthControllerTest][] › `AuthController_logout_autenticado_devuelve204`, `AuthController_logout_sinAutenticar_devuelve401`. |

Nota: la única excepción al aislamiento son los procesos compartidos de HU-23, siempre de solo lectura.

## Gestión de procesos

### HU-04 · Crear proceso

**Qué modela.** Un proceso es el contenedor (pool) donde se agregan eventos, actividades, gateways y arcos. Crearlo es crear el contenedor vacío, con el pool de la empresa ya definido.

**Dónde está.** `POST /api/v1/procesos` → [ProcesoController][] → [ProcesoOrquestadorServiceImpl][].crearConPoolInicial → [ProcesoServiceImpl][].crear + [PoolServiceImpl][].crear.

| # | Criterio | Estado | Evidencia |
|---|---|---|---|
| 1 | Se registran nombre, descripción y categoría | ✅ | [ProcesoRequest][] con los tres campos `@NotBlank`. Test: [ProcesoControllerTest][] › `crear_proceso`. |
| 2 | El proceso queda asociado a la empresa del usuario autenticado | ✅ | `principal.empresaId()` en el controller. Test: [SeguridadIntegracionTest][] › `Seguridad_crearProceso_registraHistorialDeProcesoYPoolEnOrden`. |
| 3 | Nace en borrador y puede pasar a publicado | ✅ | [ProcesoServiceImpl][].crear fija `BORRADOR`; `PATCH /procesos/{id}` publica tras validar el modelo (409 si hay advertencias). Tests: [ProcesoServiceTest][] › `crear_exitoso`, `publicar_exitoso`. |
| 4 | El nombre es único dentro de la empresa | ✅ | Entre procesos activos, sin distinguir mayúsculas y con el nombre recortado; en PostgreSQL además por índice parcial ([V3__unicidad_entre_activos][]). En H2 (desarrollo) solo lo garantiza el servicio. Tests: [ProcesoServiceTest][] › `crear_nombre_duplicado`, `ProcesoServiceImpl_crear_nombreDuplicadoSoloPorEspacios_lanzaReglaNegocio`. |
| 5 | Al crearlo queda listo para agregar elementos, con el pool de la empresa definido | ✅ | Se crea un pool de tipo `EMPRESA` con el nombre de la empresa, en la misma transacción. Tests: [ProcesoOrquestadorServiceTest][], [CreacionProcesoTransaccionalTest][] › `crearConPoolInicial_revierte_el_proceso_si_falla_el_pool`. |
| 6 | El borrador permite un diagrama incompleto sin exigir coherencia | ✅ | La validación solo se ejecuta al publicar ([ValidacionModeloServiceImpl][].alPublicarProceso) o al consultarla. Tests: [ProcesoServiceTest][] › `mantener_borrador_no_publica_evento`, [InvariantesDirectasEnServicioTest][] › `cambiarEstado_directo_rechaza_modelo_invalido`. |

### HU-05 · Editar el proceso

**Qué modela.** Los datos del proceso (nombre, descripción, categoría, estado), no el contenido del diagrama.

**Dónde está.** `PUT /api/v1/procesos/{id}` (datos) y `PATCH /api/v1/procesos/{id}` (estado) → [ProcesoController][] → [ProcesoServiceImpl][].editarDatos / cambiarEstado.

| # | Criterio | Estado | Evidencia |
|---|---|---|---|
| 1 | Solo administrador o editor pueden modificar | ✅ | [SecurityConfig][]: toda escritura exige `ADMINISTRADOR` o `EDITOR`. Tests: [SeguridadIntegracionTest][] › `Seguridad_crearProceso_soloLectura_devuelve403`, [AutorizacionPorRolTest][]. |
| 2 | Se pueden editar nombre, descripción, categoría y estado | ✅ | `PUT` para los tres datos y `PATCH` para el estado. Regla del modelo: un proceso publicado no vuelve a borrador (409). Tests: [ProcesoControllerTest][] › `editar_proceso`, `publicar_proceso`; [ProcesoServiceTest][] › `proceso_publicado_no_vuelve_a_borrador`. |
| 3 | Cada cambio queda en el historial con usuario y fecha | ✅ | El historial detalla valor anterior y nuevo. Tests: [ProcesoServiceTest][] › `ProcesoServiceImpl_editarDatos_cambiaCampos_historialDetallaValorAnteriorYNuevo`, `ProcesoServiceImpl_cambiarEstado_aPublicado_historialIncluyeEstadoAnteriorYNuevo`. |
| 4 | Los usuarios de solo lectura ven el proceso pero no la opción de edición | UI | El backend responde 403 a cualquier escritura. Para ocultar la opción el frontend usa `usuario.rolAcceso` de la respuesta del login y `GET /api/v1/permisos-estructura/mio`. |
| 5 | Se validan los mismos campos obligatorios que en la creación | ✅ | [EditarProcesoRequest][] (`@NotBlank`) y la misma comprobación de nombre único, excluyendo al propio proceso. Test: [ProcesoServiceTest][] › `ProcesoServiceImpl_editarDatos_nombreRecortadoYaExiste_lanzaReglaNegocio`. |

### HU-06 · Eliminar proceso

**Qué modela.** Eliminar un proceso retira el pool completo; por eso es lógica: el diagrama se conserva para auditoría.

**Dónde está.** `DELETE /api/v1/procesos/{id}` (solo administrador) → [ProcesoController][] → [ProcesoServiceImpl][].eliminarLogico. El "estado inactivo" se modela con el campo `activo = false`; el valor de `estado` (borrador o publicado) no cambia.

| # | Criterio | Estado | Evidencia |
|---|---|---|---|
| 1 | Se solicita confirmación antes de eliminar | UI | El backend no pide confirmación: el frontend debe pedirla antes de llamar al `DELETE`. |
| 2 | La eliminación es lógica: el proceso pasa a inactivo y no se borra | ✅ | `activo = false`; pools, nodos y arcos permanecen. Test: [ProcesoServiceTest][] › `eliminarLogico_exitoso`. |
| 3 | Un proceso inactivo no aparece por defecto, pero se consulta con filtro | ✅ | `GET /procesos?activo=false` lista los inactivos; detalle, diagrama e historial siguen consultables. Tests: [ProcesoRepositoryTest][] › `ProcesoRepository_buscarConFiltros_activoFalsoDevuelveLosInactivos`, [ProcesoDiagramaServiceTest][] › `obtener_procesoInactivo_sigueSiendoConsultable`. |
| 4 | Solo el administrador puede eliminar | ✅ | [SecurityConfig][] (`DELETE /api/v1/procesos/**` → administrador). Tests: [ProcesoControllerTest][] › `eliminar_como_editor`, [AutorizacionPorRolTest][]. |
| 5 | La eliminación queda registrada en el historial | ✅ | "Proceso eliminado (baja logica)." Test: [ProcesoServiceTest][] › `eliminarLogico_exitoso`. |

### HU-07 · Consultar procesos

**Qué modela.** La consulta llega hasta el diagrama completo (pool, lanes y elementos conectados), no solo a una ficha.

**Dónde está.** `GET /api/v1/procesos` (listado), `GET /api/v1/procesos/{id}` (detalle con historial), `GET /api/v1/procesos/{id}/diagrama`, `GET /api/v1/procesos/{id}/historial` → [ProcesoController][] → [ProcesoServiceImpl][], [ProcesoDiagramaServiceImpl][].

| # | Criterio | Estado | Evidencia |
|---|---|---|---|
| 1 | Lista con búsqueda por nombre y filtros por estado y categoría | ✅ | Parámetros `nombre` (fragmento, sin distinguir mayúsculas), `estado`, `categoria` y `activo`. Tests: [ProcesoRepositoryTest][] › `ProcesoRepository_buscarConFiltros_nombreBuscaPorFragmentoSinMayusculas`, `ProcesoRepository_buscarConFiltros_estadoYCategoriaSeCombinan`. |
| 2 | El listado es paginado | ✅ | `pagina` desde 0 y `tamano` de 1 a 100 (10 por defecto), respuesta `PageResponse`. Tests: [ProcesoControllerTest][] › `listar_tamano_personalizado`, `listar_tamano_fuera_de_rango`. |
| 3 | Solo se muestran los procesos de la empresa autenticada | ✅ | Test: [ProcesoRepositoryTest][] › `ProcesoRepository_buscarConFiltros_noMuestraProcesosDeOtraEmpresa`; [AislamientoEmpresasIntegracionTest][]. |
| 4 | Al abrir un proceso se visualiza su diagrama completo | UI | Dibujarlo es del frontend. El backend entrega en una sola respuesta `GET /procesos/{id}/diagrama` el proceso, pools, lanes, actividades, gateways, eventos de mensaje, arcos y mensajes. Tests: [ProcesoControllerTest][] › `obtener_diagrama_retorna_200`, [ProcesoDiagramaServiceTest][] › `obtener_clasifica_nodos_y_excluye_inactivos`. |
| 5 | Se puede consultar el historial de cambios del proceso | ✅ | `GET /procesos/{id}/historial` (más reciente primero) y dentro del detalle. Test: [ProcesoControllerTest][] › `consultar_historial_separado`. |

## Modelado del proceso: actividades, arcos y gateways

### HU-08 · Crear actividad

**Qué modela.** Una actividad (task) es la unidad de trabajo; la lane donde se ubica define su rol responsable.

**Dónde está.** `POST /api/v1/lanes/{laneId}/actividades` → [ActividadController][] → [ActividadServiceImpl][].crear. Consulta: `GET /api/v1/actividades/{id}` y `GET /api/v1/lanes/{laneId}/actividades`.

| # | Criterio | Estado | Evidencia |
|---|---|---|---|
| 1 | Se especifican el nombre y el tipo de actividad | ✅ | [ActividadRequest][] con `nombre` obligatorio y `tipoActividad` ([TipoActividad][]: tarea, usuario, servicio, manual, script, regla de negocio; si se omite, `TAREA`). Test: [ActividadServiceTest][] › `crear_actividad_exitosamente`. |
| 2 | Queda asociada al proceso y a una lane que define su rol responsable | ✅ | La lane va en la ruta; el proceso se deduce de lane → pool → proceso. Se rechaza en pools de caja negra y en procesos inactivos. Tests: [ActividadServiceTest][] › `crear_en_pool_caja_negra_falla`, [InvariantesModeladoIntegracionTest][] › `crear_actividad_en_proceso_inactivo_falla`. |
| 3 | El nombre es obligatorio y único dentro del proceso | ✅ | Único entre todos los nodos activos del proceso (actividades, gateways y eventos), sin distinguir mayúsculas; lo garantiza solo el servicio, sin restricción en base de datos. Test: [ActividadServiceTest][] › `crear_con_nombre_duplicado_falla`. |
| 4 | La actividad aparece en el diagrama en la posición donde el usuario la ubicó | UI | El backend persiste `posicionX` y `posicionY` y los devuelve en `ActividadResponse` y en `/diagrama`; el dibujo es del frontend. |
| 5 | La creación queda registrada en el historial del proceso | ✅ | "Actividad creada: …" vía [AuditoriaModeladoServiceImpl][]. Tests: [AuditoriaModeladoServiceTest][] › `usuario_existente_registra_historial`, [ActividadServiceTest][] › `crear_actividad_exitosamente`. |

### HU-09 · Editar actividad

**Qué modela.** Cambiar la lane de una actividad cambia su responsable: la responsabilidad es la banda donde está dibujada.

**Dónde está.** `PUT /api/v1/actividades/{id}` → [ActividadController][] → [ActividadServiceImpl][].editar.

| # | Criterio | Estado | Evidencia |
|---|---|---|---|
| 1 | Se pueden modificar nombre, tipo y lane responsable | ✅ | [ActividadRequest][] (`nombre`, `tipoActividad`, `laneId`). Tests: [ActividadServiceTest][] › `mover_a_lane_mismo_pool`, `editar_nombre_duplicado`. |
| 2 | Al cambiar de lane, la actividad se mueve visualmente a la banda del nuevo rol | UI | El backend guarda el nuevo `laneId` (y valida que sea del mismo proceso y no de un pool de caja negra); mover el rectángulo es del frontend. |
| 3 | Los arcos conectados se conservan | ✅ | Dentro del mismo pool los arcos no se tocan; a otro pool solo se permite si no tiene arcos (si no, 409), porque un arco no puede cruzar pools ([ReglasMovimientoNodo][]). Tests: [ActividadServiceTest][] › `mover_a_otro_pool_sin_arcos`, `mover_a_otro_pool_con_arcos`. |
| 4 | Los cambios se guardan en el historial | ✅ | El texto incluye nombre, lane, rol y tipo anterior y nuevo. Test: [ActividadServiceTest][] › `editar_describe_nombre_lane_rol_y_tipo_anterior_y_nuevo`. |
| 5 | Solo usuarios con permisos de edición pueden modificarla | ✅ | Administrador o editor ([SecurityConfig][]). Test: [AutorizacionPorRolTest][]. |

### HU-10 · Eliminar actividad

**Qué modela.** Quitar un rectángulo deja arcos sueltos, que BPMN no admite; la eliminación arrastra sus conexiones y el editor advierte si el flujo se parte.

**Dónde está.** `GET /api/v1/actividades/{id}/impacto-eliminacion` y `DELETE /api/v1/actividades/{id}` → [ActividadController][] → [ActividadServiceImpl][].evaluarImpactoEliminacion / eliminar.

| # | Criterio | Estado | Evidencia |
|---|---|---|---|
| 1 | Se solicita confirmación antes de eliminar | UI | El frontend consulta `impacto-eliminacion` (`requiereConfirmacion`, `advertencias`) y pide confirmar. |
| 2 | Se eliminan también los arcos que entraban y salían | ✅ | `ArcoService.desactivarPorNodo` da de baja los arcos de entrada y salida. Test: [ActividadServiceTest][] › `eliminar_hace_baja_logica_y_desactiva_arcos`. |
| 3 | El editor advierte si el proceso queda con elementos desconectados | ✅ | `impacto-eliminacion` informa qué nodo queda "sin salida" o "sin entrada" y que se eliminarán los arcos (`rompeContinuidad`). Tests: [ActividadServiceTest][] › `impacto_eliminacion_detecta_desconexion`, [ActividadControllerTest][] › `impacto_eliminacion_retorna_advertencias`. |
| 4 | La eliminación es lógica y queda en el historial | ✅ | `activo = false` y "Actividad eliminada (baja logica)". Test: [InvariantesModeladoIntegracionTest][] › `actividad_eliminada_queda_inaccesible`. |
| 5 | Solo el administrador puede eliminar | ✅ | [SecurityConfig][] (`DELETE /api/v1/actividades/**`). Test: [AutorizacionPorRolTest][]. |

### HU-11 · Crear arco

**Qué modela.** El arco es el flujo de secuencia: la línea continua con punta sólida que ordena el trabajo dentro de un mismo pool.

**Dónde está.** `POST /api/v1/arcos` y `GET /api/v1/pools/{poolId}/arcos`, `GET /api/v1/arcos/{id}` → [ArcoController][] → [ArcoServiceImpl][].crear / validarConexion.

| # | Criterio | Estado | Evidencia |
|---|---|---|---|
| 1 | Se define nodo de origen y de destino (actividades, gateways o eventos) | ✅ | [ArcoRequest][] (`origenId`, `destinoId` obligatorios), sobre cualquier `NodoFlujo` activo. Test: [ArcoServiceTest][] › `crear_con_nodo_inactivo_falla`. |
| 2 | No se permite un arco con el mismo origen y destino | ✅ | Test: [ArcoServiceTest][] › `crear_con_mismo_origen_destino_falla`. |
| 3 | No se permite un arco entre elementos de pools distintos | ✅ | `validarConexion` exige el mismo pool ("esa comunicación se modela como mensaje"). Test: [ArcoServiceTest][] › `editar_pool_diferente` (la misma comprobación aplica a la creación). |
| 4 | No se permiten dos arcos idénticos entre el mismo par de nodos | ✅ | `existsByOrigenIdAndDestinoId…ActivoTrue`; lo garantiza el servicio, sin restricción en base de datos. Test: [ArcoServiceTest][] › `editar_arco_duplicado`. |
| 5 | El arco se ve como línea continua con punta sólida | UI | `ArcoResponse` entrega `origenId`, `destinoId`, `poolId`, `etiqueta` y `condicion`; el trazo es del frontend. |

Reglas añadidas en esta misma validación: un arco desde un gateway exclusivo o inclusivo exige condición (salvo en convergencia); solo esos gateways admiten condición; y un Message Catch de inicio no puede recibir arcos. Prueba: [ModeladoBrechasIntegracionTest][] › `Arcos_condicion_soloSalenConCondicionLosGatewaysExclusivosEInclusivos`.

### HU-12 · Editar arco

**Qué modela.** Un arco que sale de un gateway lleva además una condición; editarla es editar el arco, no el gateway.

**Dónde está.** `PUT /api/v1/arcos/{id}` → [ArcoController][] → [ArcoServiceImpl][].editar.

| # | Criterio | Estado | Evidencia |
|---|---|---|---|
| 1 | Se puede cambiar el nodo de origen y el de destino | ✅ | [EditarArcoRequest][] (`origenId`, `destinoId` opcionales). Tests: [ArcoServiceTest][] › `editar_cambiar_ambos_nodos`, [ArcoEdicionConProxiesLazyIntegracionTest][]. |
| 2 | Se pueden editar la etiqueta y, si sale de un gateway, la condición | ✅ | La condición solo se acepta si el origen es gateway exclusivo o inclusivo (en otros casos 409, en lugar de descartarla en silencio). Tests: [ArcoServiceTest][] › `editar_gateway_inclusivo_origen_con_condicion`, `crear_desde_una_actividad_con_condicion_se_rechaza_y_no_guarda`. |
| 3 | Se aplican las mismas validaciones de la creación | ✅ | Ambos métodos usan `validarConexion`. Tests: [ArcoServiceTest][] › `editar_origen_igual_destino`, `editar_arco_duplicado`. |
| 4 | El diagrama se redibuja al guardar | UI | El `PUT` devuelve el `ArcoResponse` actualizado. |
| 5 | Los cambios quedan en el historial | ✅ | "Arco editado: origen -> destino". |

### HU-13 · Eliminar arco

**Qué modela.** Quitar un flujo puede dejar un elemento inalcanzable; el editor debe advertirlo.

**Dónde está.** `GET /api/v1/arcos/{id}/impacto-eliminacion` y `DELETE /api/v1/arcos/{id}` → [ArcoController][] → [ArcoServiceImpl][].evaluarImpactoEliminacion / eliminar.

| # | Criterio | Estado | Evidencia |
|---|---|---|---|
| 1 | Se solicita confirmación previa | UI | El frontend consulta `impacto-eliminacion` y pide confirmar. |
| 2 | El diagrama se ajusta y los nodos quedan sin esa conexión | ✅ | Baja lógica del arco; `/diagrama` solo devuelve arcos activos (el redibujo es del frontend). Test: [ArcoServiceTest][] › `eliminar_hace_baja_logica`. |
| 3 | El editor advierte si queda un elemento sin camino de entrada o de salida | ✅ | `impacto-eliminacion` avisa "El nodo … quedara sin salida / sin entrada". Tests: [ArcoServiceTest][] › `impacto_sin_alternativas_advierte_ambos_extremos`, `impacto_con_alternativas_no_advierte`. |
| 4 | La eliminación queda registrada en el historial | ✅ | "Arco eliminado (baja logica)". |
| 5 | Solo el administrador puede eliminar | ✅ | [SecurityConfig][] (`DELETE /api/v1/arcos/**`). Test: [AutorizacionPorRolTest][]. |

### HU-14 · Crear gateway

**Qué modela.** Un gateway (rombo) controla cómo se divide o se junta el flujo: exclusivo (X), paralelo (+) o inclusivo (círculo). No ejecuta trabajo, solo decide por dónde sigue.

**Dónde está.** `POST /api/v1/lanes/{laneId}/gateways` → [GatewayController][] → [GatewayServiceImpl][].crear. Reglas de ramas en [ArcoServiceImpl][] y [ValidacionModeloServiceImpl][].

| # | Criterio | Estado | Evidencia |
|---|---|---|---|
| 1 | Se permite crear gateways exclusivos, paralelos o inclusivos | ✅ | [GatewayRequest][] con `tipoGateway` obligatorio ([TipoGateway][]). Test: [GatewayServiceTest][] › `crear_gateway_exitosamente`. |
| 2 | El tipo determina el símbolo dentro del rombo | UI | `GatewayResponse.tipoGateway` es el dato que usa el frontend para dibujar X, + o círculo. |
| 3 | Un gateway de divergencia debe tener al menos dos arcos salientes | ✅ | Se exige en la validación del modelo (no al crear el gateway, porque los arcos llegan después y el borrador admite diagramas incompletos): aparece en `GET /procesos/{id}/validacion` y bloquea la publicación. Un gateway convergente (2 o más entradas) solo necesita una salida. Tests: [ValidacionModeloServiceTest][] › `gateway_divergente_con_menos_de_dos_salidas_falla`, `obtenerAdvertencias_gatewayDivergenteConUnaSalida_advierte`. |
| 4 | En gateways exclusivos o inclusivos, cada arco saliente debe tener condición | ✅ | [ArcoServiceImpl][] responde 409 al crear o editar un arco sin condición desde esos gateways (salvo convergencia) y la validación lo vuelve a comprobar. Tests: [ArcoServiceTest][] › `crear_arco_desde_gateway_exclusivo_sin_condicion`, [ValidacionModeloServiceTest][] › `obtenerAdvertencias_gatewayInclusivoConSalidaSinCondicion_advierte`. |
| 5 | La creación queda registrada en el historial | ✅ | "Gateway creado: nombre (tipo)". |

### HU-15 · Editar gateway

**Qué modela.** Cambiar el tipo cambia el significado del diagrama: pasar de exclusivo a paralelo deja de ser una decisión y las condiciones pierden sentido.

**Dónde está.** `PUT /api/v1/gateways/{id}` → [GatewayController][] → [GatewayServiceImpl][].editar; condiciones por `PUT /api/v1/arcos/{id}`.

| # | Criterio | Estado | Evidencia |
|---|---|---|---|
| 1 | Se puede cambiar el tipo y el símbolo se actualiza | ✅ | El backend cambia y devuelve `tipoGateway`; el símbolo lo redibuja el frontend. Tests: [GatewayServiceTest][] › `cambiar_paralelo_a_exclusivo_con_condicion`, `cambiar_paralelo_a_inclusivo_sin_arcos`. |
| 2 | Se pueden actualizar las condiciones de cada arco saliente | ✅ | `PUT /arcos/{id}` (ver HU-12). |
| 3 | Al pasar a exclusivo, el editor advierte si las condiciones no son mutuamente excluyentes | ❌ | No existe esa comprobación: las condiciones son texto libre y el backend no las analiza. Lo único que se verifica al pasar de paralelo a exclusivo o inclusivo es que todos los arcos salientes tengan condición (409 si falta alguna, test `cambiar_paralelo_a_exclusivo_sin_condicion`); eso no equivale a comprobar la exclusión mutua. |
| 4 | Al pasar a paralelo se eliminan las condiciones | ✅ | Se borran las condiciones de los arcos salientes. Tests: [GatewayServiceTest][] › `cambiar_a_paralelo_limpia_condiciones`, [ModeladoBrechasIntegracionTest][] › `Gateway_editarAParalelo_limpiaLasCondicionesDeSusSalidas`. |
| 5 | Los cambios quedan en el historial | ✅ | Registra nombre, tipo y número de condiciones eliminadas. Test: [GatewayServiceTest][] › `editar_describe_el_nombre_el_tipo_y_las_condiciones_eliminadas`. |

### HU-16 · Eliminar gateway

**Qué modela.** Un gateway suele ser el punto donde el flujo se abre; al eliminarlo las ramas quedan sin origen, y el editor debe advertirlo.

**Dónde está.** `GET /api/v1/gateways/{id}/impacto-eliminacion` y `DELETE /api/v1/gateways/{id}` → [GatewayController][] → [GatewayServiceImpl][].

| # | Criterio | Estado | Evidencia |
|---|---|---|---|
| 1 | Se solicita confirmación antes de eliminar | UI | El frontend usa `impacto-eliminacion` para pedir confirmación. |
| 2 | Se eliminan los arcos conectados | ✅ | Test: [GatewayServiceTest][] › `eliminar_desactiva_gateway_y_arcos`. |
| 3 | El editor advierte si la ramificación queda sin punto de decisión | ✅ | Avisa "rompe una ramificación con N salidas" o "una convergencia de N entradas". Tests: [GatewayServiceTest][] › `impacto_eliminacion_detecta_ramificacion`, `impacto_de_romper_una_convergencia_avisa_de_las_entradas_y_la_salida`. |
| 4 | La eliminación es lógica y queda en el historial | ✅ | "Gateway eliminado (baja logica)". |
| 5 | Solo el administrador puede eliminar | ✅ | [SecurityConfig][] (`DELETE /api/v1/gateways/**`). Test: [AutorizacionPorRolTest][]. |

## Roles de proceso

### HU-17 · Crear rol de proceso

**Qué modela.** El rol de proceso es lo que BPMN dibuja como lane: una función (Analista, Supervisor, Auditor), no una persona. Distinto del rol de acceso (HU-02).

**Dónde está.** `POST /api/v1/roles` → [RolProcesoController][] → [RolProcesoServiceImpl][].crear. Solo administrador.

| # | Criterio | Estado | Evidencia |
|---|---|---|---|
| 1 | Se registran el nombre del rol y su descripción | ✅ | [RolProcesoRequest][]: nombre obligatorio, descripción opcional. Test: [RolProcesoControllerTest][] › `crear_rol`. |
| 2 | El nombre es único dentro de la empresa | ✅ | Entre roles activos, sin distinguir mayúsculas y con el nombre recortado; en PostgreSQL además por índice parcial ([V3__unicidad_entre_activos][]). Tests: [RolProcesoServiceTest][] › `crear_nombre_duplicado_falla`, `crear_nombre_duplicado_con_espacios_falla`. |
| 3 | El rol queda asociado a la empresa y puede usarse en varios procesos | ✅ | [RolProceso][] extiende [EntidadEmpresa][]; las lanes de distintos procesos lo referencian por id. |
| 4 | Queda disponible para nombrar lanes en cualquier proceso de la empresa | ✅ | [LaneOrquestadorServiceImpl][] resuelve el rol activo de la empresa al crear o editar una lane. Test: [LaneOrquestadorServiceTest][] › `LaneOrquestadorServiceImpl_crear_resuelveElRolYDevuelveResponse`. |
| 5 | Solo el administrador de la empresa puede crear roles | ✅ | Tests: [RolProcesoControllerTest][] › `crear_como_editor`, [AutorizacionPorRolTest][]. |

### HU-18 · Editar rol de proceso

**Qué modela.** Renombrar un rol renombra todas las lanes que lo usan: el diagrama guarda la referencia al rol, no una copia del nombre.

**Dónde está.** `PUT /api/v1/roles/{id}` → [RolProcesoController][] → [RolProcesoServiceImpl][].editar → `LaneService.renombrarLanesDeRol`.

| # | Criterio | Estado | Evidencia |
|---|---|---|---|
| 1 | Se pueden modificar nombre y descripción | ✅ | Test: [RolProcesoControllerTest][] › `editar_rol`. |
| 2 | Los procesos que referencian el rol mantienen la referencia y muestran el nombre actualizado | ✅ | `Lane.rolProceso` es una referencia; `LaneResponse` entrega `rolProcesoId` y `rolProcesoNombre`. Test: [ModeladoBrechasIntegracionTest][] › `RolProceso_renombrar_actualizaLasLanes_yLasDeProcesosInactivosNoBloqueanLaBaja`. |
| 3 | El nuevo nombre sigue siendo único dentro de la empresa | ✅ | Misma validación que al crear, excluyendo al propio rol. |
| 4 | Las lanes nombradas con ese rol actualizan su etiqueta | ✅ | `renombrarLanesDeRol` pone el nuevo nombre en todas las lanes del rol (sobrescribe la etiqueta que tuvieran). Test: [LaneServiceTest][] › `renombrarLanesDeRol_actualiza_la_etiqueta_de_todas_las_lanes_del_rol`. |
| 5 | El cambio queda registrado en el historial | 🟡 | Se registra en el historial de cada proceso que tiene lanes con ese rol, con el usuario autenticado. No existe un historial propio del rol, así que un cambio sobre un rol sin lanes no deja rastro. Test: [RolProcesoServiceTest][] › `editar_actualiza_y_registra_historial_con_usuario_autenticado`. |

### HU-19 · Eliminar rol de proceso

**Qué modela.** Eliminar un rol en uso dejaría lanes sin responsable; por eso la validación previa es obligatoria y la baja es lógica.

**Dónde está.** `DELETE /api/v1/roles/{id}` → [RolProcesoController][] → [RolProcesoServiceImpl][].eliminar. Solo administrador. El uso se informa en `GET /roles` y `GET /roles/consulta`.

| # | Criterio | Estado | Evidencia |
|---|---|---|---|
| 1 | Se valida que el rol no esté asignado a ninguna lane ni actividad antes de eliminarlo | 🟡 | Se valida contra las lanes (la actividad hereda su rol de la lane), pero **solo cuentan las lanes de procesos activos**: un rol usado únicamente en procesos dados de baja sí puede eliminarse. Es una decisión deliberada y está probada. Tests: [RolProcesoServiceTest][] › `eliminar_rol_en_uso`, `eliminar_rol_con_lanes_solo_en_procesos_inactivos_se_permite`. |
| 2 | Si está en uso, el sistema indica en qué procesos y no permite eliminar | ✅ | 409 con el listado de procesos en el mensaje. Test: [RolProcesoServiceTest][] › `eliminar_rol_en_uso`. |
| 3 | Se solicita confirmación previa a la eliminación | UI | El frontend usa `enUso` y `procesosQueLoUsan` de `GET /roles` para avisar y confirmar. |
| 4 | La eliminación es lógica y se conserva el historial | ✅ | `activo = false`. Test: [RolProcesoServiceTest][] › `eliminar_rol_sin_uso`. |
| 5 | Solo el administrador puede eliminar roles | ✅ | Tests: [AutorizacionPorRolTest][], [RolProcesoControllerTest][] › `crear_como_editor`. |

### HU-20 · Consultar roles de proceso

**Qué modela.** El catálogo de roles disponibles para nombrar lanes.

**Dónde está.** `GET /api/v1/roles` (listado completo con conteo de uso), `GET /api/v1/roles/consulta` (paginado y con búsqueda), `GET /api/v1/roles/{id}` → [RolProcesoController][] → [RolProcesoServiceImpl][]. Lo puede consultar cualquier usuario autenticado.

| # | Criterio | Estado | Evidencia |
|---|---|---|---|
| 1 | Lista de roles con nombre y descripción | ✅ | [RolProcesoVistaResponse][]. Test: [RolProcesoControllerTest][] › `listar_roles`. |
| 2 | Búsqueda por nombre y listado paginado | ✅ | `GET /roles/consulta?nombre=&pagina=&tamano=` (tamaño de 1 a 100, orden por nombre). Tests: [RolProcesoControllerTest][] › `consulta_paginada_retorna_procesos_de_uso`, `consulta_paginada_con_tamano_fuera_de_rango_retorna_400`. |
| 3 | Solo se muestran los roles de la empresa autenticada | ✅ | Test: [RolProcesoServiceTest][] › `buscar_sin_nombre_usa_paginacion_y_lista_procesos`; [AislamientoEmpresasIntegracionTest][]. |
| 4 | Para cada rol se puede ver en qué procesos se usa | ✅ | `RolProcesoConsultaResponse.procesos` (nombres de procesos activos). Test: [ModeladoBrechasIntegracionTest][] › `RolProceso_consultaPaginada_agrupaLosProcesosPorRolYOrdenaPorNombre`. |
| 5 | El listado indica si el rol puede eliminarse o está en uso | ✅ | Campo `enUso` en ambas respuestas. Test: [RolProcesoServiceTest][] › `listar_con_uso_mapea_conteo_y_bandera`. |

## Pools y lanes

### HU-21 · Configurar pool por empresa

**Qué modela.** El pool es un participante del proceso (la empresa, un cliente, un proveedor o un sistema externo). Un participante externo cuyo interior no interesa se dibuja como caja negra.

**Dónde está.** `GET/POST /api/v1/procesos/{procesoId}/pools`, `GET/PUT/DELETE /api/v1/pools/{id}` → [PoolController][] → [PoolServiceImpl][], [PoolOrquestadorServiceImpl][] (eliminación). Tipos en [TipoParticipante][].

| # | Criterio | Estado | Evidencia |
|---|---|---|---|
| 1 | Al modelar se puede crear el pool que representa a la empresa propietaria | ✅ | Se crea automáticamente al crear el proceso (HU-04) y se pueden añadir más con `POST .../pools`. La publicación exige exactamente un pool `EMPRESA` que no sea caja negra. Tests: [ProcesoOrquestadorServiceTest][], [ValidacionModeloServiceTest][] › `obtenerAdvertencias_dosPoolesEmpresa_advierte`. |
| 2 | Todo elemento (actividad, gateway, arco, evento) queda contenido en un pool | ✅ | `lane_id`, `pool_id` y los pools de origen y destino de los mensajes son obligatorios en [V1__esquema_inicial][]; los nodos cuelgan de una lane y esta de un pool. |
| 3 | Un diagrama puede tener varios pools | ✅ | `tipoParticipante`: `EMPRESA`, `CLIENTE`, `PROVEEDOR`, `SISTEMA_EXTERNO`. Test: [PoolServiceTest][] › `crear_exitoso`. |
| 4 | Un participante externo se modela como caja negra, sin elementos internos | ✅ | Un pool con `cajaNegra = true` rechaza lanes, actividades, gateways y eventos (409), y no se puede marcar caja negra si ya tiene lanes. Tests: [LaneServiceTest][] › `alMarcarPoolCajaNegra_con_lanes_lanza_excepcion`, [ActividadServiceTest][] › `crear_en_pool_caja_negra_falla`, [GatewayServiceTest][] › `crear_en_pool_caja_negra_falla`, [EventoMensajeServiceTest][] › `crear_en_pool_caja_negra_falla`. |
| 5 | El pool se representa como contenedor y muestra su nombre | UI | `PoolResponse` entrega `nombre`, `tipoParticipante`, `cajaNegra` y `orden`. |
| 6 | Solo usuarios con permisos de edición crean o modifican pools, y los cambios quedan en el historial | ✅ | Administrador o editor, más el permiso de estructura configurado (HU-24); el historial registra "Pool creado / editado / eliminado". Un pool solo se elimina si no tiene elementos ni mensajes activos. Tests: [PoolControllerTest][], [PoolServiceTest][] › `eliminar_pool_con_actividades`, [PoolOrquestadorServiceTest][] › `eliminar_no_continua_si_tiene_mensajes_activos`. |

### HU-22 · Diferenciar pool y lane (swimlane)

**Qué modela.** El pool dice quién participa; la lane dice quién hace qué dentro de esa organización. Los arcos se mueven entre lanes del mismo pool pero nunca cruzan de pool.

**Dónde está.** `GET/POST /api/v1/pools/{poolId}/lanes`, `GET/PUT/DELETE /api/v1/lanes/{id}`, `PATCH /api/v1/pools/{poolId}/lanes/orden` → [LaneController][] → [LaneOrquestadorServiceImpl][], [LaneServiceImpl][].

| # | Criterio | Estado | Evidencia |
|---|---|---|---|
| 1 | Se pueden crear, renombrar, reordenar y eliminar lanes dentro de un pool | ✅ | `POST`, `PUT`, `PATCH .../orden` (exige la lista completa de ids, sin duplicados) y `DELETE` (baja lógica con `@SQLDelete`). Tests: [LaneControllerTest][], [LaneServiceTest][] › `reordenar_exitoso`, `reordenar_con_duplicados`. |
| 2 | Cada lane se asocia a un rol de proceso de la empresa (HU-17) | ✅ | [LaneRequest][] exige `rolProcesoId`; se resuelve solo entre roles activos de la empresa (404 si no). Tests: [LaneOrquestadorServiceTest][] › `LaneOrquestadorServiceImpl_crearConRolInexistente_propagaErrorYNoCreaLane`. |
| 3 | Al crear o editar una actividad se selecciona la lane, quedando definido el rol responsable | ✅ | La lane va en la ruta del `POST` de la actividad y en `laneId` del `PUT` (HU-08, HU-09). |
| 4 | Una actividad pertenece a exactamente una lane | ✅ | `nodos_flujo.lane_id` es una sola clave foránea obligatoria. |
| 5 | Las lanes se muestran como divisiones internas del pool | UI | `LaneResponse` entrega `poolId`, `orden`, `rolProcesoNombre`; `GET /pools/{id}/lanes` las devuelve ordenadas. |
| 6 | No se puede eliminar una lane con actividades: primero deben reasignarse | ✅ | [LaneServiceImpl][].eliminar responde 409 si la lane tiene cualquier nodo activo (actividades, gateways o eventos); la reasignación se hace con `PUT /actividades/{id}` y `laneId`. Sin prueba unitaria dedicada de este rechazo; sí del flujo de reasignación ([ActividadServiceTest][] › `mover_a_lane_mismo_pool`). |

### HU-23 · Compartir procesos entre pools (alcance y límites)

**Qué modela.** Una colaboración entre empresas: un proceso puede compartirse en solo lectura con otras empresas sin exponer sus usuarios ni sus roles. Es la única excepción al aislamiento por empresa.

**Dónde está.** Propietaria: `GET/POST /api/v1/procesos/{procesoId}/compartidos`, `DELETE /api/v1/procesos/{procesoId}/compartidos/{empresaInvitadaId}`. Invitada: `GET /api/v1/procesos-compartidos` y `GET /api/v1/procesos-compartidos/{procesoId}` → [ProcesoCompartidoController][] → [ProcesoCompartidoServiceImpl][].

| # | Criterio | Estado | Evidencia |
|---|---|---|---|
| 1 | Un proceso puede marcarse como compartido indicando qué empresas pueden consultarlo | ✅ | `POST .../compartidos` con `empresaInvitadaId` (una llamada por empresa; `DELETE` retira el acceso). Requiere conocer el id numérico de la empresa invitada (ver *Limitaciones*). Test: [ProcesoCompartidoServiceTest][] › `compartir_crea_acceso_solo_lectura`. |
| 2 | Por defecto los procesos no son compartidos | ✅ | Sin fila en `procesos_compartidos` solo existe para la empresa propietaria, porque toda consulta filtra por la empresa del token. |
| 3 | Solo el administrador de la empresa propietaria cambia el estado de compartición | ✅ | [SecurityConfig][] y propiedad del proceso. Tests: [ProcesoCompartidoControllerTest][] › `editor_no_puede_compartir`, [ProcesoCompartidoServiceTest][] › `compartir_con_si_misma_falla`. |
| 4 | El acceso compartido es de solo lectura | ✅ | La empresa invitada solo dispone de `GET`; cualquier escritura resuelve el recurso con su propio `empresaId` y responde 404. `soloLectura` siempre es `true`. Tests: [AislamientoEmpresasIntegracionTest][] › `Aislamiento_recursoDeOtraEmpresaPorId_devuelve404SinCambiarlo`, [ProcesoCompartidoServiceTest][] › `obtener_compartido_arma_detalle_y_filtra_inactivos`. |
| 5 | Los flujos de mensaje entre pools solo pueden dirigirse a pools declarados en el mismo diagrama | ✅ | [MensajeServiceImpl][].validarPoolsDelProceso rechaza pools que no pertenezcan al proceso indicado (409). |
| 6 | Cada cambio del estado de compartición queda en el historial | ✅ | "Proceso compartido en modo solo lectura con …" y "Se retiro el acceso compartido a …". |

Nota (no se comparten usuarios ni roles): la vista de la invitada usa `LaneCompartidaResponse`, sin el id del rol (solo el nombre del rol como etiqueta de la lane), y no incluye usuarios.

### HU-24 · Asociar roles y permisos a un pool

**Qué modela.** Conecta el catálogo de roles de proceso con las lanes del diagrama y define qué roles de acceso pueden reorganizar la estructura (pools y lanes).

**Dónde está.** `GET /api/v1/permisos-estructura`, `PUT /api/v1/permisos-estructura/{rol}` (solo administrador) y `GET /api/v1/permisos-estructura/mio` (cualquier usuario) → [PermisoEstructuraController][] → [PermisoEstructuraServiceImpl][]; se aplica en [PoolController][] y [LaneController][].

| # | Criterio | Estado | Evidencia |
|---|---|---|---|
| 1 | La empresa expone los roles de proceso como los únicos asignables a sus lanes | ✅ | El catálogo es `GET /roles` (HU-20) y la API lo hace cumplir: una lane solo acepta un `rolProcesoId` activo de la empresa. No hay un campo con los roles dentro de `PoolResponse`. |
| 2 | Se puede indicar qué roles de acceso crean, editan o eliminan pools y lanes | ✅ | Seis permisos por empresa y rol ([PermisoEstructura][]); por defecto el editor crea y edita pero no elimina; el administrador siempre conserva todos y solo lectura nunca recibe ninguno. Tests: [PermisoEstructuraServiceTest][], [PermisoEstructuraIntegracionTest][] › `PermisoEstructura_eliminar_editorConPermisoConcedidoYLuegoRevocado_pasaDe404A403`. |
| 3 | Los usuarios de solo lectura visualizan el pool y sus lanes pero no los modifican | ✅ | Los `GET` están abiertos y toda escritura responde 403. Test: [PermisoEstructuraIntegracionTest][] › `PermisoEstructura_eliminar_soloLecturaConCualquierConfiguracion_siempre403`. |
| 4 | Tanto la interfaz como la API respetan estos permisos | UI | La API los aplica (403, probado). Para que la interfaz oculte o deshabilite las acciones, `GET /permisos-estructura/mio` devuelve los permisos del rol del usuario autenticado. Test: [PermisoEstructuraControllerTest][] › `mio_cualquierRolAutenticado_devuelveLosPermisosDeSuPropioRol`. |
| 5 | No se puede eliminar un rol asociado a una lane: primero debe reasignarse | 🟡 | Es la misma validación de HU-19 y hereda su salvedad: las lanes de procesos dados de baja no bloquean la eliminación. |

## Mensajes y colaboración entre pools

### HU-25 · Enviar mensaje entre procesos (Message Throw)

**Qué modela.** Un evento de lanzamiento de mensaje (círculo con sobre relleno) del que sale un flujo de mensaje punteado hacia otro pool. El sistema modela y valida el intercambio; no lo ejecuta.

**Dónde está.** El evento: `POST /api/v1/lanes/{laneId}/eventos-mensaje` con `tipoEvento = THROW` → [EventoMensajeController][] → [EventoMensajeServiceImpl][]. El flujo de mensaje: `POST /api/v1/procesos/{procesoId}/mensajes`, `GET/PUT/DELETE /api/v1/mensajes/{id}` → [MensajeController][] → [MensajeServiceImpl][]. Advertencias: [ValidacionModeloServiceImpl][].

| # | Criterio | Estado | Evidencia |
|---|---|---|---|
| 1 | Se puede agregar un Message Throw indicando el nombre del mensaje | ✅ | [EventoMensajeRequest][] (`nombre`, `tipoEvento`). Test: [EventoMensajeServiceTest][] › `crear_evento_guarda_y_audita`. |
| 2 | Se documenta el contenido: campos que se envían y su tipo de dato | 🟡 | `contenido` es un único texto libre obligatorio (en el evento y en el mensaje); no hay estructura de campos ni tipos de dato. Se puede escribir ahí la descripción, pero el sistema no la valida. |
| 3 | El envío se ve como flujo de mensaje punteado dirigido al pool destino | UI | `MensajeResponse` entrega `poolOrigenId`, `poolDestinoId`, `eventoThrowId` y `eventoCatchId`, y también viaja en `/procesos/{id}/diagrama`; el trazo punteado es del frontend. |
| 4 | Un flujo de mensaje solo es válido si cruza de un pool a otro; se rechaza dentro del mismo pool | ✅ | "Un mensaje debe conectar dos pools diferentes" (409); además el Throw debe estar en el pool de origen. Tests: [MensajeServiceTest][] › `crear_mismo_pool`, `crear_throw_en_pool_incorrecto_falla`. |
| 5 | El nombre debe corresponder con un Message Catch del pool destino; si no existe, se advierte que queda sin receptor | ✅ | El evento debe llamarse como el mensaje; el mensaje sin Catch ni destino externo aparece en `GET /procesos/{id}/validacion` y bloquea la publicación; `MensajeResponse.sinReceptor` lo marca. Tests: [ValidacionModeloServiceTest][] › `mensaje_sin_receptor_falla`, [MensajeServiceMapeoTest][], [MensajeServiceTest][] › `crear_throw_con_nombre_distinto_falla`. |
| 6 | Se puede editar y eliminar, con confirmación previa y registro en el historial | ✅ | `PUT`/`DELETE` del evento y del mensaje, con historial; el evento ofrece `impacto-eliminacion` y no se elimina mientras tenga mensajes activos (409). La confirmación es del frontend. Tests: [EventoMensajeServiceTest][] › `eliminar_evento_con_mensajes_activos_falla_y_no_toca_los_arcos`, [MensajeServiceTest][] › `eliminar_con_correlacion`. |

### HU-26 · Envío de notificaciones externas (mensaje a sistema externo)

**Qué modela.** Un sistema externo es un pool de caja negra; solo se dibuja el flujo de mensaje que sale hacia él. Se documenta la integración, no se ejecuta: no se guardan credenciales ni direcciones reales.

**Dónde está.** Un Message Throw (HU-25) y un `Mensaje` con `tipoDestinoExterno` dirigido a un pool `SISTEMA_EXTERNO` de caja negra → [MensajeServiceImpl][].validarDestinoExterno; coherencia en [ValidacionModeloServiceImpl][].

| # | Criterio | Estado | Evidencia |
|---|---|---|---|
| 1 | El sistema externo es un pool de participante externo sin actividades internas | ✅ | El mensaje externo exige pool `SISTEMA_EXTERNO` con `cajaNegra = true`, y ese pool no admite elementos internos. Tests: [MensajeServiceTest][] › `destino_externo_requiere_pool_externo_caja_negra`, [ValidacionModeloServiceTest][] › `mensaje_externo_con_pool_invalido_falla`. |
| 2 | Se puede agregar un evento de envío dirigido a ese pool indicando el tipo de destino (correo, servicio web o cola) | ✅ | El envío se modela con el evento Message Throw y el `tipoDestinoExterno` del mensaje ([TipoDestinoExterno][]: `CORREO`, `SERVICIO_WEB`, `COLA`). No existe una "tarea de envío" vinculable a un mensaje; el evento es la única vía. Test: [MensajeServiceTest][] › `crear_destino_externo_valido_funciona`. |
| 3 | Se documentan los datos que se envían y el momento del proceso en que ocurre | 🟡 | Los datos van en `contenido` (texto libre, sin campos ni tipos) y `destinoExterno` describe el destino (hasta 200 caracteres, solo documentación). El momento no es un campo: queda implícito en la posición del evento Throw dentro del flujo de arcos. Tests: [MensajeServiceTest][] › `destino_externo_requiere_direccion_documentada`, [MensajeControllerTest][] › `crear_con_destino_externo_largo_retorna_400`. |
| 4 | Se puede indicar qué ocurre si la notificación falla: continuar, derivar a una actividad de error o finalizar | ✅ | `politicaFalloNotificacion` ([PoliticaFalloNotificacion][]) es obligatoria en mensajes externos; `DERIVAR_ACTIVIDAD_ERROR` exige `actividadErrorId` de una actividad del mismo proceso. Tests: [MensajeServiceTest][] › `destino_externo_requiere_politica_de_fallo`, `derivar_error_requiere_actividad`, `derivar_error_con_actividad_valida_funciona`. |
| 5 | El elemento se puede editar y eliminar, con confirmación previa y registro en el historial | ✅ | Igual que HU-25. Test: [MensajeServiceTest][] › `editar_parcial_conserva_configuracion_externa`. |

### HU-27 · Recibir mensaje y activar proceso (Message Catch)

**Qué modela.** Un evento de recepción de mensaje (sobre sin rellenar): de inicio, arranca el proceso; intermedio, lo deja en espera. El sistema modela la espera, no la ejecuta.

**Dónde está.** `POST /api/v1/lanes/{laneId}/eventos-mensaje` con `tipoEvento = CATCH_INICIO` o `CATCH_INTERMEDIO` → [EventoMensajeController][] → [EventoMensajeServiceImpl][]; vínculo con el mensaje en [MensajeServiceImpl][]; relación evento-actividades en [V5__eventos_mensaje_actividades][].

| # | Criterio | Estado | Evidencia |
|---|---|---|---|
| 1 | Se puede agregar un Message Catch indicando el nombre del mensaje esperado | ✅ | `nombre` del evento, que debe coincidir con el del mensaje al vincularlo. Test: [ModeladoBrechasIntegracionTest][] › `Mensajeria_throwYCatchConElMismoNombre_seVinculanYSePublica`. |
| 2 | Se distinguen las variantes de inicio e intermedio | ✅ | [TipoEventoMensaje][]: `THROW`, `CATCH_INICIO`, `CATCH_INTERMEDIO`. |
| 3 | Se documentan los datos que se esperan y qué actividades los utilizan | 🟡 | Las actividades que usan los datos sí son estructuradas (`actividadIds`, solo en Catch, activas y del mismo proceso). Los datos esperados van en `contenido`, texto libre sin campos ni tipos. Tests: [EventoMensajeServiceTest][] › `crear_catch_con_actividades_las_asocia`, `crear_throw_con_actividades_falla`. |
| 4 | El editor valida que exista un Message Throw con el mismo nombre, o que el mensaje sea de origen externo | ✅ | Un Catch debe estar vinculado a un mensaje (que a su vez exige Throw) o marcarse `origenExterno`; si no, `/validacion` lo reporta como huérfano y se bloquea la publicación. Tests: [ValidacionModeloServiceTest][] › `obtenerAdvertencias_catchSinMensajeNiOrigenExterno_esHuerfano`, `obtenerAdvertencias_catchConOrigenExterno_noEsHuerfano`. |
| 5 | Un Message Catch de inicio no puede tener arcos entrantes | ✅ | Se impide al crear o editar un arco (409), al convertir un evento en Catch de inicio y se revalida al publicar. Tests: [ArcoServiceTest][] › `crear_hacia_catch_inicio_falla`, [ValidacionModeloServiceTest][] › `catch_inicio_con_entrada_falla`. |
| 6 | Se puede editar y eliminar, con confirmación previa y registro en el historial | ✅ | `PUT`/`DELETE`, con `impacto-eliminacion`; no se puede renombrar ni eliminar un evento vinculado a mensajes activos. Test: [ModeladoBrechasIntegracionTest][] › `EventoMensaje_vinculadoAUnMensaje_noSePuedeRenombrarNiEliminar_yElImpactoLoAvisa`. |

### HU-28 · Correlación de mensajes con instancias de proceso

**Qué modela.** La clave de correlación (por ejemplo el número de factura) indica a qué caso concreto corresponde un mensaje. No tiene símbolo propio: es una propiedad del mensaje. El sistema valida su coherencia en el modelo; no correlaciona mensajes reales.

**Dónde está.** `PUT/GET/DELETE /api/v1/mensajes/{mensajeId}/correlacion` → [CorrelacionController][] → [CorrelacionOrquestadorServiceImpl][] / [CorrelacionServiceImpl][]. La clave también se declara al crear el mensaje y el evento (`claveCorrelacion`). `Mensaje.politicaSinCaso` documenta qué hacer si no hay caso.

| # | Criterio | Estado | Evidencia |
|---|---|---|---|
| 1 | Cada mensaje declara una clave de correlación | ✅ | `claveCorrelacion` en el mensaje (se hereda del Throw si no se envía) más la entidad `Correlacion` con el `criterio`. Un mensaje creado por la ruta básica, sin Throw, puede carecer de clave, pero se reporta en `/validacion` y bloquea la publicación. Tests: [MensajeServiceTest][] › `crear_sin_clave_hereda_la_del_message_throw_y_guarda_la_correlacion`, [ValidacionModeloServiceTest][] › `mensaje_sin_clave_correlacion_falla`. |
| 2 | La clave se define de forma coherente en el Message Throw y en el Message Catch correspondientes | ✅ | Se rechaza con 409 una clave distinta entre mensaje, Throw y Catch, y la validación lo reporta. Tests: [CorrelacionServiceTest][] › `definir_rechaza_clave_distinta_al_throw`, [ValidacionModeloServiceTest][] › `obtenerAdvertencias_claveDelThrowYDelCatchDistintaALaDelMensaje_advierteLosDos`. |
| 3 | El editor advierte cuando un Message Catch intermedio no tiene clave de correlación | ✅ | Está en la validación del modelo (`/validacion` y publicación). Test: [ValidacionModeloServiceTest][] › `obtenerAdvertencias_catchIntermedioSinClave_advierte`. |
| 4 | El editor advierte cuando dos mensajes de un mismo proceso comparten nombre y clave | ✅ | Se rechaza al crear o definir la correlación (409) y se reporta una sola vez en la validación. Tests: [CorrelacionServiceTest][] › `definir_rechaza_ambiguedad_en_mismo_proceso`, [ValidacionModeloServiceTest][] › `obtenerAdvertencias_mensajesAmbiguos_seReportaUnaSolaVez`. |
| 5 | Se documenta qué ocurre cuando un mensaje no corresponde a ningún caso: descartarlo o iniciar un caso nuevo | ✅ | `politicaSinCaso` ([PoliticaMensajeSinCaso][]: `DESCARTAR`, `INICIAR_CASO_NUEVO`), `DESCARTAR` por defecto. Test: [MensajeServiceTest][] › `crear_extendido_interno_con_throw_y_catch_funciona`. |

## Limitaciones conocidas

Lo que sigue es lo que el backend **no** hace o hace de forma parcial. Ninguna de estas limitaciones se oculta en las tablas anteriores.

1. **Invitación sin envío de correo (HU-02).** No hay proveedor SMTP: el token de la invitación se devuelve al administrador, que debe entregarlo por su cuenta. Debe tratarse como credencial temporal.
2. **Contenido de mensajes en texto libre (HU-25, HU-26, HU-27).** `contenido` (en `Mensaje` y en `EventoMensaje`) es un único campo de texto: no hay estructura de campos ni tipos de dato, y el "momento" del envío externo no es un campo.
3. **Último administrador sin bloqueo de fila (HU-02).** [UsuarioServiceImpl][] cuenta los administradores activos y rechaza degradar o desactivar al último, pero no bloquea filas: dos peticiones simultáneas podrían dejar a la empresa sin administrador.
4. **Sin control de concurrencia optimista.** Ninguna entidad usa `@Version`: si dos usuarios editan el mismo elemento a la vez, gana la última escritura.
5. **Unicidades solo por servicio.** El nombre único de nodos dentro de un proceso, los arcos duplicados y los nombres de pool o lane se validan en el servicio, sin restricción en base de datos. Para procesos y roles de proceso existe además un índice parcial, pero solo en PostgreSQL ([V3__unicidad_entre_activos][]); en H2 (desarrollo) solo valida el servicio.
6. **Cierre de sesión sin invalidación de token (HU-03).** `POST /auth/logout` no revoca el JWT; vale hasta expirar (1800 s por defecto).
7. **Correo único en todo el sistema.** Una misma persona no puede pertenecer a dos empresas, porque el login pide solo correo y contraseña. Además, el registro de empresa, el alta de colaboradores y las invitaciones responden 409 cuando el correo ya existe, dato que el inicio de sesión sí oculta.
8. **Compartir exige el id de la empresa invitada (HU-23).** No hay directorio ni búsqueda de empresas: quien comparte debe conocer el identificador numérico de la otra empresa.
9. **Condiciones de arco en texto libre (HU-12, HU-15).** El backend exige que exista una condición, pero no la interpreta: no comprueba que las condiciones de un gateway exclusivo sean mutuamente excluyentes.
10. **Elementos propios del diagrama.** Solo existen eventos de mensaje (Throw y Catch): no hay eventos genéricos de inicio y fin. La validación de publicación no comprueba que todos los nodos sean alcanzables; las advertencias de elementos desconectados se calculan al eliminar (`impacto-eliminacion`), no en `/validacion`.
11. **Roles de proceso e historial (HU-18, HU-19).** Las lanes de procesos dados de baja no impiden eliminar el rol, y el cambio de un rol solo se registra en el historial de los procesos que tienen lanes con ese rol.
12. **Referencias a actividades eliminadas.** Eliminar una actividad que es la "actividad de error" de un mensaje no produce advertencia ni lo impide; la referencia queda apuntando a un nodo inactivo.
13. **Historial de modelado sin truncado.** `descripcion_cambio` es `varchar(255)`; [ProcesoServiceImpl][] recorta sus textos, pero [AuditoriaModeladoServiceImpl][] no: nombres de elementos muy largos podrían provocar un 409 de integridad al registrar el cambio. Además, esa bitácora se omite cuando no hay un usuario autenticado en el contexto (por ejemplo, el cargador de datos de demostración escribe directamente en los repositorios).
14. **Pools de empresa extra y mensajes incompletos en borrador.** El borrador admite más de un pool `EMPRESA` y mensajes sin Throw (ruta de compatibilidad); la publicación exige exactamente un pool `EMPRESA` y un Throw por mensaje.
15. **Lane sin prueba dedicada del rechazo.** El rechazo de eliminar una lane con elementos activos (HU-22, criterio 6) está en [LaneServiceImpl][] pero no tiene una prueba propia.

[ActividadController]: ../src/main/java/com/facimus/procesos/modelado/controller/ActividadController.java
[ActividadControllerTest]: ../src/test/java/com/facimus/procesos/modelado/controller/ActividadControllerTest.java
[ActividadRequest]: ../src/main/java/com/facimus/procesos/modelado/controller/dto/ActividadRequest.java
[ActividadServiceImpl]: ../src/main/java/com/facimus/procesos/modelado/service/ActividadServiceImpl.java
[ActividadServiceTest]: ../src/test/java/com/facimus/procesos/modelado/service/ActividadServiceTest.java
[AislamientoEmpresasIntegracionTest]: ../src/test/java/com/facimus/procesos/security/AislamientoEmpresasIntegracionTest.java
[AislamientoTenantTest]: ../src/test/java/com/facimus/procesos/arquitectura/AislamientoTenantTest.java
[ApiExceptionHandler]: ../src/main/java/com/facimus/procesos/common/api/ApiExceptionHandler.java
[ArcoController]: ../src/main/java/com/facimus/procesos/modelado/controller/ArcoController.java
[ArcoEdicionConProxiesLazyIntegracionTest]: ../src/test/java/com/facimus/procesos/transaccionalidad/ArcoEdicionConProxiesLazyIntegracionTest.java
[ArcoRequest]: ../src/main/java/com/facimus/procesos/modelado/controller/dto/ArcoRequest.java
[ArcoServiceImpl]: ../src/main/java/com/facimus/procesos/modelado/service/ArcoServiceImpl.java
[ArcoServiceTest]: ../src/test/java/com/facimus/procesos/modelado/service/ArcoServiceTest.java
[AuditoriaModeladoServiceImpl]: ../src/main/java/com/facimus/procesos/modelado/service/AuditoriaModeladoServiceImpl.java
[AuditoriaModeladoServiceTest]: ../src/test/java/com/facimus/procesos/modelado/service/AuditoriaModeladoServiceTest.java
[AuthController]: ../src/main/java/com/facimus/procesos/gestion/controller/AuthController.java
[AuthControllerTest]: ../src/test/java/com/facimus/procesos/gestion/controller/AuthControllerTest.java
[AutorizacionPorRolTest]: ../src/test/java/com/facimus/procesos/security/AutorizacionPorRolTest.java
[CorrelacionController]: ../src/main/java/com/facimus/procesos/modelado/controller/CorrelacionController.java
[CorrelacionOrquestadorServiceImpl]: ../src/main/java/com/facimus/procesos/modelado/service/CorrelacionOrquestadorServiceImpl.java
[CorrelacionServiceImpl]: ../src/main/java/com/facimus/procesos/modelado/service/CorrelacionServiceImpl.java
[CorrelacionServiceTest]: ../src/test/java/com/facimus/procesos/modelado/service/CorrelacionServiceTest.java
[CreacionProcesoTransaccionalTest]: ../src/test/java/com/facimus/procesos/transaccionalidad/CreacionProcesoTransaccionalTest.java
[CrearUsuarioRequest]: ../src/main/java/com/facimus/procesos/gestion/controller/dto/CrearUsuarioRequest.java
[DatosDemoInitializerTest]: ../src/test/java/com/facimus/procesos/config/DatosDemoInitializerTest.java
[EditarArcoRequest]: ../src/main/java/com/facimus/procesos/modelado/controller/dto/EditarArcoRequest.java
[EditarProcesoRequest]: ../src/main/java/com/facimus/procesos/gestion/controller/dto/EditarProcesoRequest.java
[EmpresaController]: ../src/main/java/com/facimus/procesos/gestion/controller/EmpresaController.java
[EmpresaControllerTest]: ../src/test/java/com/facimus/procesos/gestion/controller/EmpresaControllerTest.java
[EmpresaOrquestadorServiceImpl]: ../src/main/java/com/facimus/procesos/gestion/service/EmpresaOrquestadorServiceImpl.java
[EmpresaOrquestadorServiceTest]: ../src/test/java/com/facimus/procesos/gestion/service/EmpresaOrquestadorServiceTest.java
[EmpresaServiceImpl]: ../src/main/java/com/facimus/procesos/gestion/service/EmpresaServiceImpl.java
[EmpresaServiceTest]: ../src/test/java/com/facimus/procesos/gestion/service/EmpresaServiceTest.java
[EntidadEmpresa]: ../src/main/java/com/facimus/procesos/common/EntidadEmpresa.java
[EventoMensajeController]: ../src/main/java/com/facimus/procesos/modelado/controller/EventoMensajeController.java
[EventoMensajeRequest]: ../src/main/java/com/facimus/procesos/modelado/controller/dto/EventoMensajeRequest.java
[EventoMensajeServiceImpl]: ../src/main/java/com/facimus/procesos/modelado/service/EventoMensajeServiceImpl.java
[EventoMensajeServiceTest]: ../src/test/java/com/facimus/procesos/modelado/service/EventoMensajeServiceTest.java
[GatewayController]: ../src/main/java/com/facimus/procesos/modelado/controller/GatewayController.java
[GatewayRequest]: ../src/main/java/com/facimus/procesos/modelado/controller/dto/GatewayRequest.java
[GatewayServiceImpl]: ../src/main/java/com/facimus/procesos/modelado/service/GatewayServiceImpl.java
[GatewayServiceTest]: ../src/test/java/com/facimus/procesos/modelado/service/GatewayServiceTest.java
[HistorialCambioServiceImpl]: ../src/main/java/com/facimus/procesos/gestion/service/HistorialCambioServiceImpl.java
[InvariantesDirectasEnServicioTest]: ../src/test/java/com/facimus/procesos/transaccionalidad/InvariantesDirectasEnServicioTest.java
[InvariantesModeladoIntegracionTest]: ../src/test/java/com/facimus/procesos/transaccionalidad/InvariantesModeladoIntegracionTest.java
[InvitacionUsuarioController]: ../src/main/java/com/facimus/procesos/gestion/controller/InvitacionUsuarioController.java
[InvitacionUsuarioControllerTest]: ../src/test/java/com/facimus/procesos/gestion/controller/InvitacionUsuarioControllerTest.java
[InvitacionUsuarioRequest]: ../src/main/java/com/facimus/procesos/gestion/controller/dto/InvitacionUsuarioRequest.java
[InvitacionUsuarioResponse]: ../src/main/java/com/facimus/procesos/gestion/controller/dto/InvitacionUsuarioResponse.java
[InvitacionUsuarioServiceImpl]: ../src/main/java/com/facimus/procesos/gestion/service/InvitacionUsuarioServiceImpl.java
[InvitacionUsuarioServiceTest]: ../src/test/java/com/facimus/procesos/gestion/service/InvitacionUsuarioServiceTest.java
[JwtAuthenticationFilter]: ../src/main/java/com/facimus/procesos/security/JwtAuthenticationFilter.java
[JwtService]: ../src/main/java/com/facimus/procesos/security/JwtService.java
[Lane]: ../src/main/java/com/facimus/procesos/modelado/model/Lane.java
[LaneController]: ../src/main/java/com/facimus/procesos/modelado/controller/LaneController.java
[LaneControllerTest]: ../src/test/java/com/facimus/procesos/modelado/controller/LaneControllerTest.java
[LaneOrquestadorServiceImpl]: ../src/main/java/com/facimus/procesos/modelado/service/LaneOrquestadorServiceImpl.java
[LaneOrquestadorServiceTest]: ../src/test/java/com/facimus/procesos/modelado/service/LaneOrquestadorServiceTest.java
[LaneRequest]: ../src/main/java/com/facimus/procesos/modelado/controller/dto/LaneRequest.java
[LaneServiceImpl]: ../src/main/java/com/facimus/procesos/modelado/service/LaneServiceImpl.java
[LaneServiceTest]: ../src/test/java/com/facimus/procesos/modelado/service/LaneServiceTest.java
[LoginRequest]: ../src/main/java/com/facimus/procesos/gestion/controller/dto/LoginRequest.java
[MensajeController]: ../src/main/java/com/facimus/procesos/modelado/controller/MensajeController.java
[MensajeControllerTest]: ../src/test/java/com/facimus/procesos/modelado/controller/MensajeControllerTest.java
[MensajeServiceImpl]: ../src/main/java/com/facimus/procesos/modelado/service/MensajeServiceImpl.java
[MensajeServiceMapeoTest]: ../src/test/java/com/facimus/procesos/modelado/service/MensajeServiceMapeoTest.java
[MensajeServiceTest]: ../src/test/java/com/facimus/procesos/modelado/service/MensajeServiceTest.java
[MigracionesTest]: ../src/test/java/com/facimus/procesos/migraciones/MigracionesTest.java
[ModeladoBrechasIntegracionTest]: ../src/test/java/com/facimus/procesos/modelado/ModeladoBrechasIntegracionTest.java
[MultitenenciaTest]: ../src/test/java/com/facimus/procesos/arquitectura/MultitenenciaTest.java
[PermisoEstructura]: ../src/main/java/com/facimus/procesos/gestion/model/PermisoEstructura.java
[PermisoEstructuraController]: ../src/main/java/com/facimus/procesos/gestion/controller/PermisoEstructuraController.java
[PermisoEstructuraControllerTest]: ../src/test/java/com/facimus/procesos/gestion/controller/PermisoEstructuraControllerTest.java
[PermisoEstructuraIntegracionTest]: ../src/test/java/com/facimus/procesos/security/PermisoEstructuraIntegracionTest.java
[PermisoEstructuraServiceImpl]: ../src/main/java/com/facimus/procesos/gestion/service/PermisoEstructuraServiceImpl.java
[PermisoEstructuraServiceTest]: ../src/test/java/com/facimus/procesos/gestion/service/PermisoEstructuraServiceTest.java
[PoliticaFalloNotificacion]: ../src/main/java/com/facimus/procesos/modelado/model/PoliticaFalloNotificacion.java
[PoliticaMensajeSinCaso]: ../src/main/java/com/facimus/procesos/modelado/model/PoliticaMensajeSinCaso.java
[Pool]: ../src/main/java/com/facimus/procesos/modelado/model/Pool.java
[PoolController]: ../src/main/java/com/facimus/procesos/modelado/controller/PoolController.java
[PoolControllerTest]: ../src/test/java/com/facimus/procesos/modelado/controller/PoolControllerTest.java
[PoolOrquestadorServiceImpl]: ../src/main/java/com/facimus/procesos/modelado/service/PoolOrquestadorServiceImpl.java
[PoolOrquestadorServiceTest]: ../src/test/java/com/facimus/procesos/modelado/service/PoolOrquestadorServiceTest.java
[PoolServiceImpl]: ../src/main/java/com/facimus/procesos/modelado/service/PoolServiceImpl.java
[PoolServiceTest]: ../src/test/java/com/facimus/procesos/modelado/service/PoolServiceTest.java
[Proceso]: ../src/main/java/com/facimus/procesos/gestion/model/Proceso.java
[ProcesoCompartidoController]: ../src/main/java/com/facimus/procesos/gestion/controller/ProcesoCompartidoController.java
[ProcesoCompartidoControllerTest]: ../src/test/java/com/facimus/procesos/gestion/controller/ProcesoCompartidoControllerTest.java
[ProcesoCompartidoServiceImpl]: ../src/main/java/com/facimus/procesos/gestion/service/ProcesoCompartidoServiceImpl.java
[ProcesoCompartidoServiceTest]: ../src/test/java/com/facimus/procesos/gestion/service/ProcesoCompartidoServiceTest.java
[ProcesoController]: ../src/main/java/com/facimus/procesos/gestion/controller/ProcesoController.java
[ProcesoControllerTest]: ../src/test/java/com/facimus/procesos/gestion/controller/ProcesoControllerTest.java
[ProcesoDiagramaServiceImpl]: ../src/main/java/com/facimus/procesos/gestion/service/ProcesoDiagramaServiceImpl.java
[ProcesoDiagramaServiceTest]: ../src/test/java/com/facimus/procesos/gestion/service/ProcesoDiagramaServiceTest.java
[ProcesoOrquestadorServiceImpl]: ../src/main/java/com/facimus/procesos/gestion/service/ProcesoOrquestadorServiceImpl.java
[ProcesoOrquestadorServiceTest]: ../src/test/java/com/facimus/procesos/gestion/service/ProcesoOrquestadorServiceTest.java
[ProcesoRepositoryTest]: ../src/test/java/com/facimus/procesos/gestion/repository/ProcesoRepositoryTest.java
[ProcesoRequest]: ../src/main/java/com/facimus/procesos/gestion/controller/dto/ProcesoRequest.java
[ProcesoServiceImpl]: ../src/main/java/com/facimus/procesos/gestion/service/ProcesoServiceImpl.java
[ProcesoServiceTest]: ../src/test/java/com/facimus/procesos/gestion/service/ProcesoServiceTest.java
[RegistroEmpresaRequest]: ../src/main/java/com/facimus/procesos/gestion/controller/dto/RegistroEmpresaRequest.java
[RegistroEmpresaTransaccionalTest]: ../src/test/java/com/facimus/procesos/transaccionalidad/RegistroEmpresaTransaccionalTest.java
[ReglasMovimientoNodo]: ../src/main/java/com/facimus/procesos/modelado/service/ReglasMovimientoNodo.java
[RepositorioTenant]: ../src/main/java/com/facimus/procesos/common/RepositorioTenant.java
[RepositorioTenantTest]: ../src/test/java/com/facimus/procesos/arquitectura/RepositorioTenantTest.java
[RespuestasSegurasTest]: ../src/test/java/com/facimus/procesos/security/RespuestasSegurasTest.java
[RolAcceso]: ../src/main/java/com/facimus/procesos/gestion/model/RolAcceso.java
[RolProceso]: ../src/main/java/com/facimus/procesos/gestion/model/RolProceso.java
[RolProcesoController]: ../src/main/java/com/facimus/procesos/gestion/controller/RolProcesoController.java
[RolProcesoControllerTest]: ../src/test/java/com/facimus/procesos/gestion/controller/RolProcesoControllerTest.java
[RolProcesoRequest]: ../src/main/java/com/facimus/procesos/gestion/controller/dto/RolProcesoRequest.java
[RolProcesoServiceImpl]: ../src/main/java/com/facimus/procesos/gestion/service/RolProcesoServiceImpl.java
[RolProcesoServiceTest]: ../src/test/java/com/facimus/procesos/gestion/service/RolProcesoServiceTest.java
[RolProcesoVistaResponse]: ../src/main/java/com/facimus/procesos/gestion/controller/dto/RolProcesoVistaResponse.java
[SecurityConfig]: ../src/main/java/com/facimus/procesos/security/SecurityConfig.java
[SeguridadIntegracionTest]: ../src/test/java/com/facimus/procesos/security/SeguridadIntegracionTest.java
[TipoActividad]: ../src/main/java/com/facimus/procesos/modelado/model/TipoActividad.java
[TipoDestinoExterno]: ../src/main/java/com/facimus/procesos/modelado/model/TipoDestinoExterno.java
[TipoEventoMensaje]: ../src/main/java/com/facimus/procesos/modelado/model/TipoEventoMensaje.java
[TipoGateway]: ../src/main/java/com/facimus/procesos/modelado/model/TipoGateway.java
[TipoParticipante]: ../src/main/java/com/facimus/procesos/modelado/model/TipoParticipante.java
[UsuarioController]: ../src/main/java/com/facimus/procesos/gestion/controller/UsuarioController.java
[UsuarioControllerTest]: ../src/test/java/com/facimus/procesos/gestion/controller/UsuarioControllerTest.java
[UsuarioServiceImpl]: ../src/main/java/com/facimus/procesos/gestion/service/UsuarioServiceImpl.java
[UsuarioServiceTest]: ../src/test/java/com/facimus/procesos/gestion/service/UsuarioServiceTest.java
[V1__esquema_inicial]: ../src/main/resources/db/migration/common/V1__esquema_inicial.sql
[V2__indices_por_empresa]: ../src/main/resources/db/migration/common/V2__indices_por_empresa.sql
[V3__unicidad_entre_activos]: ../src/main/resources/db/migration/postgresql/V3__unicidad_entre_activos.sql
[V5__eventos_mensaje_actividades]: ../src/main/resources/db/migration/common/V5__eventos_mensaje_actividades.sql
[ValidacionModeloServiceImpl]: ../src/main/java/com/facimus/procesos/modelado/service/ValidacionModeloServiceImpl.java
[ValidacionModeloServiceTest]: ../src/test/java/com/facimus/procesos/modelado/service/ValidacionModeloServiceTest.java
[ValidacionRequestsTest]: ../src/test/java/com/facimus/procesos/gestion/controller/ValidacionRequestsTest.java

