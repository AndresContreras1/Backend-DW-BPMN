package com.facimus.procesos.gestion.service;

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
}
