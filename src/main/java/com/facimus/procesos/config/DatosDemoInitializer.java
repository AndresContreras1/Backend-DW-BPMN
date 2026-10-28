package com.facimus.procesos.config;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.HistorialCambio;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.HistorialCambioRepository;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.repository.RolProcesoRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.Arco;
import com.facimus.procesos.modelado.model.Correlacion;
import com.facimus.procesos.modelado.model.EventoMensaje;
import com.facimus.procesos.modelado.model.Gateway;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.Mensaje;
import com.facimus.procesos.modelado.model.NodoFlujo;
import com.facimus.procesos.modelado.model.PoliticaFalloNotificacion;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoDestinoExterno;
import com.facimus.procesos.modelado.model.TipoEventoMensaje;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.repository.ArcoRepository;
import com.facimus.procesos.modelado.repository.CorrelacionRepository;
import com.facimus.procesos.modelado.repository.LaneRepository;
import com.facimus.procesos.modelado.repository.MensajeRepository;
import com.facimus.procesos.modelado.repository.NodoFlujoRepository;
import com.facimus.procesos.modelado.repository.PoolRepository;

import lombok.RequiredArgsConstructor;

/**
 * Datos de ejemplo para poder iniciar sesion y recorrer la API sin pasar antes por el registro.
 * Solo corre en el perfil por defecto (ni en prod ni en test) y unicamente si la base esta vacia,
 * de modo que reiniciar la aplicacion no duplica nada. Es el unico lugar que habla directo con los
 * repositorios, saltandose los services.
 * <p>
 * Crea dos empresas independientes. La primera trae un usuario por cada rol de acceso, los roles
 * de proceso, el proceso "Solicitud de vacaciones" completo (pool, lanes, actividades, gateway,
 * arcos, mensaje a un sistema externo y correlacion), un proceso en borrador y uno dado de baja
 * logica. La segunda reutiliza el nombre del proceso para comprobar el aislamiento por empresa.
 */
@Component
@Profile("default")
@Transactional
@RequiredArgsConstructor
public class DatosDemoInitializer implements CommandLineRunner {

    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final RolProcesoRepository rolProcesoRepository;
    private final ProcesoRepository procesoRepository;
    private final PoolRepository poolRepository;
    private final LaneRepository laneRepository;
    private final NodoFlujoRepository nodoFlujoRepository;
    private final ArcoRepository arcoRepository;
    private final MensajeRepository mensajeRepository;
    private final CorrelacionRepository correlacionRepository;
    private final HistorialCambioRepository historialCambioRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (empresaRepository.count() > 0) {
            return;
        }

        Empresa demo = crearEmpresa("Empresa Demo S.A.S.", "900123456-1", "contacto@demo.com");
        Usuario admin = crearUsuario(demo, "Administrador Demo", "admin@demo.com", "Admin-Demo-2026",
                RolAcceso.ADMINISTRADOR);
        crearUsuario(demo, "Editor Demo", "editor@demo.com", "Editor-Demo-2026", RolAcceso.EDITOR);
        crearUsuario(demo, "Lector Demo", "lectura@demo.com", "Lector-Demo-2026", RolAcceso.SOLO_LECTURA);
        crearProcesoVacaciones(demo, admin);
        crearProcesoSimple(demo, admin, "Compra de insumos", "Compras", EstadoProceso.BORRADOR, true);
        crearProcesoSimple(demo, admin, "Proceso retirado", "Archivo", EstadoProceso.PUBLICADO, false);

        Empresa beta = crearEmpresa("Empresa Beta S.A.S.", "900654321-2", "contacto@beta.com");
        Usuario adminBeta = crearUsuario(beta, "Administrador Beta", "admin@beta.com", "Admin-Beta-2026",
                RolAcceso.ADMINISTRADOR);
        crearProcesoSimple(beta, adminBeta, "Solicitud de vacaciones", "Talento Humano", EstadoProceso.BORRADOR,
                true);
    }

    private Empresa crearEmpresa(String nombre, String nit, String correoContacto) {
        Empresa empresa = new Empresa();
        empresa.setNombre(nombre);
        empresa.setNit(nit);
        empresa.setCorreoContacto(correoContacto);
        empresa.setFechaRegistro(LocalDate.now());
        return empresaRepository.save(empresa);
    }

    private Usuario crearUsuario(Empresa empresa, String nombre, String email, String password, RolAcceso rol) {
        Usuario usuario = new Usuario();
        usuario.setEmpresa(empresa);
        usuario.setNombre(nombre);
        usuario.setEmail(email);
        usuario.setPasswordHash(passwordEncoder.encode(password));
        usuario.setRolAcceso(rol);
        usuario.setActivo(true);
        return usuarioRepository.save(usuario);
    }

    private RolProceso crearRol(Empresa empresa, String nombre, String descripcion) {
        RolProceso rol = new RolProceso();
        rol.setEmpresa(empresa);
        rol.setNombre(nombre);
        rol.setDescripcion(descripcion);
        rol.setActivo(true);
        return rolProcesoRepository.save(rol);
    }

    private Proceso crearProceso(Empresa empresa, Usuario autor, String nombre, String categoria,
            EstadoProceso estado, boolean activo) {
        LocalDateTime ahora = LocalDateTime.now();
        Proceso proceso = new Proceso();
        proceso.setEmpresa(empresa);
        proceso.setNombre(nombre);
        proceso.setDescripcion("Proceso de ejemplo: " + nombre + ".");
        proceso.setCategoria(categoria);
        proceso.setEstado(estado);
        proceso.setActivo(activo);
        proceso.setFechaCreacion(ahora);
        proceso.setFechaModificacion(ahora);
        proceso = procesoRepository.save(proceso);

        HistorialCambio cambio = new HistorialCambio();
        cambio.setEmpresa(empresa);
        cambio.setProceso(proceso);
        cambio.setAutor(autor);
        cambio.setFechaCambio(ahora);
        cambio.setDescripcionCambio("Proceso creado con datos de ejemplo.");
        historialCambioRepository.save(cambio);
        return proceso;
    }

    private Pool crearPool(Empresa empresa, Proceso proceso, String nombre, TipoParticipante tipo,
            boolean cajaNegra, int orden) {
        Pool pool = new Pool();
        pool.setEmpresa(empresa);
        pool.setProceso(proceso);
        pool.setNombre(nombre);
        pool.setTipoParticipante(tipo);
        pool.setCajaNegra(cajaNegra);
        pool.setOrden(orden);
        return poolRepository.save(pool);
    }

    private Lane crearLane(Empresa empresa, Pool pool, RolProceso rol, int orden) {
        Lane lane = new Lane();
        lane.setEmpresa(empresa);
        lane.setPool(pool);
        lane.setRolProceso(rol);
        lane.setNombre(rol.getNombre());
        lane.setOrden(orden);
        return laneRepository.save(lane);
    }

    private Actividad crearActividad(Empresa empresa, Lane lane, String nombre, TipoActividad tipo, int x, int y) {
        Actividad actividad = new Actividad();
        posicionar(actividad, empresa, lane, nombre, x, y);
        actividad.setTipoActividad(tipo);
        return nodoFlujoRepository.save(actividad);
    }

    private Gateway crearGateway(Empresa empresa, Lane lane, String nombre, TipoGateway tipo, int x, int y) {
        Gateway gateway = new Gateway();
        posicionar(gateway, empresa, lane, nombre, x, y);
        gateway.setTipoGateway(tipo);
        return nodoFlujoRepository.save(gateway);
    }

    private EventoMensaje crearEventoThrow(Empresa empresa, Lane lane, String nombre, String contenido,
            String clave, int x, int y) {
        EventoMensaje evento = new EventoMensaje();
        posicionar(evento, empresa, lane, nombre, x, y);
        evento.setTipoEvento(TipoEventoMensaje.THROW);
        evento.setContenido(contenido);
        evento.setClaveCorrelacion(clave);
        evento.setOrigenExterno(false);
        return nodoFlujoRepository.save(evento);
    }

    private void posicionar(NodoFlujo nodo, Empresa empresa, Lane lane, String nombre, int x, int y) {
        nodo.setEmpresa(empresa);
        nodo.setLane(lane);
        nodo.setNombre(nombre);
        nodo.setPosicionX(x);
        nodo.setPosicionY(y);
        nodo.setActivo(true);
    }

    private void conectar(Empresa empresa, Pool pool, NodoFlujo origen, NodoFlujo destino, String etiqueta,
            String condicion) {
        Arco arco = new Arco();
        arco.setEmpresa(empresa);
        arco.setPool(pool);
        arco.setOrigen(origen);
        arco.setDestino(destino);
        arco.setEtiqueta(etiqueta);
        arco.setCondicion(condicion);
        arco.setActivo(true);
        arcoRepository.save(arco);
    }

    /** El proceso de referencia del enunciado: solicitud de vacaciones con todos los elementos. */
    private void crearProcesoVacaciones(Empresa empresa, Usuario autor) {
        RolProceso empleado = crearRol(empresa, "Empleado", "Quien radica la solicitud");
        RolProceso jefe = crearRol(empresa, "Jefe inmediato", "Quien revisa y decide la solicitud");
        RolProceso talentoHumano = crearRol(empresa, "Talento Humano", "Quien registra el resultado");

        Proceso proceso = crearProceso(empresa, autor, "Solicitud de vacaciones", "Talento Humano",
                EstadoProceso.PUBLICADO, true);
        Pool pool = crearPool(empresa, proceso, "Empresa", TipoParticipante.EMPRESA, false, 0);
        Pool correo = crearPool(empresa, proceso, "Servicio de correo", TipoParticipante.SISTEMA_EXTERNO, true, 1);

        Lane laneEmpleado = crearLane(empresa, pool, empleado, 0);
        Lane laneJefe = crearLane(empresa, pool, jefe, 1);
        Lane laneTalento = crearLane(empresa, pool, talentoHumano, 2);

        Actividad radicar = crearActividad(empresa, laneEmpleado, "Radicar solicitud", TipoActividad.TAREA, 80, 60);
        Actividad revisar = crearActividad(empresa, laneJefe, "Revisar solicitud", TipoActividad.USUARIO, 240, 60);
        Gateway aprobada = crearGateway(empresa, laneJefe, "Solicitud aprobada", TipoGateway.EXCLUSIVO, 400, 60);
        Actividad rechazo = crearActividad(empresa, laneTalento, "Registrar rechazo", TipoActividad.TAREA, 560, 30);
        Actividad dias = crearActividad(empresa, laneTalento, "Actualizar dias", TipoActividad.TAREA, 560, 110);
        EventoMensaje notificar = crearEventoThrow(empresa, laneTalento, "NotificarAprobacion",
                "numeroSolicitud (texto), fechaInicio (fecha), fechaFin (fecha)", "numeroSolicitud", 720, 110);

        conectar(empresa, pool, radicar, revisar, null, null);
        conectar(empresa, pool, revisar, aprobada, null, null);
        conectar(empresa, pool, aprobada, rechazo, "No", "La solicitud no es aprobada");
        conectar(empresa, pool, aprobada, dias, "Si", "La solicitud es aprobada");
        conectar(empresa, pool, dias, notificar, null, null);

        Mensaje mensaje = new Mensaje();
        mensaje.setEmpresa(empresa);
        mensaje.setProceso(proceso);
        mensaje.setNombre("NotificarAprobacion");
        mensaje.setContenido(notificar.getContenido());
        mensaje.setClaveCorrelacion("numeroSolicitud");
        mensaje.setPoolOrigen(pool);
        mensaje.setPoolDestino(correo);
        mensaje.setEventoThrow(notificar);
        mensaje.setTipoDestinoExterno(TipoDestinoExterno.CORREO);
        mensaje.setDestinoExterno("Notificacion al empleado por correo electronico");
        mensaje.setPoliticaFalloNotificacion(PoliticaFalloNotificacion.CONTINUAR_FLUJO);
        mensaje = mensajeRepository.save(mensaje);

        Correlacion correlacion = new Correlacion();
        correlacion.setEmpresa(empresa);
        correlacion.setMensaje(mensaje);
        correlacion.setCriterio("numeroSolicitud");
        correlacionRepository.save(correlacion);
    }

    /** Proceso minimo: su pool de empresa y una lane con una actividad, sin mensajes. */
    private void crearProcesoSimple(Empresa empresa, Usuario autor, String nombre, String categoria,
            EstadoProceso estado, boolean activo) {
        RolProceso rol = crearRol(empresa, "Responsable de " + nombre.toLowerCase(), "Rol de ejemplo");
        Proceso proceso = crearProceso(empresa, autor, nombre, categoria, estado, activo);
        Pool pool = crearPool(empresa, proceso, "Empresa", TipoParticipante.EMPRESA, false, 0);
        Lane lane = crearLane(empresa, pool, rol, 0);
        crearActividad(empresa, lane, "Ejecutar tarea", TipoActividad.TAREA, 80, 60);
    }
}
