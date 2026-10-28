package com.facimus.procesos.migraciones;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Ejecuta las migraciones comunes de Flyway sobre H2 en modo PostgreSQL y deja que Hibernate las
 * valide contra las entidades (ddl-auto=validate): si una entidad cambia y falta su migracion, el
 * contexto no arranca y la prueba falla. Los indices parciales de PostgreSQL (carpeta postgresql)
 * no los entiende H2 y quedan fuera.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:migraciones;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration/common" })
@ActiveProfiles("test")
class MigracionesTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("Migraciones_aplicar_esquemaCoincideConLasEntidades")
    void Migraciones_aplicar_esquemaCoincideConLasEntidades() {
        Flyway flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration/common").load();

        assertThat(flyway.info().applied()).isNotEmpty();
        assertThat(flyway.info().pending()).isEmpty();
    }
}
