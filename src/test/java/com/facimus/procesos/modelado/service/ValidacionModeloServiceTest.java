package com.facimus.procesos.modelado.service;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.event.ProcesoPublicacionEvent;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.modelado.model.*;

@ExtendWith(MockitoExtension.class)
class ValidacionModeloServiceTest {

    @Mock private PoolService poolService;
    @Mock private LaneService laneService;
    @Mock private NodoFlujoService nodoFlujoService;
    @Mock private ArcoService arcoService;
    @Mock private MensajeService mensajeService;
    @Mock private CorrelacionService correlacionService;

    @InjectMocks
    private ValidacionModeloServiceImpl service;

    private Pool pool;
    private Lane lane;

    @BeforeEach
    void setUp() {
        Proceso proceso = new Proceso();
        proceso.setId(10L);

        pool = new Pool();
        pool.setId(100L);
        pool.setNombre("Interno");
        pool.setProceso(proceso);
        pool.setCajaNegra(false);
        pool.setTipoParticipante(TipoParticipante.EMPRESA);

        lane = new Lane();
        lane.setId(200L);
        lane.setPool(pool);
    }

    @Test
    void alPublicarProceso_delega_en_validarParaPublicacion() {
        pool.setCajaNegra(true);
        when(poolService.listarPorProceso(1L, 10L)).thenReturn(List.of(pool));
        when(laneService.listarPorProceso(1L, 10L)).thenReturn(List.of(lane));

        assertThrows(ReglaNegocioException.class,
                () -> service.alPublicarProceso(new ProcesoPublicacionEvent(1L, 10L)));
    }

    @Test
    void modelo_vacio_es_valido() {
        when(poolService.listarPorProceso(1L, 10L)).thenReturn(List.of(pool));
        when(laneService.listarPorProceso(1L, 10L)).thenReturn(List.of());
        when(nodoFlujoService.listarActivosPorProceso(1L, 10L)).thenReturn(List.of());
        when(arcoService.listarActivosPorProceso(1L, 10L)).thenReturn(List.of());
        when(mensajeService.listarPorProceso(1L, 10L)).thenReturn(List.of());

        assertDoesNotThrow(() -> service.validarParaPublicacion(1L, 10L));
    }

    @Test
    void caja_negra_con_lane_falla() {
        pool.setCajaNegra(true);
        when(poolService.listarPorProceso(1L, 10L)).thenReturn(List.of(pool));
        when(laneService.listarPorProceso(1L, 10L)).thenReturn(List.of(lane));

        assertThrows(ReglaNegocioException.class, () -> service.validarParaPublicacion(1L, 10L));
    }

    @Test
    void gateway_divergente_con_menos_de_dos_salidas_falla() {
        Gateway gateway = new Gateway();
        gateway.setId(1L);
        gateway.setNombre("Decision");
        gateway.setTipoGateway(TipoGateway.EXCLUSIVO);
        gateway.setActivo(true);

        when(poolService.listarPorProceso(1L, 10L)).thenReturn(List.of(pool));
        when(laneService.listarPorProceso(1L, 10L)).thenReturn(List.of());
        when(nodoFlujoService.listarActivosPorProceso(1L, 10L)).thenReturn(List.of(gateway));
        when(arcoService.listarActivosPorProceso(1L, 10L)).thenReturn(List.of());

        assertThrows(ReglaNegocioException.class, () -> service.validarParaPublicacion(1L, 10L));
    }

    @Test
    void gateway_exclusivo_con_salida_sin_condicion_falla() {
        Gateway gateway = new Gateway();
        gateway.setId(1L);
        gateway.setNombre("Decision");
        gateway.setTipoGateway(TipoGateway.EXCLUSIVO);
        gateway.setActivo(true);

        Actividad a = actividad(2L);
        Actividad b = actividad(3L);
        Arco ab = arco(11L, gateway, a, "");
        Arco ac = arco(12L, gateway, b, "x > 0");

        when(poolService.listarPorProceso(1L, 10L)).thenReturn(List.of(pool));
        when(laneService.listarPorProceso(1L, 10L)).thenReturn(List.of());
        when(nodoFlujoService.listarActivosPorProceso(1L, 10L)).thenReturn(List.of(gateway, a, b));
        when(arcoService.listarActivosPorProceso(1L, 10L)).thenReturn(List.of(ab, ac));

        assertThrows(ReglaNegocioException.class, () -> service.validarParaPublicacion(1L, 10L));
    }

    @Test
    void catch_inicio_con_entrada_falla() {
        EventoMensaje evento = new EventoMensaje();
        evento.setId(5L);
        evento.setNombre("Recepcion");
        evento.setTipoEvento(TipoEventoMensaje.CATCH_INICIO);
        evento.setActivo(true);
        Actividad origen = actividad(6L);
        Arco entrada = arco(20L, origen, evento, null);

        when(poolService.listarPorProceso(1L, 10L)).thenReturn(List.of(pool));
        when(laneService.listarPorProceso(1L, 10L)).thenReturn(List.of());
        when(nodoFlujoService.listarActivosPorProceso(1L, 10L)).thenReturn(List.of(evento, origen));
        when(arcoService.listarActivosPorProceso(1L, 10L)).thenReturn(List.of(entrada));

        assertThrows(ReglaNegocioException.class, () -> service.validarParaPublicacion(1L, 10L));
    }

    @Test
    void mensaje_sin_throw_falla() {
        Mensaje mensaje = new Mensaje();
        mensaje.setId(1L);
        mensaje.setNombre("Orden");
        mensaje.setActivo(true);

        when(poolService.listarPorProceso(1L, 10L)).thenReturn(List.of(pool));
        when(laneService.listarPorProceso(1L, 10L)).thenReturn(List.of());
        when(nodoFlujoService.listarActivosPorProceso(1L, 10L)).thenReturn(List.of());
        when(arcoService.listarActivosPorProceso(1L, 10L)).thenReturn(List.of());
        when(mensajeService.listarPorProceso(1L, 10L)).thenReturn(List.of(mensaje));

        assertThrows(ReglaNegocioException.class, () -> service.validarParaPublicacion(1L, 10L));
    }


    @Test
    void mensaje_sin_receptor_falla() {
        Mensaje mensaje = mensajeBase();
        mensaje.setEventoThrow(new EventoMensaje());
        mensaje.setClaveCorrelacion("pedidoId");

        prepararEstructuraVacia();
        when(mensajeService.listarPorProceso(1L, 10L)).thenReturn(List.of(mensaje));

        assertThrows(ReglaNegocioException.class, () -> service.validarParaPublicacion(1L, 10L));
    }

    @Test
    void mensaje_sin_clave_correlacion_falla() {
        Mensaje mensaje = mensajeBase();
        mensaje.setEventoThrow(new EventoMensaje());
        mensaje.setEventoCatch(new EventoMensaje());

        prepararEstructuraVacia();
        when(mensajeService.listarPorProceso(1L, 10L)).thenReturn(List.of(mensaje));

        assertThrows(ReglaNegocioException.class, () -> service.validarParaPublicacion(1L, 10L));
    }

    @Test
    void mensaje_sin_entidad_correlacion_falla() {
        Mensaje mensaje = mensajeBase();
        mensaje.setEventoThrow(new EventoMensaje());
        mensaje.setEventoCatch(new EventoMensaje());
        mensaje.setClaveCorrelacion("pedidoId");

        prepararEstructuraVacia();
        when(mensajeService.listarPorProceso(1L, 10L)).thenReturn(List.of(mensaje));
        when(correlacionService.buscarPorMensaje(1L, 1L)).thenReturn(Optional.empty());

        assertThrows(ReglaNegocioException.class, () -> service.validarParaPublicacion(1L, 10L));
    }

    @Test
    void mensajes_duplicados_por_nombre_y_clave_fallan() {
        Mensaje a = mensajeBase();
        a.setEventoThrow(new EventoMensaje());
        a.setEventoCatch(new EventoMensaje());
        a.setClaveCorrelacion("pedidoId");

        Mensaje b = mensajeBase();
        b.setId(2L);
        b.setNombre(" orden ");
        b.setEventoThrow(new EventoMensaje());
        b.setEventoCatch(new EventoMensaje());
        b.setClaveCorrelacion(" PEDIDOID ");

        Correlacion correlacion = new Correlacion();
        prepararEstructuraVacia();
        when(mensajeService.listarPorProceso(1L, 10L)).thenReturn(List.of(a, b));
        when(correlacionService.buscarPorMensaje(eq(1L), anyLong()))
                .thenReturn(Optional.of(correlacion));

        assertThrows(ReglaNegocioException.class, () -> service.validarParaPublicacion(1L, 10L));
    }

    @Test
    void mensaje_externo_con_pool_invalido_falla() {
        Mensaje mensaje = mensajeBase();
        mensaje.setEventoThrow(new EventoMensaje());
        mensaje.setClaveCorrelacion("pedidoId");
        mensaje.setTipoDestinoExterno(TipoDestinoExterno.CORREO);
        mensaje.setDestinoExterno("ops@demo.com");
        mensaje.setPoolDestino(pool);

        Correlacion correlacion = new Correlacion();
        prepararEstructuraVacia();
        when(mensajeService.listarPorProceso(1L, 10L)).thenReturn(List.of(mensaje));
        when(correlacionService.buscarPorMensaje(1L, 1L)).thenReturn(Optional.of(correlacion));

        assertThrows(ReglaNegocioException.class, () -> service.validarParaPublicacion(1L, 10L));
    }

    @Test
    void mensaje_externo_valido_es_aceptado() {
        Pool externo = new Pool();
        externo.setId(200L);
        externo.setTipoParticipante(TipoParticipante.SISTEMA_EXTERNO);
        externo.setCajaNegra(true);

        Mensaje mensaje = mensajeBase();
        mensaje.setEventoThrow(eventoConClave("pedidoId"));
        mensaje.setClaveCorrelacion("pedidoId");
        mensaje.setTipoDestinoExterno(TipoDestinoExterno.CORREO);
        mensaje.setDestinoExterno("ops@demo.com");
        mensaje.setPoolDestino(externo);

        Correlacion correlacion = new Correlacion();
        prepararEstructuraVacia();
        when(mensajeService.listarPorProceso(1L, 10L)).thenReturn(List.of(mensaje));
        when(correlacionService.buscarPorMensaje(1L, 1L)).thenReturn(Optional.of(correlacion));

        assertDoesNotThrow(() -> service.validarParaPublicacion(1L, 10L));
    }

    @Test
    void gateway_paralelo_con_dos_salidas_es_valido() {
        Gateway gateway = new Gateway();
        gateway.setId(1L);
        gateway.setNombre("Paralelo");
        gateway.setTipoGateway(TipoGateway.PARALELO);
        gateway.setActivo(true);

        Actividad a = actividad(2L);
        Actividad b = actividad(3L);
        Arco ab = arco(11L, gateway, a, null);
        Arco ac = arco(12L, gateway, b, null);

        when(poolService.listarPorProceso(1L, 10L)).thenReturn(List.of(pool));
        when(laneService.listarPorProceso(1L, 10L)).thenReturn(List.of());
        when(nodoFlujoService.listarActivosPorProceso(1L, 10L)).thenReturn(List.of(gateway, a, b));
        when(arcoService.listarActivosPorProceso(1L, 10L)).thenReturn(List.of(ab, ac));
        when(mensajeService.listarPorProceso(1L, 10L)).thenReturn(List.of());

        assertDoesNotThrow(() -> service.validarParaPublicacion(1L, 10L));
    }

    @Test
    void mensaje_interno_completo_es_valido() {
        EventoMensaje throwEvt = eventoConClave("pedidoId");
        throwEvt.setId(7L);
        EventoMensaje catchEvt = eventoConClave("pedidoId");
        catchEvt.setId(8L);

        Mensaje mensaje = new Mensaje();
        mensaje.setId(1L);
        mensaje.setNombre("Orden");
        mensaje.setEventoThrow(throwEvt);
        mensaje.setEventoCatch(catchEvt);
        mensaje.setClaveCorrelacion("pedidoId");
        mensaje.setActivo(true);

        Correlacion correlacion = new Correlacion();
        correlacion.setId(4L);

        when(poolService.listarPorProceso(1L, 10L)).thenReturn(List.of(pool));
        when(laneService.listarPorProceso(1L, 10L)).thenReturn(List.of());
        when(nodoFlujoService.listarActivosPorProceso(1L, 10L)).thenReturn(List.of());
        when(arcoService.listarActivosPorProceso(1L, 10L)).thenReturn(List.of());
        when(mensajeService.listarPorProceso(1L, 10L)).thenReturn(List.of(mensaje));
        when(correlacionService.buscarPorMensaje(1L, 1L)).thenReturn(Optional.of(correlacion));

        assertDoesNotThrow(() -> service.validarParaPublicacion(1L, 10L));
    }


    private void prepararEstructuraVacia() {
        when(poolService.listarPorProceso(1L, 10L)).thenReturn(List.of(pool));
        when(laneService.listarPorProceso(1L, 10L)).thenReturn(List.of());
        when(nodoFlujoService.listarActivosPorProceso(1L, 10L)).thenReturn(List.of());
        when(arcoService.listarActivosPorProceso(1L, 10L)).thenReturn(List.of());
    }

    private EventoMensaje eventoConClave(String clave) {
        EventoMensaje evento = new EventoMensaje();
        evento.setClaveCorrelacion(clave);
        return evento;
    }

    private Mensaje mensajeBase() {
        Mensaje mensaje = new Mensaje();
        mensaje.setId(1L);
        mensaje.setNombre("Orden");
        mensaje.setActivo(true);
        return mensaje;
    }

    private Actividad actividad(Long id) {
        Actividad a = new Actividad();
        a.setId(id);
        a.setActivo(true);
        return a;
    }

    private Arco arco(Long id, NodoFlujo origen, NodoFlujo destino, String condicion) {
        Arco a = new Arco();
        a.setId(id);
        a.setOrigen(origen);
        a.setDestino(destino);
        a.setCondicion(condicion);
        a.setActivo(true);
        return a;
    }

    // ---- obtenerAdvertencias: reune TODO sin lanzar ----

    private List<String> advertencias() {
        return service.obtenerAdvertencias(1L, 10L);
    }

    private void sinMensajes() {
        when(mensajeService.listarPorProceso(1L, 10L)).thenReturn(List.of());
    }

    @Test
    void obtenerAdvertencias_modeloValido_devuelveListaVaciaYValidoTrue() {
        prepararEstructuraVacia();
        sinMensajes();

        assertThat(advertencias()).isEmpty();
        var respuesta = service.consultarValidacion(1L, 10L);
        assertThat(respuesta.valido()).isTrue();
        assertThat(respuesta.advertencias()).isEmpty();
    }

    @Test
    void obtenerAdvertencias_sinPoolEmpresa_advierte() {
        pool.setTipoParticipante(TipoParticipante.CLIENTE);
        prepararEstructuraVacia();
        sinMensajes();

        assertThat(advertencias()).containsExactly(
                "El proceso debe tener exactamente un pool de tipo EMPRESA (tiene 0).");
    }

    @Test
    void obtenerAdvertencias_dosPoolesEmpresa_advierte() {
        Pool otro = new Pool();
        otro.setId(101L);
        otro.setNombre("Otro");
        otro.setTipoParticipante(TipoParticipante.EMPRESA);
        when(poolService.listarPorProceso(1L, 10L)).thenReturn(List.of(pool, otro));
        when(laneService.listarPorProceso(1L, 10L)).thenReturn(List.of());
        when(nodoFlujoService.listarActivosPorProceso(1L, 10L)).thenReturn(List.of());
        when(arcoService.listarActivosPorProceso(1L, 10L)).thenReturn(List.of());
        sinMensajes();

        assertThat(advertencias()).containsExactly(
                "El proceso debe tener exactamente un pool de tipo EMPRESA (tiene 2).");
    }

    @Test
    void obtenerAdvertencias_poolEmpresaCajaNegra_advierte() {
        pool.setCajaNegra(true);
        prepararEstructuraVacia();
        sinMensajes();

        assertThat(advertencias()).containsExactly("El pool EMPRESA \"Interno\" no puede ser de caja negra.");
    }

    private EventoMensaje catchEvento(Long id, TipoEventoMensaje tipo, boolean origenExterno, String clave) {
        EventoMensaje evento = new EventoMensaje();
        evento.setId(id);
        evento.setNombre("Recibo " + id);
        evento.setTipoEvento(tipo);
        evento.setOrigenExterno(origenExterno);
        evento.setClaveCorrelacion(clave);
        evento.setActivo(true);
        return evento;
    }

    private void conNodos(NodoFlujo... nodos) {
        when(poolService.listarPorProceso(1L, 10L)).thenReturn(List.of(pool));
        when(laneService.listarPorProceso(1L, 10L)).thenReturn(List.of());
        when(nodoFlujoService.listarActivosPorProceso(1L, 10L)).thenReturn(List.of(nodos));
    }

    @Test
    void obtenerAdvertencias_catchSinMensajeNiOrigenExterno_esHuerfano() {
        conNodos(catchEvento(5L, TipoEventoMensaje.CATCH_INTERMEDIO, false, "k"));
        when(arcoService.listarActivosPorProceso(1L, 10L)).thenReturn(List.of());
        sinMensajes();

        assertThat(advertencias()).containsExactly(
                "El Message Catch \"Recibo 5\" no tiene un mensaje vinculado ni origen externo.");
    }

    @Test
    void obtenerAdvertencias_catchConOrigenExterno_noEsHuerfano() {
        conNodos(catchEvento(5L, TipoEventoMensaje.CATCH_INICIO, true, "k"));
        when(arcoService.listarActivosPorProceso(1L, 10L)).thenReturn(List.of());
        sinMensajes();

        assertThat(advertencias()).isEmpty();
    }

    @Test
    void obtenerAdvertencias_catchVinculadoAUnMensaje_noEsHuerfano() {
        EventoMensaje recibe = catchEvento(5L, TipoEventoMensaje.CATCH_INTERMEDIO, false, "pedidoId");
        EventoMensaje lanza = eventoConClave("pedidoId");
        Mensaje mensaje = mensajeBase();
        mensaje.setEventoThrow(lanza);
        mensaje.setEventoCatch(recibe);
        mensaje.setClaveCorrelacion("pedidoId");
        conNodos(recibe);
        when(arcoService.listarActivosPorProceso(1L, 10L)).thenReturn(List.of());
        when(mensajeService.listarPorProceso(1L, 10L)).thenReturn(List.of(mensaje));
        when(correlacionService.buscarPorMensaje(1L, 1L)).thenReturn(Optional.of(new Correlacion()));

        assertThat(advertencias()).isEmpty();
    }

    @Test
    void obtenerAdvertencias_catchIntermedioSinClave_advierte() {
        conNodos(catchEvento(5L, TipoEventoMensaje.CATCH_INTERMEDIO, true, " "));
        when(arcoService.listarActivosPorProceso(1L, 10L)).thenReturn(List.of());
        sinMensajes();

        assertThat(advertencias()).containsExactly(
                "El Message Catch intermedio \"Recibo 5\" debe tener clave de correlacion.");
    }

    private Gateway gateway(Long id, TipoGateway tipo) {
        Gateway gateway = new Gateway();
        gateway.setId(id);
        gateway.setNombre("G" + id);
        gateway.setTipoGateway(tipo);
        gateway.setActivo(true);
        return gateway;
    }

    @Test
    void obtenerAdvertencias_gatewayConvergenteSinSalida_advierte() {
        Gateway union = gateway(1L, TipoGateway.PARALELO);
        Actividad a = actividad(2L);
        Actividad b = actividad(3L);
        conNodos(union, a, b);
        when(arcoService.listarActivosPorProceso(1L, 10L))
                .thenReturn(List.of(arco(11L, a, union, null), arco(12L, b, union, null)));
        sinMensajes();

        assertThat(advertencias()).containsExactly("El gateway convergente \"G1\" debe tener una salida.");
    }

    @Test
    void obtenerAdvertencias_gatewayConvergenteConUnaSalida_esValidoAunqueSeaExclusivoSinCondicion() {
        Gateway union = gateway(1L, TipoGateway.EXCLUSIVO);
        Actividad a = actividad(2L);
        Actividad b = actividad(3L);
        Actividad c = actividad(4L);
        conNodos(union, a, b, c);
        when(arcoService.listarActivosPorProceso(1L, 10L)).thenReturn(List.of(arco(11L, a, union, null),
                arco(12L, b, union, null), arco(13L, union, c, null)));
        sinMensajes();

        assertThat(advertencias()).isEmpty();
    }

    @Test
    void obtenerAdvertencias_gatewayDivergenteConUnaSalida_advierte() {
        Gateway reparte = gateway(1L, TipoGateway.PARALELO);
        Actividad a = actividad(2L);
        conNodos(reparte, a);
        when(arcoService.listarActivosPorProceso(1L, 10L)).thenReturn(List.of(arco(11L, reparte, a, null)));
        sinMensajes();

        assertThat(advertencias()).containsExactly(
                "El gateway divergente \"G1\" debe tener al menos dos salidas.");
    }

    @Test
    void obtenerAdvertencias_gatewayInclusivoConSalidaSinCondicion_advierte() {
        Gateway decide = gateway(1L, TipoGateway.INCLUSIVO);
        Actividad a = actividad(2L);
        Actividad b = actividad(3L);
        conNodos(decide, a, b);
        when(arcoService.listarActivosPorProceso(1L, 10L))
                .thenReturn(List.of(arco(11L, decide, a, "x > 1"), arco(12L, decide, b, " ")));
        sinMensajes();

        assertThat(advertencias()).containsExactly(
                "Todos los arcos salientes del gateway \"G1\" deben tener condicion.");
    }

    @Test
    void obtenerAdvertencias_mensajeSinReceptor_advierte() {
        Mensaje mensaje = mensajeBase();
        mensaje.setEventoThrow(eventoConClave("pedidoId"));
        mensaje.setClaveCorrelacion("pedidoId");
        prepararEstructuraVacia();
        when(mensajeService.listarPorProceso(1L, 10L)).thenReturn(List.of(mensaje));
        when(correlacionService.buscarPorMensaje(1L, 1L)).thenReturn(Optional.of(new Correlacion()));

        assertThat(advertencias()).containsExactly(
                "El mensaje \"Orden\" no tiene receptor: debe tener un Message Catch o un destino externo.");
    }

    @Test
    void obtenerAdvertencias_claveDelThrowYDelCatchDistintaALaDelMensaje_advierteLosDos() {
        EventoMensaje lanza = eventoConClave("otraClave");
        lanza.setNombre("Orden");
        EventoMensaje recibe = catchEvento(5L, TipoEventoMensaje.CATCH_INTERMEDIO, false, "tercera");
        recibe.setNombre("Orden");
        Mensaje mensaje = mensajeBase();
        mensaje.setEventoThrow(lanza);
        mensaje.setEventoCatch(recibe);
        mensaje.setClaveCorrelacion("pedidoId");
        conNodos(recibe);
        when(arcoService.listarActivosPorProceso(1L, 10L)).thenReturn(List.of());
        when(mensajeService.listarPorProceso(1L, 10L)).thenReturn(List.of(mensaje));
        when(correlacionService.buscarPorMensaje(1L, 1L)).thenReturn(Optional.of(new Correlacion()));

        assertThat(advertencias()).containsExactly(
                "La clave de correlacion del Message Throw \"Orden\" no coincide con la del mensaje \"Orden\".",
                "La clave de correlacion del Message Catch \"Orden\" no coincide con la del mensaje \"Orden\".");
    }

    @Test
    void obtenerAdvertencias_mensajesAmbiguos_seReportaUnaSolaVez() {
        List<Mensaje> mensajes = new java.util.ArrayList<>();
        for (long id = 1; id <= 3; id++) {
            Mensaje m = mensajeBase();
            m.setId(id);
            m.setEventoThrow(eventoConClave("pedidoId"));
            m.setEventoCatch(eventoConClave("pedidoId"));
            m.setClaveCorrelacion("pedidoId");
            mensajes.add(m);
        }
        prepararEstructuraVacia();
        when(mensajeService.listarPorProceso(1L, 10L)).thenReturn(mensajes);
        when(correlacionService.buscarPorMensaje(eq(1L), anyLong())).thenReturn(Optional.of(new Correlacion()));

        assertThat(advertencias()).containsExactly(
                "Hay mensajes ambiguos con el mismo nombre y clave de correlacion.");
    }

    @Test
    void validarParaPublicacion_variosProblemas_fallaConTodosEnElMensaje() {
        pool.setTipoParticipante(TipoParticipante.CLIENTE);
        Gateway reparte = gateway(1L, TipoGateway.PARALELO);
        conNodos(reparte);
        when(arcoService.listarActivosPorProceso(1L, 10L)).thenReturn(List.of());
        sinMensajes();

        assertThatThrownBy(() -> service.validarParaPublicacion(1L, 10L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("exactamente un pool de tipo EMPRESA")
                .hasMessageContaining("\"G1\" debe tener al menos dos salidas");
    }
}
