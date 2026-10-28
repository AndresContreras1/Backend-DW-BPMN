package com.facimus.procesos.modelado.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import com.facimus.procesos.config.ModelMapperConfig;
import com.facimus.procesos.modelado.controller.dto.CorrelacionRequest;
import com.facimus.procesos.modelado.controller.dto.CorrelacionResponse;
import com.facimus.procesos.modelado.model.Correlacion;
import com.facimus.procesos.modelado.model.Mensaje;

/** Metodo de CorrelacionOrquestadorService que devuelve el DTO de respuesta. */
@ExtendWith(MockitoExtension.class)
class CorrelacionOrquestadorServiceMapeoTest {

    @Mock
    private MensajeService mensajeService;
    @Mock
    private CorrelacionService correlacionService;
    @Spy
    private ModelMapper modelMapper = new ModelMapperConfig().modelMapper();

    @Spy
    @InjectMocks
    private CorrelacionOrquestadorServiceImpl correlacionOrquestadorService;

    @Test
    void CorrelacionOrquestadorServiceImpl_definirConRequest_devuelveResponse() {
        Mensaje mensaje = new Mensaje();
        mensaje.setId(5L);
        Correlacion c = new Correlacion();
        c.setId(1L);
        c.setCriterio("orderId");
        c.setMensaje(mensaje);
        doReturn(c).when(correlacionOrquestadorService).definir(1L, 5L, "orderId");

        CorrelacionResponse r = correlacionOrquestadorService.definir(1L, 5L, new CorrelacionRequest("orderId"));

        assertThat(r.id()).isEqualTo(1L);
        assertThat(r.criterio()).isEqualTo("orderId");
        assertThat(r.mensajeId()).isEqualTo(5L);
    }
}
