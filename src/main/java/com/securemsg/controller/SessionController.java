package com.securemsg.controller;

import com.securemsg.service.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Контроллер для получения информации о сессиях и контейнерах.
 * Используется скейлером (scale.ps1) для мониторинга загрузки контейнеров.
 */
@RestController
public class SessionController {

    private static final Logger log = LoggerFactory.getLogger(SessionController.class);

    @Autowired
    private SessionManager sessionManager;

    /**
     * Возвращает статистику по контейнерам: количество активных пользователей
     * в каждом контейнере приложения.
     * Используется скейлером для принятия решений о масштабировании.
     *
     * @return Map, где ключ — имя контейнера (например, "app-1"),
     *         значение — количество активных пользователей в нём
     */
    @GetMapping("/api/session/containers")
    public Map<String, Integer> getContainers() {
        log.debug("Container stats requested");
        Map<String, Integer> stats = sessionManager.getContainersStats();
        log.debug("Returning container stats: {}", stats);
        return stats;
    }
}