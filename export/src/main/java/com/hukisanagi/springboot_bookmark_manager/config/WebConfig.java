package com.hukisanagi.springboot_bookmark_manager.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String faviconDir = Paths.get("data/favicons").toAbsolutePath().toString();
        registry.addResourceHandler("/favicons/**")
                .addResourceLocations("file:" + faviconDir + "/");
    }
}
