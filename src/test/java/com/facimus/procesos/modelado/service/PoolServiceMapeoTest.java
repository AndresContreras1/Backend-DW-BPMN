package com.facimus.procesos.modelado.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
import org.springframework.context.ApplicationEventPublisher;

import com.facimus.procesos.config.ModelMapperConfig;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.modelado.controller.dto.EditarPoolRequest;
import com.facimus.procesos.modelado.controller.dto.PoolRequest;
import com.facimus.procesos.modelado.controller.dto.PoolResponse;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.repository.PoolRepository;

/** Metodos de PoolService que devuelven el DTO de respuesta (mapeo dentro del service). */
@ExtendWith(MockitoExtension.class)
class PoolServiceMapeoTest {

    @Mock
    private PoolRepository poolRepository;
    @Mock
    private ProcesoService procesoService;
    @Mock
    private NodoFlujoService nodoFlujoService;
    @Mock
    private AuditoriaModeladoService auditoriaModeladoService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Spy
    private ModelMapper modelMapper = new ModelMapperConfig().modelMapper();

    @Spy
    @InjectMocks
    private PoolServiceImpl poolService;

    @Test
    void PoolServiceImpl_crearConRequest_devuelveResponse() {
        Pool pool = pool(2L, "Proveedor");
        doReturn(pool).when(poolService).crear(1L, 10L, "Proveedor", TipoParticipante.CLIENTE, true);

        PoolResponse r = poolService.crear(1L, 10L, new PoolRequest("Proveedor", TipoParticipante.CLIENTE, true));

        assertThat(r.id()).isEqualTo(2L);
        assertThat(r.nombre()).isEqualTo("Proveedor");
        assertThat(r.procesoId()).isEqualTo(10L);
    }

    @Test
    void PoolServiceImpl_editarSinCajaNegra_usaFirmaCortaYDevuelveResponse() {
        Pool pool = pool(5L, "Editado");
        doReturn(pool).when(poolService).editar(1L, 5L, "Editado", TipoParticipante.CLIENTE);

        PoolResponse r = poolService.editar(1L, 5L, new EditarPoolRequest("Editado", TipoParticipante.CLIENTE));

        assertThat(r.nombre()).isEqualTo("Editado");
        verify(poolService, never()).editar(anyLong(), anyLong(), anyString(), any(), anyBoolean());
    }

    @Test
    void PoolServiceImpl_editarConCajaNegra_usaFirmaLargaYDevuelveResponse() {
        Pool pool = pool(5L, "Caja");
        pool.setCajaNegra(true);
        doReturn(pool).when(poolService).editar(1L, 5L, "Caja", TipoParticipante.CLIENTE, true);

        PoolResponse r = poolService.editar(1L, 5L, new EditarPoolRequest("Caja", TipoParticipante.CLIENTE, true));

        assertThat(r.cajaNegra()).isTrue();
    }

    @Test
    void PoolServiceImpl_consultarPorProceso_devuelveListaDeResponses() {
        doReturn(List.of(pool(1L, "A"), pool(2L, "B"))).when(poolService).listarPorProceso(1L, 10L);

        List<PoolResponse> r = poolService.consultarPorProceso(1L, 10L);

        assertThat(r).extracting(PoolResponse::nombre).containsExactly("A", "B");
    }

    @Test
    void PoolServiceImpl_consultarDetalle_devuelveResponse() {
        doReturn(pool(1L, "A")).when(poolService).obtener(1L, 1L);

        PoolResponse r = poolService.consultarDetalle(1L, 1L);

        assertThat(r.id()).isEqualTo(1L);
        assertThat(r.nombre()).isEqualTo("A");
    }

    private Pool pool(Long id, String nombre) {
        Proceso proceso = new Proceso();
        proceso.setId(10L);
        Pool pool = new Pool();
        pool.setId(id);
        pool.setNombre(nombre);
        pool.setTipoParticipante(TipoParticipante.CLIENTE);
        pool.setProceso(proceso);
        return pool;
    }
}
