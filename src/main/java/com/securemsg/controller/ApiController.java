package com.securemsg.controller;

import com.securemsg.service.MessageStorageService;
import com.securemsg.service.ApiKeyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API контроллер для интеграции с внешними системами (1C, CRM, ERP).
 * Предоставляет эндпоинты для отправки сообщений, получения входящих писем,
 * проверки доступности системы.
 * Аутентификация осуществляется по API-ключу в заголовке X-API-Key.
 */
@RestController
@RequestMapping("/api/v1")
public class ApiController {

    private static final Logger log = LoggerFactory.getLogger(ApiController.class);

    @Autowired
    private MessageStorageService messageStorageService;

    @Autowired
    private ApiKeyService apiKeyService;

    /**
     * Конструктор. Вызывается при инициализации контроллера.
     */
    public ApiController() {
        log.info("=== ApiController INITIALIZED ===");
    }

    /**
     * Проверка доступности системы (health check).
     * Используется для мониторинга и балансировки нагрузки.
     *
     * @return JSON с статусом UP и текущим timestamp
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        log.debug("Health check requested");

        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        return ResponseEntity.ok(response);
    }


    /**
     * Отправка сообщения из внешней системы.
     * Требует валидный API-ключ в заголовке X-API-Key.
     *
     * @param apiKey API-ключ для аутентификации (в заголовке X-API-Key)
     * @param to email получателя
     * @param subject тема сообщения
     * @param message текст сообщения
     * @return JSON с результатом операции
     */
    @PostMapping("/messages")
    public ResponseEntity<Map<String, Object>> sendMessage(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestParam String to,
            @RequestParam String subject,
            @RequestParam String message) {

        log.info("=== API: Send message request ===");
        log.info("Recipient: {}, Subject: {}", to, subject);

        Map<String, Object> response = new HashMap<>();

        // Получаем email отправителя из API-ключа
        String from;
        try {
            from = apiKeyService.getUserEmailByKey(apiKey)
                    .orElseThrow(() -> new RuntimeException("User not found from API key"));
            log.info("Authenticated as: {}", from);
        } catch (Exception e) {
            log.warn("Authentication failed: {}", e.getMessage());
            response.put("success", false);
            response.put("error", "Invalid or missing API key");
            return ResponseEntity.status(401).body(response);
        }

        try {
            // Проверяем, существует ли получатель
            if (!messageStorageService.userExists(to)) {
                log.warn("Recipient not found: {}", to);
                response.put("success", false);
                response.put("error", "Recipient not found: " + to);
                return ResponseEntity.badRequest().body(response);
            }

            // Отправляем сообщение
            messageStorageService.sendEmail(from, to, subject, message);
            log.info("Message sent successfully from {} to {}", from, to);

            response.put("success", true);
            response.put("messageId", "generated-id");
            response.put("to", to);
            response.put("subject", subject);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error sending message: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Получение списка входящих сообщений пользователя.
     * Требует валидный API-ключ в заголовке X-API-Key.
     * Поддерживает пагинацию.
     *
     * @param apiKey API-ключ для аутентификации (в заголовке X-API-Key)
     * @param page номер страницы (начиная с 0)
     * @param size размер страницы (количество сообщений на странице)
     * @return JSON со списком сообщений, общим количеством и параметрами пагинации
     */
    @GetMapping("/messages/inbox")
    public ResponseEntity<Map<String, Object>> getInbox(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("=== API: Get inbox request ===");
        log.info("Page: {}, Size: {}", page, size);

        Map<String, Object> response = new HashMap<>();

        // Получаем email пользователя из API-ключа
        String userEmail;
        try {
            userEmail = apiKeyService.getUserEmailByKey(apiKey)
                    .orElseThrow(() -> new RuntimeException("User not found from API key"));
            log.info("Authenticated as: {}", userEmail);
        } catch (Exception e) {
            log.warn("Authentication failed: {}", e.getMessage());
            response.put("success", false);
            response.put("error", "Invalid or missing API key");
            return ResponseEntity.status(401).body(response);
        }

        try {
            List<MessageStorageService.EmailDTO> emails = messageStorageService.getInboxForUser(userEmail);
            log.debug("Total emails for user {}: {}", userEmail, emails.size());

            // Пагинация (в памяти, для простоты)
            int start = page * size;
            int end = Math.min(start + size, emails.size());
            List<MessageStorageService.EmailDTO> pagedEmails;
            if (start < emails.size()) {
                pagedEmails = emails.subList(start, end);
            } else {
                pagedEmails = List.of();
            }

            response.put("success", true);
            response.put("messages", pagedEmails);
            response.put("total", emails.size());
            response.put("page", page);
            response.put("size", size);

            log.info("Returning {} messages (total: {})", pagedEmails.size(), emails.size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting inbox: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Получение информации о конкретном сообщении.
     * Можно указать папку: "inbox" (входящие) или "sent" (отправленные).
     *
     * @param apiKey API-ключ для аутентификации
     * @param id идентификатор сообщения
     * @param folder папка, откуда брать сообщение (inbox или sent, по умолчанию inbox)
     * @return JSON с данными сообщения
     */
    @GetMapping("/messages/{id}")
    public ResponseEntity<Map<String, Object>> getMessageById(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @PathVariable Long id,
            @RequestParam(defaultValue = "inbox") String folder) {

        log.info("=== API: Get message by id ===");
        log.info("Message ID: {}, Folder: {}", id, folder);

        Map<String, Object> response = new HashMap<>();

        // Валидация параметра folder
        if (!folder.equals("inbox") && !folder.equals("sent")) {
            response.put("success", false);
            response.put("error", "Invalid folder parameter. Allowed values: inbox, sent");
            return ResponseEntity.badRequest().body(response);
        }

        String userEmail;
        try {
            userEmail = apiKeyService.getUserEmailByKey(apiKey)
                    .orElseThrow(() -> new RuntimeException("User not found from API key"));
            log.info("Authenticated as: {}", userEmail);
        } catch (Exception e) {
            log.warn("Authentication failed: {}", e.getMessage());
            response.put("success", false);
            response.put("error", "Invalid or missing API key");
            return ResponseEntity.status(401).body(response);
        }

        try {
            MessageStorageService.EmailDTO email = messageStorageService.getEmailById(userEmail, id, folder);

            if (email == null) {
                log.warn("Message not found: id={}, folder={}, user={}", id, folder, userEmail);
                response.put("success", false);
                response.put("error", "Message not found");
                return ResponseEntity.status(404).body(response);
            }

            log.info("Message found: subject={}, from={}, to={}", email.getSubject(), email.getFrom(), email.getTo());

            response.put("success", true);
            response.put("message", email);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting message: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }


}