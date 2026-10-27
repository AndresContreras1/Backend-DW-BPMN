package com.facimus.procesos.config;

import org.modelmapper.ModelMapper;
import org.modelmapper.AbstractConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.facimus.procesos.gestion.controller.dto.EmpresaResponse;
import com.facimus.procesos.gestion.controller.dto.HistorialCambioResponse;
import com.facimus.procesos.gestion.controller.dto.InvitacionUsuarioResponse;
import com.facimus.procesos.gestion.controller.dto.PermisoEstructuraResponse;
import com.facimus.procesos.gestion.controller.dto.ProcesoCompartidoDetalleResponse;
import com.facimus.procesos.gestion.controller.dto.ProcesoCompartidoResponse;
import com.facimus.procesos.gestion.controller.dto.ProcesoResponse;
import com.facimus.procesos.gestion.controller.dto.RolProcesoConsultaResponse;
import com.facimus.procesos.gestion.controller.dto.RolProcesoVistaResponse;
import com.facimus.procesos.gestion.controller.dto.UsuarioResponse;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.HistorialCambio;
import com.facimus.procesos.gestion.model.InvitacionUsuario;
import com.facimus.procesos.gestion.model.PermisoEstructura;
import com.facimus.procesos.gestion.model.ProcesoCompartido;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.service.dto.ProcesoCompartidoDetalle;
import com.facimus.procesos.gestion.service.dto.RolProcesoConsulta;
import com.facimus.procesos.gestion.service.dto.RolProcesoVista;
import com.facimus.procesos.modelado.controller.dto.ActividadResponse;
import com.facimus.procesos.modelado.controller.dto.ArcoResponse;
import com.facimus.procesos.modelado.controller.dto.CorrelacionResponse;
import com.facimus.procesos.modelado.controller.dto.EventoMensajeResponse;
import com.facimus.procesos.modelado.controller.dto.GatewayResponse;
import com.facimus.procesos.modelado.controller.dto.ImpactoEliminacionResponse;
import com.facimus.procesos.modelado.controller.dto.LaneResponse;
import com.facimus.procesos.modelado.controller.dto.MensajeResponse;
import com.facimus.procesos.modelado.controller.dto.PoolResponse;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.Arco;
import com.facimus.procesos.modelado.model.Correlacion;
import com.facimus.procesos.modelado.model.EventoMensaje;
import com.facimus.procesos.modelado.model.Gateway;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.Mensaje;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.service.dto.ImpactoEliminacion;

/**
 * Registra el mapeo Entity -> DTO de respuesta para todos los casos de "una entidad
 * mapea a un DTO" (usados por los controllers vía {@code modelMapper.map(entity, X.class)}).
 * <p>
 * Los DTOs que NO estan aqui (p. ej. {@code LoginResponse}, {@code ProcesoDetalleResponse},
 * {@code ProcesoDiagramaResponse}) se construyen a mano a proposito: necesitan logica de
 * composicion que no encaja en un solo converter de ModelMapper.
 */
@Configuration
public class ModelMapperConfig {

    @Bean
    public ModelMapper modelMapper() {
        ModelMapper mapper = new ModelMapper();

        // Estos 5 converters no tienen ningun campo derivado de una relacion: se probo
        // quitarlos confiando en el matching automatico de ModelMapper por nombre, pero
        // fallan en runtime (los DTO de respuesta son records sin setters, y ModelMapper
        // 3.2.0 no los resuelve solo por coincidencia de nombre). Se mantienen explicitos.
        mapper.addConverter(new AbstractConverter<Empresa, EmpresaResponse>() {
            @Override
            protected EmpresaResponse convert(Empresa e) {
                return new EmpresaResponse(e.getId(), e.getNombre(), e.getNit(),
                        e.getCorreoContacto(), e.getFechaRegistro());
            }
        });

        mapper.addConverter(new AbstractConverter<Usuario, UsuarioResponse>() {
            @Override
            protected UsuarioResponse convert(Usuario u) {
                return new UsuarioResponse(u.getId(), u.getNombre(), u.getEmail(),
                        u.getRolAcceso(), u.isActivo());
            }
        });

        mapper.addConverter(new AbstractConverter<Proceso, ProcesoResponse>() {
            @Override
            protected ProcesoResponse convert(Proceso p) {
                return new ProcesoResponse(p.getId(), p.getNombre(), p.getDescripcion(),
                        p.getCategoria(), p.getEstado(), p.isActivo(),
                        p.getFechaCreacion(), p.getFechaModificacion());
            }
        });

        mapper.addConverter(new AbstractConverter<InvitacionUsuario, InvitacionUsuarioResponse>() {
            @Override
            protected InvitacionUsuarioResponse convert(InvitacionUsuario i) {
                return new InvitacionUsuarioResponse(i.getId(), i.getEmail(),
                        i.getRolAcceso(), i.getToken(), i.getFechaExpiracion());
            }
        });

        mapper.addConverter(new AbstractConverter<PermisoEstructura, PermisoEstructuraResponse>() {
            @Override
            protected PermisoEstructuraResponse convert(PermisoEstructura p) {
                return new PermisoEstructuraResponse(p.getRolAcceso(), p.isCrearPool(),
                        p.isEditarPool(), p.isEliminarPool(), p.isCrearLane(),
                        p.isEditarLane(), p.isEliminarLane());
            }
        });

        // autorNombre se deriva de la relacion autor (Usuario), no de un campo propio de
        // HistorialCambio: ModelMapper no puede inferir ese salto sin ayuda.
        mapper.addConverter(new AbstractConverter<HistorialCambio, HistorialCambioResponse>() {
            @Override
            protected HistorialCambioResponse convert(HistorialCambio h) {
                return new HistorialCambioResponse(h.getId(), h.getFechaCambio(),
                        h.getDescripcionCambio(), h.getAutor().getNombre());
            }
        });

        // Los 6 campos derivados de proceso/empresa/empresaInvitada requieren navegar sus
        // relaciones; ModelMapper no puede resolverlos por coincidencia de nombre.
        mapper.addConverter(new AbstractConverter<ProcesoCompartido, ProcesoCompartidoResponse>() {
            @Override
            protected ProcesoCompartidoResponse convert(ProcesoCompartido c) {
                return new ProcesoCompartidoResponse(c.getId(), c.getProceso().getId(),
                        c.getProceso().getNombre(), c.getEmpresa().getId(),
                        c.getEmpresa().getNombre(), c.getEmpresaInvitada().getId(),
                        c.getEmpresaInvitada().getNombre(), c.isSoloLectura());
            }
        });

        // procesoId se deriva de la relacion proceso.
        mapper.addConverter(new AbstractConverter<Pool, PoolResponse>() {
            @Override
            protected PoolResponse convert(Pool p) {
                return new PoolResponse(p.getId(), p.getNombre(), p.getTipoParticipante(),
                        p.isCajaNegra(), p.getOrden(), p.getProceso().getId());
            }
        });

        // poolId, rolProcesoId y rolProcesoNombre se derivan de las relaciones pool/rolProceso.
        mapper.addConverter(new AbstractConverter<Lane, LaneResponse>() {
            @Override
            protected LaneResponse convert(Lane l) {
                return new LaneResponse(l.getId(), l.getNombre(), l.getOrden(),
                        l.getPool().getId(), l.getRolProceso().getId(),
                        l.getRolProceso().getNombre());
            }
        });

        // laneId se deriva de la relacion lane; tipoActividad ademas necesita un valor por
        // defecto (TAREA) cuando es nulo en filas antiguas.
        mapper.addConverter(new AbstractConverter<Actividad, ActividadResponse>() {
            @Override
            protected ActividadResponse convert(Actividad a) {
                TipoActividad tipo = a.getTipoActividad() != null
                        ? a.getTipoActividad() : TipoActividad.TAREA;
                return new ActividadResponse(a.getId(), a.getNombre(), a.getDescripcion(),
                        a.getPosicionX(), a.getPosicionY(), a.getLane().getId(), tipo);
            }
        });

        // laneId se deriva de la relacion lane.
        mapper.addConverter(new AbstractConverter<Gateway, GatewayResponse>() {
            @Override
            protected GatewayResponse convert(Gateway g) {
                return new GatewayResponse(g.getId(), g.getNombre(), g.getTipoGateway(),
                        g.getPosicionX(), g.getPosicionY(), g.getLane().getId());
            }
        });

        // laneId se deriva de la relacion lane.
        mapper.addConverter(new AbstractConverter<EventoMensaje, EventoMensajeResponse>() {
            @Override
            protected EventoMensajeResponse convert(EventoMensaje e) {
                return new EventoMensajeResponse(e.getId(), e.getNombre(), e.getTipoEvento(),
                        e.getContenido(), e.getClaveCorrelacion(), e.getPosicionX(),
                        e.getPosicionY(), e.getLane().getId(), e.isOrigenExterno());
            }
        });

        // origenId, destinoId y poolId se derivan de las relaciones origen/destino/pool.
        mapper.addConverter(new AbstractConverter<Arco, ArcoResponse>() {
            @Override
            protected ArcoResponse convert(Arco a) {
                return new ArcoResponse(a.getId(), a.getEtiqueta(), a.getCondicion(),
                        a.getOrigen().getId(), a.getDestino().getId(), a.getPool().getId());
            }
        });

        // mensajeId se deriva de la relacion mensaje.
        mapper.addConverter(new AbstractConverter<Correlacion, CorrelacionResponse>() {
            @Override
            protected CorrelacionResponse convert(Correlacion c) {
                return new CorrelacionResponse(c.getId(), c.getCriterio(),
                        c.getMensaje().getId());
            }
        });

        // Varios IDs se derivan de relaciones (pools, eventos, actividad de error), y el
        // ultimo campo (esExterno) es un valor calculado que no existe como campo propio.
        mapper.addConverter(new AbstractConverter<Mensaje, MensajeResponse>() {
            @Override
            protected MensajeResponse convert(Mensaje m) {
                return new MensajeResponse(m.getId(), m.getNombre(), m.getContenido(),
                        m.getPoolOrigen().getId(), m.getPoolDestino().getId(),
                        m.getProceso().getId(),
                        m.getEventoThrow() != null ? m.getEventoThrow().getId() : null,
                        m.getEventoCatch() != null ? m.getEventoCatch().getId() : null,
                        m.getClaveCorrelacion(), m.getTipoDestinoExterno(),
                        m.getDestinoExterno(), m.getPoliticaFalloNotificacion(),
                        m.getActividadError() != null ? m.getActividadError().getId() : null,
                        m.getPoliticaSinCaso(),
                        m.getEventoCatch() == null && m.getTipoDestinoExterno() == null);
            }
        });

        // -- Converters compuestos: el origen es un DTO de servicio, no una entidad JPA. --

        mapper.addConverter(new AbstractConverter<ImpactoEliminacion, ImpactoEliminacionResponse>() {
            @Override
            protected ImpactoEliminacionResponse convert(ImpactoEliminacion i) {
                return new ImpactoEliminacionResponse(true, i.rompeContinuidad(), i.advertencias());
            }
        });

        mapper.addConverter(new AbstractConverter<RolProcesoVista, RolProcesoVistaResponse>() {
            @Override
            protected RolProcesoVistaResponse convert(RolProcesoVista v) {
                return new RolProcesoVistaResponse(v.rol().getId(), v.rol().getNombre(),
                        v.rol().getDescripcion(), v.procesosQueLoUsan(), v.enUso());
            }
        });

        mapper.addConverter(new AbstractConverter<RolProcesoConsulta, RolProcesoConsultaResponse>() {
            @Override
            protected RolProcesoConsultaResponse convert(RolProcesoConsulta c) {
                return new RolProcesoConsultaResponse(c.rol().getId(), c.rol().getNombre(),
                        c.rol().getDescripcion(), c.procesos(), !c.procesos().isEmpty());
            }
        });

        mapper.addConverter(new AbstractConverter<ProcesoCompartidoDetalle, ProcesoCompartidoDetalleResponse>() {
            @Override
            protected ProcesoCompartidoDetalleResponse convert(ProcesoCompartidoDetalle d) {
                return new ProcesoCompartidoDetalleResponse(
                        mapper.map(d.proceso(), ProcesoResponse.class),
                        d.compartido().getEmpresa().getNombre(),
                        true,
                        d.pools().stream().map(p -> mapper.map(p, PoolResponse.class)).toList(),
                        d.lanes().stream().map(l -> mapper.map(l, LaneResponse.class)).toList(),
                        d.actividades().stream().map(a -> mapper.map(a, ActividadResponse.class)).toList(),
                        d.gateways().stream().map(g -> mapper.map(g, GatewayResponse.class)).toList(),
                        d.eventos().stream().map(e -> mapper.map(e, EventoMensajeResponse.class)).toList(),
                        d.arcos().stream().map(a -> mapper.map(a, ArcoResponse.class)).toList(),
                        d.mensajes().stream().map(m -> mapper.map(m, MensajeResponse.class)).toList());
            }
        });

        return mapper;
    }
}
