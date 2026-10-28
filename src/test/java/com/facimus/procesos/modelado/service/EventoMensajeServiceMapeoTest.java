package com.facimus.procesos.modelado.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import com.facimus.procesos.config.ModelMapperConfig;
import com.facimus.procesos.modelado.controller.dto.EventoMensajeRequest;
import com.facimus.procesos.modelado.controller.dto.EventoMensajeResponse;
import com.facimus.procesos.modelado.model.EventoMensaje;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.TipoEventoMensaje;

/** Metodos de EventoMensajeService que devuelven el DTO de respuesta (mapeo dentro del service). */
@ExtendWith(MockitoExtension.class)
class EventoMensajeServiceMapeoTest {

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
    @Spy
    private ModelMapper modelMapper = new ModelMapperConfig().modelMapper();

    @Spy
    @InjectMocks
    private EventoMensajeServiceImpl eventoMensajeService;

    @Test
    void EventoMensajeServiceImpl_crearConRequest_devuelveResponse() {
        doReturn(evento(5L, "Orden", TipoEventoMensaje.THROW)).when(eventoMensajeService)
                .crear(1L, 2L, "Orden", TipoEventoMensaje.THROW, "payload", "pedidoId", 10, 20, false, null);

        EventoMensajeResponse r = eventoMensajeService.crear(1L, 2L, new EventoMensajeRequest("Orden",
                TipoEventoMensaje.THROW, "payload", "pedidoId", 10, 20, false));

        assertThat(r.id()).isEqualTo(5L);
        assertThat(r.laneId()).isEqualTo(2L);
        assertThat(r.tipoEvento()).isEqualTo(TipoEventoMensaje.THROW);
    }

    @Test
    void EventoMensajeServiceImpl_editarConRequest_devuelveResponse() {
        doReturn(evento(5L, "Orden v2", TipoEventoMensaje.CATCH_INTERMEDIO)).when(eventoMensajeService)
                .editar(1L, 5L, "Orden v2", TipoEventoMensaje.CATCH_INTERMEDIO, "payload", "pedidoId", 10, 20, false, null,
                        null);

        EventoMensajeResponse r = eventoMensajeService.editar(1L, 5L, new EventoMensajeRequest("Orden v2",
                TipoEventoMensaje.CATCH_INTERMEDIO, "payload", "pedidoId", 10, 20, false));

        assertThat(r.nombre()).isEqualTo("Orden v2");
    }

    @Test
    void EventoMensajeServiceImpl_consultarDetalle_devuelveResponse() {
        doReturn(evento(5L, "Orden", TipoEventoMensaje.THROW)).when(eventoMensajeService).obtener(1L, 5L);

        assertThat(eventoMensajeService.consultarDetalle(1L, 5L).nombre()).isEqualTo("Orden");
    }

    @Test
    void EventoMensajeServiceImpl_consultarPorLane_devuelveListaDeResponses() {
        doReturn(List.of(evento(5L, "A", TipoEventoMensaje.THROW), evento(6L, "B", TipoEventoMensaje.THROW)))
                .when(eventoMensajeService).listarPorLane(1L, 2L);

        List<EventoMensajeResponse> r = eventoMensajeService.consultarPorLane(1L, 2L);

        assertThat(r).extracting(EventoMensajeResponse::nombre).containsExactly("A", "B");
    }

    private EventoMensaje evento(Long id, String nombre, TipoEventoMensaje tipo) {
        Lane lane = new Lane();
        lane.setId(2L);
        EventoMensaje e = new EventoMensaje();
        e.setId(id);
        e.setNombre(nombre);
        e.setTipoEvento(tipo);
        e.setContenido("payload");
        e.setClaveCorrelacion("pedidoId");
        e.setPosicionX(10);
        e.setPosicionY(20);
        e.setLane(lane);
        return e;
    }
}
