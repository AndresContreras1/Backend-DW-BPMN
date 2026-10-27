package com.facimus.procesos.gestion.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.ProcesoCompartido;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.ProcesoCompartidoRepository;
import com.facimus.procesos.gestion.service.dto.ProcesoDiagrama;
import com.facimus.procesos.gestion.service.dto.ProcesoCompartidoDetalle;

import lombok.RequiredArgsConstructor;

/** HU-23: comparticion de procesos entre empresas en modo solo lectura. */
@Service
@RequiredArgsConstructor
public class ProcesoCompartidoService {

    private final ProcesoCompartidoRepository procesoCompartidoRepository;
    private final ProcesoService procesoService;
    private final EmpresaService empresaService;
    private final UsuarioService usuarioService;
    private final ProcesoDiagramaService procesoDiagramaService;
    private final HistorialCambioService historialCambioService;

    @Transactional
    public ProcesoCompartido compartir(Long empresaId, Long usuarioId, Long procesoId, Long empresaInvitadaId) {
        Proceso proceso = procesoService.obtener(empresaId, procesoId);
        if (empresaId.equals(empresaInvitadaId)) {
            throw new ReglaNegocioException("No es necesario compartir un proceso con la empresa propietaria.");
        }
        Empresa invitada = empresaService.obtener(empresaInvitadaId);
        Usuario autor = usuarioService.obtener(empresaId, usuarioId);

        ProcesoCompartido compartido = procesoCompartidoRepository
                .findByProcesoIdAndEmpresaIdAndEmpresaInvitadaId(procesoId, empresaId, empresaInvitadaId)
                .orElseGet(ProcesoCompartido::new);
        // proceso.getEmpresa() es LAZY: se resuelve con empresaService (ya tenemos empresaId)
        // en vez de navegar el proxy, que aqui no esta inicializado.
        compartido.setEmpresa(empresaService.obtener(empresaId));
        compartido.setProceso(proceso);
        compartido.setEmpresaInvitada(invitada);
        compartido.setSoloLectura(true);
        compartido.setActivo(true);
        compartido = procesoCompartidoRepository.save(compartido);

        historialCambioService.registrar(proceso, autor,
                "Proceso compartido en modo solo lectura con " + invitada.getNombre() + ".");
        return compartido;
    }

    @Transactional
    public void dejarDeCompartir(Long empresaId, Long usuarioId, Long procesoId, Long empresaInvitadaId) {
        Proceso proceso = procesoService.obtener(empresaId, procesoId);
        Usuario autor = usuarioService.obtener(empresaId, usuarioId);
        ProcesoCompartido compartido = procesoCompartidoRepository
                .findByProcesoIdAndEmpresaIdAndEmpresaInvitadaIdAndActivoTrue(
                        procesoId, empresaId, empresaInvitadaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("La empresa indicada no tiene acceso compartido."));
        compartido.setActivo(false);
        procesoCompartidoRepository.save(compartido);
        historialCambioService.registrar(proceso, autor,
                "Se retiro el acceso compartido a la empresa " + compartido.getEmpresaInvitada().getNombre() + ".");
    }

    @Transactional(readOnly = true)
    public List<ProcesoCompartido> listarCompartidosPorPropietario(Long empresaId, Long procesoId) {
        if (!procesoService.existe(empresaId, procesoId)) {
            throw new RecursoNoEncontradoException("Proceso no encontrado.");
        }
        return procesoCompartidoRepository.findAllByProcesoIdAndEmpresaIdAndActivoTrue(procesoId, empresaId);
    }

    @Transactional(readOnly = true)
    public List<ProcesoCompartido> listarRecibidos(Long empresaInvitadaId) {
        return procesoCompartidoRepository.findAllByEmpresaInvitadaIdAndActivoTrue(empresaInvitadaId);
    }

    @Transactional(readOnly = true)
    public ProcesoCompartidoDetalle obtenerCompartido(Long empresaInvitadaId, Long procesoId) {
        ProcesoCompartido compartido = procesoCompartidoRepository
                .findByProcesoIdAndEmpresaInvitadaIdAndActivoTrue(procesoId, empresaInvitadaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proceso compartido no encontrado."));
        Long empresaPropietariaId = compartido.getEmpresa().getId();
        ProcesoDiagrama diagrama = procesoDiagramaService.obtener(empresaPropietariaId, procesoId);
        return new ProcesoCompartidoDetalle(compartido.getProceso(), compartido,
                diagrama.pools(), diagrama.lanes(), diagrama.actividades(), diagrama.gateways(),
                diagrama.eventos(), diagrama.arcos(), diagrama.mensajes());
    }
}
