package com.facimus.procesos.gestion.service;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.repository.EmpresaRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * EmpresaService ya no depende de UsuarioService (para evitar el ciclo
 * EmpresaService<->UsuarioService): la creacion del usuario administrador inicial la
 * orquesta EmpresaController despues de llamar a registrar(). Esa orquestacion se prueba
 * en EmpresaControllerTest.
 */
@ExtendWith(MockitoExtension.class)
class EmpresaServiceTest {

    @Mock
    private EmpresaRepository empresaRepository;

    @InjectMocks
    private EmpresaService empresaService;

    @Test
    @DisplayName("HU-01: registrar crea la empresa")
    void registrar_crea_empresa() {
        when(empresaRepository.existsByNit("900123456")).thenReturn(false);
        when(empresaRepository.save(any(Empresa.class))).thenAnswer(inv -> {
            Empresa e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });

        Empresa resultado = empresaService.registrar("Acme", "900123456", "info@acme.com");

        assertNotNull(resultado);
        assertEquals("Acme", resultado.getNombre());
        assertEquals("900123456", resultado.getNit());
        assertEquals(1L, resultado.getId());
    }

    @Test
    @DisplayName("HU-01: registrar con NIT duplicado lanza excepcion")
    void registrar_nit_duplicado_lanza_excepcion() {
        when(empresaRepository.existsByNit("900123456")).thenReturn(true);

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> empresaService.registrar("Acme", "900123456", "info@acme.com"));

        assertTrue(ex.getMessage().contains("NIT"));
        verify(empresaRepository, never()).save(any());
    }
}
