package com.facimus.procesos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.modelado.repository.ArcoRepository;
import com.facimus.procesos.modelado.repository.MensajeRepository;
import com.facimus.procesos.modelado.service.ValidacionModeloService;

/**
 * El inicializador solo corre en el perfil por defecto, asi que esta prueba lo activa de forma
 * explicita y reemplaza la base en archivo por una en memoria para no tocar ./data.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:semilla;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=clave-de-pruebas-de-al-menos-32-caracteres" })
@ActiveProfiles("default")
class DatosDemoInitializerTest {

    @Autowired
    private DatosDemoInitializer inicializador;
    @Autowired
    private EmpresaRepository empresaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private ProcesoRepository procesoRepository;
    @Autowired
    private ArcoRepository arcoRepository;
    @Autowired
    private MensajeRepository mensajeRepository;
    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private ValidacionModeloService validacionModeloService;

    @Test
    @DisplayName("DatosDemoInitializer_run_creaDosEmpresasConUnUsuarioPorRol")
    void DatosDemoInitializer_run_creaDosEmpresasConUnUsuarioPorRol() {
        assertThat(empresaRepository.count()).isEqualTo(2);
        assertThat(usuarioRepository.count()).isEqualTo(4);
        assertThat(usuarioRepository.findAll()).extracting(Usuario::getRolAcceso)
                .containsExactlyInAnyOrder(RolAcceso.ADMINISTRADOR, RolAcceso.EDITOR, RolAcceso.SOLO_LECTURA,
                        RolAcceso.ADMINISTRADOR);
    }

    @Test
    @DisplayName("DatosDemoInitializer_run_noGuardaContrasenasEnTextoPlano")
    void DatosDemoInitializer_run_noGuardaContrasenasEnTextoPlano() {
        assertThat(usuarioRepository.findAll())
                .allSatisfy(usuario -> assertThat(usuario.getPasswordHash()).startsWith("$2"));
        assertThat(usuarioService.autenticar("admin@demo.com", "Admin-Demo-2026").getEmail())
                .isEqualTo("admin@demo.com");
    }

    @Test
    @DisplayName("DatosDemoInitializer_run_elProcesoDeVacacionesEsCoherenteYSePuedePublicar")
    void DatosDemoInitializer_run_elProcesoDeVacacionesEsCoherenteYSePuedePublicar() {
        Empresa demo = empresaRepository.findAll().stream()
                .filter(empresa -> empresa.getNit().equals("900123456-1")).findFirst().orElseThrow();
        Long procesoId = procesoRepository.findAllByEmpresaId(demo.getId()).stream()
                .filter(proceso -> proceso.getNombre().equals("Solicitud de vacaciones")).findFirst().orElseThrow()
                .getId();

        assertThatCode(() -> validacionModeloService.validarParaPublicacion(demo.getId(), procesoId))
                .doesNotThrowAnyException();
        assertThat(arcoRepository.findAllByEmpresaId(demo.getId())).hasSize(5);
        assertThat(mensajeRepository.findAllByEmpresaId(demo.getId())).hasSize(1);
    }

    @Test
    @DisplayName("DatosDemoInitializer_run_incluyeUnProcesoDadoDeBajaLogicaYNoDuplicaAlReejecutar")
    void DatosDemoInitializer_run_incluyeUnProcesoDadoDeBajaLogicaYNoDuplicaAlReejecutar() {
        long procesos = procesoRepository.count();

        inicializador.run();

        assertThat(procesoRepository.count()).isEqualTo(procesos);
        assertThat(procesoRepository.findAll()).filteredOn(proceso -> !proceso.isActivo())
                .extracting("nombre").containsExactly("Proceso retirado");
        assertThat(empresaRepository.count()).isEqualTo(2);
    }
}
