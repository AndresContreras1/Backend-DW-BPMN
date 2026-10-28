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
import com.facimus.procesos.modelado.controller.dto.CorrelacionResponse;
import com.facimus.procesos.modelado.model.Correlacion;
import com.facimus.procesos.modelado.model.Mensaje;
import com.facimus.procesos.modelado.repository.CorrelacionRepository;

/** Metodos de CorrelacionService que devuelven el DTO de respuesta (mapeo dentro del service). */
@ExtendWith(MockitoExtension.class)
class CorrelacionServiceMapeoTest {

    @Mock
    private CorrelacionRepository correlacionRepository;
    @Mock
    private AuditoriaModeladoService auditoriaModeladoService;
    @Spy
    private ModelMapper modelMapper = new ModelMapperConfig().modelMapper();

    @Spy
    @InjectMocks
    private CorrelacionServiceImpl correlacionService;

    @Test
    void CorrelacionServiceImpl_consultarPorMensaje_devuelveResponse() {
        Mensaje mensaje = new Mensaje();
        mensaje.setId(5L);
        Correlacion c = new Correlacion();
        c.setId(1L);
        c.setCriterio("customerId");
        c.setMensaje(mensaje);
        doReturn(c).when(correlacionService).obtener(1L, 5L);

        CorrelacionResponse r = correlacionService.consultarPorMensaje(1L, 5L);

        assertThat(r.id()).isEqualTo(1L);
        assertThat(r.criterio()).isEqualTo("customerId");
        assertThat(r.mensajeId()).isEqualTo(5L);
    }
}
