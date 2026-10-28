package com.facimus.procesos.gestion.service;

import com.facimus.procesos.gestion.controller.dto.ActualizarUsuarioRequest;
import com.facimus.procesos.gestion.controller.dto.CrearUsuarioRequest;
import com.facimus.procesos.gestion.controller.dto.LoginRequest;
import com.facimus.procesos.gestion.controller.dto.LoginResponse;
import com.facimus.procesos.gestion.controller.dto.UsuarioResponse;
import com.facimus.procesos.security.ApiPrincipal;
import com.facimus.procesos.security.JwtService;
import org.modelmapper.ModelMapper;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.List;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/** HU-02: alta y administracion de colaboradores. HU-03: inicio de sesion. */
@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    private static final String TIPO_TOKEN = "Bearer";
    private static final String CREDENCIALES_INVALIDAS = "Correo o contrasena incorrectos.";
    /** Hash BCrypt precalculado de una clave que nadie usa: iguala el tiempo de respuesta cuando el correo no sirve. */
    private static final String HASH_FICTICIO = "$2a$10$9gXDubFVHPZOG1sk665ql.Qiwrep1XCkebtnBCwlTnEvibos4UlY.";

    private final UsuarioRepository usuarioRepository;
    private final EmpresaService empresaService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ModelMapper modelMapper;

    @Transactional
    @Override
    public Usuario crearColaborador(Long empresaId, String nombre, String email, String password,
            RolAcceso rolAcceso) {
        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw new ReglaNegocioException("Ya existe un usuario registrado con el correo " + email + ".");
        }
        Empresa empresa = empresaService.obtener(empresaId);

        Usuario usuario = new Usuario();
        usuario.setEmpresa(empresa);
        usuario.setNombre(nombre);
        usuario.setEmail(email);
        usuario.setPasswordHash(passwordEncoder.encode(password));
        usuario.setRolAcceso(rolAcceso);
        usuario.setActivo(true);
        return usuarioRepository.save(usuario);
    }

    @Transactional
    @Override
    public Usuario cambiarRolAcceso(Long empresaId, Long usuarioId, RolAcceso nuevoRol) {
        return actualizar(empresaId, usuarioId, nuevoRol, null);
    }

    @Transactional
    @Override
    public Usuario actualizar(Long empresaId, Long usuarioId, RolAcceso rolAcceso, Boolean activo) {
        Usuario usuario = obtener(empresaId, usuarioId);
        boolean dejaDeSerAdmin = rolAcceso != null && rolAcceso != RolAcceso.ADMINISTRADOR;
        boolean seDesactiva = Boolean.FALSE.equals(activo);
        if (dejaDeSerAdmin || seDesactiva) {
            validarQueNoSeaElUltimoAdministrador(empresaId, usuario);
        }
        if (rolAcceso != null) {
            usuario.setRolAcceso(rolAcceso);
        }
        if (activo != null) {
            usuario.setActivo(activo);
        }
        return usuarioRepository.save(usuario);
    }

    @Transactional
    @Override
    public void desactivar(Long empresaId, Long usuarioId) {
        Usuario usuario = obtener(empresaId, usuarioId);
        validarQueNoSeaElUltimoAdministrador(empresaId, usuario);
        usuario.setActivo(false);
        usuarioRepository.save(usuario);
    }

    /** La empresa siempre debe conservar al menos un administrador activo: ni se degrada ni se desactiva al ultimo. */
    private void validarQueNoSeaElUltimoAdministrador(Long empresaId, Usuario usuario) {
        if (usuario.isActivo() && usuario.getRolAcceso() == RolAcceso.ADMINISTRADOR
                && usuarioRepository.countByEmpresaIdAndRolAccesoAndActivoTrue(empresaId,
                        RolAcceso.ADMINISTRADOR) <= 1) {
            throw new ReglaNegocioException(
                    "No se puede degradar ni desactivar al ultimo administrador activo de la empresa.");
        }
    }

    @Transactional(readOnly = true)
    @Override
    public Usuario autenticar(String email, String password) {
        Optional<Usuario> candidato = usuarioRepository.findByEmailIgnoreCase(email).filter(Usuario::isActivo);
        // Siempre se ejecuta un matches (contra un hash ficticio si no hay usuario valido) para que el tiempo
        // de respuesta no revele si el correo existe o esta inactivo.
        String hash = candidato.map(Usuario::getPasswordHash).orElse(HASH_FICTICIO);
        boolean coincide = passwordEncoder.matches(password, hash);
        if (candidato.isEmpty() || !coincide) {
            throw new ReglaNegocioException(CREDENCIALES_INVALIDAS);
        }
        return candidato.get();
    }

    @Transactional(readOnly = true)
    @Override
    public List<Usuario> listarPorEmpresa(Long empresaId) {
        return usuarioRepository.findAllByEmpresaIdAndActivoTrue(empresaId);
    }

    @Transactional(readOnly = true)
    @Override
    public Usuario obtener(Long empresaId, Long usuarioId) {
        return usuarioRepository.findByIdAndEmpresaId(usuarioId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));
    }

    @Transactional(readOnly = true)
    @Override
    public boolean existePorEmail(String email) {
        return usuarioRepository.existsByEmailIgnoreCase(email);
    }

    @Transactional(readOnly = true)
    @Override
    public java.util.Optional<Usuario> buscar(Long empresaId, Long usuarioId) {
        return usuarioRepository.findByIdAndEmpresaId(usuarioId, empresaId);
    }

    @Transactional(readOnly = true)
    @Override
    public LoginResponse autenticar(LoginRequest request) {
        Usuario usuario;
        try {
            usuario = autenticar(request.email(), request.password());
        } catch (ReglaNegocioException e) {
            // Sin la causa a proposito: Spring MVC buscaria un handler para ella y responderia 409.
            // Asi lo resuelve JwtAuthEntryPoint con 401 y el mismo mensaje generico, exista o no el correo (HU-03).
            throw new BadCredentialsException(e.getMessage());
        }
        String token = jwtService.generarToken(ApiPrincipal.of(usuario));
        return new LoginResponse(token, TIPO_TOKEN, jwtService.getExpirationSeconds(),
                modelMapper.map(usuario, UsuarioResponse.class));
    }

    @Transactional(readOnly = true)
    @Override
    public List<UsuarioResponse> consultarPorEmpresa(Long empresaId) {
        return listarPorEmpresa(empresaId).stream()
                .map(u -> modelMapper.map(u, UsuarioResponse.class))
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<UsuarioResponse> consultarPorEmpresa(Long empresaId, Boolean activo) {
        boolean soloActivos = activo == null || activo;
        return usuarioRepository.findAllByEmpresaIdAndActivo(empresaId, soloActivos).stream()
                .map(u -> modelMapper.map(u, UsuarioResponse.class))
                .toList();
    }

    @Transactional
    @Override
    public UsuarioResponse crearColaborador(Long empresaId, CrearUsuarioRequest request) {
        Usuario usuario = crearColaborador(empresaId, request.nombre(), request.email(), request.password(),
                request.rolAcceso());
        return modelMapper.map(usuario, UsuarioResponse.class);
    }

    @Transactional(readOnly = true)
    @Override
    public UsuarioResponse consultarDetalle(Long empresaId, Long usuarioId) {
        return modelMapper.map(obtener(empresaId, usuarioId), UsuarioResponse.class);
    }

    @Transactional
    @Override
    public UsuarioResponse actualizar(Long empresaId, Long usuarioId, ActualizarUsuarioRequest request) {
        Usuario usuario = actualizar(empresaId, usuarioId, request.rolAcceso(), request.activo());
        return modelMapper.map(usuario, UsuarioResponse.class);
    }
}
