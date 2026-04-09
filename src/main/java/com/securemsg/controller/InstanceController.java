package com.securemsg.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Контроллер для получения информации о текущем экземпляре приложения.
 * Используется балансировщиком нагрузки (Nginx) и скейлером для
 * отслеживания состояния контейнеров.
 */
@RestController
public class InstanceController {

    private static final Logger log = LoggerFactory.getLogger(InstanceController.class);

    /**
     * Имя экземпляра (например, "app-1", "app-2").
     * Значение берётся из переменной окружения INSTANCE_NAME.
     * По умолчанию: "unknown".
     */
    @Value("${INSTANCE_NAME:unknown}")
    private String instanceName;

    /**
     * ID экземпляра (например, "1", "2").
     * Значение берётся из переменной окружения INSTANCE_ID.
     * По умолчанию: "0".
     */
    @Value("${INSTANCE_ID:0}")
    private String instanceId;

    /**
     * Возвращает информацию о текущем экземпляре приложения.
     * Используется:
     * <ul>
     *     <li>Балансировщиком нагрузки Nginx для проверки доступности</li>
     *     <li>Скейлером для отслеживания количества контейнеров</li>
     *     <li>Для отладки и мониторинга</li>
     * </ul>
     *
     * @return Map с полями:
     *         <ul>
     *             <li>instance_name - имя экземпляра</li>
     *             <li>instance_id - ID экземпляра</li>
     *             <li>timestamp - текущее время в миллисекундах</li>
     *         </ul>
     */
    @GetMapping("/instance-info")
    public Map<String, String> getInstanceInfo() {
        log.debug("Instance info requested: name={}, id={}", instanceName, instanceId);

        Map<String, String> info = new HashMap<>();
        info.put("instance_name", instanceName);
        info.put("instance_id", instanceId);
        info.put("timestamp", String.valueOf(System.currentTimeMillis()));

        log.debug("Returning instance info: {}", info);
        return info;
    }
}