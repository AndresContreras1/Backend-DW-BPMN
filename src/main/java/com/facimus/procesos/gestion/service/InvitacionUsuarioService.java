package com.facimus.procesos.gestion.service;

import com.facimus.procesos.gestion.model.InvitacionUsuario;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.model.Usuario;

public interface InvitacionUsuarioService {

    InvitacionUsuario crear(Long empresaId, String email, RolAcceso rolAcceso);

    Usuario aceptar(String token, String nombre, String password);
}
