package com.facimus.procesos.gestion.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.HistorialCambio;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.ProcesoCompartido;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.model.Usuario;

/** Consultas derivadas de HU-02, HU-06, HU-23 y de la bitacora: siempre acotadas por empresa. */
@DataJpaTest
@ActiveProfiles("test")
class ConsultasGestionRepositoryTest {

    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ProcesoRepository procesoRepository;
    @Autowired private ProcesoCompartidoRepository procesoCompartidoRepository;
    @Autowired private HistorialCambioRepository historialCambioRepository;

    private Empresa empresaA;
    private Empresa empresaB;

    @BeforeEach
    void datos() {
        empresaA = empresaRepository.save(empresa("Empresa A", "900000001-1"));
        empresaB = empresaRepository.save(empresa("Empresa B", "900000002-2"));
    }

    @Test
    @DisplayName("UsuarioRepository_conteoDeAdministradoresActivos_cuentaSoloLosDeLaEmpresaYActivos")
    void UsuarioRepository_conteoDeAdministradoresActivos_cuentaSoloLosDeLaEmpresaYActivos() {
        usuarioRepository.save(usuario(empresaA, "a1@a.com", RolAcceso.ADMINISTRADOR, true));
        usuarioRepository.save(usuario(empresaA, "a2@a.com", RolAcceso.ADMINISTRADOR, false));
        usuarioRepository.save(usuario(empresaA, "a3@a.com", RolAcceso.EDITOR, true));
        usuarioRepository.save(usuario(empresaB, "b1@b.com", RolAcceso.ADMINISTRADOR, true));
        usuarioRepository.save(usuario(empresaB, "b2@b.com", RolAcceso.ADMINISTRADOR, true));

        assertThat(usuarioRepository.countByEmpresaIdAndRolAccesoAndActivoTrue(empresaA.getId(),
                RolAcceso.ADMINISTRADOR)).isEqualTo(1);
        assertThat(usuarioRepository.countByEmpresaIdAndRolAccesoAndActivoTrue(empresaB.getId(),
                RolAcceso.ADMINISTRADOR)).isEqualTo(2);
    }

    @Test
    @DisplayName("UsuarioRepository_findAllByEmpresaIdAndActivo_separaActivosDeDesactivadosPorEmpresa")
    void UsuarioRepository_findAllByEmpresaIdAndActivo_separaActivosDeDesactivadosPorEmpresa() {
        usuarioRepository.save(usuario(empresaA, "a1@a.com", RolAcceso.EDITOR, true));
        usuarioRepository.save(usuario(empresaA, "a2@a.com", RolAcceso.EDITOR, false));
        usuarioRepository.save(usuario(empresaB, "b1@b.com", RolAcceso.EDITOR, false));

        assertThat(usuarioRepository.findAllByEmpresaIdAndActivo(empresaA.getId(), true))
                .extracting(Usuario::getEmail).containsExactly("a1@a.com");
        assertThat(usuarioRepository.findAllByEmpresaIdAndActivo(empresaA.getId(), false))
                .extracting(Usuario::getEmail).containsExactly("a2@a.com");
    }

    @Test
    @DisplayName("ProcesoCompartidoRepository_recibidos_excluyeProcesosInactivosYCompartidosRetirados")
    void ProcesoCompartidoRepository_recibidos_excluyeProcesosInactivosYCompartidosRetirados() {
        Proceso activo = procesoRepository.save(proceso(empresaA, "Activo", true));
        Proceso inactivo = procesoRepository.save(proceso(empresaA, "Inactivo", false));
        Proceso retirado = procesoRepository.save(proceso(empresaA, "Retirado", true));
        procesoCompartidoRepository.save(compartido(activo, true));
        procesoCompartidoRepository.save(compartido(inactivo, true));
        procesoCompartidoRepository.save(compartido(retirado, false));

        var recibidos = procesoCompartidoRepository
                .findAllByEmpresaInvitadaIdAndActivoTrueAndProceso_ActivoTrue(empresaB.getId());

        assertThat(recibidos).extracting(c -> c.getProceso().getNombre()).containsExactly("Activo");
    }

    @Test
    @DisplayName("ProcesoRepository_findByIdAndEmpresaId_procesoInactivo_loEncuentraSoloParaSuEmpresa")
    void ProcesoRepository_findByIdAndEmpresaId_procesoInactivo_loEncuentraSoloParaSuEmpresa() {
        Proceso inactivo = procesoRepository.save(proceso(empresaA, "Inactivo", false));

        assertThat(procesoRepository.findByIdAndEmpresaId(inactivo.getId(), empresaA.getId())).isPresent();
        assertThat(procesoRepository.findByIdAndEmpresaId(inactivo.getId(), empresaB.getId())).isEmpty();
        assertThat(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(inactivo.getId(), empresaA.getId())).isEmpty();
    }

    @Test
    @DisplayName("HistorialCambioRepository_listar_mismoInstante_desempataPorIdDescendente")
    void HistorialCambioRepository_listar_mismoInstante_desempataPorIdDescendente() {
        Proceso proceso = procesoRepository.save(proceso(empresaA, "Con historial", true));
        Usuario autor = usuarioRepository.save(usuario(empresaA, "autor@a.com", RolAcceso.EDITOR, true));
        LocalDateTime instante = LocalDateTime.now();
        historialCambioRepository.save(cambio(proceso, autor, instante, "primero"));
        historialCambioRepository.save(cambio(proceso, autor, instante, "segundo"));

        var historial = historialCambioRepository
                .findAllByProcesoIdAndEmpresaIdOrderByFechaCambioDescIdDesc(proceso.getId(), empresaA.getId());

        assertThat(historial).extracting(HistorialCambio::getDescripcionCambio).containsExactly("segundo", "primero");
    }

    private ProcesoCompartido compartido(Proceso proceso, boolean activo) {
        ProcesoCompartido compartido = new ProcesoCompartido();
        compartido.setEmpresa(empresaA);
        compartido.setProceso(proceso);
        compartido.setEmpresaInvitada(empresaB);
        compartido.setSoloLectura(true);
        compartido.setActivo(activo);
        return compartido;
    }

    private static HistorialCambio cambio(Proceso proceso, Usuario autor, LocalDateTime fecha, String descripcion) {
        HistorialCambio cambio = new HistorialCambio();
        cambio.setEmpresa(proceso.getEmpresa());
        cambio.setProceso(proceso);
        cambio.setAutor(autor);
        cambio.setFechaCambio(fecha);
        cambio.setDescripcionCambio(descripcion);
        return cambio;
    }

    private static Empresa empresa(String nombre, String nit) {
        Empresa empresa = new Empresa();
        empresa.setNombre(nombre);
        empresa.setNit(nit);
        empresa.setCorreoContacto("contacto@" + nit + ".com");
        empresa.setFechaRegistro(LocalDate.now());
        return empresa;
    }

    private static Usuario usuario(Empresa empresa, String email, RolAcceso rol, boolean activo) {
        Usuario usuario = new Usuario();
        usuario.setEmpresa(empresa);
        usuario.setNombre(email);
        usuario.setEmail(email);
        usuario.setPasswordHash("hash");
        usuario.setRolAcceso(rol);
        usuario.setActivo(activo);
        return usuario;
    }

    private static Proceso proceso(Empresa empresa, String nombre, boolean activo) {
        Proceso proceso = new Proceso();
        proceso.setEmpresa(empresa);
        proceso.setNombre(nombre);
        proceso.setDescripcion("Descripcion de " + nombre);
        proceso.setCategoria("Categoria");
        proceso.setEstado(EstadoProceso.BORRADOR);
        proceso.setActivo(activo);
        proceso.setFechaCreacion(LocalDateTime.now());
        proceso.setFechaModificacion(LocalDateTime.now());
        return proceso;
    }
}
