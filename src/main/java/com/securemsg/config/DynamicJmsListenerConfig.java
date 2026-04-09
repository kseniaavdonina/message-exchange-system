package com.securemsg.config;

import com.securemsg.dto.EmailMessage;
import com.securemsg.service.MessageStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerEndpointRegistry;
import org.springframework.jms.config.SimpleJmsListenerEndpoint;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.MessageListenerContainer;

import javax.jms.Message;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Конфигурация динамических JMS-слушателей.
 * Позволяет регистрировать и отключать слушатели для персональных очередей пользователей
 * во время работы приложения (без перезапуска).
 */
@Configuration
public class DynamicJmsListenerConfig {

    private static final Logger log = LoggerFactory.getLogger(DynamicJmsListenerConfig.class);

    private static final String EMAIL_QUEUE_PREFIX = "email.queue.";

    @Autowired
    private DefaultJmsListenerContainerFactory jmsListenerContainerFactory;

    @Autowired
    private JmsListenerEndpointRegistry registry;

    @Autowired
    private MessageStorageService messageStorageService;

    @Autowired
    private JmsTemplate jmsTemplate;

    private final Map<String, String> activeListeners = new ConcurrentHashMap<>();

    /**
     * Регистрирует и запускает JMS-слушатель для персональной очереди пользователя.
     * Слушатель будет обрабатывать сообщения, адресованные конкретному пользователю.
     *
     * @param email email пользователя (используется как идентификатор очереди)
     */
    public void registerUserQueueListener(String email) {
        String queueName = EMAIL_QUEUE_PREFIX + email;
        log.info("Registering JMS listener for queue: {}", queueName);

        if (activeListeners.containsKey(queueName)) {
            log.warn("Listener already exists for: {}", queueName);
            return;
        }

        String listenerId = "listener-" + email.replace("@", "-").replace(".", "-");

        SimpleJmsListenerEndpoint endpoint = new SimpleJmsListenerEndpoint();
        endpoint.setId(listenerId);
        endpoint.setDestination(queueName);
        endpoint.setMessageListener(message -> {
            processMessage(message, email);
        });

        // Регистрируем слушателя
        registry.registerListenerContainer(endpoint, jmsListenerContainerFactory);

        // Запускаем контейнер по ID
        MessageListenerContainer container = registry.getListenerContainer(endpoint.getId());
        if (container != null && !container.isRunning()) {
            container.start();
            log.info("✅ Dynamic JMS listener started for: {}", queueName);
        }

        activeListeners.put(queueName, email);
        log.info("✅ Dynamic JMS listener registered for: {}", queueName);
    }

    /**
     * Обрабатывает входящее JMS-сообщение из очереди пользователя.
     * Проверяет, что сообщение предназначено именно этому пользователю,
     * и подтверждает его получение.
     *
     * @param message JMS-сообщение
     * @param expectedUser ожидаемый получатель (email)
     */
    private void processMessage(Message message, String expectedUser) {
        try {
            EmailMessage emailMessage = (EmailMessage) jmsTemplate.getMessageConverter().fromMessage(message);

            if (emailMessage == null) {
                log.error("Failed to convert JMS message");
                return;
            }

            log.info("=== MESSAGE RECEIVED FROM USER QUEUE ===");
            log.info("Expected user: {}", expectedUser);
            log.info("Message for: {}", emailMessage.getTo());
            log.info("From: {}", emailMessage.getFrom());
            log.info("Subject: {}", emailMessage.getSubject());

            if (emailMessage.getTo().equals(expectedUser)) {
                log.info("✅ Message is for the correct user. Processing...");
                message.acknowledge();
                log.info("Message acknowledged and removed from queue");
            } else {
                log.warn("⚠️ Message user mismatch! Expected: {}, Got: {}", expectedUser, emailMessage.getTo());
            }

        } catch (Exception e) {
            log.error("Error processing message: {}", e.getMessage(), e);
        }
    }

    /**
     * Останавливает и удаляет JMS-слушатель для очереди пользователя.
     *
     * @param email email пользователя
     */
    public void unregisterUserQueueListener(String email) {
        String queueName = EMAIL_QUEUE_PREFIX + email;
        String listenerId = "listener-" + email.replace("@", "-").replace(".", "-");

        log.info("Unregistering JMS listener for queue: {}", queueName);

        if (activeListeners.containsKey(queueName)) {
            try {
                MessageListenerContainer container = registry.getListenerContainer(listenerId);
                if (container != null && container.isRunning()) {
                    container.stop();
                    log.info("Stopped listener for: {}", queueName);
                }
                activeListeners.remove(queueName);
                log.info("🗑️ JMS listener removed for: {}", queueName);
            } catch (Exception e) {
                log.error("Error stopping listener: {}", e.getMessage(), e);
            }
        } else {
            log.warn("No active listener found for: {}", queueName);
        }
    }
}