package com.facimus.procesos.gestion.service;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.event.ProcesoPublicacionEvent;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.ProcesoRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcesoServiceTest {

    @Mock
    private ProcesoRepository procesoRepository;
    @Mock
    private EmpresaService empresaService;
    @Mock
    private UsuarioService usuarioService;
    @Mock
    private HistorialCambioService historialCambioService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ProcesoService procesoService;

    private Empresa empresa;
    private Usuario usuario;
    private Proceso proceso;

    @BeforeEach
    void setUp() {
        empresa = new Empresa();
        empresa.setId(1L);
        empresa.setNombre("Acme");

        usuario = new Usuario();
        usuario.setId(10L);
        usuario.setEmpresa(empresa);

        proceso = new Proceso();
        proceso.setId(100L);
        proceso.setEmpresa(empresa);
        proceso.setNombre("Compras");
        proceso.setEstado(EstadoProceso.BORRADOR);
        proceso.setActivo(true);
    }

    @Test
    @DisplayName("HU-04: crear proceso en BORRADOR")
    void crear_exitoso() {
        // La creacion del Pool inicial ya no la hace este servicio (se movio a
        // ProcesoOrquestadorService, para evitar el ciclo ProcesoService<->PoolService);
        // se prueba en ProcesoOrquestadorServiceTest.
        when(procesoRepository.existsByEmpresaIdAndNombreIgnoreCaseAndActivoTrue(1L, "Compras")).thenReturn(false);
        when(empresaService.obtener(1L)).thenReturn(empresa);
        when(usuarioService.obtener(1L, 10L)).thenReturn(usuario);
        when(procesoRepository.save(any(Proceso.class))).thenAnswer(inv -> {
            Proceso p = inv.getArgument(0);
            p.setId(100L);
            return p;
        });

        Proceso result = procesoService.crear(1L, 10L, "Compras", "Proceso de compras", "Operativo");

        assertEquals(EstadoProceso.BORRADOR, result.getEstado());
        assertTrue(result.isActivo());
        assertSame(empresa, result.getEmpresa());

        verify(historialCambioService).registrar(eq(result), eq(usuario), anyString());
    }

    @Test
    @DisplayName("HU-04: nombre duplicado en empresa lanza excepcion")
    void crear_nombre_duplicado() {
        when(procesoRepository.existsByEmpresaIdAndNombreIgnoreCaseAndActivoTrue(1L, "Compras")).thenReturn(true);

        assertThrows(ReglaNegocioException.class,
                () -> procesoService.crear(1L, 10L, "Compras", "desc", "cat"));

        verify(procesoRepository, never()).save(any());
    }

    @Test
    @DisplayName("HU-05: publicar cambia estado a PUBLICADO")
    void publicar_exitoso() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(100L, 1L)).thenReturn(Optional.of(proceso));
        when(usuarioService.obtener(1L, 10L)).thenReturn(usuario);
        when(procesoRepository.save(any(Proceso.class))).thenAnswer(inv -> inv.getArgument(0));

        Proceso result = procesoService.cambiarEstado(1L, 100L, 10L, EstadoProceso.PUBLICADO);

        assertEquals(EstadoProceso.PUBLICADO, result.getEstado());
        verify(historialCambioService).registrar(eq(result), eq(usuario), contains("publicado"));
    }

    @Test
    @DisplayName("Publicar publica ProcesoPublicacionEvent antes de guardar, para que "
            + "ValidacionModeloService pueda rechazar la publicacion sin que ProcesoService dependa de el")
    void publicar_publica_evento_antes_de_guardar() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(100L, 1L)).thenReturn(Optional.of(proceso));
        when(usuarioService.obtener(1L, 10L)).thenReturn(usuario);
        when(procesoRepository.save(any(Proceso.class))).thenAnswer(inv -> inv.getArgument(0));

        procesoService.cambiarEstado(1L, 100L, 10L, EstadoProceso.PUBLICADO);

        InOrder orden = inOrder(eventPublisher, procesoRepository);
        orden.verify(eventPublisher).publishEvent(new ProcesoPublicacionEvent(1L, 100L));
        orden.verify(procesoRepository).save(any(Proceso.class));
    }

    @Test
    @DisplayName("Si el listener del evento rechaza la publicacion, no se guarda el proceso")
    void publicar_no_guarda_si_el_evento_lanza_excepcion() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(100L, 1L)).thenReturn(Optional.of(proceso));
        doThrow(new ReglaNegocioException("modelo invalido"))
                .when(eventPublisher).publishEvent(any(ProcesoPublicacionEvent.class));

        assertThrows(ReglaNegocioException.class,
                () -> procesoService.cambiarEstado(1L, 100L, 10L, EstadoProceso.PUBLICADO));

        verify(procesoRepository, never()).save(any());
        verify(historialCambioService, never()).registrar(any(), any(), anyString());
    }

    @Test
    @DisplayName("Cambiar a un estado que no cambia (ya esta en BORRADOR) no publica ProcesoPublicacionEvent")
    void mantener_borrador_no_publica_evento() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(100L, 1L)).thenReturn(Optional.of(proceso));

        procesoService.cambiarEstado(1L, 100L, 10L, EstadoProceso.BORRADOR);

        verify(eventPublisher, never()).publishEvent(any());
        verify(procesoRepository, never()).save(any());
    }

    @Test
    @DisplayName("HU-06: eliminar logico pone activo=false sin DELETE fisico")
    void eliminarLogico_exitoso() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(100L, 1L)).thenReturn(Optional.of(proceso));
        when(usuarioService.obtener(1L, 10L)).thenReturn(usuario);
        when(procesoRepository.save(any(Proceso.class))).thenAnswer(inv -> inv.getArgument(0));

        procesoService.eliminarLogico(1L, 100L, 10L);

        assertFalse(proceso.isActivo());
        verify(procesoRepository).save(proceso);
        verify(procesoRepository, never()).delete(any(Proceso.class));
        verify(procesoRepository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("Obtener proceso de otra empresa lanza RecursoNoEncontrado")
    void obtener_otra_empresa() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(100L, 2L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class,
                () -> procesoService.obtener(2L, 100L));
    }

    @Test
    void proceso_publicado_no_vuelve_a_borrador() {
        proceso.setEstado(EstadoProceso.PUBLICADO);
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(100L, 1L)).thenReturn(Optional.of(proceso));

        assertThrows(ReglaNegocioException.class,
                () -> procesoService.cambiarEstado(1L, 100L, 10L, EstadoProceso.BORRADOR));
    }
}
