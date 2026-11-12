package com.iplytics.system.service;

import java.util.Optional;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

@Service
public class SystemService {

    private final Flyway flyway;
    private final String applicationName;
    private final String version;

    public SystemService(Flyway flyway,
                         Optional<BuildProperties> buildProperties,
                         @Value("${spring.application.name}") String applicationName) {
        this.flyway = flyway;
        this.applicationName = applicationName;
        this.version = buildProperties.map(BuildProperties::getVersion).orElse("dev");
    }

    public SystemStatus status() {
        MigrationInfo current = flyway.info().current();
        String schemaVersion = (current == null || current.getVersion() == null)
                ? null
                : current.getVersion().getVersion();
        return new SystemStatus(applicationName, version, schemaVersion);
    }
}
