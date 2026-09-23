package org.example.communityapi.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    // http://localhost:8080/swagger-ui/index.html
    @Bean
    public OpenAPI openAPI() {
        String securityJwtName = "JWT Token";

        SecurityRequirement securityRequirement = new SecurityRequirement().addList(securityJwtName);

        Components components = new Components()
                .addSecuritySchemes(securityJwtName, new SecurityScheme()
                        .name(securityJwtName)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"));

        return new OpenAPI()
                .info(new Info()
                        .title("Community API Document")
                        .description("커뮤니티 프로젝트 API 명세서")
                        .version("v1.1.0"))
                .addSecurityItem(securityRequirement)
                .components(components);
    }
}