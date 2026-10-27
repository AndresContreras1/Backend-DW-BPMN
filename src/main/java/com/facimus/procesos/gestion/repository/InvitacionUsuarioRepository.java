package com.facimus.procesos.gestion.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.model.InvitacionUsuario;

public interface InvitacionUsuarioRepository extends RepositorioTenant<InvitacionUsuario> {

    Optional<InvitacionUsuario> findByTokenAndUsadaFalse(String token);

    boolean existsByEmpresaIdAndEmailIgnoreCaseAndUsadaFalseAndFechaExpiracionAfter(
            Long empresaId, String email, LocalDateTime fecha);

    long deleteAllByFechaExpiracionBeforeAndUsadaFalse(LocalDateTime fecha);
}
