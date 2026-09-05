package com.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class SwaggerConfig {

    @Value("${spring.application.name:Service}")
    private String applicationName;

    @Bean
    public OpenAPI customOpenAPI() {
        String friendlyName = StringUtils.hasText(applicationName) 
            ? StringUtils.capitalize(applicationName.replace("-", " "))
            : "Microservice";

        return new OpenAPI()
                .info(new Info()
                        .title(friendlyName + " API")
                        .version("1.0.0")
                        .description("API documentation for " + friendlyName)
                        .license(new License().name("Apache 2.0").url("http://springdoc.org")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new io.swagger.v3.oas.models.Components()
                        .addSecuritySchemes("Bearer Authentication",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
