package com.facimus.procesos.modelado.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.config.ModelMapperConfig;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.modelado.controller.dto.LaneRequest;
import com.facimus.procesos.modelado.controller.dto.LaneResponse;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.Pool;

@ExtendWith(MockitoExtension.class)
class LaneOrquestadorServiceTest {

    @Mock
    private LaneService laneService;
    @Mock
    private RolProcesoService rolProcesoService;
    @Spy
    private ModelMapper modelMapper = new ModelMapperConfig().modelMapper();

    @InjectMocks
    private LaneOrquestadorServiceImpl laneOrquestadorService;

    @Test
    void LaneOrquestadorServiceImpl_crear_resuelveElRolYDevuelveResponse() {
        RolProceso rol = rol();
        when(rolProcesoService.obtener(1L, 7L)).thenReturn(rol);
        when(laneService.crear(1L, 5L, "Analisis", rol)).thenReturn(lane(2L, "Analisis", rol));

        LaneResponse r = laneOrquestadorService.crear(1L, 5L, new LaneRequest("Analisis", 7L));

        assertThat(r.id()).isEqualTo(2L);
        assertThat(r.nombre()).isEqualTo("Analisis");
        assertThat(r.rolProcesoId()).isEqualTo(7L);
        assertThat(r.poolId()).isEqualTo(5L);
    }

    @Test
    void LaneOrquestadorServiceImpl_crearConRolInexistente_propagaErrorYNoCreaLane() {
        when(rolProcesoService.obtener(1L, 7L))
                .thenThrow(new RecursoNoEncontradoException("Rol de proceso no encontrado."));
        LaneRequest request = new LaneRequest("Analisis", 7L);

        assertThatThrownBy(() -> laneOrquestadorService.crear(1L, 5L, request))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(laneService, never()).crear(1L, 5L, "Analisis", null);
    }

    @Test
    void LaneOrquestadorServiceImpl_editar_resuelveElRolYDevuelveResponse() {
        RolProceso rol = rol();
        when(rolProcesoService.obtener(1L, 7L)).thenReturn(rol);
        when(laneService.editar(1L, 2L, "Nuevo", rol)).thenReturn(lane(2L, "Nuevo", rol));

        LaneResponse r = laneOrquestadorService.editar(1L, 2L, new LaneRequest("Nuevo", 7L));

        assertThat(r.nombre()).isEqualTo("Nuevo");
        assertThat(r.rolProcesoNombre()).isEqualTo("Analista");
    }

    private RolProceso rol() {
        RolProceso rol = new RolProceso();
        rol.setId(7L);
        rol.setNombre("Analista");
        return rol;
    }

    private Lane lane(Long id, String nombre, RolProceso rol) {
        Pool pool = new Pool();
        pool.setId(5L);
        Lane lane = new Lane();
        lane.setId(id);
        lane.setNombre(nombre);
        lane.setPool(pool);
        lane.setRolProceso(rol);
        return lane;
    }
}
