package com.securemsg.service;

import com.securemsg.config.DynamicJmsListenerConfig;
import com.securemsg.dto.EmailMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис для работы с очередями сообщений ActiveMQ.
 * Обеспечивает:
 * <ul>
 *     <li>Отправку сообщений в персональные очереди пользователей</li>
 *     <li>Регистрацию и создание очередей</li>
 *     <li>Получение информации об очередях (количество сообщений)</li>
 *     <li>Очистку очередей</li>
 * </ul>
 */
@Service
public class MessageQueueService {

    private static final Logger log = LoggerFactory.getLogger(MessageQueueService.class);

    private static final String EMAIL_QUEUE_PREFIX = "email.queue.";

    @Autowired
    private JmsTemplate jmsTemplate;

    @Autowired
    @Lazy
    private DynamicJmsListenerConfig dynamicJmsListenerConfig;

    // Кэш для отслеживания активных пользовательских очередей
    private final Map<String, Boolean> userQueues = new ConcurrentHashMap<>();

    /**
     * Инициализация сервиса при старте приложения.
     * Регистрирует очереди для известных пользователей.
     */
    @PostConstruct
    public void init() {
        log.info("=== MESSAGE QUEUE SERVICE INITIALIZED ===");
        log.info("Queue prefix: {}", EMAIL_QUEUE_PREFIX);

        // Предварительно регистрируем известных пользователей
        registerUserQueue("user@sam.reshu");
        registerUserQueue("admin@company.org");
        registerUserQueue("test1@sam.reshu");
        registerUserQueue("test2@company.org");
        registerUserQueue("security@system.com");
    }

    /**
     * Отправляет сообщение в персональную очередь получателя.
     *
     * @param emailMessage сообщение для отправки
     */
    public void sendEmailMessage(EmailMessage emailMessage) {
        try {
            String recipientQueue = EMAIL_QUEUE_PREFIX + emailMessage.getTo();

            jmsTemplate.convertAndSend(recipientQueue, emailMessage);

            log.info("=== MESSAGE SENT TO ACTIVEMQ ===");
            log.info("From: {}", emailMessage.getFrom());
            log.info("To: {}", emailMessage.getTo());
            log.info("Recipient Queue: {}", recipientQueue);
            log.info("Subject: {}", emailMessage.getSubject());

            registerUserQueue(emailMessage.getTo());

        } catch (Exception e) {
            log.error("ERROR sending message to ActiveMQ: {}", e.getMessage(), e);
        }
    }

    /**
     * Регистрирует очередь пользователя в кэше.
     *
     * @param username email пользователя
     */
    public void registerUserQueue(String username) {
        try {
            if (username != null && !username.trim().isEmpty()) {
                userQueues.put(username, true);
                log.debug("Registered user queue: {}{}", EMAIL_QUEUE_PREFIX, username);
            }
        } catch (Exception e) {
            log.error("ERROR registering user queue: {}", e.getMessage(), e);
        }
    }

    /**
     * Создаёт персональную очередь для пользователя.
     * Также регистрирует динамический JMS-слушатель.
     *
     * @param email email пользователя
     */
    public void createUserQueue(String email) {
        log.info("createUserQueue CALLED for email: {}", email);

        if (email == null || email.trim().isEmpty()) {
            log.warn("Email is empty, skipping");
            return;
        }

        String queueName = EMAIL_QUEUE_PREFIX + email;
        userQueues.put(email, true);

        //Регистрируем динамический слушатель
        dynamicJmsListenerConfig.registerUserQueueListener(email);

        log.info("User queue created: {}", queueName);
    }

    /**
     * Получает количество сообщений в очереди пользователя.
     *
     * @param username email пользователя
     * @return количество сообщений (0, если очереди не существует)
     */
    public int getPendingMessageCountForUser(String username) {
        try {
            String userQueue = EMAIL_QUEUE_PREFIX + username;
            return jmsTemplate.browse(userQueue, (session, browser) -> {
                int count = 0;
                while (browser.getEnumeration().hasMoreElements()) {
                    browser.getEnumeration().nextElement();
                    count++;
                }
                return count;
            });
        } catch (Exception e) {
            log.debug("Queue not found or error browsing: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Получает общее количество сообщений во всех очередях.
     *
     * @return общее количество сообщений, или -1 в случае ошибки
     */
    public int getTotalPendingMessageCount() {
        try {
            int total = 0;
            for (String username : userQueues.keySet()) {
                total += getPendingMessageCountForUser(username);
            }
            return total;
        } catch (Exception e) {
            log.error("ERROR getting total queue size: {}", e.getMessage(), e);
            return -1;
        }
    }

    /**
     * Возвращает подробную информацию о всех очередях.
     *
     * @return Map с информацией об очередях
     */
    public Map<String, Object> getQueueInfo() {
        Map<String, Object> info = new HashMap<>();
        try {
            int totalMessages = getTotalPendingMessageCount();
            info.put("totalQueues", userQueues.size());
            info.put("totalPendingMessages", totalMessages);
            info.put("activeUsers", userQueues.keySet());
            info.put("status", "active");

            // Детальная информация по каждой очереди
            Map<String, Integer> queueDetails = new HashMap<>();
            for (String username : userQueues.keySet()) {
                int count = getPendingMessageCountForUser(username);
                queueDetails.put(EMAIL_QUEUE_PREFIX + username, count);
            }
            info.put("queueDetails", queueDetails);
            log.debug("Queue info: totalQueues={}, totalMessages={}", userQueues.size(), totalMessages);

        } catch (Exception e) {
            log.error("Error getting queue info: {}", e.getMessage(), e);
            info.put("error", e.getMessage());
            info.put("status", "error");
        }
        return info;
    }

    /**
     * Очищает очередь конкретного пользователя (удаляет все сообщения).
     *
     * @param username email пользователя
     */
    public void purgeUserQueue(String username) {
        try {
            String userQueue = EMAIL_QUEUE_PREFIX + username;
            log.info("Purging user queue: {}", userQueue);

            int count = 0;
            Object message;
            do {
                message = jmsTemplate.receiveAndConvert(userQueue);
                if (message != null) {
                    count++;
                }
            } while (message != null);

            log.info("Purged {} messages from {}", count, userQueue);

        } catch (Exception e) {
            log.error("ERROR purging user queue: {}", e.getMessage(), e);
        }
    }

    /**
     * Очищает все очереди всех пользователей.
     */
    public void purgeAllQueues() {
        try {
            log.info("Purging all user queues");
            for (String username : userQueues.keySet()) {
                purgeUserQueue(username);
            }
            log.info("All queues purged successfully");
        } catch (Exception e) {
            log.error("ERROR purging all queues: {}", e.getMessage(), e);
        }
    }

    /**
     * Alias для purgeAllQueues().
     */
    public void purgeQueue() {
        purgeAllQueues();
    }
}