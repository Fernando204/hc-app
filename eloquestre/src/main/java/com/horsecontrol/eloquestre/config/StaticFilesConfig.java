package com.horsecontrol.eloquestre.config;

import com.horsecontrol.eloquestre.services.files.FileStorageService;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

/** Serve os arquivos enviados em GET /files/** (públicos: nomes são UUIDs imprevisíveis). */
@Configuration
public class StaticFilesConfig implements WebMvcConfigurer {

    private final FileStorageService storage;

    public StaticFilesConfig(FileStorageService storage) {
        this.storage = storage;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/files/**")
                .addResourceLocations(storage.root().toUri().toString())
                .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic()); // arquivo nunca muda (nome novo a cada envio)
    }
}
