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
import com.facimus.procesos.modelado.controller.dto.ArcoRequest;
import com.facimus.procesos.modelado.controller.dto.ArcoResponse;
import com.facimus.procesos.modelado.controller.dto.EditarArcoRequest;
import com.facimus.procesos.modelado.controller.dto.ImpactoEliminacionResponse;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.Arco;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.repository.ArcoRepository;
import com.facimus.procesos.modelado.service.dto.ImpactoEliminacion;

/** Metodos de ArcoService que devuelven el DTO de respuesta (mapeo dentro del service). */
@ExtendWith(MockitoExtension.class)
class ArcoServiceMapeoTest {

    @Mock
    private ArcoRepository arcoRepository;
    @Mock
    private NodoFlujoService nodoFlujoService;
    @Mock
    private PoolService poolService;
    @Mock
    private AuditoriaModeladoService auditoriaModeladoService;
    @Spy
    private ModelMapper modelMapper = new ModelMapperConfig().modelMapper();

    @Spy
    @InjectMocks
    private ArcoServiceImpl arcoService;

    @Test
    void ArcoServiceImpl_crearConRequest_devuelveResponse() {
        doReturn(arco(1L)).when(arcoService).crear(1L, 10L, 20L, "si", "aprobado");

        ArcoResponse r = arcoService.crear(1L, new ArcoRequest(10L, 20L, "si", "aprobado"));

        assertThat(r.id()).isEqualTo(1L);
        assertThat(r.origenId()).isEqualTo(10L);
        assertThat(r.destinoId()).isEqualTo(20L);
        assertThat(r.poolId()).isEqualTo(5L);
    }

    @Test
    void ArcoServiceImpl_editarConRequest_devuelveResponse() {
        doReturn(arco(1L)).when(arcoService).editar(1L, 1L, "si", "aprobado", 10L, 20L);

        ArcoResponse r = arcoService.editar(1L, 1L, new EditarArcoRequest("si", "aprobado", 10L, 20L));

        assertThat(r.etiqueta()).isEqualTo("si");
    }

    @Test
    void ArcoServiceImpl_consultarDetalle_devuelveResponse() {
        doReturn(arco(1L)).when(arcoService).obtener(1L, 1L);

        assertThat(arcoService.consultarDetalle(1L, 1L).condicion()).isEqualTo("aprobado");
    }

    @Test
    void ArcoServiceImpl_consultarPorPool_devuelveListaDeResponses() {
        doReturn(List.of(arco(1L), arco(2L))).when(arcoService).listarPorPool(1L, 5L);

        List<ArcoResponse> r = arcoService.consultarPorPool(1L, 5L);

        assertThat(r).extracting(ArcoResponse::id).containsExactly(1L, 2L);
    }

    @Test
    void ArcoServiceImpl_consultarImpactoEliminacion_devuelveResponseConConfirmacion() {
        doReturn(new ImpactoEliminacion(true, List.of("Nodo desconectado")))
                .when(arcoService).evaluarImpactoEliminacion(1L, 1L);

        ImpactoEliminacionResponse r = arcoService.consultarImpactoEliminacion(1L, 1L);

        assertThat(r.requiereConfirmacion()).isTrue();
        assertThat(r.rompeContinuidad()).isTrue();
        assertThat(r.advertencias()).containsExactly("Nodo desconectado");
    }

    private Arco arco(Long id) {
        Pool pool = new Pool();
        pool.setId(5L);
        Actividad origen = new Actividad();
        origen.setId(10L);
        Actividad destino = new Actividad();
        destino.setId(20L);
        Arco arco = new Arco();
        arco.setId(id);
        arco.setEtiqueta("si");
        arco.setCondicion("aprobado");
        arco.setOrigen(origen);
        arco.setDestino(destino);
        arco.setPool(pool);
        return arco;
    }
}
