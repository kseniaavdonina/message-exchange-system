package com.securemsg.controller;

import com.securemsg.dto.EmailMessage;
import com.securemsg.entity.Attachment;
import com.securemsg.service.MessageQueueService;
import com.securemsg.service.MessageStorageService;
import com.securemsg.service.S3StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Контроллер для работы с сообщениями.
 * Обрабатывает:
 * <ul>
 *     <li>Форму создания нового письма (/compose)</li>
 *     <li>Отправку письма с вложениями (/send)</li>
 *     <li>Скачивание вложений (/download/{attachmentId})</li>
 *     <li>Управление очередями ActiveMQ (/queue/*)</li>
 *     <li>Отладку вложений (/debug/attachments/{emailId})</li>
 * </ul>
 */
@Controller
public class MessageController {

    private static final Logger log = LoggerFactory.getLogger(MessageController.class);

    @Autowired
    private MessageStorageService messageStorageService;

    @Autowired
    private MessageQueueService messageQueueService;

    @Autowired
    private S3StorageService s3StorageService;

    /**
     * Отображает форму создания нового письма.
     * Поддерживает предзаполнение полей (to, subject, message)
     * и пересылку писем с вложениями (forwardId).
     *
     * @param to получатель (опционально)
     * @param subject тема (опционально)
     * @param message текст сообщения (опционально)
     * @param forwardId ID пересылаемого письма (опционально)
     * @param model модель для передачи данных в представление
     * @return название шаблона compose
     */
    @GetMapping("/compose")
    public String compose(
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String message,
            @RequestParam(required = false) Long forwardId,
            Model model) {

        log.info("=== COMPOSE FORM REQUESTED ===");
        log.info("ForwardId: {}", forwardId);

        if (to != null) model.addAttribute("to", to);
        if (subject != null) model.addAttribute("subject", subject);
        if (message != null) model.addAttribute("message", message);

        // Если пересылаем письмо, подгружаем его вложения
        if (forwardId != null) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String currentUser = auth.getName();

            // Получаем письмо и его вложения
            MessageStorageService.EmailDTO originalEmail = messageStorageService.getEmailById(currentUser, forwardId, "inbox");
            if (originalEmail != null && originalEmail.hasAttachments()) {
                log.info("Forwarding email with {} attachments", originalEmail.getAttachments().size());
                model.addAttribute("forwardAttachments", originalEmail.getAttachments());
                model.addAttribute("forwardId", forwardId);
            } else if (originalEmail != null) {
                log.info("Forwarding email with no attachments");
                model.addAttribute("forwardAttachments", new ArrayList<>());
            } else {
                log.warn("Original email not found or access denied for forwardId: {}", forwardId);
            }
        }

        return "compose";
    }

    /**
     * Обрабатывает отправку письма.
     * Поддерживает:
     * <ul>
     *     <li>Множественные вложения</li>
     *     <li>Пересылку файлов из другого письма</li>
     *     <li>Асинхронную отправку уведомления в ActiveMQ</li>
     * </ul>
     *
     * @param to получатель
     * @param subject тема
     * @param message текст сообщения
     * @param files прикреплённые файлы (опционально)
     * @param forwardAttachmentIds ID вложений из пересылаемого письма (опционально)
     * @param redirectAttributes для flash-сообщений
     * @return редирект на дашборд
     */
    @PostMapping("/send")
    public String sendMessage(
            @RequestParam String to,
            @RequestParam String subject,
            @RequestParam String message,
            @RequestParam(value = "attachments", required = false) MultipartFile[] files,
            @RequestParam(value = "forwardAttachmentIds", required = false) List<Long> forwardAttachmentIds,
            RedirectAttributes redirectAttributes) {

        log.info("=== SEND MESSAGE REQUEST ===");

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String currentUser = auth.getName();

            log.info("Current user: {}", currentUser);
            log.info("Recipient: {}", to);
            log.info("Files count: {}", files != null ? files.length : 0);
            log.info("Forward attachment IDs: {}", forwardAttachmentIds);

            if (!messageStorageService.userExists(to)) {
                log.warn("Recipient not found: {}", to);
                redirectAttributes.addFlashAttribute("error", "Получатель " + to + " не существует в системе");
                redirectAttributes.addFlashAttribute("to", to);
                redirectAttributes.addFlashAttribute("subject", subject);
                redirectAttributes.addFlashAttribute("message", message);
                return "redirect:/compose";
            }

            List<MultipartFile> newFilesList = new ArrayList<>();
            if (files != null) {
                newFilesList = Arrays.asList(files);
            }

            List<Long> forwardedIds = new ArrayList<>();
            if (forwardAttachmentIds != null) {
                forwardedIds = forwardAttachmentIds;
            }

            messageStorageService.sendEmailWithForwardedAttachments(
                    currentUser, to, subject, message,
                    newFilesList, forwardedIds
            );

            EmailMessage emailMessage = new EmailMessage(currentUser, to, subject, message);
            messageQueueService.sendEmailMessage(emailMessage);

            int totalFiles = (files != null ? files.length : 0) + (forwardAttachmentIds != null ? forwardAttachmentIds.size() : 0);
            String successMessage = "Сообщение успешно отправлено пользователю " + to;
            if (totalFiles > 0) {
                successMessage += " с " + totalFiles + " файлом(ами)";
            }

            log.info("Message sent successfully from {} to {} with {} files", currentUser, to, totalFiles);
            redirectAttributes.addFlashAttribute("success", successMessage);

        } catch (Exception e) {
            log.error("Error sending message: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Ошибка отправки: " + e.getMessage());
            redirectAttributes.addFlashAttribute("to", to);
            redirectAttributes.addFlashAttribute("subject", subject);
            redirectAttributes.addFlashAttribute("message", message);
            return "redirect:/compose";
        }

        return "redirect:/dashboard";
    }

    /**
     * Скачивает файл вложения по его ID.
     * Проверяет права доступа пользователя.
     *
     * @param attachmentId ID вложения
     * @return файл для скачивания или ошибка 404/500
     */
    @GetMapping("/download/{attachmentId}")
    public ResponseEntity<byte[]> downloadAttachment(@PathVariable Long attachmentId) {
        log.info("=== DOWNLOAD ATTACHMENT ===");
        log.info("Attachment ID: {}", attachmentId);

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String currentUser = auth.getName();
            log.debug("User: {}", currentUser);

            Optional<Attachment> attachmentOpt = messageStorageService.getAttachment(attachmentId);

            if (attachmentOpt.isEmpty()) {
                log.warn("Attachment not found: {}", attachmentId);
                return ResponseEntity.notFound().build();
            }

            Attachment attachment = attachmentOpt.get();
            log.info("Downloading file: {}, size: {} bytes", attachment.getFileName(), attachment.getFileSize());

            InputStream stream = s3StorageService.downloadFile(attachment.getS3ObjectKey());
            byte[] content = stream.readAllBytes();

            String encodedFileName = URLEncoder.encode(attachment.getFileName(), StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + encodedFileName + "\"")
                    .contentType(MediaType.parseMediaType(attachment.getFileType()))
                    .contentLength(attachment.getFileSize())
                    .body(content);

        } catch (Exception e) {
            log.error("Error downloading attachment {}: {}", attachmentId, e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Возвращает статус всех очередей ActiveMQ в формате JSON.
     *
     * @return JSON с информацией об очередях
     */
    @GetMapping("/queue/status")
    @ResponseBody
    public Map<String, Object> getQueueStatus() {
        log.debug("Queue status requested");
        return messageQueueService.getQueueInfo();
    }

    /**
     * Очищает все очереди ActiveMQ.
     *
     * @return JSON с результатом операции
     */
    @PostMapping("/queue/purge")
    @ResponseBody
    public Map<String, Object> purgeQueue() {
        log.info("=== PURGE ALL QUEUES ===");
        Map<String, Object> result = new HashMap<>();
        try {
            messageQueueService.purgeAllQueues();
            result.put("success", true);
            result.put("message", "All queues purged successfully");
            log.info("All queues purged successfully");
        } catch (Exception e) {
            log.error("Error purging queues: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * Отправляет тестовое сообщение в очередь текущего пользователя.
     *
     * @return JSON с результатом операции
     */
    @GetMapping("/queue/test")
    @ResponseBody
    public Map<String, Object> testQueue() {
        log.info("=== TEST QUEUE ===");
        Map<String, Object> result = new HashMap<>();
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String currentUser = auth.getName();

            EmailMessage testMessage = new EmailMessage(
                    "system@test.com",
                    currentUser,
                    "Test Message",
                    "This is a test message from the queue system"
            );
            messageQueueService.sendEmailMessage(testMessage);

            log.info("Test message sent to: {}", currentUser);
            result.put("success", true);
            result.put("message", "Test message sent to: " + currentUser);
            result.put("queueInfo", messageQueueService.getQueueInfo());

        } catch (Exception e) {
            log.error("Error sending test message: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * Возвращает статус очереди текущего пользователя.
     *
     * @return JSON с количеством сообщений в очереди пользователя
     */
    @GetMapping("/queue/user/status")
    @ResponseBody
    public Map<String, Object> getUserQueueStatus() {
        log.debug("User queue status requested");
        Map<String, Object> result = new HashMap<>();
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String currentUser = auth.getName();

            int userMessages = messageQueueService.getPendingMessageCountForUser(currentUser);

            result.put("success", true);
            result.put("user", currentUser);
            result.put("userQueue", "email.queue." + currentUser);
            result.put("pendingMessages", userMessages);
            result.put("queueInfo", messageQueueService.getQueueInfo());

            log.debug("User {} has {} pending messages", currentUser, userMessages);
        } catch (Exception e) {
            log.error("Error getting user queue status: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * Очищает очередь текущего пользователя.
     *
     * @return JSON с результатом операции
     */
    @PostMapping("/queue/user/purge")
    @ResponseBody
    public Map<String, Object> purgeUserQueue() {
        log.info("=== PURGE USER QUEUE ===");
        Map<String, Object> result = new HashMap<>();
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String currentUser = auth.getName();

            messageQueueService.purgeUserQueue(currentUser);

            log.info("User queue purged for: {}", currentUser);
            result.put("success", true);
            result.put("message", "User queue purged successfully for: " + currentUser);

        } catch (Exception e) {
            log.error("Error purging user queue: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * Отладочный метод для просмотра информации о вложениях письма.
     *
     * @param emailId ID письма
     * @return JSON с информацией о вложениях
     */
    @GetMapping("/debug/attachments/{emailId}")
    @ResponseBody
    public Map<String, Object> debugAttachments(@PathVariable long emailId) {
        log.debug("Debug attachments requested for emailId: {}", emailId);
        Map<String, Object> result = new HashMap<>();

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String currentUser = auth.getName();

            result.put("user", currentUser);
            result.put("emailId", emailId);

            List<MessageStorageService.FileAttachment> attachments = messageStorageService.getFileAttachments(emailId);
            result.put("attachmentCount", attachments.size());

            List<Map<String, Object>> attachmentList = new ArrayList<>();
            for (MessageStorageService.FileAttachment attachment : attachments) {
                Map<String, Object> attInfo = new HashMap<>();
                attInfo.put("id", attachment.id);
                attInfo.put("fileName", attachment.fileName);
                attInfo.put("fileSize", attachment.fileSize);
                attInfo.put("fileType", attachment.fileType);
                attInfo.put("s3ObjectKey", attachment.s3ObjectKey);
                attachmentList.add(attInfo);
            }
            result.put("attachments", attachmentList);
            result.put("success", true);

            log.debug("Found {} attachments for email {}", attachments.size(), emailId);

        } catch (Exception e) {
            log.error("Error debugging attachments: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }
}