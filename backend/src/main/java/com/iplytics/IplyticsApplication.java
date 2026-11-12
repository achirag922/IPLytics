package com.iplytics;

import com.iplytics.ingestion.IngestionRunner;
import com.iplytics.ingestion.cricsheet.CricsheetConvertRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
@ConfigurationPropertiesScan
public class IplyticsApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(IplyticsApplication.class, args);
        if (context.getEnvironment().matchesProfiles(IngestionRunner.PROFILE, CricsheetConvertRunner.PROFILE)) {
            // One-shot CLI: close the context and return the runner's exit code to the shell.
            System.exit(SpringApplication.exit(context));
        }
    }
}
