package com.securemsg.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Менеджер сессий для управления распределением пользователей по контейнерам.
 * Использует Redis для хранения информации о:
 * <ul>
 *     <li>Активных контейнерах</li>
 *     <li>Привязке пользователей к контейнерам</li>
 *     <li>Количестве пользователей в каждом контейнере</li>
 * </ul>
 * Обеспечивает горизонтальное масштабирование приложения.
 */
@Service
public class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final int MAX_USERS_PER_CONTAINER = 100;
    private static final String CONTAINERS_KEY = "active_containers";
    private static final String USER_CONTAINER_PREFIX = "user:";
    private static final String CONTAINER_USERS_PREFIX = "container:";

    /**
     * Регистрирует текущий контейнер в Redis при старте приложения.
     */
    @PostConstruct
    public void registerThisContainer() {
        String instanceId = System.getenv("INSTANCE_ID");
        if (instanceId == null) instanceId = "1";

        String containerName = "app-" + instanceId;
        redisTemplate.opsForSet().add(CONTAINERS_KEY, containerName);
        log.info("📦 Контейнер {} зарегистрирован", containerName);
    }

    /**
     * Привязывает пользователя к контейнеру.
     * Алгоритм:
     * <ol>
     *     <li>Проверяет, не привязан ли пользователь уже</li>
     *     <li>Ищет первый контейнер со свободным местом</li>
     *     <li>Если свободных нет — создаёт новый контейнер</li>
     * </ol>
     *
     * @param userId идентификатор пользователя (email)
     * @return имя контейнера, к которому привязан пользователь
     */
    public String assignUserToContainer(String userId) {
        // 1. Проверяем, может пользователь уже где-то есть
        String existing = redisTemplate.opsForValue().get(USER_CONTAINER_PREFIX + userId);
        if (existing != null) {
            log.debug("User {} already assigned to container: {}", userId, existing);
            return existing;
        }

        // 2. Получаем все активные контейнеры
        Set<String> containers = redisTemplate.opsForSet().members(CONTAINERS_KEY);

        // 3. Сортируем по номеру (app-1, app-2, app-3...)
        List<String> sortedContainers = new ArrayList<>(containers);
        sortedContainers.sort((a, b) -> {
            int num1 = Integer.parseInt(a.replace("app-", ""));
            int num2 = Integer.parseInt(b.replace("app-", ""));
            return Integer.compare(num1, num2);
        });

        log.info("=== ASSIGNING USER {} ===", userId);
        log.debug("Available containers: {}", sortedContainers);

        // 4. Ищем первый контейнер с местом
        for (String container : sortedContainers) {
            Long count = redisTemplate.opsForSet().size(CONTAINER_USERS_PREFIX + container);
            Long currentCount = count != null ? count : 0L;
            log.debug("  {}: {}/{} users", container, currentCount, MAX_USERS_PER_CONTAINER);

            if (count < MAX_USERS_PER_CONTAINER) {
                // Назначаем пользователя
                redisTemplate.opsForSet().add(CONTAINER_USERS_PREFIX + container, userId);
                redisTemplate.opsForValue().set(USER_CONTAINER_PREFIX + userId, container);

                // Устанавливаем TTL
                redisTemplate.expire(USER_CONTAINER_PREFIX + userId, 30, TimeUnit.MINUTES);

                log.info("✅ ASSIGNED to {} ({}/{})", container, currentCount + 1, MAX_USERS_PER_CONTAINER);
                return container;
            }
        }

        // 5. Нет свободных - создаём новый
        log.warn("⚠️ No free containers, creating new one for user: {}", userId);
        return createNewContainer(userId);
    }

    /**
     * Удаляет пользователя из контейнера при выходе из системы.
     * Если контейнер стал пустым (и это не app-1), отправляет сигнал скейлеру.
     *
     * @param userId идентификатор пользователя (email)
     */
    public void removeUserFromContainer(String userId) {
        String container = redisTemplate.opsForValue().get(USER_CONTAINER_PREFIX + userId);
        if (container != null) {
            redisTemplate.opsForSet().remove(CONTAINER_USERS_PREFIX + container, userId);
            redisTemplate.delete(USER_CONTAINER_PREFIX + userId);

            Long remaining = redisTemplate.opsForSet().size(CONTAINER_USERS_PREFIX + container);
            long remainingCount = remaining != null ? remaining : 0L;

            log.info("👋 Пользователь {} покинул {} (осталось {}/{})", userId, container, remainingCount, MAX_USERS_PER_CONTAINER);

            // Сигнал для скейлера
            if (remaining == 0 && !container.equals("app-1")) {
                log.debug("Empty container {} detected, signaling scaler", container);
                redisTemplate.convertAndSend("scale-channel", "CHECK_EMPTY:" + container);
            }
        } else {
            log.debug("User {} not found in any container", userId);
        }
    }

    /**
     * Создаёт новый контейнер для пользователя.
     * Определяет следующий доступный ID, регистрирует контейнер в Redis
     * и отправляет сигнал скейлеру для физического создания контейнера.
     *
     * @param userId идентификатор пользователя (email)
     * @return имя созданного контейнера
     */
    private String createNewContainer(String userId) {
        Set<String> containers = redisTemplate.opsForSet().members(CONTAINERS_KEY);
        int nextId = 1;
        if (containers != null) {
            nextId = containers.stream()
                    .mapToInt(c -> Integer.parseInt(c.replace("app-", "")))
                    .max().orElse(0) + 1;
        }

        String newContainer = "app-" + nextId;

        // Регистрируем новый контейнер
        redisTemplate.opsForSet().add(CONTAINERS_KEY, newContainer);
        redisTemplate.opsForSet().add(CONTAINER_USERS_PREFIX + newContainer, userId);
        redisTemplate.opsForValue().set(USER_CONTAINER_PREFIX + userId, newContainer);

        // Отправляем сигнал скейлеру
        redisTemplate.convertAndSend("scale-channel", "CREATE:" + newContainer);

        log.info("🚀 Создан новый контейнер {} для пользователя {}", newContainer, userId);
        return newContainer;
    }

    /**
     * Возвращает статистику по всем контейнерам.
     *
     * @return Map, где ключ — имя контейнера, значение — количество пользователей
     */
    public Map<String, Integer> getContainersStats() {
        Map<String, Integer> stats = new LinkedHashMap<>();
        Set<String> containers = redisTemplate.opsForSet().members(CONTAINERS_KEY);

        if (containers != null) {
            containers.stream()
                    .sorted((a, b) -> {
                        int num1 = Integer.parseInt(a.replace("app-", ""));
                        int num2 = Integer.parseInt(b.replace("app-", ""));
                        return Integer.compare(num1, num2);
                    })
                    .forEach(c -> {
                        Long count = redisTemplate.opsForSet().size(CONTAINER_USERS_PREFIX + c);
                        stats.put(c, count != null ? count.intValue() : 0);
                    });
        }

        log.debug("Container stats: {}", stats);
        return stats;
    }
}
