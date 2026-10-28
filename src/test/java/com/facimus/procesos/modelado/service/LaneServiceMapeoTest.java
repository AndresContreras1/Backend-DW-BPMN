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
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.modelado.controller.dto.LaneResponse;
import com.facimus.procesos.modelado.controller.dto.ReordenarLanesRequest;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.repository.LaneRepository;

/** Metodos de LaneService que devuelven el DTO de respuesta (mapeo dentro del service). */
@ExtendWith(MockitoExtension.class)
class LaneServiceMapeoTest {

    @Mock
    private LaneRepository laneRepository;
    @Mock
    private PoolService poolService;
    @Mock
    private NodoFlujoService nodoFlujoService;
    @Mock
    private AuditoriaModeladoService auditoriaModeladoService;
    @Spy
    private ModelMapper modelMapper = new ModelMapperConfig().modelMapper();

    @Spy
    @InjectMocks
    private LaneServiceImpl laneService;

    @Test
    void LaneServiceImpl_reordenarConRequest_devuelveResponsesEnElNuevoOrden() {
        doReturn(List.of(lane(2L, "Segunda"), lane(1L, "Primera")))
                .when(laneService).reordenar(1L, 5L, List.of(2L, 1L));

        List<LaneResponse> r = laneService.reordenar(1L, 5L, new ReordenarLanesRequest(List.of(2L, 1L)));

        assertThat(r).extracting(LaneResponse::nombre).containsExactly("Segunda", "Primera");
        assertThat(r.get(0).rolProcesoNombre()).isEqualTo("Analista");
    }

    @Test
    void LaneServiceImpl_consultarPorPool_devuelveListaDeResponses() {
        doReturn(List.of(lane(1L, "A"))).when(laneService).listarPorPool(1L, 5L);

        List<LaneResponse> r = laneService.consultarPorPool(1L, 5L);

        assertThat(r).hasSize(1);
        assertThat(r.get(0).poolId()).isEqualTo(5L);
    }

    @Test
    void LaneServiceImpl_consultarDetalle_devuelveResponse() {
        doReturn(lane(1L, "A")).when(laneService).obtener(1L, 1L);

        LaneResponse r = laneService.consultarDetalle(1L, 1L);

        assertThat(r.id()).isEqualTo(1L);
        assertThat(r.rolProcesoId()).isEqualTo(7L);
    }

    private Lane lane(Long id, String nombre) {
        Pool pool = new Pool();
        pool.setId(5L);
        RolProceso rol = new RolProceso();
        rol.setId(7L);
        rol.setNombre("Analista");
        Lane lane = new Lane();
        lane.setId(id);
        lane.setNombre(nombre);
        lane.setPool(pool);
        lane.setRolProceso(rol);
        return lane;
    }
}
