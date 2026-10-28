package com.facimus.procesos.gestion.service;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.gestion.controller.dto.CambiarEstadoProcesoRequest;
import com.facimus.procesos.gestion.controller.dto.EditarProcesoRequest;
import com.facimus.procesos.gestion.controller.dto.HistorialCambioResponse;
import com.facimus.procesos.gestion.controller.dto.ProcesoDetalleResponse;
import com.facimus.procesos.gestion.controller.dto.ProcesoResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.event.ProcesoPublicacionEvent;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.ProcesoRepository;

import lombok.RequiredArgsConstructor;

/**
 * HU-04 a HU-07: ciclo de vida y consulta de procesos.
 * La creacion del Pool inicial (PoolService) la orquesta ProcesoOrquestadorService despues de
 * llamar a este servicio, para no crear una dependencia circular ProcesoService<->PoolService.
 * La validacion previa a publicar no depende de ValidacionModeloService directamente (eso si
 * seria un ciclo, via PoolService/MensajeService): en vez de eso, cambiarEstado publica
 * ProcesoPublicacionEvent y ValidacionModeloService lo escucha, para que la regla se cumpla
 * incluso si algo llama a este metodo directamente, sin pasar por un Controller u orquestador.
 */
@Service
@RequiredArgsConstructor
public class ProcesoServiceImpl implements ProcesoService {

    private static final int LONGITUD_MAXIMA_HISTORIAL = 255;
    private static final int LONGITUD_MAXIMA_VALOR = 40;

    private final ProcesoRepository procesoRepository;
    private final EmpresaService empresaService;
    private final UsuarioService usuarioService;
    private final HistorialCambioService historialCambioService;
    private final ApplicationEventPublisher eventPublisher;
    private final ModelMapper modelMapper;

    @Transactional
    @Override
    public Proceso crear(Long empresaId, Long usuarioId, String nombre, String descripcion, String categoria) {
        nombre = recortar(nombre);
        categoria = recortar(categoria);
        if (procesoRepository.existsByEmpresaIdAndNombreIgnoreCaseAndActivoTrue(empresaId, nombre)) {
            throw new ReglaNegocioException("Ya existe un proceso activo con el nombre \"" + nombre + "\" en esta empresa.");
        }
        Empresa empresa = empresaService.obtener(empresaId);
        Usuario autor = usuarioService.obtener(empresaId, usuarioId);

        LocalDateTime ahora = LocalDateTime.now();
        Proceso proceso = new Proceso();
        proceso.setEmpresa(empresa);
        proceso.setNombre(nombre);
        proceso.setDescripcion(descripcion);
        proceso.setCategoria(categoria);
        proceso.setEstado(EstadoProceso.BORRADOR);
        proceso.setActivo(true);
        proceso.setFechaCreacion(ahora);
        proceso.setFechaModificacion(ahora);
        proceso = procesoRepository.save(proceso);

        historialCambioService.registrar(proceso, autor, "Proceso creado.");
        return proceso;
    }

    @Transactional
    @Override
    public Proceso editarDatos(Long empresaId, Long procesoId, Long usuarioId, String nombre, String descripcion,
            String categoria) {
        nombre = recortar(nombre);
        categoria = recortar(categoria);
        Proceso proceso = obtener(empresaId, procesoId);
        Usuario autor = usuarioService.obtener(empresaId, usuarioId);

        if (!proceso.getNombre().equalsIgnoreCase(nombre)
                && procesoRepository.existsByEmpresaIdAndNombreIgnoreCaseAndActivoTrue(empresaId, nombre)) {
            throw new ReglaNegocioException("Ya existe un proceso activo con el nombre \"" + nombre + "\" en esta empresa.");
        }

        List<String> cambios = new ArrayList<>();
        registrarCambio(cambios, "nombre", proceso.getNombre(), nombre);
        registrarCambio(cambios, "descripcion", proceso.getDescripcion(), descripcion);
        registrarCambio(cambios, "categoria", proceso.getCategoria(), categoria);

        proceso.setNombre(nombre);
        proceso.setDescripcion(descripcion);
        proceso.setCategoria(categoria);
        proceso.setFechaModificacion(LocalDateTime.now());
        proceso = procesoRepository.save(proceso);

        String detalle = cambios.isEmpty() ? "Proceso editado sin cambios en los datos."
                : "Proceso editado. " + String.join("; ", cambios) + ".";
        historialCambioService.registrar(proceso, autor, limitar(detalle, LONGITUD_MAXIMA_HISTORIAL));
        return proceso;
    }

    @Transactional
    @Override
    public Proceso publicar(Long empresaId, Long procesoId, Long usuarioId) {
        return cambiarEstado(empresaId, procesoId, usuarioId, EstadoProceso.PUBLICADO);
    }

    @Transactional
    @Override
    public Proceso cambiarEstado(Long empresaId, Long procesoId, Long usuarioId, EstadoProceso nuevoEstado) {
        Proceso proceso = obtener(empresaId, procesoId);
        if (proceso.getEstado() == nuevoEstado) {
            return proceso;
        }
        if (proceso.getEstado() == EstadoProceso.PUBLICADO && nuevoEstado == EstadoProceso.BORRADOR) {
            throw new ReglaNegocioException("Un proceso publicado no puede volver a borrador.");
        }
        if (nuevoEstado == EstadoProceso.PUBLICADO) {
            eventPublisher.publishEvent(new ProcesoPublicacionEvent(empresaId, procesoId));
        }
        Usuario autor = usuarioService.obtener(empresaId, usuarioId);

        EstadoProceso estadoAnterior = proceso.getEstado();
        proceso.setEstado(nuevoEstado);
        proceso.setFechaModificacion(LocalDateTime.now());
        proceso = procesoRepository.save(proceso);

        historialCambioService.registrar(proceso, autor, nuevoEstado == EstadoProceso.PUBLICADO
                ? "Proceso publicado (estado: " + estadoAnterior + " → " + nuevoEstado + ")."
                : "Estado del proceso actualizado: " + estadoAnterior + " → " + nuevoEstado + ".");
        return proceso;
    }

    @Transactional
    @Override
    public void eliminarLogico(Long empresaId, Long procesoId, Long usuarioId) {
        Proceso proceso = obtener(empresaId, procesoId);
        Usuario autor = usuarioService.obtener(empresaId, usuarioId);

        proceso.setActivo(false);
        proceso.setFechaModificacion(LocalDateTime.now());
        procesoRepository.save(proceso);

        historialCambioService.registrar(proceso, autor, "Proceso eliminado (baja logica).");
    }

    @Transactional(readOnly = true)
    @Override
    public Page<Proceso> buscar(Long empresaId, String nombre, EstadoProceso estado, String categoria,
            Boolean activo, Pageable pageable) {
        boolean filtrarCategoria = StringUtils.hasText(categoria);
        return procesoRepository.buscarConFiltros(empresaId, activo == null || activo,
                patronDeBusqueda(nombre), estado != null, estado != null ? estado : EstadoProceso.BORRADOR,
                filtrarCategoria, filtrarCategoria ? categoria.trim().toLowerCase() : "", pageable);
    }

    /** Fragmento en minusculas rodeado de %, con los comodines del usuario escapados con '!'. */
    private static String patronDeBusqueda(String nombre) {
        if (!StringUtils.hasText(nombre)) {
            return "%";
        }
        String escapado = nombre.trim().toLowerCase().replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escapado + "%";
    }

    @Transactional(readOnly = true)
    @Override
    public boolean existe(Long empresaId, Long procesoId) {
        return procesoRepository.existsByIdAndEmpresaId(procesoId, empresaId);
    }

    /** Unica puerta de carga del proceso: un proceso eliminado (inactivo) no admite cambios. */
    @Transactional(readOnly = true)
    @Override
    public Proceso obtener(Long empresaId, Long procesoId) {
        return procesoRepository.findByIdAndEmpresaIdAndActivoTrue(procesoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proceso no encontrado."));
    }

    /** Carga para solo lectura: un proceso eliminado (inactivo) sigue pudiendo consultarse. */
    @Transactional(readOnly = true)
    @Override
    public Proceso obtenerParaConsulta(Long empresaId, Long procesoId) {
        return procesoRepository.findByIdAndEmpresaId(procesoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proceso no encontrado."));
    }

    private static String recortar(String texto) {
        return texto == null ? null : texto.trim();
    }

    private static void registrarCambio(List<String> cambios, String campo, String anterior, String nuevo) {
        if (!Objects.equals(anterior, nuevo)) {
            cambios.add(campo + ": \"" + limitar(anterior, LONGITUD_MAXIMA_VALOR) + "\" → \""
                    + limitar(nuevo, LONGITUD_MAXIMA_VALOR) + "\"");
        }
    }

    private static String limitar(String texto, int maximo) {
        if (texto == null) {
            return "";
        }
        return texto.length() <= maximo ? texto : texto.substring(0, maximo - 1) + "…";
    }

    @Transactional
    @Override
    public ProcesoResponse editarDatos(Long empresaId, Long procesoId, Long usuarioId, EditarProcesoRequest request) {
        Proceso proceso = editarDatos(empresaId, procesoId, usuarioId, request.nombre(), request.descripcion(),
                request.categoria());
        return modelMapper.map(proceso, ProcesoResponse.class);
    }

    @Transactional
    @Override
    public ProcesoResponse cambiarEstado(Long empresaId, Long procesoId, Long usuarioId,
            CambiarEstadoProcesoRequest request) {
        Proceso proceso = cambiarEstado(empresaId, procesoId, usuarioId, request.estado());
        return modelMapper.map(proceso, ProcesoResponse.class);
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<ProcesoResponse> consultarPagina(Long empresaId, String nombre, EstadoProceso estado,
            String categoria, Boolean activo, int pagina, int tamano) {
        Page<ProcesoResponse> procesos = buscar(empresaId, nombre, estado, categoria, activo,
                PageRequest.of(pagina, tamano, Sort.by("fechaModificacion").descending()))
                .map(p -> modelMapper.map(p, ProcesoResponse.class));
        return PageResponse.from(procesos);
    }

    @Transactional(readOnly = true)
    @Override
    public ProcesoDetalleResponse consultarDetalle(Long empresaId, Long procesoId) {
        Proceso proceso = obtenerParaConsulta(empresaId, procesoId);
        List<HistorialCambioResponse> historial = historialCambioService.listarPorProceso(empresaId, procesoId)
                .stream()
                .map(h -> modelMapper.map(h, HistorialCambioResponse.class))
                .toList();
        return new ProcesoDetalleResponse(modelMapper.map(proceso, ProcesoResponse.class), historial);
    }

    @Transactional(readOnly = true)
    @Override
    public List<HistorialCambioResponse> consultarHistorial(Long empresaId, Long procesoId) {
        obtenerParaConsulta(empresaId, procesoId);
        return historialCambioService.listarPorProceso(empresaId, procesoId).stream()
                .map(h -> modelMapper.map(h, HistorialCambioResponse.class))
                .toList();
    }
}
