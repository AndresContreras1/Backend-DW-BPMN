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
import com.facimus.procesos.modelado.controller.dto.GatewayRequest;
import com.facimus.procesos.modelado.controller.dto.GatewayResponse;
import com.facimus.procesos.modelado.controller.dto.ImpactoEliminacionResponse;
import com.facimus.procesos.modelado.model.Gateway;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.service.dto.ImpactoEliminacion;

/** Metodos de GatewayService que devuelven el DTO de respuesta (mapeo dentro del service). */
@ExtendWith(MockitoExtension.class)
class GatewayServiceMapeoTest {

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
    private GatewayServiceImpl gatewayService;

    @Test
    void GatewayServiceImpl_crearConRequest_devuelveResponse() {
        doReturn(gateway(1L, "Decision")).when(gatewayService)
                .crear(1L, 3L, "Decision", TipoGateway.EXCLUSIVO, 300, 150);

        GatewayResponse r = gatewayService.crear(1L, 3L,
                new GatewayRequest("Decision", TipoGateway.EXCLUSIVO, 300, 150));

        assertThat(r.id()).isEqualTo(1L);
        assertThat(r.laneId()).isEqualTo(3L);
        assertThat(r.tipoGateway()).isEqualTo(TipoGateway.EXCLUSIVO);
    }

    @Test
    void GatewayServiceImpl_editarConRequest_devuelveResponse() {
        doReturn(gateway(1L, "Nuevo")).when(gatewayService)
                .editar(1L, 1L, "Nuevo", TipoGateway.EXCLUSIVO, 300, 150, null);

        GatewayResponse r = gatewayService.editar(1L, 1L,
                new GatewayRequest("Nuevo", TipoGateway.EXCLUSIVO, 300, 150));

        assertThat(r.nombre()).isEqualTo("Nuevo");
    }

    @Test
    void GatewayServiceImpl_consultarDetalle_devuelveResponse() {
        doReturn(gateway(1L, "Decision")).when(gatewayService).obtener(1L, 1L);

        assertThat(gatewayService.consultarDetalle(1L, 1L).nombre()).isEqualTo("Decision");
    }

    @Test
    void GatewayServiceImpl_consultarPorLane_devuelveListaDeResponses() {
        doReturn(List.of(gateway(1L, "A"), gateway(2L, "B"))).when(gatewayService).listarPorLane(1L, 3L);

        List<GatewayResponse> r = gatewayService.consultarPorLane(1L, 3L);

        assertThat(r).extracting(GatewayResponse::nombre).containsExactly("A", "B");
    }

    @Test
    void GatewayServiceImpl_consultarImpactoEliminacion_devuelveResponseConConfirmacion() {
        doReturn(new ImpactoEliminacion(false, List.of("Aviso"))).when(gatewayService)
                .evaluarImpactoEliminacion(1L, 1L);

        ImpactoEliminacionResponse r = gatewayService.consultarImpactoEliminacion(1L, 1L);

        assertThat(r.requiereConfirmacion()).isTrue();
        assertThat(r.rompeContinuidad()).isFalse();
        assertThat(r.advertencias()).containsExactly("Aviso");
    }

    private Gateway gateway(Long id, String nombre) {
        Lane lane = new Lane();
        lane.setId(3L);
        Gateway g = new Gateway();
        g.setId(id);
        g.setNombre(nombre);
        g.setTipoGateway(TipoGateway.EXCLUSIVO);
        g.setPosicionX(300);
        g.setPosicionY(150);
        g.setLane(lane);
        return g;
    }
}
