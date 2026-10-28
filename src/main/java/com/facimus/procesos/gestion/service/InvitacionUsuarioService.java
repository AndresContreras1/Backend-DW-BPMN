package com.facimus.procesos.gestion.service;

import com.facimus.procesos.gestion.controller.dto.AceptarInvitacionRequest;
import com.facimus.procesos.gestion.controller.dto.InvitacionUsuarioRequest;
import com.facimus.procesos.gestion.controller.dto.InvitacionUsuarioResponse;
import com.facimus.procesos.gestion.controller.dto.UsuarioResponse;

import com.facimus.procesos.gestion.model.InvitacionUsuario;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.model.Usuario;

public interface InvitacionUsuarioService {

    InvitacionUsuario crear(Long empresaId, String email, RolAcceso rolAcceso);

    Usuario aceptar(String token, String nombre, String password);

    InvitacionUsuarioResponse crear(Long empresaId, InvitacionUsuarioRequest request);
    UsuarioResponse aceptar(String token, AceptarInvitacionRequest request);
}
