package com.facimus.procesos.gestion.service;

import java.util.Optional;
import com.facimus.procesos.gestion.model.Empresa;

public interface EmpresaService {

    Empresa registrar(String nombre, String nit, String correoContacto);

    Empresa obtener(Long empresaId);

    Optional<Empresa> buscarPorNit(String nit);
}
