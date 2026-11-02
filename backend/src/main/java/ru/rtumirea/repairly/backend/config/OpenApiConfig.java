package ru.rtumirea.repairly.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Конфигурирует документацию REST API. */
@Configuration
public class OpenApiConfig {
  @Bean
  public OpenAPI repairlyOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Repairly REST API")
                .version("1.0.0")
                .description("Единый API для web и Android"))
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
  }
}
