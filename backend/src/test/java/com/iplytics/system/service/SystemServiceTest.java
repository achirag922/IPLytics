package com.iplytics.system.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Properties;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.info.BuildProperties;

@ExtendWith(MockitoExtension.class)
class SystemServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Flyway flyway;

    @Test
    void reportsBuildVersionAndCurrentSchemaVersion() {
        Properties build = new Properties();
        build.setProperty("version", "1.2.3");
        when(flyway.info().current().getVersion()).thenReturn(MigrationVersion.fromVersion("7"));

        SystemService service = new SystemService(flyway, Optional.of(new BuildProperties(build)), "iplytics");

        assertThat(service.status()).isEqualTo(new SystemStatus("iplytics", "1.2.3", "7"));
    }

    @Test
    void fallsBackWhenNoBuildInfoAndNoMigrationApplied() {
        when(flyway.info().current()).thenReturn(null);

        SystemService service = new SystemService(flyway, Optional.empty(), "iplytics");

        assertThat(service.status()).isEqualTo(new SystemStatus("iplytics", "dev", null));
    }
}
