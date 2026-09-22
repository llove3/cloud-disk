package com.example.clouddisk.config;

import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String avatarLocation = Paths.get(uploadDir, "avatars")
                .toAbsolutePath().normalize().toUri().toString();
        if (!avatarLocation.endsWith("/")) {
            avatarLocation += "/";
        }
        registry.addResourceHandler("/avatars/**")
                .addResourceLocations(avatarLocation);
    }
}
