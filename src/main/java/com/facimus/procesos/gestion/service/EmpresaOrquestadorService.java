package com.facimus.procesos.gestion.service;

import com.facimus.procesos.gestion.model.Empresa;

public interface EmpresaOrquestadorService {

    Empresa registrarConAdministrador(String nombreEmpresa, String nit, String correoContacto, String nombreAdmin, String emailAdmin, String passwordAdmin);
}
