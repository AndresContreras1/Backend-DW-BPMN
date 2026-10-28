package com.facimus.procesos.modelado.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import com.facimus.procesos.config.ModelMapperConfig;
import com.facimus.procesos.modelado.controller.dto.ActividadRequest;
import com.facimus.procesos.modelado.controller.dto.ActividadResponse;
import com.facimus.procesos.modelado.controller.dto.ImpactoEliminacionResponse;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.service.dto.ImpactoEliminacion;

/** Metodos de ActividadService que devuelven el DTO de respuesta (mapeo dentro del service). */
@ExtendWith(MockitoExtension.class)
class ActividadServiceMapeoTest {

    @Mock
    private NodoFlujoService nodoFlujoService;
    @Mock
    private LaneService laneService;
    @Mock
    private ArcoService arcoService;
    @Mock
    private AuditoriaModeladoService auditoriaModeladoService;
    @Spy
    private ModelMapper modelMapper = new ModelMapperConfig().modelMapper();

    @Spy
    @InjectMocks
    private ActividadServiceImpl actividadService;

    @Test
    void ActividadServiceImpl_crearSinTipo_usaFirmaCortaYDevuelveResponse() {
        doReturn(actividad(1L, "Revisar")).when(actividadService).crear(1L, 3L, "Revisar", "Desc", 100, 200);

        ActividadResponse r = actividadService.crear(1L, 3L,
                new ActividadRequest("Revisar", "Desc", 100, 200, null, null));

        assertThat(r.id()).isEqualTo(1L);
        assertThat(r.laneId()).isEqualTo(3L);
        assertThat(r.tipoActividad()).isEqualTo(TipoActividad.TAREA);
        verify(actividadService, never()).crear(anyLong(), anyLong(), anyString(), anyString(), anyInt(), anyInt(),
                any(TipoActividad.class));
    }

    @Test
    void ActividadServiceImpl_crearConTipo_usaFirmaConTipoYDevuelveResponse() {
        Actividad a = actividad(1L, "Revisar");
        a.setTipoActividad(TipoActividad.SERVICIO);
        doReturn(a).when(actividadService).crear(1L, 3L, "Revisar", "Desc", 100, 200, TipoActividad.SERVICIO);

        ActividadResponse r = actividadService.crear(1L, 3L,
                new ActividadRequest("Revisar", "Desc", 100, 200, null, TipoActividad.SERVICIO));

        assertThat(r.tipoActividad()).isEqualTo(TipoActividad.SERVICIO);
    }

    @Test
    void ActividadServiceImpl_editarSinTipo_usaFirmaCortaYDevuelveResponse() {
        doReturn(actividad(1L, "v2")).when(actividadService).editar(1L, 1L, "v2", "Desc", 100, 200, 3L);

        ActividadResponse r = actividadService.editar(1L, 1L,
                new ActividadRequest("v2", "Desc", 100, 200, 3L, null));

        assertThat(r.nombre()).isEqualTo("v2");
        verify(actividadService, never()).editar(anyLong(), anyLong(), anyString(), anyString(), anyInt(), anyInt(),
                anyLong(), any(TipoActividad.class));
    }

    @Test
    void ActividadServiceImpl_editarConTipo_usaFirmaConTipoYDevuelveResponse() {
        Actividad a = actividad(1L, "v2");
        a.setTipoActividad(TipoActividad.MANUAL);
        doReturn(a).when(actividadService).editar(1L, 1L, "v2", "Desc", 100, 200, 3L, TipoActividad.MANUAL);

        ActividadResponse r = actividadService.editar(1L, 1L,
                new ActividadRequest("v2", "Desc", 100, 200, 3L, TipoActividad.MANUAL));

        assertThat(r.tipoActividad()).isEqualTo(TipoActividad.MANUAL);
    }

    @Test
    void ActividadServiceImpl_consultarDetalle_devuelveResponse() {
        doReturn(actividad(1L, "Revisar")).when(actividadService).obtener(1L, 1L);

        ActividadResponse r = actividadService.consultarDetalle(1L, 1L);

        assertThat(r.nombre()).isEqualTo("Revisar");
        assertThat(r.posicionX()).isEqualTo(100);
    }

    @Test
    void ActividadServiceImpl_consultarPorLane_devuelveListaDeResponses() {
        doReturn(List.of(actividad(1L, "A"), actividad(2L, "B"))).when(actividadService).listarPorLane(1L, 3L);

        List<ActividadResponse> r = actividadService.consultarPorLane(1L, 3L);

        assertThat(r).extracting(ActividadResponse::nombre).containsExactly("A", "B");
    }

    @Test
    void ActividadServiceImpl_consultarImpactoEliminacion_devuelveResponseConConfirmacion() {
        doReturn(new ImpactoEliminacion(true, List.of("Quedara sin salida")))
                .when(actividadService).evaluarImpactoEliminacion(1L, 1L);

        ImpactoEliminacionResponse r = actividadService.consultarImpactoEliminacion(1L, 1L);

        assertThat(r.requiereConfirmacion()).isTrue();
        assertThat(r.rompeContinuidad()).isTrue();
        assertThat(r.advertencias()).containsExactly("Quedara sin salida");
    }

    private Actividad actividad(Long id, String nombre) {
        Lane lane = new Lane();
        lane.setId(3L);
        Actividad a = new Actividad();
        a.setId(id);
        a.setNombre(nombre);
        a.setDescripcion("Desc");
        a.setPosicionX(100);
        a.setPosicionY(200);
        a.setLane(lane);
        return a;
    }
}
