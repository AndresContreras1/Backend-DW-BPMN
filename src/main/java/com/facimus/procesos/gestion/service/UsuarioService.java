package com.facimus.procesos.gestion.service;

import com.facimus.procesos.gestion.controller.dto.ActualizarUsuarioRequest;
import com.facimus.procesos.gestion.controller.dto.CrearUsuarioRequest;
import com.facimus.procesos.gestion.controller.dto.LoginRequest;
import com.facimus.procesos.gestion.controller.dto.LoginResponse;
import com.facimus.procesos.gestion.controller.dto.UsuarioResponse;

import java.util.List;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.model.Usuario;

public interface UsuarioService {

    Usuario crearColaborador(Long empresaId, String nombre, String email, String password, RolAcceso rolAcceso);

    Usuario cambiarRolAcceso(Long empresaId, Long usuarioId, RolAcceso nuevoRol);

    Usuario actualizar(Long empresaId, Long usuarioId, RolAcceso rolAcceso, Boolean activo);

    void desactivar(Long empresaId, Long usuarioId);

    Usuario autenticar(String email, String password);

    List<Usuario> listarPorEmpresa(Long empresaId);

    Usuario obtener(Long empresaId, Long usuarioId);

    boolean existePorEmail(String email);

    java.util.Optional<Usuario> buscar(Long empresaId, Long usuarioId);

    LoginResponse autenticar(LoginRequest request);
    List<UsuarioResponse> consultarPorEmpresa(Long empresaId);
    /** {@code activo} nulo equivale a true; false lista los usuarios desactivados. */
    List<UsuarioResponse> consultarPorEmpresa(Long empresaId, Boolean activo);
    UsuarioResponse crearColaborador(Long empresaId, CrearUsuarioRequest request);
    UsuarioResponse consultarDetalle(Long empresaId, Long usuarioId);
    UsuarioResponse actualizar(Long empresaId, Long usuarioId, ActualizarUsuarioRequest request);
}
