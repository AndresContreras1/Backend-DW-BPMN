package com.facimus.procesos.modelado.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.Correlacion;
import com.facimus.procesos.modelado.model.EventoMensaje;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.Mensaje;
import com.facimus.procesos.modelado.model.PoliticaFalloNotificacion;
import com.facimus.procesos.modelado.model.PoliticaMensajeSinCaso;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoDestinoExterno;
import com.facimus.procesos.modelado.model.TipoEventoMensaje;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.repository.MensajeRepository;

@ExtendWith(MockitoExtension.class)
class MensajeServiceTest {

    @Mock
    private MensajeRepository mensajeRepository;
    @Mock
    private PoolService poolService;
    @Mock
    private ProcesoService procesoService;
    @Mock
    private CorrelacionService correlacionService;
    @Mock
    private NodoFlujoService nodoFlujoService;
    @Mock
    private AuditoriaModeladoService auditoriaModeladoService;

    @InjectMocks
    private MensajeService mensajeService;

    private Empresa empresa;
    private Proceso proceso;
    private Pool poolOrigen;
    private Pool poolDestino;
    private Mensaje mensaje;

    @BeforeEach
    void setUp() {
        empresa = new Empresa();
        empresa.setId(1L);

        proceso = new Proceso();
        proceso.setId(10L);
        proceso.setEmpresa(empresa);

        poolOrigen = new Pool();
        poolOrigen.setId(100L);
        poolOrigen.setEmpresa(empresa);
        poolOrigen.setProceso(proceso);

        poolDestino = new Pool();
        poolDestino.setId(200L);
        poolDestino.setEmpresa(empresa);
        poolDestino.setProceso(proceso);

        mensaje = new Mensaje();
        mensaje.setId(1L);
        mensaje.setNombre("Orden");
        mensaje.setContenido("Datos de la orden");
        mensaje.setEmpresa(empresa);
        mensaje.setProceso(proceso);
        mensaje.setPoolOrigen(poolOrigen);
        mensaje.setPoolDestino(poolDestino);
        mensaje.setActivo(true);
    }


    @Test
    void crear_extendido_interno_con_throw_y_catch_funciona() {
        EventoMensaje throwEvt = evento(11L, TipoEventoMensaje.THROW, poolOrigen, "Orden", "pedidoId");
        EventoMensaje catchEvt = evento(12L, TipoEventoMensaje.CATCH_INTERMEDIO, poolDestino, "Orden", "pedidoId");
        prepararCreacionBase();
        when(nodoFlujoService.buscar(1L, 11L)).thenReturn(Optional.of(throwEvt));
        when(nodoFlujoService.buscar(1L, 12L)).thenReturn(Optional.of(catchEvt));
        when(mensajeRepository.findAllByProcesoIdAndEmpresaIdAndActivoTrue(10L, 1L)).thenReturn(List.of());
        when(mensajeRepository.save(any(Mensaje.class))).thenAnswer(inv -> inv.getArgument(0));
        when(correlacionService.guardar(any(Correlacion.class))).thenAnswer(inv -> inv.getArgument(0));

        Mensaje resultado = mensajeService.crear(
                1L, 10L, "Orden", "Datos", 100L, 200L,
                11L, 12L, "pedidoId", null, null, null, null,
                PoliticaMensajeSinCaso.DESCARTAR);

        assertSame(throwEvt, resultado.getEventoThrow());
        assertSame(catchEvt, resultado.getEventoCatch());
        assertEquals("pedidoId", resultado.getClaveCorrelacion());
    }

    @Test
    void crear_extendido_sin_throw_falla() {
        prepararCreacionBase();

        assertThrows(ReglaNegocioException.class, () ->
                mensajeService.crear(1L, 10L, "Orden", "Datos", 100L, 200L,
                        null, null, "pedidoId", null, null, null, null,
                        PoliticaMensajeSinCaso.DESCARTAR));
    }

    @Test
    void crear_throw_de_tipo_incorrecto_falla() {
        EventoMensaje catchEvt = evento(11L, TipoEventoMensaje.CATCH_INTERMEDIO, poolOrigen, "Orden", "pedidoId");
        prepararCreacionBase();
        when(nodoFlujoService.buscar(1L, 11L)).thenReturn(Optional.of(catchEvt));

        assertThrows(ReglaNegocioException.class, () ->
                mensajeService.crear(1L, 10L, "Orden", "Datos", 100L, 200L,
                        11L, null, "pedidoId", null, null, null, null,
                        PoliticaMensajeSinCaso.DESCARTAR));
    }

    @Test
    void crear_throw_en_pool_incorrecto_falla() {
        EventoMensaje throwEvt = evento(11L, TipoEventoMensaje.THROW, poolDestino, "Orden", "pedidoId");
        prepararCreacionBase();
        when(nodoFlujoService.buscar(1L, 11L)).thenReturn(Optional.of(throwEvt));

        assertThrows(ReglaNegocioException.class, () ->
                mensajeService.crear(1L, 10L, "Orden", "Datos", 100L, 200L,
                        11L, null, "pedidoId", null, null, null, null,
                        PoliticaMensajeSinCaso.DESCARTAR));
    }

    @Test
    void crear_throw_con_nombre_distinto_falla() {
        EventoMensaje throwEvt = evento(11L, TipoEventoMensaje.THROW, poolOrigen, "Otro", "pedidoId");
        prepararCreacionBase();
        when(nodoFlujoService.buscar(1L, 11L)).thenReturn(Optional.of(throwEvt));

        assertThrows(ReglaNegocioException.class, () ->
                mensajeService.crear(1L, 10L, "Orden", "Datos", 100L, 200L,
                        11L, null, "pedidoId", null, null, null, null,
                        PoliticaMensajeSinCaso.DESCARTAR));
    }

    @Test
    void crear_throw_con_correlacion_distinta_falla() {
        EventoMensaje throwEvt = evento(11L, TipoEventoMensaje.THROW, poolOrigen, "Orden", "otra");
        prepararCreacionBase();
        when(nodoFlujoService.buscar(1L, 11L)).thenReturn(Optional.of(throwEvt));

        assertThrows(ReglaNegocioException.class, () ->
                mensajeService.crear(1L, 10L, "Orden", "Datos", 100L, 200L,
                        11L, null, "pedidoId", null, null, null, null,
                        PoliticaMensajeSinCaso.DESCARTAR));
    }

    @Test
    void crear_destino_externo_valido_funciona() {
        poolDestino.setTipoParticipante(TipoParticipante.SISTEMA_EXTERNO);
        poolDestino.setCajaNegra(true);
        EventoMensaje throwEvt = evento(11L, TipoEventoMensaje.THROW, poolOrigen, "Orden", "pedidoId");
        prepararCreacionBase();
        when(nodoFlujoService.buscar(1L, 11L)).thenReturn(Optional.of(throwEvt));
        when(mensajeRepository.findAllByProcesoIdAndEmpresaIdAndActivoTrue(10L, 1L)).thenReturn(List.of());
        when(mensajeRepository.save(any(Mensaje.class))).thenAnswer(inv -> inv.getArgument(0));
        when(correlacionService.guardar(any(Correlacion.class))).thenAnswer(inv -> inv.getArgument(0));

        Mensaje resultado = mensajeService.crear(
                1L, 10L, "Orden", "Datos", 100L, 200L,
                11L, null, "pedidoId", TipoDestinoExterno.CORREO, "ops@demo.com",
                PoliticaFalloNotificacion.CONTINUAR_FLUJO, null, PoliticaMensajeSinCaso.DESCARTAR);

        assertEquals(TipoDestinoExterno.CORREO, resultado.getTipoDestinoExterno());
        assertEquals("ops@demo.com", resultado.getDestinoExterno());
    }

    @Test
    void destino_externo_requiere_pool_externo_caja_negra() {
        EventoMensaje throwEvt = evento(11L, TipoEventoMensaje.THROW, poolOrigen, "Orden", "pedidoId");
        prepararCreacionBase();
        when(nodoFlujoService.buscar(1L, 11L)).thenReturn(Optional.of(throwEvt));

        assertThrows(ReglaNegocioException.class, () ->
                mensajeService.crear(1L, 10L, "Orden", "Datos", 100L, 200L,
                        11L, null, "pedidoId", TipoDestinoExterno.CORREO, "ops@demo.com",
                        PoliticaFalloNotificacion.CONTINUAR_FLUJO, null, PoliticaMensajeSinCaso.DESCARTAR));
    }

    @Test
    void destino_externo_requiere_direccion_documentada() {
        poolDestino.setTipoParticipante(TipoParticipante.SISTEMA_EXTERNO);
        poolDestino.setCajaNegra(true);
        EventoMensaje throwEvt = evento(11L, TipoEventoMensaje.THROW, poolOrigen, "Orden", "pedidoId");
        prepararCreacionBase();
        when(nodoFlujoService.buscar(1L, 11L)).thenReturn(Optional.of(throwEvt));

        assertThrows(ReglaNegocioException.class, () ->
                mensajeService.crear(1L, 10L, "Orden", "Datos", 100L, 200L,
                        11L, null, "pedidoId", TipoDestinoExterno.CORREO, " ",
                        PoliticaFalloNotificacion.CONTINUAR_FLUJO, null, PoliticaMensajeSinCaso.DESCARTAR));
    }

    @Test
    void destino_externo_requiere_politica_de_fallo() {
        poolDestino.setTipoParticipante(TipoParticipante.SISTEMA_EXTERNO);
        poolDestino.setCajaNegra(true);
        EventoMensaje throwEvt = evento(11L, TipoEventoMensaje.THROW, poolOrigen, "Orden", "pedidoId");
        prepararCreacionBase();
        when(nodoFlujoService.buscar(1L, 11L)).thenReturn(Optional.of(throwEvt));

        assertThrows(ReglaNegocioException.class, () ->
                mensajeService.crear(1L, 10L, "Orden", "Datos", 100L, 200L,
                        11L, null, "pedidoId", TipoDestinoExterno.CORREO, "ops@demo.com",
                        null, null, PoliticaMensajeSinCaso.DESCARTAR));
    }

    @Test
    void derivar_error_requiere_actividad() {
        poolDestino.setTipoParticipante(TipoParticipante.SISTEMA_EXTERNO);
        poolDestino.setCajaNegra(true);
        EventoMensaje throwEvt = evento(11L, TipoEventoMensaje.THROW, poolOrigen, "Orden", "pedidoId");
        prepararCreacionBase();
        when(nodoFlujoService.buscar(1L, 11L)).thenReturn(Optional.of(throwEvt));

        assertThrows(ReglaNegocioException.class, () ->
                mensajeService.crear(1L, 10L, "Orden", "Datos", 100L, 200L,
                        11L, null, "pedidoId", TipoDestinoExterno.CORREO, "ops@demo.com",
                        PoliticaFalloNotificacion.DERIVAR_ACTIVIDAD_ERROR, null,
                        PoliticaMensajeSinCaso.DESCARTAR));
    }

    @Test
    void derivar_error_con_actividad_valida_funciona() {
        poolDestino.setTipoParticipante(TipoParticipante.SISTEMA_EXTERNO);
        poolDestino.setCajaNegra(true);
        EventoMensaje throwEvt = evento(11L, TipoEventoMensaje.THROW, poolOrigen, "Orden", "pedidoId");
        Actividad error = new Actividad();
        error.setId(50L);
        error.setActivo(true);
        Lane laneError = new Lane();
        laneError.setPool(poolOrigen);
        error.setLane(laneError);

        prepararCreacionBase();
        when(nodoFlujoService.buscar(1L, 11L)).thenReturn(Optional.of(throwEvt));
        when(nodoFlujoService.buscar(1L, 50L)).thenReturn(Optional.of(error));
        when(mensajeRepository.findAllByProcesoIdAndEmpresaIdAndActivoTrue(10L, 1L)).thenReturn(List.of());
        when(mensajeRepository.save(any(Mensaje.class))).thenAnswer(inv -> inv.getArgument(0));

        Mensaje resultado = mensajeService.crear(
                1L, 10L, "Orden", "Datos", 100L, 200L,
                11L, null, "pedidoId", TipoDestinoExterno.CORREO, "ops@demo.com",
                PoliticaFalloNotificacion.DERIVAR_ACTIVIDAD_ERROR, 50L,
                PoliticaMensajeSinCaso.DESCARTAR);

        assertSame(error, resultado.getActividadError());
    }

    @Test
    void mensaje_duplicado_por_nombre_y_clave_falla() {
        Mensaje existente = new Mensaje();
        existente.setId(90L);
        existente.setNombre("orden");
        existente.setClaveCorrelacion("idProceso");
        existente.setActivo(true);

        prepararCreacionBase();
        when(mensajeRepository.findAllByProcesoIdAndEmpresaIdAndActivoTrue(10L, 1L))
                .thenReturn(List.of(existente));

        assertThrows(ReglaNegocioException.class,
                () -> mensajeService.crear(1L, 10L, "Orden", "Datos", 100L, 200L));
    }

    @Test
    void politica_de_fallo_sin_destino_externo_falla() {
        EventoMensaje throwEvt = evento(11L, TipoEventoMensaje.THROW, poolOrigen, "Orden", "pedidoId");
        prepararCreacionBase();
        when(nodoFlujoService.buscar(1L, 11L)).thenReturn(Optional.of(throwEvt));

        assertThrows(ReglaNegocioException.class, () ->
                mensajeService.crear(1L, 10L, "Orden", "Datos", 100L, 200L,
                        11L, null, "pedidoId", null, null,
                        PoliticaFalloNotificacion.CONTINUAR_FLUJO, null,
                        PoliticaMensajeSinCaso.DESCARTAR));
    }

    @Test
    @DisplayName("Crear mensaje entre pools diferentes funciona")
    void crear_exitoso() {
        when(procesoService.obtener(1L, 10L)).thenReturn(proceso);
        when(poolService.obtener(1L, 100L)).thenReturn(poolOrigen);
        when(poolService.obtener(1L, 200L)).thenReturn(poolDestino);
        when(mensajeRepository.save(any(Mensaje.class))).thenAnswer(inv -> inv.getArgument(0));

        Mensaje resultado = mensajeService.crear(1L, 10L, "Orden", "Datos", 100L, 200L);

        assertEquals("Orden", resultado.getNombre());
    }

    @Test
    @DisplayName("Crear mensaje con mismo pool origen y destino lanza excepcion")
    void crear_mismo_pool() {
        assertThrows(ReglaNegocioException.class,
                () -> mensajeService.crear(1L, 10L, "Orden", "Datos", 100L, 100L));
    }

    @Test
    @DisplayName("Crear mensaje con proceso inexistente lanza excepcion")
    void crear_proceso_inexistente() {
        when(procesoService.obtener(1L, 10L)).thenThrow(new RecursoNoEncontradoException("Proceso no encontrado."));

        assertThrows(RecursoNoEncontradoException.class,
                () -> mensajeService.crear(1L, 10L, "Orden", "Datos", 100L, 200L));
    }

    @Test
    @DisplayName("Editar mensaje actualiza nombre y contenido")
    void editar_exitoso() {
        when(mensajeRepository.findByIdAndEmpresaId(1L, 1L)).thenReturn(Optional.of(mensaje));
        when(mensajeRepository.save(any(Mensaje.class))).thenAnswer(inv -> inv.getArgument(0));

        Mensaje resultado = mensajeService.editar(1L, 1L, "Orden v2", "Nuevos datos");

        assertEquals("Orden v2", resultado.getNombre());
        assertEquals("Nuevos datos", resultado.getContenido());
    }

    @Test
    @DisplayName("Edicion parcial conserva configuracion externa cuando campos opcionales llegan null")
    void editar_parcial_conserva_configuracion_externa() {
        poolDestino.setTipoParticipante(TipoParticipante.SISTEMA_EXTERNO);
        poolDestino.setCajaNegra(true);
        mensaje.setTipoDestinoExterno(TipoDestinoExterno.CORREO);
        mensaje.setDestinoExterno("operaciones@demo.com");
        mensaje.setPoliticaFalloNotificacion(PoliticaFalloNotificacion.CONTINUAR_FLUJO);
        mensaje.setPoliticaSinCaso(PoliticaMensajeSinCaso.DESCARTAR);

        when(mensajeRepository.findByIdAndEmpresaId(1L, 1L)).thenReturn(Optional.of(mensaje));
        when(mensajeRepository.findAllByProcesoIdAndEmpresaIdAndActivoTrue(10L, 1L)).thenReturn(List.of());
        when(correlacionService.buscarPorMensaje(1L, 1L)).thenReturn(Optional.empty());
        when(mensajeRepository.save(any(Mensaje.class))).thenAnswer(inv -> inv.getArgument(0));

        Mensaje resultado = mensajeService.editar(
                1L, 1L, "Orden", "Datos actualizados",
                null, null, null, null, null, null, null);

        assertEquals(TipoDestinoExterno.CORREO, resultado.getTipoDestinoExterno());
        assertEquals("operaciones@demo.com", resultado.getDestinoExterno());
        assertEquals(PoliticaFalloNotificacion.CONTINUAR_FLUJO, resultado.getPoliticaFalloNotificacion());
        assertEquals(PoliticaMensajeSinCaso.DESCARTAR, resultado.getPoliticaSinCaso());
    }

    @Test
    @DisplayName("Eliminar mensaje sin correlacion elimina solo el mensaje")
    void eliminar_sin_correlacion() {
        when(mensajeRepository.findByIdAndEmpresaId(1L, 1L)).thenReturn(Optional.of(mensaje));

        mensajeService.eliminar(1L, 1L);

        verify(correlacionService).eliminarPorMensaje(1L, 1L);
        verify(mensajeRepository).save(mensaje);
        assertFalse(mensaje.isActivo());
        verify(mensajeRepository, never()).delete(any(Mensaje.class));
    }

    @Test
    @DisplayName("Eliminar mensaje con correlacion elimina ambos")
    void eliminar_con_correlacion() {
        when(mensajeRepository.findByIdAndEmpresaId(1L, 1L)).thenReturn(Optional.of(mensaje));

        mensajeService.eliminar(1L, 1L);

        verify(correlacionService).eliminarPorMensaje(1L, 1L);
        verify(mensajeRepository).save(mensaje);
        assertFalse(mensaje.isActivo());
        verify(mensajeRepository, never()).delete(any(Mensaje.class));
    }

    @Test
    @DisplayName("Listar mensajes de proceso inexistente lanza excepcion")
    void listar_proceso_inexistente() {
        when(procesoService.existe(1L, 10L)).thenReturn(false);

        assertThrows(RecursoNoEncontradoException.class,
                () -> mensajeService.listarPorProceso(1L, 10L));
    }

    @Test
    @DisplayName("Listar mensajes de proceso existente retorna lista")
    void listar_exitoso() {
        when(procesoService.existe(1L, 10L)).thenReturn(true);
        when(mensajeRepository.findAllByProcesoIdAndEmpresaIdAndActivoTrue(10L, 1L)).thenReturn(List.of(mensaje));

        List<Mensaje> resultado = mensajeService.listarPorProceso(1L, 10L);

        assertEquals(1, resultado.size());
    }

    @Test
    @DisplayName("Obtener mensaje inexistente lanza excepcion")
    void obtener_inexistente() {
        when(mensajeRepository.findByIdAndEmpresaId(999L, 1L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class,
                () -> mensajeService.obtener(1L, 999L));
    }

    private void prepararCreacionBase() {
        when(procesoService.obtener(1L, 10L)).thenReturn(proceso);
        when(poolService.obtener(1L, 100L)).thenReturn(poolOrigen);
        when(poolService.obtener(1L, 200L)).thenReturn(poolDestino);
    }

    private EventoMensaje evento(Long id, TipoEventoMensaje tipo, Pool pool, String nombre, String clave) {
        Lane lane = new Lane();
        lane.setPool(pool);
        EventoMensaje evento = new EventoMensaje();
        evento.setId(id);
        evento.setActivo(true);
        evento.setTipoEvento(tipo);
        evento.setLane(lane);
        evento.setNombre(nombre);
        evento.setClaveCorrelacion(clave);
        return evento;
    }

}
