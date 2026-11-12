package com.iplytics.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.util.Optional;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI document metadata. Docs: /v3/api-docs, UI: /swagger-ui.html */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI iplyticsOpenApi(Optional<BuildProperties> buildProperties) {
        return new OpenAPI().info(new Info()
                .title("IPLytics API")
                .description("IPL Analytics & Intelligence Platform - read-only analytics API")
                .version(buildProperties.map(BuildProperties::getVersion).orElse("dev")));
    }
}
