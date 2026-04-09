package com.securemsg.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Конфигурация Web MVC.
 * Настраивает view controllers (контроллеры без бизнес-логики)
 * для простого отображения страниц.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

    /**
     * Регистрирует простые view controllers для страниц,
     * которые не требуют дополнительной бизнес-логики.
     *
     * @param registry реестр view controllers
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        log.debug("Registering view controllers");

        registry.addViewController("/").setViewName("redirect:/dashboard");
        registry.addViewController("/dashboard").setViewName("dashboard");
        registry.addViewController("/login").setViewName("login");
        registry.addViewController("/compose").setViewName("compose");
        registry.addViewController("/access-denied").setViewName("access-denied");

        log.debug("View controllers registered: / -> redirect:/dashboard, /dashboard, /login, /compose");
    }
}