package com.toyproject.eron.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final EternalReturnApiProperties properties;

    public WebConfig(EternalReturnApiProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(properties.getCorsAllowedOrigins())
                .allowedMethods("GET")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
