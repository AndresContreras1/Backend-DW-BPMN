package com.facimus.procesos.transaccionalidad;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.modelado.model.Mensaje;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.repository.CorrelacionRepository;
import com.facimus.procesos.modelado.repository.MensajeRepository;
import com.facimus.procesos.modelado.service.CorrelacionOrquestadorService;
import com.facimus.procesos.modelado.service.MensajeService;
import com.facimus.procesos.modelado.service.PoolService;

/**
 * Prueba de integracion pedida por la review del PR #37 (hallazgo bloqueante en
 * CorrelacionController): si falla guardar el mensaje con la nueva clave de correlacion, la
 * correlacion tampoco debe quedar persistida. Usa el contexto Spring real y la base H2 real;
 * solo MensajeService se reemplaza por un mock (stubando obtener/listarTodosPorProceso con el
 * fixture real y forzando la excepcion en guardar), para que CorrelacionService haga una
 * escritura real que despues se debe revertir.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:correlacion-mensaje-it;DB_CLOSE_DELAY=-1")
class CorrelacionMensajeTransaccionalTest {

    @Autowired
    private CorrelacionOrquestadorService correlacionOrquestadorService;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private ProcesoRepository procesoRepository;

    @Autowired
    private PoolService poolService;

    @Autowired
    private MensajeRepository mensajeRepository;

    @Autowired
    private CorrelacionRepository correlacionRepository;

    @MockitoBean
    private MensajeService mensajeService;

    @Test
    @DisplayName("Si falla guardar el mensaje, la correlacion no queda persistida (rollback real)")
    void definir_revierte_la_correlacion_si_falla_guardar_el_mensaje() {
        Empresa empresa = empresaService.registrar("Acme Correlacion", "900777777", "info@acme-correlacion.com");

        Proceso proceso = new Proceso();
        proceso.setEmpresa(empresa);
        proceso.setNombre("Proceso con mensaje");
        proceso.setDescripcion("desc");
        proceso.setCategoria("cat");
        proceso.setEstado(EstadoProceso.BORRADOR);
        proceso.setActivo(true);
        proceso.setFechaCreacion(LocalDateTime.now());
        proceso.setFechaModificacion(LocalDateTime.now());
        proceso = procesoRepository.save(proceso);

        Pool poolOrigen = poolService.crear(empresa.getId(), proceso.getId(), "Origen", TipoParticipante.EMPRESA, false);
        Pool poolDestino = poolService.crear(empresa.getId(), proceso.getId(), "Destino", TipoParticipante.CLIENTE, false);

        Mensaje mensaje = new Mensaje();
        mensaje.setEmpresa(empresa);
        mensaje.setProceso(proceso);
        mensaje.setNombre("Orden");
        mensaje.setContenido("contenido");
        mensaje.setPoolOrigen(poolOrigen);
        mensaje.setPoolDestino(poolDestino);
        mensaje.setActivo(true);
        mensaje = mensajeRepository.save(mensaje);

        Long empresaId = empresa.getId();
        Long mensajeId = mensaje.getId();

        when(mensajeService.obtener(empresaId, mensajeId)).thenReturn(mensaje);
        when(mensajeService.listarTodosPorProceso(empresaId, proceso.getId())).thenReturn(List.of(mensaje));
        when(mensajeService.guardar(any(Mensaje.class))).thenThrow(new RuntimeException("fallo forzado"));

        assertThrows(RuntimeException.class,
                () -> correlacionOrquestadorService.definir(empresaId, mensajeId, "orderId"));

        assertTrue(correlacionRepository.findByMensajeIdAndEmpresaId(mensajeId, empresaId).isEmpty(),
                "La correlacion no debio quedar persistida si el mensaje no se pudo guardar");
    }
}
