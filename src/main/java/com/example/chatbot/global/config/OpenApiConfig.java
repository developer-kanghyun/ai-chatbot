package com.example.chatbot.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        String securitySchemeName = "X-API-Key";
        
        return new OpenAPI()
                .info(new Info()
                        .title("AI Chatbot API")
                        .version("1.0.0")
                        .description("4주차 미션: OpenAI 기반 채팅 서비스 API 문서입니다.")
                        .contact(new Contact().name("김강현").url("https://github.com/developer-kanghyun")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name("X-API-Key")
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .description("API 호출을 위해 발급된 키를 입력하세요.")));
    }
}
