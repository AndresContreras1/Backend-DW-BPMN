package com.facimus.procesos.transaccionalidad;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.ProcesoCompartido;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoCompartidoService;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.gestion.service.dto.ProcesoCompartidoDetalle;

/**
 * Issue #42 (configurar FetchType.LAZY en todas las relaciones @ManyToOne): ProcesoCompartido
 * tiene 3 relaciones LAZY (empresa, proceso, empresaInvitada) y el DTO de respuesta las navega
 * todas (ver ModelMapperConfig, converter de ProcesoCompartido) desde el controller, es decir
 * fuera de la transaccion del service. Este test llama a ProcesoCompartidoService igual que lo
 * hace el controller (el objeto que recibe de vuelta ya esta detached) y verifica que leer esas
 * relaciones no lanza LazyInitializationException gracias a los @EntityGraph declarados en
 * ProcesoCompartidoRepository.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:proceso-compartido-lazy-it;DB_CLOSE_DELAY=-1")
class ProcesoCompartidoLazyIntegracionTest {

    @Autowired
    private EmpresaService empresaService;
    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private ProcesoRepository procesoRepository;
    @Autowired
    private ProcesoCompartidoService procesoCompartidoService;

    @Test
    @DisplayName("compartir/listar/detalle no lanzan LazyInitializationException al leer proceso/empresa/empresaInvitada")
    void relaciones_de_procesoCompartido_se_leen_sin_excepcion_fuera_de_la_transaccion() {
        Empresa propietaria = empresaService.registrar("Acme Propietaria", "900999004", "info@acme-propietaria.com");
        Usuario admin = usuarioService.crearColaborador(propietaria.getId(), "Admin",
                "admin@acme-propietaria.com", "secret123", RolAcceso.ADMINISTRADOR);
        Empresa invitada = empresaService.registrar("Acme Invitada", "900999005", "info@acme-invitada.com");

        Proceso proceso = new Proceso();
        proceso.setEmpresa(propietaria);
        proceso.setNombre("Proceso compartible");
        proceso.setDescripcion("desc");
        proceso.setCategoria("cat");
        proceso.setEstado(EstadoProceso.BORRADOR);
        proceso.setActivo(true);
        proceso.setFechaCreacion(LocalDateTime.now());
        proceso.setFechaModificacion(LocalDateTime.now());
        proceso = procesoRepository.save(proceso);
        Long procesoId = proceso.getId();

        ProcesoCompartido compartido = procesoCompartidoService.compartir(
                propietaria.getId(), admin.getId(), procesoId, invitada.getId());

        assertDoesNotThrow(() -> {
            assertEquals("Acme Propietaria", compartido.getEmpresa().getNombre());
            assertEquals("Proceso compartible", compartido.getProceso().getNombre());
            assertEquals("Acme Invitada", compartido.getEmpresaInvitada().getNombre());
        });

        List<ProcesoCompartido> compartidos = procesoCompartidoService
                .listarCompartidosPorPropietario(propietaria.getId(), procesoId);
        assertDoesNotThrow(() -> {
            for (ProcesoCompartido c : compartidos) {
                c.getEmpresa().getNombre();
                c.getProceso().getNombre();
                c.getEmpresaInvitada().getNombre();
            }
        });

        List<ProcesoCompartido> recibidos = procesoCompartidoService.listarRecibidos(invitada.getId());
        assertDoesNotThrow(() -> {
            for (ProcesoCompartido c : recibidos) {
                c.getEmpresa().getNombre();
                c.getProceso().getNombre();
                c.getEmpresaInvitada().getNombre();
            }
        });

        ProcesoCompartidoDetalle detalle = procesoCompartidoService.obtenerCompartido(invitada.getId(), procesoId);
        assertDoesNotThrow(() -> {
            detalle.compartido().getEmpresa().getNombre();
            detalle.proceso().getNombre();
        });
    }
}
