package org.example.communityapi.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// http://localhost:8080/swagger-ui.html
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        String securityJwtName = "JWT Token";

        // SecurityRequirement 설정 (요청 헤더에 토큰 포함)
        SecurityRequirement securityRequirement = new SecurityRequirement().addList(securityJwtName);

        // SecurityScheme 설정 (Authorize 버튼 생성 및 Bearer 방식 지정)
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