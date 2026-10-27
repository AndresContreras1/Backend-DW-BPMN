package com.facimus.procesos.gestion.service;

import java.util.List;

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
public class UsuarioService {

    private static final String CREDENCIALES_INVALIDAS = "Correo o contrasena incorrectos.";

    private final UsuarioRepository usuarioRepository;
    private final EmpresaService empresaService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
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
    public Usuario cambiarRolAcceso(Long empresaId, Long usuarioId, RolAcceso nuevoRol) {
        return actualizar(empresaId, usuarioId, nuevoRol, null);
    }

    @Transactional
    public Usuario actualizar(Long empresaId, Long usuarioId, RolAcceso rolAcceso, Boolean activo) {
        Usuario usuario = obtener(empresaId, usuarioId);
        if (rolAcceso != null) {
            usuario.setRolAcceso(rolAcceso);
        }
        if (activo != null) {
            usuario.setActivo(activo);
        }
        return usuarioRepository.save(usuario);
    }

    @Transactional
    public void desactivar(Long empresaId, Long usuarioId) {
        Usuario usuario = obtener(empresaId, usuarioId);
        usuario.setActivo(false);
        usuarioRepository.save(usuario);
    }

    @Transactional(readOnly = true)
    public Usuario autenticar(String email, String password) {
        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(email)
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new ReglaNegocioException(CREDENCIALES_INVALIDAS));
        if (!passwordEncoder.matches(password, usuario.getPasswordHash())) {
            throw new ReglaNegocioException(CREDENCIALES_INVALIDAS);
        }
        return usuario;
    }

    @Transactional(readOnly = true)
    public List<Usuario> listarPorEmpresa(Long empresaId) {
        return usuarioRepository.findAllByEmpresaIdAndActivoTrue(empresaId);
    }

    @Transactional(readOnly = true)
    public Usuario obtener(Long empresaId, Long usuarioId) {
        return usuarioRepository.findByIdAndEmpresaId(usuarioId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));
    }

    @Transactional(readOnly = true)
    public boolean existePorEmail(String email) {
        return usuarioRepository.existsByEmailIgnoreCase(email);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<Usuario> buscar(Long empresaId, Long usuarioId) {
        return usuarioRepository.findByIdAndEmpresaId(usuarioId, empresaId);
    }
}
