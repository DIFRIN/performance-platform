package com.performance.platform.app.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration de l'IHM web — activee uniquement quand {@code web.ui.enabled=true}.
 * <p>
 * Configure le service des ressources statiques de l'interface utilisateur :
 * <ul>
 *   <li>{@code /} — sert {@code index.html} (forward)</li>
 *   <li>{@code /index.html} — fichier statique depuis {@code classpath:/static/}</li>
 *   <li>{@code /assets/**} — ressources statiques depuis {@code classpath:/static/assets/}</li>
 * </ul>
 * <p>
 * Les routes SPA sont gerees cote client en mode hash ({@code #/...}),
 * aucun fallback serveur n'est necessaire.
 * <p>
 * <b>ADR-019</b> : en mode AGENT, cette configuration n'est jamais chargee
 * car le {@code WebApplicationType.NONE} est force dans {@code main()}
 * et le profil agent definit {@code web.ui.enabled=false}.
 */
@Configuration
@ConditionalOnProperty(prefix = "web.ui", name = "enabled", havingValue = "true")
public class WebUiConfiguration implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebUiConfiguration.class);

    public WebUiConfiguration() {
        log.info("action=web_ui_configuration status=enabled");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/");
        registry.addResourceHandler("/index.html")
                .addResourceLocations("classpath:/static/index.html");
        log.info("action=resource_handlers_registered paths=/assets/**,/index.html");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
        log.info("action=view_controller_registered path=/ forward=/index.html");
    }
}
