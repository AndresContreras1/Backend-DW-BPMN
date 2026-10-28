package com.facimus.procesos.modelado.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.Arco;
import com.facimus.procesos.modelado.model.EventoMensaje;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoEventoMensaje;

@ExtendWith(MockitoExtension.class)
class EventoMensajeServiceTest {

    @Mock
    private NodoFlujoService nodoFlujoService;
    @Mock
    private LaneService laneService;
    @Mock
    private ArcoService arcoService;
    @Mock
    private ActividadService actividadService;
    @Mock
    private MensajeService mensajeService;
    @Mock
    private AuditoriaModeladoService auditoriaModeladoService;

    @InjectMocks
    private EventoMensajeServiceImpl eventoMensajeService;

    @Test
    void crear_evento_guarda_y_audita() {
        Empresa empresa = new Empresa();
        empresa.setId(1L);
        Proceso proceso = new Proceso();
        proceso.setId(10L);
        Pool pool = new Pool();
        pool.setId(3L);
        pool.setProceso(proceso);
        pool.setCajaNegra(false);
        Lane lane = new Lane();
        lane.setId(2L);
        lane.setEmpresa(empresa);
        lane.setPool(pool);

        when(laneService.obtener(1L, 2L)).thenReturn(lane);
        when(nodoFlujoService.guardar(any())).thenAnswer(inv -> {
            EventoMensaje e = inv.getArgument(0);
            e.setId(5L);
            return e;
        });

        EventoMensaje resultado = eventoMensajeService.crear(
                1L, 2L, "Orden", TipoEventoMensaje.THROW,
                "payload", "pedidoId", 10, 20, false);

        assertEquals(5L, resultado.getId());
        assertEquals(TipoEventoMensaje.THROW, resultado.getTipoEvento());
        verify(auditoriaModeladoService).registrar(eq(proceso), contains("Evento de mensaje creado"));
    }

    @Test
    void crear_en_pool_caja_negra_falla() {
        Proceso proceso = new Proceso();
        proceso.setId(10L);
        Pool pool = new Pool();
        pool.setCajaNegra(true);
        pool.setProceso(proceso);
        Lane lane = new Lane();
        lane.setPool(pool);

        when(laneService.obtener(1L, 2L)).thenReturn(lane);

        assertThrows(com.facimus.procesos.common.ReglaNegocioException.class, () ->
                eventoMensajeService.crear(1L, 2L, "Orden", TipoEventoMensaje.THROW,
                        "payload", "pedidoId", 0, 0, false));
    }

    @Test
    void eliminar_desactiva_evento_y_arcos() {
        Empresa empresa = new Empresa();
        empresa.setId(1L);
        Proceso proceso = new Proceso();
        proceso.setId(10L);
        Pool pool = new Pool();
        pool.setProceso(proceso);
        Lane lane = new Lane();
        lane.setPool(pool);

        EventoMensaje evento = new EventoMensaje();
        evento.setId(5L);
        evento.setNombre("Orden");
        evento.setEmpresa(empresa);
        evento.setLane(lane);
        evento.setActivo(true);

        when(nodoFlujoService.buscar(1L, 5L)).thenReturn(Optional.of(evento));

        eventoMensajeService.eliminar(1L, 5L);

        assertFalse(evento.isActivo());
        verify(arcoService).desactivarPorNodo(1L, 5L);
        verify(nodoFlujoService).guardar(evento);
    }

    @Test
    void listar_por_lane_solo_retorna_eventos_activos() {
        EventoMensaje activo = new EventoMensaje();
        activo.setActivo(true);
        Actividad actividad = new Actividad();
        actividad.setActivo(true);
        EventoMensaje inactivo = new EventoMensaje();
        inactivo.setActivo(false);

        Lane lane = new Lane();
        when(laneService.obtener(1L, 2L)).thenReturn(lane);
        when(nodoFlujoService.listarPorLane(1L, 2L))
                .thenReturn(List.of(activo, actividad, inactivo));

        List<EventoMensaje> resultado = eventoMensajeService.listarPorLane(1L, 2L);

        assertEquals(1, resultado.size());
        assertSame(activo, resultado.get(0));
    }


    @Test
    void arco_inactivo_no_bloquea_catch_inicio() {
        Empresa empresa = new Empresa();
        empresa.setId(1L);
        Proceso proceso = new Proceso();
        proceso.setId(10L);
        Pool pool = new Pool();
        pool.setProceso(proceso);
        Lane lane = new Lane();
        lane.setPool(pool);

        EventoMensaje evento = new EventoMensaje();
        evento.setId(5L);
        evento.setNombre("Recepcion");
        evento.setEmpresa(empresa);
        evento.setLane(lane);
        evento.setActivo(true);
        evento.setTipoEvento(TipoEventoMensaje.CATCH_INTERMEDIO);

        Arco arcoInactivo = new Arco();
        arcoInactivo.setActivo(false);

        when(nodoFlujoService.buscar(1L, 5L)).thenReturn(Optional.of(evento));
        when(arcoService.listarPorDestino(1L, 5L)).thenReturn(List.of(arcoInactivo));
        when(nodoFlujoService.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        EventoMensaje resultado = eventoMensajeService.editar(
                1L, 5L, "Recepcion", TipoEventoMensaje.CATCH_INICIO,
                "payload", "pedidoId", 10, 20, false);

        assertEquals(TipoEventoMensaje.CATCH_INICIO, resultado.getTipoEvento());
    }

    // ---- unicidad de nombre: Throw y Catch (lados distintos) pueden compartir nombre ----

    private Lane laneEnPool(Long laneId, Long poolId, boolean cajaNegra) {
        Empresa empresa = new Empresa();
        empresa.setId(1L);
        Proceso proceso = new Proceso();
        proceso.setId(10L);
        proceso.setEmpresa(empresa);
        Pool pool = new Pool();
        pool.setId(poolId);
        pool.setProceso(proceso);
        pool.setCajaNegra(cajaNegra);
        Lane lane = new Lane();
        lane.setId(laneId);
        lane.setEmpresa(empresa);
        lane.setPool(pool);
        return lane;
    }

    private EventoMensaje eventoEn(Long id, String nombre, TipoEventoMensaje tipo, Lane lane) {
        EventoMensaje evento = new EventoMensaje();
        evento.setId(id);
        evento.setNombre(nombre);
        evento.setTipoEvento(tipo);
        evento.setClaveCorrelacion("pedidoId");
        evento.setContenido("payload");
        evento.setEmpresa(lane.getEmpresa());
        evento.setLane(lane);
        evento.setActivo(true);
        return evento;
    }

    private com.facimus.procesos.modelado.model.Mensaje mensajeNombrado(String nombre) {
        var mensaje = new com.facimus.procesos.modelado.model.Mensaje();
        mensaje.setNombre(nombre);
        return mensaje;
    }

    private void crearOk(Lane lane, String nombre, TipoEventoMensaje tipo) {
        when(laneService.obtener(1L, lane.getId())).thenReturn(lane);
        when(nodoFlujoService.guardar(any())).thenAnswer(inv -> inv.getArgument(0));
        eventoMensajeService.crear(1L, lane.getId(), nombre, tipo, "payload", "pedidoId", 0, 0, false);
    }

    @Test
    void crear_throw_con_el_nombre_de_un_catch_existente_se_permite() {
        Lane lane = laneEnPool(2L, 3L, false);
        when(nodoFlujoService.listarActivosPorNombre("Orden", 10L, 1L))
                .thenReturn(List.of(eventoEn(7L, "Orden", TipoEventoMensaje.CATCH_INTERMEDIO, lane)));

        crearOk(lane, "Orden", TipoEventoMensaje.THROW);

        verify(nodoFlujoService).guardar(any(EventoMensaje.class));
    }

    @Test
    void crear_catch_con_el_nombre_de_un_throw_existente_se_permite() {
        Lane lane = laneEnPool(2L, 3L, false);
        when(nodoFlujoService.listarActivosPorNombre("Orden", 10L, 1L))
                .thenReturn(List.of(eventoEn(7L, "Orden", TipoEventoMensaje.THROW, lane)));

        crearOk(lane, "Orden", TipoEventoMensaje.CATCH_INICIO);

        verify(nodoFlujoService).guardar(any(EventoMensaje.class));
    }

    @Test
    void crear_catch_con_el_nombre_de_otro_catch_falla_aunque_cambie_el_subtipo() {
        Lane lane = laneEnPool(2L, 3L, false);
        when(laneService.obtener(1L, 2L)).thenReturn(lane);
        when(nodoFlujoService.listarActivosPorNombre("Orden", 10L, 1L))
                .thenReturn(List.of(eventoEn(7L, "Orden", TipoEventoMensaje.CATCH_INICIO, lane)));

        var ex = assertThrows(com.facimus.procesos.common.ReglaNegocioException.class, () ->
                eventoMensajeService.crear(1L, 2L, "Orden", TipoEventoMensaje.CATCH_INTERMEDIO, "p", "k", 0, 0, false));

        assertTrue(ex.getMessage().contains("Ya existe un nodo"));
        verify(nodoFlujoService, never()).guardar(any());
    }

    @Test
    void crear_throw_con_el_nombre_de_otro_throw_falla() {
        Lane lane = laneEnPool(2L, 3L, false);
        when(laneService.obtener(1L, 2L)).thenReturn(lane);
        when(nodoFlujoService.listarActivosPorNombre("Orden", 10L, 1L))
                .thenReturn(List.of(eventoEn(7L, "Orden", TipoEventoMensaje.THROW, lane)));

        assertThrows(com.facimus.procesos.common.ReglaNegocioException.class, () ->
                eventoMensajeService.crear(1L, 2L, "Orden", TipoEventoMensaje.THROW, "p", "k", 0, 0, false));
    }

    @Test
    void crear_con_el_nombre_de_una_actividad_o_gateway_falla_en_cualquier_lado() {
        Lane lane = laneEnPool(2L, 3L, false);
        when(laneService.obtener(1L, 2L)).thenReturn(lane);
        Actividad actividad = new Actividad();
        actividad.setId(8L);
        actividad.setNombre("Orden");
        when(nodoFlujoService.listarActivosPorNombre("Orden", 10L, 1L)).thenReturn(List.of(actividad));

        assertThrows(com.facimus.procesos.common.ReglaNegocioException.class, () ->
                eventoMensajeService.crear(1L, 2L, "Orden", TipoEventoMensaje.THROW, "p", "k", 0, 0, false));
        assertThrows(com.facimus.procesos.common.ReglaNegocioException.class, () ->
                eventoMensajeService.crear(1L, 2L, "Orden", TipoEventoMensaje.CATCH_INTERMEDIO, "p", "k", 0, 0, false));
    }

    @Test
    void editar_ignora_al_propio_evento_en_la_comprobacion_de_nombre() {
        Lane lane = laneEnPool(2L, 3L, false);
        EventoMensaje evento = eventoEn(5L, "Orden", TipoEventoMensaje.THROW, lane);
        when(nodoFlujoService.buscar(1L, 5L)).thenReturn(Optional.of(evento));
        when(nodoFlujoService.listarActivosPorNombre("Orden", 10L, 1L)).thenReturn(List.of(evento));
        when(nodoFlujoService.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        EventoMensaje resultado = eventoMensajeService.editar(1L, 5L, "Orden", TipoEventoMensaje.THROW, "nuevo",
                "pedidoId", 3, 4, false);

        assertEquals("nuevo", resultado.getContenido());
    }

    // ---- editar / eliminar un evento referenciado por mensajes activos ----

    private EventoMensaje eventoReferenciado(Lane lane) {
        EventoMensaje evento = eventoEn(5L, "Orden", TipoEventoMensaje.CATCH_INTERMEDIO, lane);
        when(nodoFlujoService.buscar(1L, 5L)).thenReturn(Optional.of(evento));
        when(mensajeService.listarActivosPorEvento(1L, 5L))
                .thenReturn(List.of(mensajeNombrado("Orden"), mensajeNombrado("Orden urgente")));
        return evento;
    }

    @Test
    void editar_evento_con_mensajes_activos_no_permite_cambiar_el_nombre_y_los_nombra() {
        eventoReferenciado(laneEnPool(2L, 3L, false));

        var ex = assertThrows(com.facimus.procesos.common.ReglaNegocioException.class, () ->
                eventoMensajeService.editar(1L, 5L, "Pedido", TipoEventoMensaje.CATCH_INTERMEDIO, "p", "pedidoId",
                        0, 0, false));

        assertTrue(ex.getMessage().contains("\"Orden\", \"Orden urgente\""), ex.getMessage());
        verify(nodoFlujoService, never()).guardar(any());
    }

    @Test
    void editar_evento_con_mensajes_activos_no_permite_cambiar_la_clave_ni_el_lado() {
        eventoReferenciado(laneEnPool(2L, 3L, false));

        assertThrows(com.facimus.procesos.common.ReglaNegocioException.class, () ->
                eventoMensajeService.editar(1L, 5L, "Orden", TipoEventoMensaje.CATCH_INTERMEDIO, "p", "otraClave",
                        0, 0, false));
        assertThrows(com.facimus.procesos.common.ReglaNegocioException.class, () ->
                eventoMensajeService.editar(1L, 5L, "Orden", TipoEventoMensaje.THROW, "p", "pedidoId", 0, 0, false));
    }

    @Test
    void editar_evento_con_mensajes_activos_admite_cambios_compatibles() {
        EventoMensaje evento = eventoReferenciado(laneEnPool(2L, 3L, false));
        when(nodoFlujoService.listarActivosPorNombre("ORDEN", 10L, 1L)).thenReturn(List.of(evento));
        when(nodoFlujoService.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        // mayusculas del nombre, subtipo de catch, clave en otras mayusculas, contenido y posicion
        EventoMensaje resultado = eventoMensajeService.editar(1L, 5L, "ORDEN", TipoEventoMensaje.CATCH_INICIO,
                "otro contenido", "PEDIDOID", 50, 60, true);

        assertEquals("otro contenido", resultado.getContenido());
        assertEquals(TipoEventoMensaje.CATCH_INICIO, resultado.getTipoEvento());
    }

    @Test
    void eliminar_evento_con_mensajes_activos_falla_y_no_toca_los_arcos() {
        eventoReferenciado(laneEnPool(2L, 3L, false));

        var ex = assertThrows(com.facimus.procesos.common.ReglaNegocioException.class,
                () -> eventoMensajeService.eliminar(1L, 5L));

        assertTrue(ex.getMessage().contains("\"Orden urgente\""));
        verify(arcoService, never()).desactivarPorNodo(anyLong(), anyLong());
        verify(nodoFlujoService, never()).guardar(any());
    }

    @Test
    void impacto_de_eliminar_un_evento_lista_los_mensajes_y_los_arcos_afectados() {
        Lane lane = laneEnPool(2L, 3L, false);
        EventoMensaje evento = eventoReferenciado(lane);
        Actividad origen = new Actividad();
        origen.setId(20L);
        origen.setNombre("Origen");
        Arco entrada = new Arco();
        entrada.setId(100L);
        entrada.setOrigen(origen);
        entrada.setDestino(evento);
        entrada.setActivo(true);
        when(arcoService.listarPorDestino(1L, 5L)).thenReturn(List.of(entrada));
        when(arcoService.listarPorOrigen(1L, 5L)).thenReturn(List.of());
        when(arcoService.listarPorOrigen(1L, 20L)).thenReturn(List.of(entrada));

        var impacto = eventoMensajeService.evaluarImpactoEliminacion(1L, 5L);

        assertTrue(impacto.rompeContinuidad());
        assertTrue(impacto.advertencias().contains("El nodo Origen quedara sin salida."));
        assertTrue(impacto.advertencias().stream().anyMatch(a -> a.contains("\"Orden\", \"Orden urgente\"")));
        assertTrue(impacto.advertencias().contains("Se eliminaran los arcos conectados a Orden."));
    }

    @Test
    void impacto_de_un_evento_sin_arcos_ni_mensajes_no_advierte_nada() {
        Lane lane = laneEnPool(2L, 3L, false);
        EventoMensaje evento = eventoEn(5L, "Orden", TipoEventoMensaje.THROW, lane);
        when(nodoFlujoService.buscar(1L, 5L)).thenReturn(Optional.of(evento));

        var impacto = eventoMensajeService.evaluarImpactoEliminacion(1L, 5L);

        assertFalse(impacto.rompeContinuidad());
        assertTrue(impacto.advertencias().isEmpty());
    }

    // ---- HU-27: actividades que usan los datos del mensaje ----

    private Actividad actividadEn(Long id, Lane lane) {
        Actividad actividad = new Actividad();
        actividad.setId(id);
        actividad.setNombre("Actividad " + id);
        actividad.setLane(lane);
        actividad.setActivo(true);
        return actividad;
    }

    @Test
    void crear_catch_con_actividades_las_asocia() {
        Lane lane = laneEnPool(2L, 3L, false);
        Actividad usa = actividadEn(30L, lane);
        when(laneService.obtener(1L, 2L)).thenReturn(lane);
        when(actividadService.obtener(1L, 30L)).thenReturn(usa);
        when(nodoFlujoService.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        EventoMensaje resultado = eventoMensajeService.crear(1L, 2L, "Recibo", TipoEventoMensaje.CATCH_INTERMEDIO,
                "p", "k", 0, 0, false, List.of(30L, 30L));

        assertEquals(java.util.Set.of(usa), resultado.getActividades());
    }

    @Test
    void crear_throw_con_actividades_falla() {
        Lane lane = laneEnPool(2L, 3L, false);
        when(laneService.obtener(1L, 2L)).thenReturn(lane);

        assertThrows(com.facimus.procesos.common.ReglaNegocioException.class, () ->
                eventoMensajeService.crear(1L, 2L, "Envio", TipoEventoMensaje.THROW, "p", "k", 0, 0, false,
                        List.of(30L)));
        verify(nodoFlujoService, never()).guardar(any());
    }

    @Test
    void crear_catch_con_actividad_de_otro_proceso_falla() {
        Lane lane = laneEnPool(2L, 3L, false);
        Lane laneAjena = laneEnPool(9L, 90L, false);
        laneAjena.getPool().getProceso().setId(99L);
        when(laneService.obtener(1L, 2L)).thenReturn(lane);
        when(actividadService.obtener(1L, 30L)).thenReturn(actividadEn(30L, laneAjena));

        var ex = assertThrows(com.facimus.procesos.common.ReglaNegocioException.class, () ->
                eventoMensajeService.crear(1L, 2L, "Recibo", TipoEventoMensaje.CATCH_INTERMEDIO, "p", "k", 0, 0,
                        false, List.of(30L)));

        assertTrue(ex.getMessage().contains("no pertenece al mismo proceso"));
    }

    @Test
    void crear_catch_con_actividad_inexistente_o_inactiva_falla_con_404() {
        Lane lane = laneEnPool(2L, 3L, false);
        when(laneService.obtener(1L, 2L)).thenReturn(lane);
        when(actividadService.obtener(1L, 30L))
                .thenThrow(new com.facimus.procesos.common.RecursoNoEncontradoException("Actividad no encontrada."));

        assertThrows(com.facimus.procesos.common.RecursoNoEncontradoException.class, () ->
                eventoMensajeService.crear(1L, 2L, "Recibo", TipoEventoMensaje.CATCH_INTERMEDIO, "p", "k", 0, 0,
                        false, List.of(30L)));
    }

    @Test
    void editar_con_actividadIds_reemplaza_null_conserva_y_lista_vacia_limpia() {
        Lane lane = laneEnPool(2L, 3L, false);
        Actividad vieja = actividadEn(30L, lane);
        Actividad nueva = actividadEn(31L, lane);
        EventoMensaje evento = eventoEn(5L, "Recibo", TipoEventoMensaje.CATCH_INTERMEDIO, lane);
        evento.getActividades().add(vieja);
        when(nodoFlujoService.buscar(1L, 5L)).thenReturn(Optional.of(evento));
        when(nodoFlujoService.guardar(any())).thenAnswer(inv -> inv.getArgument(0));
        when(actividadService.obtener(1L, 31L)).thenReturn(nueva);

        EventoMensaje reemplazado = eventoMensajeService.editar(1L, 5L, "Recibo",
                TipoEventoMensaje.CATCH_INTERMEDIO, "p", "pedidoId", 0, 0, false, null, List.of(31L));
        assertEquals(java.util.Set.of(nueva), reemplazado.getActividades());

        EventoMensaje conservado = eventoMensajeService.editar(1L, 5L, "Recibo",
                TipoEventoMensaje.CATCH_INTERMEDIO, "p", "pedidoId", 0, 0, false, null, null);
        assertEquals(java.util.Set.of(nueva), conservado.getActividades());

        EventoMensaje limpio = eventoMensajeService.editar(1L, 5L, "Recibo",
                TipoEventoMensaje.CATCH_INTERMEDIO, "p", "pedidoId", 0, 0, false, null, List.of());
        assertTrue(limpio.getActividades().isEmpty());
    }

    // ---- laneId opcional al editar (misma regla que ActividadService.editar) ----

    private EventoMensaje eventoEditable(Lane lane) {
        EventoMensaje evento = eventoEn(5L, "Orden", TipoEventoMensaje.THROW, lane);
        when(nodoFlujoService.buscar(1L, 5L)).thenReturn(Optional.of(evento));
        return evento;
    }

    @Test
    void editar_con_laneId_del_mismo_pool_mueve_el_evento() {
        Lane actual = laneEnPool(2L, 3L, false);
        Lane otra = laneEnPool(4L, 3L, false);
        EventoMensaje evento = eventoEditable(actual);
        when(laneService.obtener(1L, 4L)).thenReturn(otra);
        when(nodoFlujoService.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        EventoMensaje resultado = eventoMensajeService.editar(1L, 5L, "Orden", TipoEventoMensaje.THROW, "p",
                "pedidoId", 0, 0, false, 4L, null);

        assertSame(otra, resultado.getLane());
        assertSame(evento, resultado);
    }

    @Test
    void editar_con_laneId_de_otro_pool_y_con_arcos_falla() {
        Lane actual = laneEnPool(2L, 3L, false);
        Lane otroPool = laneEnPool(9L, 30L, false);
        otroPool.getPool().getProceso().setId(10L);
        eventoEditable(actual);
        when(laneService.obtener(1L, 9L)).thenReturn(otroPool);
        Arco arco = new Arco();
        arco.setActivo(true);
        when(arcoService.listarPorOrigen(1L, 5L)).thenReturn(List.of(arco));

        var ex = assertThrows(com.facimus.procesos.common.ReglaNegocioException.class, () ->
                eventoMensajeService.editar(1L, 5L, "Orden", TipoEventoMensaje.THROW, "p", "pedidoId", 0, 0, false,
                        9L, null));

        assertTrue(ex.getMessage().contains("otro pool"));
    }

    @Test
    void editar_con_laneId_de_otro_pool_sin_arcos_activos_funciona() {
        Lane actual = laneEnPool(2L, 3L, false);
        Lane otroPool = laneEnPool(9L, 30L, false);
        eventoEditable(actual);
        when(laneService.obtener(1L, 9L)).thenReturn(otroPool);
        Arco inactivo = new Arco();
        inactivo.setActivo(false);
        when(arcoService.listarPorOrigen(1L, 5L)).thenReturn(List.of(inactivo));
        when(nodoFlujoService.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        EventoMensaje resultado = eventoMensajeService.editar(1L, 5L, "Orden", TipoEventoMensaje.THROW, "p",
                "pedidoId", 0, 0, false, 9L, null);

        assertSame(otroPool, resultado.getLane());
    }

    @Test
    void editar_con_laneId_de_pool_caja_negra_o_de_otro_proceso_falla() {
        Lane actual = laneEnPool(2L, 3L, false);
        Lane cajaNegra = laneEnPool(8L, 80L, true);
        Lane otroProceso = laneEnPool(9L, 90L, false);
        otroProceso.getPool().getProceso().setId(99L);
        eventoEditable(actual);
        when(laneService.obtener(1L, 8L)).thenReturn(cajaNegra);
        when(laneService.obtener(1L, 9L)).thenReturn(otroProceso);

        assertThrows(com.facimus.procesos.common.ReglaNegocioException.class, () ->
                eventoMensajeService.editar(1L, 5L, "Orden", TipoEventoMensaje.THROW, "p", "pedidoId", 0, 0, false,
                        8L, null));
        assertThrows(com.facimus.procesos.common.ReglaNegocioException.class, () ->
                eventoMensajeService.editar(1L, 5L, "Orden", TipoEventoMensaje.THROW, "p", "pedidoId", 0, 0, false,
                        9L, null));
    }
}
