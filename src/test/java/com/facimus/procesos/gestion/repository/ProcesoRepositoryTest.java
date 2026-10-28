package com.facimus.procesos.gestion.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.Proceso;

@DataJpaTest
@ActiveProfiles("test")
class ProcesoRepositoryTest {

    @Autowired
    private EmpresaRepository empresaRepository;
    @Autowired
    private ProcesoRepository procesoRepository;

    private Empresa empresaA;
    private Empresa empresaB;

    @BeforeEach
    void datos() {
        empresaA = empresaRepository.save(empresa("Empresa A", "900000001-1"));
        empresaB = empresaRepository.save(empresa("Empresa B", "900000002-2"));
        procesoRepository.save(proceso(empresaA, "Solicitud de vacaciones", "Talento Humano", EstadoProceso.PUBLICADO, true));
        procesoRepository.save(proceso(empresaA, "Compra de insumos", "Compras", EstadoProceso.BORRADOR, true));
        procesoRepository.save(proceso(empresaA, "Descuento 50% anual", "Compras", EstadoProceso.BORRADOR, true));
        procesoRepository.save(proceso(empresaA, "Proceso retirado", "Archivo", EstadoProceso.PUBLICADO, false));
        procesoRepository.save(proceso(empresaB, "Solicitud de vacaciones", "Talento Humano", EstadoProceso.BORRADOR, true));
    }

    private Page<Proceso> buscar(Long empresaId, boolean activo, String patron, EstadoProceso estado, String categoria) {
        return procesoRepository.buscarConFiltros(empresaId, activo, patron, estado != null,
                estado != null ? estado : EstadoProceso.BORRADOR, categoria != null, categoria != null ? categoria : "",
                PageRequest.of(0, 10));
    }

    @Test
    @DisplayName("ProcesoRepository_buscarConFiltros_sinFiltrosDevuelveSoloLosActivosDeLaEmpresa")
    void ProcesoRepository_buscarConFiltros_sinFiltrosDevuelveSoloLosActivosDeLaEmpresa() {
        Page<Proceso> pagina = buscar(empresaA.getId(), true, "%", null, null);

        assertThat(pagina.getContent()).extracting(Proceso::getNombre)
                .containsExactlyInAnyOrder("Solicitud de vacaciones", "Compra de insumos", "Descuento 50% anual");
    }

    @Test
    @DisplayName("ProcesoRepository_buscarConFiltros_activoFalsoDevuelveLosInactivos")
    void ProcesoRepository_buscarConFiltros_activoFalsoDevuelveLosInactivos() {
        Page<Proceso> pagina = buscar(empresaA.getId(), false, "%", null, null);

        assertThat(pagina.getContent()).extracting(Proceso::getNombre).containsExactly("Proceso retirado");
    }

    @Test
    @DisplayName("ProcesoRepository_buscarConFiltros_nombreBuscaPorFragmentoSinMayusculas")
    void ProcesoRepository_buscarConFiltros_nombreBuscaPorFragmentoSinMayusculas() {
        Page<Proceso> pagina = buscar(empresaA.getId(), true, "%vacac%", null, null);

        assertThat(pagina.getContent()).extracting(Proceso::getNombre).containsExactly("Solicitud de vacaciones");
    }

    @Test
    @DisplayName("ProcesoRepository_buscarConFiltros_porcentajeEscapadoSeBuscaComoTexto")
    void ProcesoRepository_buscarConFiltros_porcentajeEscapadoSeBuscaComoTexto() {
        Page<Proceso> pagina = buscar(empresaA.getId(), true, "%50!%%", null, null);

        assertThat(pagina.getContent()).extracting(Proceso::getNombre).containsExactly("Descuento 50% anual");
    }

    @Test
    @DisplayName("ProcesoRepository_buscarConFiltros_estadoYCategoriaSeCombinan")
    void ProcesoRepository_buscarConFiltros_estadoYCategoriaSeCombinan() {
        Page<Proceso> pagina = buscar(empresaA.getId(), true, "%", EstadoProceso.BORRADOR, "compras");

        assertThat(pagina.getContent()).extracting(Proceso::getNombre)
                .containsExactlyInAnyOrder("Compra de insumos", "Descuento 50% anual");
        assertThat(buscar(empresaA.getId(), true, "%", EstadoProceso.PUBLICADO, "compras").getContent()).isEmpty();
    }

    @Test
    @DisplayName("ProcesoRepository_buscarConFiltros_noMuestraProcesosDeOtraEmpresa")
    void ProcesoRepository_buscarConFiltros_noMuestraProcesosDeOtraEmpresa() {
        Page<Proceso> pagina = buscar(empresaB.getId(), true, "%", null, null);

        assertThat(pagina.getContent()).hasSize(1);
        assertThat(pagina.getContent().get(0).getEmpresa().getId()).isEqualTo(empresaB.getId());
    }

    private static Empresa empresa(String nombre, String nit) {
        Empresa empresa = new Empresa();
        empresa.setNombre(nombre);
        empresa.setNit(nit);
        empresa.setCorreoContacto("contacto@" + nit + ".com");
        empresa.setFechaRegistro(LocalDate.now());
        return empresa;
    }

    private static Proceso proceso(Empresa empresa, String nombre, String categoria, EstadoProceso estado,
            boolean activo) {
        Proceso proceso = new Proceso();
        proceso.setEmpresa(empresa);
        proceso.setNombre(nombre);
        proceso.setDescripcion("Descripcion de " + nombre);
        proceso.setCategoria(categoria);
        proceso.setEstado(estado);
        proceso.setActivo(activo);
        proceso.setFechaCreacion(LocalDateTime.now());
        proceso.setFechaModificacion(LocalDateTime.now());
        return proceso;
    }
}
