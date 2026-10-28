package com.facimus.procesos.modelado.service;

import com.facimus.procesos.modelado.controller.dto.MensajeRequest;
import com.facimus.procesos.modelado.controller.dto.EditarMensajeRequest;
import com.facimus.procesos.modelado.controller.dto.MensajeResponse;
import org.modelmapper.ModelMapper;
import java.util.List;
import java.util.Optional;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.SolicitudInvalidaException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.event.PoolParticipanteModificadoEvent;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.Correlacion;
import com.facimus.procesos.modelado.model.EventoMensaje;
import com.facimus.procesos.modelado.model.Mensaje;
import com.facimus.procesos.modelado.model.NodoFlujo;
import com.facimus.procesos.modelado.model.PoliticaFalloNotificacion;
import com.facimus.procesos.modelado.model.PoliticaMensajeSinCaso;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoDestinoExterno;
import com.facimus.procesos.modelado.model.TipoEventoMensaje;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.repository.MensajeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MensajeServiceImpl implements MensajeService {

    private final MensajeRepository mensajeRepository;
    private final PoolService poolService;
    private final ProcesoService procesoService;
    private final CorrelacionService correlacionService;
    private final NodoFlujoService nodoFlujoService;
    private final AuditoriaModeladoService auditoriaModeladoService;
    private final ModelMapper modelMapper;

    /** Compatibilidad con clientes que todavia modelan el mensaje en varias llamadas. */
    @Transactional
    @Override
    public Mensaje crear(Long empresaId, Long procesoId, String nombre, String contenido,
            Long poolOrigenId, Long poolDestinoId) {
        return crearInterno(empresaId, procesoId, nombre, contenido, poolOrigenId, poolDestinoId,
                null, null, null, null, null, null, null,
                PoliticaMensajeSinCaso.DESCARTAR, false);
    }

    @Transactional
    @Override
    public Mensaje crear(Long empresaId, Long procesoId, String nombre, String contenido,
            Long poolOrigenId, Long poolDestinoId, Long eventoThrowId, Long eventoCatchId,
            String claveCorrelacion, TipoDestinoExterno tipoDestinoExterno, String destinoExterno,
            PoliticaFalloNotificacion politicaFalloNotificacion, Long actividadErrorId,
            PoliticaMensajeSinCaso politicaSinCaso) {
        return crearInterno(empresaId, procesoId, nombre, contenido, poolOrigenId, poolDestinoId,
                eventoThrowId, eventoCatchId, claveCorrelacion, tipoDestinoExterno, destinoExterno,
                politicaFalloNotificacion, actividadErrorId, politicaSinCaso, true);
    }

    private Mensaje crearInterno(Long empresaId, Long procesoId, String nombre, String contenido,
            Long poolOrigenId, Long poolDestinoId, Long eventoThrowId, Long eventoCatchId,
            String claveCorrelacion, TipoDestinoExterno tipoDestinoExterno, String destinoExterno,
            PoliticaFalloNotificacion politicaFalloNotificacion, Long actividadErrorId,
            PoliticaMensajeSinCaso politicaSinCaso, boolean exigirThrow) {
        if (poolOrigenId.equals(poolDestinoId)) {
            throw new ReglaNegocioException("Un mensaje debe conectar dos pools diferentes.");
        }

        Proceso proceso = procesoService.obtener(empresaId, procesoId);
        Pool poolOrigen = poolService.obtener(empresaId, poolOrigenId);
        Pool poolDestino = poolService.obtener(empresaId, poolDestinoId);

        validarPoolsDelProceso(procesoId, poolOrigen, poolDestino);

        EventoMensaje eventoThrow = resolverEvento(empresaId, eventoThrowId, "Message Throw");
        EventoMensaje eventoCatch = resolverEvento(empresaId, eventoCatchId, "Message Catch");

        if (exigirThrow && eventoThrow == null) {
            throw new ReglaNegocioException("El mensaje debe estar asociado a un evento Message Throw.");
        }
        // La clave la define el cliente o se hereda del Message Throw; ya no se inventa un valor.
        String correlacion = StringUtils.hasText(claveCorrelacion)
                ? claveCorrelacion
                : (eventoThrow != null && StringUtils.hasText(eventoThrow.getClaveCorrelacion())
                        ? eventoThrow.getClaveCorrelacion()
                        : null);
        if (exigirThrow && correlacion == null) {
            throw new ReglaNegocioException(
                    "Debe indicar la clave de correlacion del mensaje o definirla en el Message Throw.");
        }
        if (eventoThrow != null) {
            validarThrow(eventoThrow, poolOrigen, nombre, correlacion);
        }
        if (eventoCatch != null) {
            validarCatch(eventoCatch, poolDestino, nombre, correlacion);
        }
        Actividad actividadError = validarDestinoExterno(empresaId, procesoId, poolDestino, eventoCatch,
                tipoDestinoExterno, destinoExterno, politicaFalloNotificacion, actividadErrorId);
        validarAmbiguedad(empresaId, procesoId, nombre, correlacion, null);

        Mensaje mensaje = new Mensaje();
        mensaje.setEmpresa(proceso.getEmpresa());
        mensaje.setProceso(proceso);
        mensaje.setNombre(nombre);
        mensaje.setContenido(contenido);
        mensaje.setPoolOrigen(poolOrigen);
        mensaje.setPoolDestino(poolDestino);
        mensaje.setEventoThrow(eventoThrow);
        mensaje.setEventoCatch(eventoCatch);
        mensaje.setClaveCorrelacion(correlacion);
        mensaje.setTipoDestinoExterno(tipoDestinoExterno);
        mensaje.setDestinoExterno(destinoExterno);
        mensaje.setPoliticaFalloNotificacion(politicaFalloNotificacion);
        mensaje.setActividadError(actividadError);
        mensaje.setPoliticaSinCaso(politicaSinCaso != null
                ? politicaSinCaso
                : PoliticaMensajeSinCaso.DESCARTAR);
        mensaje = mensajeRepository.save(mensaje);

        if (correlacion != null) {
            Correlacion entidadCorrelacion = new Correlacion();
            entidadCorrelacion.setEmpresa(proceso.getEmpresa());
            entidadCorrelacion.setMensaje(mensaje);
            entidadCorrelacion.setCriterio(correlacion);
            correlacionService.guardar(entidadCorrelacion);
        }

        auditoriaModeladoService.registrar(proceso, "Mensaje creado: " + mensaje.getNombre() + ".");
        return mensaje;
    }

    @Transactional
    @Override
    public Mensaje editar(Long empresaId, Long mensajeId, String nombre, String contenido) {
        Mensaje mensaje = obtener(empresaId, mensajeId);
        return editar(empresaId, mensajeId, nombre, contenido,
                mensaje.getEventoCatch() != null ? mensaje.getEventoCatch().getId() : null,
                mensaje.getClaveCorrelacion(), mensaje.getTipoDestinoExterno(),
                mensaje.getDestinoExterno(), mensaje.getPoliticaFalloNotificacion(),
                mensaje.getActividadError() != null ? mensaje.getActividadError().getId() : null,
                mensaje.getPoliticaSinCaso());
    }

    @Transactional
    @Override
    public Mensaje editar(Long empresaId, Long mensajeId, String nombre, String contenido,
            Long eventoCatchId, String claveCorrelacion, TipoDestinoExterno tipoDestinoExterno,
            String destinoExterno, PoliticaFalloNotificacion politicaFalloNotificacion,
            Long actividadErrorId, PoliticaMensajeSinCaso politicaSinCaso) {
        Mensaje mensaje = obtener(empresaId, mensajeId);
        validarProcesoActivo(mensaje.getProceso());
        Long catchEfectivoId = eventoCatchId != null
                ? eventoCatchId
                : (mensaje.getEventoCatch() != null ? mensaje.getEventoCatch().getId() : null);
        EventoMensaje eventoCatch = resolverEvento(empresaId, catchEfectivoId, "Message Catch");

        String correlacion = StringUtils.hasText(claveCorrelacion)
                ? claveCorrelacion
                : (StringUtils.hasText(mensaje.getClaveCorrelacion())
                        ? mensaje.getClaveCorrelacion()
                        : (mensaje.getEventoThrow() != null
                                && StringUtils.hasText(mensaje.getEventoThrow().getClaveCorrelacion())
                                ? mensaje.getEventoThrow().getClaveCorrelacion()
                                : null));
        if (correlacion == null && mensaje.getEventoThrow() != null) {
            throw new ReglaNegocioException(
                    "Debe indicar la clave de correlacion del mensaje o definirla en el Message Throw.");
        }

        TipoDestinoExterno tipoDestinoEfectivo = tipoDestinoExterno != null
                ? tipoDestinoExterno : mensaje.getTipoDestinoExterno();
        String destinoEfectivo = destinoExterno != null ? destinoExterno : mensaje.getDestinoExterno();
        PoliticaFalloNotificacion politicaFalloEfectiva = politicaFalloNotificacion != null
                ? politicaFalloNotificacion : mensaje.getPoliticaFalloNotificacion();
        Long actividadErrorEfectivaId = actividadErrorId != null
                ? actividadErrorId
                : (mensaje.getActividadError() != null ? mensaje.getActividadError().getId() : null);
        PoliticaMensajeSinCaso politicaSinCasoEfectiva = politicaSinCaso != null
                ? politicaSinCaso : mensaje.getPoliticaSinCaso();

        if (mensaje.getEventoThrow() != null) {
            validarThrow(mensaje.getEventoThrow(), mensaje.getPoolOrigen(), nombre, correlacion);
        }
        if (eventoCatch != null) {
            validarCatch(eventoCatch, mensaje.getPoolDestino(), nombre, correlacion);
        }

        Actividad actividadError = validarDestinoExterno(empresaId, mensaje.getProceso().getId(),
                mensaje.getPoolDestino(), eventoCatch, tipoDestinoEfectivo, destinoEfectivo,
                politicaFalloEfectiva, actividadErrorEfectivaId);
        validarAmbiguedad(empresaId, mensaje.getProceso().getId(), nombre, correlacion, mensajeId);

        mensaje.setNombre(nombre);
        mensaje.setContenido(contenido);
        mensaje.setEventoCatch(eventoCatch);
        mensaje.setClaveCorrelacion(correlacion);
        mensaje.setTipoDestinoExterno(tipoDestinoEfectivo);
        mensaje.setDestinoExterno(destinoEfectivo);
        mensaje.setPoliticaFalloNotificacion(politicaFalloEfectiva);
        mensaje.setActividadError(actividadError);
        mensaje.setPoliticaSinCaso(politicaSinCasoEfectiva);
        mensaje = mensajeRepository.save(mensaje);

        if (correlacion != null) {
            Correlacion correlacionEntidad = correlacionService.buscarPorMensaje(empresaId, mensajeId)
                    .orElse(null);
            if (correlacionEntidad == null) {
                correlacionEntidad = new Correlacion();
                correlacionEntidad.setEmpresa(mensaje.getEmpresa());
                correlacionEntidad.setMensaje(mensaje);
            }
            correlacionEntidad.setCriterio(correlacion);
            correlacionService.guardar(correlacionEntidad);
        }

        auditoriaModeladoService.registrar(mensaje.getProceso(),
                "Mensaje editado: " + mensaje.getNombre() + ".");
        return mensaje;
    }

    @Transactional
    @Override
    public void eliminar(Long empresaId, Long mensajeId) {
        Mensaje mensaje = obtener(empresaId, mensajeId);
        validarProcesoActivo(mensaje.getProceso());
        Proceso proceso = mensaje.getProceso();
        String nombre = mensaje.getNombre();

        correlacionService.eliminarPorMensaje(empresaId, mensajeId);
        mensaje.setActivo(false);
        mensajeRepository.save(mensaje);

        auditoriaModeladoService.registrar(proceso, "Mensaje eliminado (baja logica): " + nombre + ".");
    }

    @Transactional(readOnly = true)
    @Override
    public List<Mensaje> listarPorProceso(Long empresaId, Long procesoId) {
        if (!procesoService.existe(empresaId, procesoId)) {
            throw new RecursoNoEncontradoException("Proceso no encontrado.");
        }
        return mensajeRepository.findAllByProcesoIdAndEmpresaIdAndActivoTrue(procesoId, empresaId);
    }

    @Transactional(readOnly = true)
    @Override
    public Mensaje obtener(Long empresaId, Long mensajeId) {
        return mensajeRepository.findByIdAndEmpresaId(mensajeId, empresaId)
                .filter(Mensaje::isActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mensaje no encontrado."));
    }

    @Transactional
    @Override
    public Mensaje guardar(Mensaje mensaje) {
        return mensajeRepository.save(mensaje);
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<Mensaje> buscar(Long empresaId, Long mensajeId) {
        return mensajeRepository.findByIdAndEmpresaId(mensajeId, empresaId);
    }

    @Transactional(readOnly = true)
    @Override
    public List<Mensaje> listarTodosPorProceso(Long empresaId, Long procesoId) {
        return mensajeRepository.findAllByProcesoIdAndEmpresaId(procesoId, empresaId);
    }

    @Transactional(readOnly = true)
    @Override
    public List<Mensaje> listarActivosPorEvento(Long empresaId, Long eventoId) {
        return mensajeRepository.findActivosPorEvento(eventoId, empresaId);
    }

    /**
     * Escucha PoolParticipanteModificadoEvent (publicado por PoolService.editar antes de guardar un
     * cambio de tipo de participante o de caja negra) para impedir el cambio si el pool es el
     * destino externo de mensajes activos, sin que PoolService dependa de MensajeService (ciclo).
     */
    @EventListener
    public void alModificarParticipanteDePool(PoolParticipanteModificadoEvent evento) {
        List<Mensaje> externos = mensajeRepository
                .findAllByPoolDestinoIdAndEmpresaId(evento.poolId(), evento.empresaId()).stream()
                .filter(Mensaje::isActivo)
                .filter(m -> m.getTipoDestinoExterno() != null)
                .toList();
        if (!externos.isEmpty()) {
            String nombres = externos.stream().map(m -> "\"" + m.getNombre() + "\"")
                    .collect(java.util.stream.Collectors.joining(", "));
            throw new ReglaNegocioException("No se puede cambiar el tipo de participante ni la caja negra del pool: "
                    + "es el destino externo de los mensajes " + nombres + ".");
        }
    }

    @Transactional(readOnly = true)
    @Override
    public boolean tieneMensajesActivosEnPool(Long empresaId, Long poolId) {
        return mensajeRepository.existsByPoolOrigenIdAndEmpresaIdAndActivoTrue(poolId, empresaId)
                || mensajeRepository.existsByPoolDestinoIdAndEmpresaIdAndActivoTrue(poolId, empresaId);
    }

    private void validarPoolsDelProceso(Long procesoId, Pool poolOrigen, Pool poolDestino) {
        if (poolOrigen.getProceso() == null || poolDestino.getProceso() == null
                || !poolOrigen.getProceso().getId().equals(procesoId)
                || !poolDestino.getProceso().getId().equals(procesoId)) {
            throw new ReglaNegocioException("Los pools del mensaje deben pertenecer al proceso indicado.");
        }
    }

    private EventoMensaje resolverEvento(Long empresaId, Long eventoId, String etiqueta) {
        if (eventoId == null) {
            return null;
        }
        return nodoFlujoService.buscar(empresaId, eventoId)
                .filter(NodoFlujo::isActivo)
                .filter(EventoMensaje.class::isInstance)
                .map(EventoMensaje.class::cast)
                .orElseThrow(() -> new RecursoNoEncontradoException(etiqueta + " no encontrado."));
    }

    private Actividad resolverActividad(Long empresaId, Long actividadId, Long procesoId) {
        if (actividadId == null) {
            return null;
        }
        Actividad actividad = nodoFlujoService.buscar(empresaId, actividadId)
                .filter(NodoFlujo::isActivo)
                .filter(Actividad.class::isInstance)
                .map(Actividad.class::cast)
                .orElseThrow(() -> new RecursoNoEncontradoException("Actividad de error no encontrada."));
        if (!actividad.getLane().getPool().getProceso().getId().equals(procesoId)) {
            throw new ReglaNegocioException("La actividad de error debe pertenecer al mismo proceso.");
        }
        return actividad;
    }

    private void validarThrow(EventoMensaje evento, Pool poolOrigen,
            String nombre, String claveCorrelacion) {
        if (evento.getTipoEvento() != TipoEventoMensaje.THROW) {
            throw new ReglaNegocioException("El evento de origen debe ser de tipo THROW.");
        }
        if (!evento.getLane().getPool().getId().equals(poolOrigen.getId())) {
            throw new ReglaNegocioException("El Message Throw debe pertenecer al pool de origen.");
        }
        validarNombreYCorrelacion(evento, nombre, claveCorrelacion);
    }

    private void validarCatch(EventoMensaje evento, Pool poolDestino,
            String nombre, String claveCorrelacion) {
        if (evento.getTipoEvento() == TipoEventoMensaje.THROW) {
            throw new ReglaNegocioException("El evento receptor debe ser un Message Catch.");
        }
        if (!evento.getLane().getPool().getId().equals(poolDestino.getId())) {
            throw new ReglaNegocioException("El Message Catch debe pertenecer al pool de destino.");
        }
        validarNombreYCorrelacion(evento, nombre, claveCorrelacion);
    }

    private void validarNombreYCorrelacion(EventoMensaje evento,
            String nombre, String claveCorrelacion) {
        if (!evento.getNombre().equalsIgnoreCase(nombre)) {
            throw new ReglaNegocioException(
                    "El nombre del evento de mensaje debe coincidir con el mensaje.");
        }
        if (StringUtils.hasText(claveCorrelacion)
                && !claveCorrelacion.equalsIgnoreCase(evento.getClaveCorrelacion())) {
            throw new ReglaNegocioException(
                    "La clave de correlacion debe coincidir entre Throw, Catch y mensaje.");
        }
    }

    private Actividad validarDestinoExterno(Long empresaId, Long procesoId, Pool poolDestino,
            EventoMensaje eventoCatch, TipoDestinoExterno tipoDestinoExterno, String destinoExterno,
            PoliticaFalloNotificacion politicaFalloNotificacion, Long actividadErrorId) {
        if (tipoDestinoExterno == null) {
            if (politicaFalloNotificacion != null || actividadErrorId != null) {
                throw new ReglaNegocioException(
                        "La politica de fallo solo aplica a mensajes dirigidos a sistemas externos.");
            }
            return null;
        }

        if (poolDestino.getTipoParticipante() != TipoParticipante.SISTEMA_EXTERNO
                || !poolDestino.isCajaNegra()) {
            throw new ReglaNegocioException(
                    "Un destino externo debe representarse con un pool SISTEMA_EXTERNO de caja negra.");
        }
        if (!StringUtils.hasText(destinoExterno)) {
            throw new ReglaNegocioException("Debe documentarse el destino externo del mensaje.");
        }
        if (eventoCatch != null) {
            throw new ReglaNegocioException(
                    "Un mensaje a un sistema externo no requiere Message Catch interno.");
        }
        if (politicaFalloNotificacion == null) {
            throw new ReglaNegocioException(
                    "Debe definirse que ocurre si falla la notificacion externa.");
        }

        if (politicaFalloNotificacion == PoliticaFalloNotificacion.DERIVAR_ACTIVIDAD_ERROR) {
            if (actividadErrorId == null) {
                throw new ReglaNegocioException(
                        "La politica DERIVAR_ACTIVIDAD_ERROR requiere una actividad de error.");
            }
            return resolverActividad(empresaId, actividadErrorId, procesoId);
        }
        if (actividadErrorId != null) {
            throw new ReglaNegocioException(
                    "La actividad de error solo aplica a la politica DERIVAR_ACTIVIDAD_ERROR.");
        }
        return null;
    }

    @Transactional
    @Override
    public MensajeResponse crear(Long empresaId, Long procesoId, MensajeRequest request) {
        Mensaje mensaje = request.eventoThrowId() == null
                ? crear(empresaId, procesoId, request.nombre(), request.contenido(),
                        request.poolOrigenId(), request.poolDestinoId())
                : crear(empresaId, procesoId, request.nombre(), request.contenido(),
                        request.poolOrigenId(), request.poolDestinoId(), request.eventoThrowId(),
                        request.eventoCatchId(), request.claveCorrelacion(), request.tipoDestinoExterno(),
                        request.destinoExterno(), request.politicaFalloNotificacion(),
                        request.actividadErrorId(), request.politicaSinCaso());
        return modelMapper.map(mensaje, MensajeResponse.class);
    }

    @Transactional
    @Override
    public MensajeResponse editar(Long empresaId, Long mensajeId, EditarMensajeRequest request) {
        boolean edicionLegacy = request.eventoCatchId() == null && request.claveCorrelacion() == null
                && request.tipoDestinoExterno() == null && request.destinoExterno() == null
                && request.politicaFalloNotificacion() == null && request.actividadErrorId() == null
                && request.politicaSinCaso() == null;
        if (!edicionLegacy && obtener(empresaId, mensajeId).getEventoThrow() == null) {
            throw new SolicitudInvalidaException("Los campos de correlacion, destino externo y politicas requieren que el mensaje tenga un Message Throw.");
        }
        Mensaje mensaje = edicionLegacy
                ? editar(empresaId, mensajeId, request.nombre(), request.contenido())
                : editar(empresaId, mensajeId, request.nombre(), request.contenido(),
                        request.eventoCatchId(), request.claveCorrelacion(), request.tipoDestinoExterno(),
                        request.destinoExterno(), request.politicaFalloNotificacion(),
                        request.actividadErrorId(), request.politicaSinCaso());
        return modelMapper.map(mensaje, MensajeResponse.class);
    }

    @Transactional(readOnly = true)
    @Override
    public List<MensajeResponse> consultarPorProceso(Long empresaId, Long procesoId) {
        return listarPorProceso(empresaId, procesoId).stream()
                .map(m -> modelMapper.map(m, MensajeResponse.class)).toList();
    }

    @Transactional(readOnly = true)
    @Override
    public MensajeResponse consultarDetalle(Long empresaId, Long mensajeId) {
        return modelMapper.map(obtener(empresaId, mensajeId), MensajeResponse.class);
    }

    private void validarProcesoActivo(Proceso proceso) {
        if (!proceso.isActivo()) {
            throw new ReglaNegocioException("No se permite modelar en un proceso inactivo.");
        }
    }

    private void validarAmbiguedad(Long empresaId, Long procesoId, String nombre,
            String clave, Long mensajeActualId) {
        boolean ambiguo = mensajeRepository.findAllByProcesoIdAndEmpresaIdAndActivoTrue(procesoId, empresaId)
                .stream()
                .filter(m -> mensajeActualId == null || !m.getId().equals(mensajeActualId))
                .anyMatch(m -> m.getNombre().equalsIgnoreCase(nombre)
                        && (clave != null ? clave : "").equalsIgnoreCase(
                                m.getClaveCorrelacion() != null ? m.getClaveCorrelacion() : ""));
        if (ambiguo) {
            throw new ReglaNegocioException(
                    "Ya existe otro mensaje con el mismo nombre y clave de correlacion en este proceso.");
        }
    }
}
