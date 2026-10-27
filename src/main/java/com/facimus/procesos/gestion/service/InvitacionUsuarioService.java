package com.facimus.procesos.gestion.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.InvitacionUsuario;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.InvitacionUsuarioRepository;

import lombok.RequiredArgsConstructor;

/** HU-02: invitacion de colaboradores mediante un enlace temporal. */
@Service
@RequiredArgsConstructor
public class InvitacionUsuarioService {

    private final InvitacionUsuarioRepository invitacionUsuarioRepository;
    private final EmpresaService empresaService;
    private final UsuarioService usuarioService;

    @Transactional
    public InvitacionUsuario crear(Long empresaId, String email, RolAcceso rolAcceso) {
        LocalDateTime ahora = LocalDateTime.now();
        invitacionUsuarioRepository.deleteAllByFechaExpiracionBeforeAndUsadaFalse(ahora);
        if (usuarioService.existePorEmail(email)) {
            throw new ReglaNegocioException("Ya existe un usuario registrado con ese correo.");
        }
        if (invitacionUsuarioRepository.existsByEmpresaIdAndEmailIgnoreCaseAndUsadaFalseAndFechaExpiracionAfter(
                empresaId, email, ahora)) {
            throw new ReglaNegocioException("Ya existe una invitacion pendiente para ese correo.");
        }
        Empresa empresa = empresaService.obtener(empresaId);

        InvitacionUsuario invitacion = new InvitacionUsuario();
        invitacion.setEmpresa(empresa);
        invitacion.setEmail(email.trim().toLowerCase());
        invitacion.setRolAcceso(rolAcceso);
        invitacion.setToken(UUID.randomUUID().toString());
        invitacion.setFechaExpiracion(ahora.plusHours(48));
        invitacion.setUsada(false);
        return invitacionUsuarioRepository.save(invitacion);
    }

    @Transactional
    public Usuario aceptar(String token, String nombre, String password) {
        InvitacionUsuario invitacion = invitacionUsuarioRepository.findByTokenAndUsadaFalse(token)
                .orElseThrow(() -> new RecursoNoEncontradoException("Invitacion no encontrada o ya utilizada."));
        if (invitacion.getFechaExpiracion().isBefore(LocalDateTime.now())) {
            throw new ReglaNegocioException("La invitacion ha expirado.");
        }
        Usuario usuario = usuarioService.crearColaborador(invitacion.getEmpresa().getId(), nombre,
                invitacion.getEmail(), password, invitacion.getRolAcceso());
        invitacion.setUsada(true);
        invitacionUsuarioRepository.save(invitacion);
        return usuario;
    }
}
