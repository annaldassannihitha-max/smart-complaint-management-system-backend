package com.smartcomplaint.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {

        return new WebMvcConfigurer() {

            @Override
            public void addCorsMappings(CorsRegistry registry) {

                registry
                    .addMapping("/**")
                    .allowedOriginPatterns(
                        "https://smart-complaint-management-system-frontend-65kcreiml.vercel.app",
                        "https://*.vercel.app",
                        "http://localhost:5173"
                    )
                    .allowedMethods(
                        "GET",
                        "POST",
                        "PUT",
                        "PATCH",
                        "DELETE",
                        "OPTIONS"
                    )
                    .allowedHeaders("*")
                    .allowCredentials(false)
                    .maxAge(3600);
            }
        };
    }
}