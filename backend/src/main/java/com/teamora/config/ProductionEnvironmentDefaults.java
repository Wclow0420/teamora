package com.teamora.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * When {@code teamora.env} (TEAMORA_ENV) is {@code production}, force the public
 * API docs off: {@code /v3/api-docs} and the Swagger UI describe every endpoint and
 * are a dev convenience only. Registered in {@code META-INF/spring.factories}.
 */
public class ProductionEnvironmentDefaults implements EnvironmentPostProcessor {

    static final String SOURCE_NAME = "teamoraProductionDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String env = environment.getProperty("teamora.env", environment.getProperty("TEAMORA_ENV", "dev"));
        if (!TeamoraProperties.ENV_PRODUCTION.equalsIgnoreCase(env.trim())) {
            return;
        }
        environment.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, Map.of(
                "springdoc.api-docs.enabled", "false",
                "springdoc.swagger-ui.enabled", "false")));
    }
}
