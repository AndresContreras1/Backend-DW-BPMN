package com.facimus.procesos.gestion.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.access.AccessDeniedException;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.PermisoEstructura;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.repository.PermisoEstructuraRepository;

@ExtendWith(MockitoExtension.class)
class PermisoEstructuraServiceTest {

    @Mock private PermisoEstructuraRepository permisoEstructuraRepository;
    @Mock private EmpresaService empresaService;

    @InjectMocks
    private PermisoEstructuraServiceImpl service;

    private Empresa empresa;

    @BeforeEach
    void setUp() {
        empresa = new Empresa();
        empresa.setId(1L);
    }

    @Test
    void administrador_tiene_permisos_por_defecto() {
        when(empresaService.obtener(1L)).thenReturn(empresa);
        var permiso = service.obtener(1L, RolAcceso.ADMINISTRADOR);
        assertTrue(permiso.isCrearPool());
        assertTrue(permiso.isEliminarLane());
    }

    @Test
    void editor_no_puede_eliminar_por_defecto() {
        when(empresaService.obtener(1L)).thenReturn(empresa);
        var permiso = service.obtener(1L, RolAcceso.EDITOR);
        assertTrue(permiso.isCrearPool());
        assertFalse(permiso.isEliminarPool());
        assertFalse(permiso.isEliminarLane());
    }

    @Test
    void solo_lectura_no_puede_recibir_escritura() {
        assertThrows(ReglaNegocioException.class, () ->
                service.actualizar(1L, RolAcceso.SOLO_LECTURA,
                        true, false, false, false, false, false));
    }

    @Test
    void validar_rechaza_operacion_no_permitida() {
        PermisoEstructura permiso = new PermisoEstructura();
        permiso.setRolAcceso(RolAcceso.EDITOR);
        permiso.setCrearPool(false);
        when(permisoEstructuraRepository.findByEmpresaIdAndRolAcceso(1L, RolAcceso.EDITOR))
                .thenReturn(Optional.of(permiso));

        assertThrows(AccessDeniedException.class,
                () -> service.validar(1L, RolAcceso.EDITOR, OperacionEstructura.CREAR_POOL));
    }

    @Test
    void actualizar_persiste_configuracion() {
        PermisoEstructura permiso = new PermisoEstructura();
        permiso.setRolAcceso(RolAcceso.EDITOR);
        when(permisoEstructuraRepository.findByEmpresaIdAndRolAcceso(1L, RolAcceso.EDITOR))
                .thenReturn(Optional.of(permiso));
        when(permisoEstructuraRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var resultado = service.actualizar(1L, RolAcceso.EDITOR,
                true, true, true, true, true, false);

        assertTrue(resultado.isEliminarPool());
        assertFalse(resultado.isEliminarLane());
    }

    @Test
    void validar_administrador_siempreEstaPermitidoAunqueLaFilaGuardadaDigaOtraCosa() {
        for (OperacionEstructura operacion : OperacionEstructura.values()) {
            assertDoesNotThrow(() -> service.validar(1L, RolAcceso.ADMINISTRADOR, operacion));
        }
        verifyNoInteractions(permisoEstructuraRepository);
    }

    @Test
    void validar_soloLectura_nuncaTienePermiso() {
        when(empresaService.obtener(1L)).thenReturn(empresa);
        when(permisoEstructuraRepository.findByEmpresaIdAndRolAcceso(1L, RolAcceso.SOLO_LECTURA))
                .thenReturn(Optional.empty());

        for (OperacionEstructura operacion : OperacionEstructura.values()) {
            assertThrows(AccessDeniedException.class,
                    () -> service.validar(1L, RolAcceso.SOLO_LECTURA, operacion));
        }
    }

    @Test
    void actualizar_administradorSinTodosLosPermisos_lanzaReglaNegocioYNoGuarda() {
        assertThrows(ReglaNegocioException.class, () -> service.actualizar(1L, RolAcceso.ADMINISTRADOR,
                true, true, false, true, true, true));

        verify(permisoEstructuraRepository, never()).save(any());
    }

    @Test
    void actualizar_administradorConTodosLosPermisos_devuelveTodosEnTrue() {
        when(empresaService.obtener(1L)).thenReturn(empresa);

        var resultado = service.actualizar(1L, RolAcceso.ADMINISTRADOR, true, true, true, true, true, true);

        assertTrue(resultado.isEliminarPool());
        assertTrue(resultado.isEliminarLane());
    }

    @Test
    void obtener_administradorConFilaRevocada_siguePermitiendoTodo() {
        when(empresaService.obtener(1L)).thenReturn(empresa);

        var permiso = service.obtener(1L, RolAcceso.ADMINISTRADOR);

        assertTrue(permiso.isCrearPool() && permiso.isEditarPool() && permiso.isEliminarPool()
                && permiso.isCrearLane() && permiso.isEditarLane() && permiso.isEliminarLane());
    }
}
