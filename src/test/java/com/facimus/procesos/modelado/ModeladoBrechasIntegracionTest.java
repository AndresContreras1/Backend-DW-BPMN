package com.facimus.procesos.modelado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.gestion.controller.dto.RolProcesoConsultaResponse;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.modelado.controller.dto.EventoMensajeRequest;
import com.facimus.procesos.modelado.controller.dto.EventoMensajeResponse;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.EventoMensaje;
import com.facimus.procesos.modelado.model.Gateway;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.Mensaje;
import com.facimus.procesos.modelado.model.PoliticaFalloNotificacion;
import com.facimus.procesos.modelado.model.PoliticaMensajeSinCaso;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoDestinoExterno;
import com.facimus.procesos.modelado.model.TipoEventoMensaje;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.service.ActividadService;
import com.facimus.procesos.modelado.service.ArcoService;
import com.facimus.procesos.modelado.service.CorrelacionOrquestadorService;
import com.facimus.procesos.modelado.service.EventoMensajeService;
import com.facimus.procesos.modelado.service.GatewayService;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.MensajeService;
import com.facimus.procesos.modelado.service.NodoFlujoService;
import com.facimus.procesos.modelado.service.PoolService;
import com.facimus.procesos.modelado.service.ValidacionModeloService;

/**
 * Integracion real (contexto Spring + H2) de las brechas de las historias de usuario: todo se crea
 * por los services, sin mocks, para comprobar tambien el mapeo JPA y las transacciones.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:modelado-brechas-it;DB_CLOSE_DELAY=-1")
class ModeladoBrechasIntegracionTest {

    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    @Autowired private EmpresaService empresaService;
    @Autowired private UsuarioService usuarioService;
    @Autowired private ProcesoService procesoService;
    @Autowired private RolProcesoService rolProcesoService;
    @Autowired private PoolService poolService;
    @Autowired private LaneService laneService;
    @Autowired private ActividadService actividadService;
    @Autowired private GatewayService gatewayService;
    @Autowired private ArcoService arcoService;
    @Autowired private EventoMensajeService eventoMensajeService;
    @Autowired private MensajeService mensajeService;
    @Autowired private CorrelacionOrquestadorService correlacionOrquestadorService;
    @Autowired private ValidacionModeloService validacionModeloService;
    // Escritura directa, sin reglas de negocio: sirve para forzar un estado incoherente (EventoMensajeService lo impide).
    @Autowired private NodoFlujoService nodoFlujoService;

    /** Empresa + admin + proceso con un pool EMPRESA y otro CLIENTE, cada uno con una lane. */
    private Escenario escenario() {
        int n = SECUENCIA.incrementAndGet();
        Escenario e = new Escenario();
        e.sufijo = String.valueOf(n);
        e.empresaId = empresaService.registrar("Empresa Brechas " + n, "9007000" + n, "brechas" + n + "@demo.com")
                .getId();
        e.adminId = usuarioService.crearColaborador(e.empresaId, "Admin " + n, "admin" + n + "@brechas.com",
                "clave12345", RolAcceso.ADMINISTRADOR).getId();
        e.procesoId = procesoService.crear(e.empresaId, e.adminId, "Proceso " + n, "desc", "cat").getId();
        e.rol = rolProcesoService.crear(e.empresaId, "Responsable " + n, "Quien responde");
        e.poolEmpresa = poolService.crear(e.empresaId, e.procesoId, "Mi empresa", TipoParticipante.EMPRESA, false);
        e.poolCliente = poolService.crear(e.empresaId, e.procesoId, "Cliente", TipoParticipante.CLIENTE, false);
        e.laneEmpresa = laneService.crear(e.empresaId, e.poolEmpresa.getId(), "Ventas", e.rol);
        e.laneCliente = laneService.crear(e.empresaId, e.poolCliente.getId(), "Compras", e.rol);
        return e;
    }

    private EventoMensaje evento(Escenario e, Lane lane, String nombre, TipoEventoMensaje tipo) {
        return eventoMensajeService.crear(e.empresaId, lane.getId(), nombre, tipo, "contenido", "pedidoId", 0, 0,
                false);
    }

    private Mensaje mensajeOrden(Escenario e, EventoMensaje lanzar, EventoMensaje recibir) {
        return mensajeService.crear(e.empresaId, e.procesoId, "Orden", "contenido", e.poolEmpresa.getId(),
                e.poolCliente.getId(), lanzar.getId(), recibir.getId(), null, null, null, null, null,
                PoliticaMensajeSinCaso.DESCARTAR);
    }

    @Test
    @DisplayName("Un Message Throw y un Message Catch con el mismo nombre coexisten, se vinculan y el proceso se publica")
    void Mensajeria_throwYCatchConElMismoNombre_seVinculanYSePublica() {
        Escenario e = escenario();
        EventoMensaje lanzar = evento(e, e.laneEmpresa, "Orden", TipoEventoMensaje.THROW);
        EventoMensaje recibir = evento(e, e.laneCliente, "Orden", TipoEventoMensaje.CATCH_INTERMEDIO);

        Mensaje mensaje = mensajeOrden(e, lanzar, recibir);

        assertThat(mensaje.getId()).isNotNull();
        assertThat(mensaje.getClaveCorrelacion()).isEqualTo("pedidoId");
        assertThat(validacionModeloService.obtenerAdvertencias(e.empresaId, e.procesoId)).isEmpty();
        assertThat(procesoService.publicar(e.empresaId, e.procesoId, e.adminId).getEstado())
                .isEqualTo(EstadoProceso.PUBLICADO);
    }

    @Test
    void Mensajeria_dosEventosDelMismoLadoConElMismoNombre_seRechaza() {
        Escenario e = escenario();
        evento(e, e.laneCliente, "Orden", TipoEventoMensaje.CATCH_INTERMEDIO);

        assertThatThrownBy(() -> evento(e, e.laneCliente, "orden", TipoEventoMensaje.CATCH_INICIO))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Ya existe un nodo");
        assertThatThrownBy(() -> actividadService.crear(e.empresaId, e.laneEmpresa.getId(), "ORDEN", "d", 0, 0))
                .isInstanceOf(ReglaNegocioException.class);
        // El lado contrario (Throw) si puede llamarse igual, pero entonces una actividad ya no puede.
        evento(e, e.laneEmpresa, "Orden", TipoEventoMensaje.THROW);
        assertThatThrownBy(() -> actividadService.crear(e.empresaId, e.laneEmpresa.getId(), "Orden", "d", 0, 0))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void Validacion_procesoIncompleto_devuelveTodasLasAdvertenciasYBloqueaLaPublicacion() {
        Escenario e = escenario();
        poolService.crear(e.empresaId, e.procesoId, "Otra empresa", TipoParticipante.EMPRESA, false);
        evento(e, e.laneCliente, "Recibo", TipoEventoMensaje.CATCH_INTERMEDIO);
        gatewayService.crear(e.empresaId, e.laneEmpresa.getId(), "Decide", TipoGateway.EXCLUSIVO, 0, 0);

        List<String> advertencias = validacionModeloService.obtenerAdvertencias(e.empresaId, e.procesoId);

        assertThat(advertencias).anyMatch(a -> a.contains("exactamente un pool de tipo EMPRESA"));
        assertThat(advertencias).anyMatch(a -> a.contains("Recibo") && a.contains("origen externo"));
        assertThat(advertencias).anyMatch(a -> a.contains("Decide") && a.contains("dos salidas"));
        assertThat(validacionModeloService.consultarValidacion(e.empresaId, e.procesoId).valido()).isFalse();
        assertThatThrownBy(() -> procesoService.publicar(e.empresaId, e.procesoId, e.adminId))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Recibo")
                .hasMessageContaining("Decide");
    }

    @Test
    void Arcos_condicion_soloSalenConCondicionLosGatewaysExclusivosEInclusivos() {
        Escenario e = escenario();
        Long lane = e.laneEmpresa.getId();
        Actividad a = actividadService.crear(e.empresaId, lane, "A", "d", 0, 0);
        Actividad b = actividadService.crear(e.empresaId, lane, "B", "d", 0, 0);
        Actividad c = actividadService.crear(e.empresaId, lane, "C", "d", 0, 0);
        Gateway exclusivo = gatewayService.crear(e.empresaId, lane, "Decide", TipoGateway.EXCLUSIVO, 0, 0);
        Gateway paralelo = gatewayService.crear(e.empresaId, lane, "Reparte", TipoGateway.PARALELO, 0, 0);

        assertThatThrownBy(() -> arcoService.crear(e.empresaId, exclusivo.getId(), a.getId(), null, null))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("requiere condicion");
        assertThatThrownBy(() -> arcoService.crear(e.empresaId, paralelo.getId(), a.getId(), null, "x > 1"))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no la admite");
        assertThatThrownBy(() -> arcoService.crear(e.empresaId, a.getId(), b.getId(), null, "x > 1"))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no la admite");

        assertThat(arcoService.crear(e.empresaId, exclusivo.getId(), a.getId(), "si", "x > 1").getCondicion())
                .isEqualTo("x > 1");
        assertThat(arcoService.crear(e.empresaId, paralelo.getId(), a.getId(), null, null).getCondicion()).isNull();

        // Gateway de convergencia (2 arcos entrantes): su salida no necesita condicion.
        Gateway union = gatewayService.crear(e.empresaId, lane, "Une", TipoGateway.EXCLUSIVO, 0, 0);
        arcoService.crear(e.empresaId, b.getId(), union.getId(), null, null);
        arcoService.crear(e.empresaId, c.getId(), union.getId(), null, null);
        assertThat(arcoService.crear(e.empresaId, union.getId(), paralelo.getId(), null, null).getCondicion())
                .isNull();
    }

    @Test
    void Gateway_editarAParalelo_limpiaLasCondicionesDeSusSalidas() {
        Escenario e = escenario();
        Long lane = e.laneEmpresa.getId();
        Actividad a = actividadService.crear(e.empresaId, lane, "A", "d", 0, 0);
        Gateway g = gatewayService.crear(e.empresaId, lane, "Decide", TipoGateway.EXCLUSIVO, 0, 0);
        var arco = arcoService.crear(e.empresaId, g.getId(), a.getId(), "si", "x > 1");

        gatewayService.editar(e.empresaId, g.getId(), "Decide", TipoGateway.PARALELO, 0, 0);

        assertThat(arcoService.obtener(e.empresaId, arco.getId()).getCondicion()).isNull();
    }

    @Test
    void Gateway_editarConLane_mueveDentroDelPoolYRechazaOtroPoolConArcos() {
        Escenario e = escenario();
        Lane segunda = laneService.crear(e.empresaId, e.poolEmpresa.getId(), "Soporte", e.rol);
        Actividad a = actividadService.crear(e.empresaId, e.laneEmpresa.getId(), "A", "d", 0, 0);
        Gateway g = gatewayService.crear(e.empresaId, e.laneEmpresa.getId(), "Reparte", TipoGateway.PARALELO, 0, 0);

        assertThat(gatewayService.editar(e.empresaId, g.getId(), "Reparte", TipoGateway.PARALELO, 0, 0,
                segunda.getId()).getLane().getId()).isEqualTo(segunda.getId());

        arcoService.crear(e.empresaId, g.getId(), a.getId(), null, null);
        assertThatThrownBy(() -> gatewayService.editar(e.empresaId, g.getId(), "Reparte", TipoGateway.PARALELO, 0,
                0, e.laneCliente.getId())).isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("otro pool");
    }

    @Test
    void Validacion_claveDelCatchIncoherenteConElMensaje_seAdvierte() {
        Escenario e = escenario();
        EventoMensaje lanzar = evento(e, e.laneEmpresa, "Orden", TipoEventoMensaje.THROW);
        EventoMensaje recibir = evento(e, e.laneCliente, "Orden", TipoEventoMensaje.CATCH_INTERMEDIO);
        mensajeOrden(e, lanzar, recibir);

        // Cambiar la clave del Catch hace incoherente el trio Mensaje/Throw/Catch.
        recibir.setClaveCorrelacion("otraClave");
        nodoFlujoService.guardar(recibir);

        assertThat(validacionModeloService.obtenerAdvertencias(e.empresaId, e.procesoId))
                .anyMatch(a -> a.contains("no coincide"));
    }

    @Test
    void Correlacion_definirEnProcesoInactivo_seRechaza() {
        Escenario e = escenario();
        EventoMensaje lanzar = evento(e, e.laneEmpresa, "Orden", TipoEventoMensaje.THROW);
        EventoMensaje recibir = evento(e, e.laneCliente, "Orden", TipoEventoMensaje.CATCH_INTERMEDIO);
        Mensaje mensaje = mensajeOrden(e, lanzar, recibir);
        procesoService.eliminarLogico(e.empresaId, e.procesoId, e.adminId);

        assertThatThrownBy(() -> correlacionOrquestadorService.definir(e.empresaId, mensaje.getId(), "pedidoId"))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("inactivo");
        assertThatThrownBy(() -> correlacionOrquestadorService.eliminar(e.empresaId, mensaje.getId()))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("inactivo");
    }

    @Test
    void EventoMensaje_vinculadoAUnMensaje_noSePuedeRenombrarNiEliminar_yElImpactoLoAvisa() {
        Escenario e = escenario();
        EventoMensaje lanzar = evento(e, e.laneEmpresa, "Orden", TipoEventoMensaje.THROW);
        EventoMensaje recibir = evento(e, e.laneCliente, "Orden", TipoEventoMensaje.CATCH_INTERMEDIO);
        Mensaje mensaje = mensajeOrden(e, lanzar, recibir);

        assertThatThrownBy(() -> eventoMensajeService.editar(e.empresaId, recibir.getId(), "Pedido",
                TipoEventoMensaje.CATCH_INTERMEDIO, "contenido", "pedidoId", 0, 0, false))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("\"Orden\"");
        assertThatThrownBy(() -> eventoMensajeService.editar(e.empresaId, recibir.getId(), "Orden",
                TipoEventoMensaje.CATCH_INTERMEDIO, "contenido", "otra", 0, 0, false))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> eventoMensajeService.eliminar(e.empresaId, recibir.getId()))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("lo usan los mensajes");
        assertThat(eventoMensajeService.evaluarImpactoEliminacion(e.empresaId, recibir.getId()).advertencias())
                .anyMatch(a -> a.contains("vinculado a los mensajes"));

        // Un cambio compatible (contenido y posicion) si se admite.
        assertThat(eventoMensajeService.editar(e.empresaId, recibir.getId(), "Orden",
                TipoEventoMensaje.CATCH_INICIO, "otro contenido", "pedidoId", 40, 50, false).getContenido())
                .isEqualTo("otro contenido");

        mensajeService.eliminar(e.empresaId, mensaje.getId());
        eventoMensajeService.eliminar(e.empresaId, recibir.getId());
        assertThat(eventoMensajeService.listarPorLane(e.empresaId, e.laneCliente.getId())).isEmpty();
    }

    @Test
    void EventoMensaje_actividadIds_seGuardanSoloEnCatchYSoloActivasDelMismoProceso() {
        Escenario e = escenario();
        Actividad usa = actividadService.crear(e.empresaId, e.laneCliente.getId(), "Procesar pedido", "d", 0, 0);
        Actividad otra = actividadService.crear(e.empresaId, e.laneCliente.getId(), "Archivar", "d", 0, 0);

        EventoMensajeResponse creado = eventoMensajeService.crear(e.empresaId, e.laneCliente.getId(),
                new EventoMensajeRequest("Recibo", TipoEventoMensaje.CATCH_INTERMEDIO, "c", "pedidoId", 0, 0, true,
                        null, List.of(usa.getId())));
        assertThat(creado.actividadIds()).containsExactly(usa.getId());
        assertThat(eventoMensajeService.consultarDetalle(e.empresaId, creado.id()).actividadIds())
                .containsExactly(usa.getId());

        EventoMensajeResponse editado = eventoMensajeService.editar(e.empresaId, creado.id(),
                new EventoMensajeRequest("Recibo", TipoEventoMensaje.CATCH_INTERMEDIO, "c", "pedidoId", 0, 0, true,
                        null, List.of(usa.getId(), otra.getId())));
        assertThat(editado.actividadIds()).containsExactlyInAnyOrder(usa.getId(), otra.getId());

        // null = no cambiar; lista vacia = quitar todas.
        assertThat(eventoMensajeService.editar(e.empresaId, creado.id(),
                new EventoMensajeRequest("Recibo", TipoEventoMensaje.CATCH_INTERMEDIO, "c", "pedidoId", 0, 0, true))
                .actividadIds()).hasSize(2);
        assertThat(eventoMensajeService.editar(e.empresaId, creado.id(),
                new EventoMensajeRequest("Recibo", TipoEventoMensaje.CATCH_INTERMEDIO, "c", "pedidoId", 0, 0, true,
                        null, List.of())).actividadIds()).isEmpty();

        // Una actividad dada de baja deja de aparecer.
        eventoMensajeService.editar(e.empresaId, creado.id(),
                new EventoMensajeRequest("Recibo", TipoEventoMensaje.CATCH_INTERMEDIO, "c", "pedidoId", 0, 0, true,
                        null, List.of(usa.getId())));
        actividadService.eliminar(e.empresaId, usa.getId());
        assertThat(eventoMensajeService.consultarDetalle(e.empresaId, creado.id()).actividadIds()).isEmpty();

        assertThatThrownBy(() -> eventoMensajeService.crear(e.empresaId, e.laneEmpresa.getId(),
                new EventoMensajeRequest("Envio", TipoEventoMensaje.THROW, "c", "pedidoId", 0, 0, false, null,
                        List.of(otra.getId())))).isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("Message Catch");

        Escenario ajeno = escenario();
        Actividad deOtroProceso = actividadService.crear(ajeno.empresaId, ajeno.laneEmpresa.getId(), "Ajena", "d",
                0, 0);
        assertThatThrownBy(() -> eventoMensajeService.crear(e.empresaId, e.laneCliente.getId(),
                new EventoMensajeRequest("Recibo 2", TipoEventoMensaje.CATCH_INTERMEDIO, "c", "pedidoId", 0, 0,
                        true, null, List.of(deOtroProceso.getId()))))
                .hasMessageContaining("Actividad no encontrada");
    }

    @Test
    void Pool_destinoExternoDeMensajesActivos_noCambiaDeTipoNiDeCajaNegra() {
        Escenario e = escenario();
        Pool externo = poolService.crear(e.empresaId, e.procesoId, "ERP", TipoParticipante.SISTEMA_EXTERNO, true);
        EventoMensaje lanzar = evento(e, e.laneEmpresa, "Factura", TipoEventoMensaje.THROW);
        Mensaje mensaje = mensajeService.crear(e.empresaId, e.procesoId, "Factura", "c", e.poolEmpresa.getId(),
                externo.getId(), lanzar.getId(), null, null, TipoDestinoExterno.SERVICIO_WEB, "API de facturacion",
                PoliticaFalloNotificacion.CONTINUAR_FLUJO, null, PoliticaMensajeSinCaso.DESCARTAR);

        assertThatThrownBy(() -> poolService.editar(e.empresaId, externo.getId(), "ERP",
                TipoParticipante.PROVEEDOR, true)).isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("Factura");
        assertThatThrownBy(() -> poolService.editar(e.empresaId, externo.getId(), "ERP",
                TipoParticipante.SISTEMA_EXTERNO, false)).isInstanceOf(ReglaNegocioException.class);
        // Renombrarlo, sin tocar tipo ni caja negra, sigue permitido.
        assertThat(poolService.editar(e.empresaId, externo.getId(), "ERP central", TipoParticipante.SISTEMA_EXTERNO,
                true).getNombre()).isEqualTo("ERP central");

        mensajeService.eliminar(e.empresaId, mensaje.getId());
        assertThat(poolService.editar(e.empresaId, externo.getId(), "ERP central", TipoParticipante.PROVEEDOR, true)
                .getTipoParticipante()).isEqualTo(TipoParticipante.PROVEEDOR);
    }

    @Test
    void RolProceso_renombrar_actualizaLasLanes_yLasDeProcesosInactivosNoBloqueanLaBaja() {
        Escenario e = escenario();
        RolProceso rol = e.rol;

        rolProcesoService.editar(e.empresaId, rol.getId(), "  Analista Senior  ", "desc");

        assertThat(laneService.obtener(e.empresaId, e.laneEmpresa.getId()).getNombre()).isEqualTo("Analista Senior");
        assertThat(laneService.obtener(e.empresaId, e.laneCliente.getId()).getNombre()).isEqualTo("Analista Senior");
        assertThat(rolProcesoService.obtener(e.empresaId, rol.getId()).getNombre()).isEqualTo("Analista Senior");

        assertThatThrownBy(() -> rolProcesoService.eliminar(e.empresaId, rol.getId()))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Proceso ");

        procesoService.eliminarLogico(e.empresaId, e.procesoId, e.adminId);
        rolProcesoService.eliminar(e.empresaId, rol.getId());
        assertThat(rolProcesoService.consultarConUso(e.empresaId)).noneMatch(r -> r.id().equals(rol.getId()));
    }

    @Test
    void RolProceso_consultaPaginada_agrupaLosProcesosPorRolYOrdenaPorNombre() {
        Escenario e = escenario();
        RolProceso otro = rolProcesoService.crear(e.empresaId, "Aaa primero", "d");
        rolProcesoService.crear(e.empresaId, "zzz ultimo", "d");

        var pagina = rolProcesoService.consultarPagina(e.empresaId, null, 0, 2);

        assertThat(pagina.size()).isEqualTo(2);
        assertThat(pagina.totalElements()).isEqualTo(3);
        assertThat(pagina.content()).extracting(RolProcesoConsultaResponse::nombre)
                .containsExactly("Aaa primero", e.rol.getNombre());
        assertThat(pagina.content().get(0).enUso()).isFalse();
        assertThat(pagina.content().get(1).procesos()).containsExactly("Proceso " + e.sufijo);
        assertThat(pagina.content().get(1).enUso()).isTrue();
        assertThat(otro.getId()).isNotNull();
    }

    private static final class Escenario {
        String sufijo;
        Long empresaId;
        Long adminId;
        Long procesoId;
        RolProceso rol;
        Pool poolEmpresa;
        Pool poolCliente;
        Lane laneEmpresa;
        Lane laneCliente;
    }
}
