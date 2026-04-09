package com.securemsg.service;

import com.securemsg.entity.Email;
import com.securemsg.entity.Attachment;
import com.securemsg.entity.User;
import com.securemsg.repository.EmailRepository;
import com.securemsg.repository.AttachmentRepository;
import com.securemsg.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис для хранения и управления сообщениями.
 * Обеспечивает:
 * <ul>
 *     <li>Отправку писем с вложениями (с загрузкой в MinIO)</li>
 *     <li>Получение входящих и отправленных писем</li>
 *     <li>Поиск по письмам</li>
 *     <li>Удаление писем и вложений</li>
 *     <li>Отметку о прочтении</li>
 * </ul>
 */
@Service
@Transactional
public class MessageStorageService {

    private static final Logger log = LoggerFactory.getLogger(MessageStorageService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailRepository emailRepository;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private S3StorageService s3StorageService;

    /**
     * Проверяет, существует ли пользователь с указанным email.
     *
     * @param email email пользователя
     * @return true, если пользователь существует
     */
    public boolean userExists(String email) {
        return userRepository.existsByEmail(email);
    }

    /**
     * Возвращает пользователя по email.
     *
     * @param email email пользователя
     * @return Optional с пользователем
     */
    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Возвращает список входящих писем пользователя.
     *
     * @param username email пользователя
     * @return список входящих писем
     */
    public List<EmailDTO> getInboxForUser(String username) {
        log.debug("Getting inbox for user: {}", username);
        List<Email> emails = emailRepository.findByRecipientEmailAndFolderOrderBySentDateDesc(username, "INBOX");
        return convertToDTO(emails);
    }

    /**
     * Возвращает список отправленных писем пользователя.
     *
     * @param username email пользователя
     * @return список отправленных писем
     */
    public List<EmailDTO> getSentForUser(String username) {
        log.debug("Getting sent emails for user: {}", username);
        List<Email> emails = emailRepository.findBySenderEmailAndFolderOrderBySentDateDesc(username, "SENT");
        return convertToDTO(emails);
    }

    /**
     * Отправляет письмо без вложений.
     *
     * @param from    отправитель
     * @param to      получатель
     * @param subject тема
     * @param content текст
     */
    public void sendEmail(String from, String to, String subject, String content) {
        sendEmailWithFiles(from, to, subject, content, null);
    }

    /**
     * Отправляет письмо с вложениями.
     * Сначала загружает файлы в MinIO, затем сохраняет письма и вложения в БД.
     * При ошибке выполняется откат (удаление файлов из MinIO).
     *
     * @param from    отправитель
     * @param to      получатель
     * @param subject тема
     * @param content текст
     * @param files   список файлов для прикрепления
     */
    @Transactional
    public void sendEmailWithFiles(String from, String to, String subject,
                                   String content, List<MultipartFile> files) {
        List<String> uploadedKeys = new ArrayList<>();
        List<Attachment> attachments = new ArrayList<>();
        Email inboxEmail = null;
        Email sentEmail = null;

        log.info("=== SENDING EMAIL WITH S3 ===");
        log.info("From: {}, To: {}", from, to);

        try {
            if (!userExists(to)) {
                throw new RuntimeException("Получатель " + to + " не существует");
            }

            // ========== ЭТАП 1: СОЗДАЁМ ОБЪЕКТЫ ПИСЕМ ==========
            inboxEmail = new Email(from, to, subject, content);
            inboxEmail.setFolder("INBOX");
            inboxEmail.setRead(false);

            sentEmail = new Email(from, to, subject, content);
            sentEmail.setFolder("SENT");
            sentEmail.setRead(true);

            // ========== ЭТАП 2: ЗАГРУЖАЕМ ФАЙЛЫ В MINIO ==========
            if (files != null && !files.isEmpty()) {
                for (MultipartFile file : files) {
                    if (!file.isEmpty()) {
                        // Загружаем в MinIO
                        String s3Key = s3StorageService.uploadFile(file);
                        uploadedKeys.add(s3Key);
                        log.debug("File uploaded to MinIO: {}", s3Key);

                        Attachment attachment = new Attachment();
                        attachment.setFileName(file.getOriginalFilename());
                        attachment.setFileSize(file.getSize());
                        attachment.setFileType(file.getContentType());
                        attachment.setS3ObjectKey(s3Key);
                        attachments.add(attachment);
                    }
                }
            }

            // ========== ЭТАП 3: СОХРАНЯЕМ ПИСЬМА И ВЛОЖЕНИЯ ==========
            emailRepository.save(inboxEmail);
            emailRepository.save(sentEmail);
            log.debug("Emails saved, INBOX ID: {}, SENT ID: {}", inboxEmail.getId(), sentEmail.getId());

            for (Attachment attachment : attachments) {
                attachment.setEmail(inboxEmail);
                attachmentRepository.save(attachment);

                Attachment sentAttachment = new Attachment();
                sentAttachment.setFileName(attachment.getFileName());
                sentAttachment.setFileSize(attachment.getFileSize());
                sentAttachment.setFileType(attachment.getFileType());
                sentAttachment.setS3ObjectKey(attachment.getS3ObjectKey());
                sentAttachment.setEmail(sentEmail);
                attachmentRepository.save(sentAttachment);
            }

            log.info("Email sent successfully from {} to {} with {} attachments", from, to, attachments.size());

        } catch (Exception e) {
            log.error("ERROR sending email: {}", e.getMessage(), e);

            // ========== ОТКАТ: УДАЛЯЕМ ФАЙЛЫ ИЗ MINIO ==========
            if (!uploadedKeys.isEmpty()) {
                log.info("Rolling back: deleting {} files from MinIO...", uploadedKeys.size());
                for (String key : uploadedKeys) {
                    try {
                        s3StorageService.deleteFile(key);
                        log.debug("Deleted from MinIO: {}", key);
                    } catch (Exception ex) {
                        log.error("Failed to delete {} from MinIO: {}", key, ex.getMessage());
                    }
                }
            }

            throw new RuntimeException("Не удалось отправить письмо: " + e.getMessage(), e);
        }
    }

    /**
     * Выполняет поиск писем по папке.
     *
     * @param username email пользователя
     * @param folder   папка (inbox/sent)
     * @param query    поисковый запрос
     * @return список писем, соответствующих запросу
     */
    public List<EmailDTO> searchEmails(String username, String folder, String query) {
        log.debug("Searching emails for user: {}, folder: {}, query: {}", username, folder, query);
        List<Email> emails;
        if ("sent".equals(folder)) {
            emails = emailRepository.searchInSent(username, query);
        } else {
            emails = emailRepository.searchInInbox(username, query);
        }
        return convertToDTO(emails);
    }

    /**
     * Возвращает письмо по ID с проверкой прав доступа.
     *
     * @param username email пользователя
     * @param emailId  ID письма
     * @param folder   папка (inbox/sent)
     * @return EmailDTO или null, если доступ запрещён
     */
    public EmailDTO getEmailById(String username, long emailId, String folder) {
        log.debug("Getting email by id: {}, user: {}, folder: {}", emailId, username, folder);

        try {
            Optional<Email> emailOpt = emailRepository.findById(emailId);
            if (emailOpt.isEmpty()) {
                log.warn("Email not found: {}", emailId);
                return null;
            }

            Email email = emailOpt.get();
            boolean hasAccess = false;

            if ("sent".equals(folder) && email.getSenderEmail().equals(username) && "SENT".equals(email.getFolder())) {
                hasAccess = true;
            } else if ("inbox".equals(folder) && email.getRecipientEmail().equals(username) && "INBOX".equals(email.getFolder())) {
                hasAccess = true;
                if (!email.isRead()) {
                    email.setRead(true);
                    emailRepository.save(email);
                    log.debug("Email marked as read: {}", emailId);
                }
            }

            if (hasAccess) {
                EmailDTO dto = convertToDTO(email);
                dto.attachments = getFileAttachments(emailId);
                log.debug("Attachments loaded: {}", dto.attachments.size());
                return dto;
            }

            log.warn("Access denied to email {} for user {}", emailId, username);
            return null;

        } catch (Exception e) {
            log.error("Error getting email by id: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Отправляет письмо с пересылаемыми вложениями.
     *
     * @param from                  отправитель
     * @param to                    получатель
     * @param subject               тема
     * @param content               текст
     * @param newFiles              новые файлы
     * @param forwardedAttachmentIds ID пересылаемых вложений
     */
    public void sendEmailWithForwardedAttachments(String from, String to, String subject,
                                                  String content, List<MultipartFile> newFiles,
                                                  List<Long> forwardedAttachmentIds) {
        List<String> uploadedKeys = new ArrayList<>();
        List<Attachment> allAttachments = new ArrayList<>();

        log.info("=== SENDING EMAIL WITH FORWARDED ATTACHMENTS ===");
        log.info("From: {}, To: {}", from, to);

        try {
            if (!userExists(to)) {
                throw new RuntimeException("Получатель " + to + " не существует");
            }

            // ========== ЭТАП 1: НОВЫЕ ФАЙЛЫ ==========
            if (newFiles != null && !newFiles.isEmpty()) {
                for (MultipartFile file : newFiles) {
                    if (!file.isEmpty()) {
                        String s3Key = s3StorageService.uploadFile(file);
                        uploadedKeys.add(s3Key);
                        log.debug("New file uploaded to MinIO: {}", s3Key);

                        Attachment attachment = new Attachment();
                        attachment.setFileName(file.getOriginalFilename());
                        attachment.setFileSize(file.getSize());
                        attachment.setFileType(file.getContentType());
                        attachment.setS3ObjectKey(s3Key);
                        allAttachments.add(attachment);
                    }
                }
            }

            // ========== ЭТАП 2: ПЕРЕСЫЛАЕМЫЕ ФАЙЛЫ ==========
            if (forwardedAttachmentIds != null && !forwardedAttachmentIds.isEmpty()) {
                for (Long attId : forwardedAttachmentIds) {
                    Optional<Attachment> attOpt = attachmentRepository.findById(attId);
                    if (attOpt.isPresent()) {
                        Attachment original = attOpt.get();
                        Attachment forwardedAtt = new Attachment();
                        forwardedAtt.setFileName(original.getFileName());
                        forwardedAtt.setFileSize(original.getFileSize());
                        forwardedAtt.setFileType(original.getFileType());
                        forwardedAtt.setS3ObjectKey(original.getS3ObjectKey());

                        allAttachments.add(forwardedAtt);
                        log.debug("Forwarded attachment (reused): {}", original.getFileName());                    }
                }
            }

            // ========== ЭТАП 3: СОЗДАЁМ ПИСЬМА ==========
            Email inboxEmail = new Email(from, to, subject, content);
            inboxEmail.setFolder("INBOX");
            inboxEmail.setRead(false);

            Email sentEmail = new Email(from, to, subject, content);
            sentEmail.setFolder("SENT");
            sentEmail.setRead(true);

            emailRepository.save(inboxEmail);
            emailRepository.save(sentEmail);
            log.debug("Emails saved");

            // ========== ЭТАП 4: СОХРАНЯЕМ ВЛОЖЕНИЯ ==========
            for (Attachment attachment : allAttachments) {
                // Для INBOX
                Attachment inboxAtt = new Attachment();
                inboxAtt.setFileName(attachment.getFileName());
                inboxAtt.setFileSize(attachment.getFileSize());
                inboxAtt.setFileType(attachment.getFileType());
                inboxAtt.setS3ObjectKey(attachment.getS3ObjectKey());
                inboxAtt.setEmail(inboxEmail);
                attachmentRepository.save(inboxAtt);

                // Для SENT
                Attachment sentAtt = new Attachment();
                sentAtt.setFileName(attachment.getFileName());
                sentAtt.setFileSize(attachment.getFileSize());
                sentAtt.setFileType(attachment.getFileType());
                sentAtt.setS3ObjectKey(attachment.getS3ObjectKey());
                sentAtt.setEmail(sentEmail);
                attachmentRepository.save(sentAtt);
            }

            log.info("Email sent successfully with {} attachments", allAttachments.size());

        } catch (Exception e) {
            log.error("ERROR sending email with forwarded attachments: {}", e.getMessage(), e);

            // Откат: удаляем новые файлы из MinIO
            if (!uploadedKeys.isEmpty()) {
                for (String key : uploadedKeys) {
                    try {
                        s3StorageService.deleteFile(key);
                        log.error("ERROR sending email with forwarded attachments: {}", e.getMessage(), e);
                    } catch (Exception ex) {
                        log.error("Failed to cleanup: {}", ex.getMessage());
                    }
                }
            }
            throw new RuntimeException("Не удалось отправить письмо: " + e.getMessage(), e);
        }
    }

    /**
     * Возвращает список вложений для письма.
     *
     * @param emailId ID письма
     * @return список вложений
     */
    public List<FileAttachment> getFileAttachments(long emailId) {
        List<Attachment> dbAttachments = attachmentRepository.findByEmailId(emailId);
        List<FileAttachment> result = new ArrayList<>();

        for (Attachment attachment : dbAttachments) {
            result.add(new FileAttachment(
                    attachment.getId(),
                    attachment.getFileName(),
                    String.valueOf(attachment.getFileSize()),
                    attachment.getFileType(),
                    attachment.getS3ObjectKey()
            ));
        }
        return result;
    }

    /**
     * Возвращает вложение по ID.
     *
     * @param attachmentId ID вложения
     * @return Optional с вложением
     */
    public Optional<Attachment> getAttachment(Long attachmentId) {
        return attachmentRepository.findById(attachmentId);
    }

    /**
     * Возвращает вложение для скачивания.
     *
     * @param attachmentId ID вложения
     * @return Optional с FileAttachment
     */
    public Optional<FileAttachment> getAttachmentForDownload(Long attachmentId) {
        return attachmentRepository.findById(attachmentId)
                .map(attachment -> new FileAttachment(
                        attachment.getId(),
                        attachment.getFileName(),
                        String.valueOf(attachment.getFileSize()),
                        attachment.getFileType(),
                        attachment.getS3ObjectKey()
                ));
    }

    /**
     * Удаляет письмо и его вложения из MinIO и БД.
     *
     * @param username email пользователя
     * @param emailId  ID письма
     * @param folder   папка
     * @return true, если удаление успешно
     */
    @Transactional
    public boolean deleteEmail(String username, long emailId, String folder) {
        log.info("Deleting email: id={}, user={}, folder={}", emailId, username, folder);

        try {
            EmailDTO email = getEmailById(username, emailId, folder);
            if (email == null) {
                log.warn("Email not found or access denied: {}", emailId);
                return false;
            }

            List<Attachment> attachments = attachmentRepository.findByEmailId(emailId);

            // Удаляем файлы из S3
            for (Attachment attachment : attachments) {
                try {
                    s3StorageService.deleteFile(attachment.getS3ObjectKey());
                    log.debug("Deleted from S3: {}", attachment.getS3ObjectKey());
                } catch (Exception e) {
                    log.error("Failed to delete from S3: {}", e.getMessage());
                }
            }

            emailRepository.deleteById(emailId);
            log.info("Email deleted successfully: {}", emailId);
            return true;
        } catch (Exception e) {
            log.error("Error deleting email: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Отмечает письмо как прочитанное/непрочитанное.
     *
     * @param username email пользователя
     * @param emailId  ID письма
     * @param read     статус прочтения
     * @return true, если операция успешна
     */
    @Transactional
    public boolean markAsRead(String username, long emailId, boolean read) {
        log.debug("Marking email as read={}, id={}, user={}", read, emailId, username);

        try {
            Optional<Email> emailOpt = emailRepository.findById(emailId);
            if (emailOpt.isEmpty()) {
                log.warn("Email not found: {}", emailId);
                return false;
            }

            Email email = emailOpt.get();
            if (!email.getRecipientEmail().equals(username) || !"INBOX".equals(email.getFolder())) {
                log.warn("Access denied to mark email as read: {}", emailId);
                return false;
            }

            email.setRead(read);
            emailRepository.save(email);
            log.debug("Email marked as read={}: {}", read, emailId);
            return true;

        } catch (Exception e) {
            log.error("Error marking email as read: {}", e.getMessage(), e);
            return false;
        }
    }

    private List<EmailDTO> convertToDTO(List<Email> emails) {
        return emails.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    private EmailDTO convertToDTO(Email email) {
        EmailDTO dto = new EmailDTO();
        dto.id = email.getId();
        dto.from = email.getSenderEmail();
        dto.to = email.getRecipientEmail();
        dto.subject = email.getSubject();
        dto.content = email.getContent();
        dto.date = Timestamp.valueOf(email.getSentDate());
        dto.read = email.isRead();
        dto.folder = email.getFolder();
        dto.attachments = new ArrayList<>();
        return dto;
    }

    // ========== Внутренние DTO классы ==========
    public static class EmailDTO {
        public long id;
        public String from;
        public String to;
        public String subject;
        public String content;
        public Date date;
        public Boolean read;
        public String folder;
        public List<FileAttachment> attachments;

        public long getId() { return id; }
        public String getFrom() { return from; }
        public String getTo() { return to; }
        public String getSubject() { return subject; }
        public String getContent() { return content; }
        public Date getDate() { return date; }
        public Boolean isRead() { return read != null ? read : false; }
        public String getFolder() { return folder; }
        public List<FileAttachment> getAttachments() { return attachments; }

        public String getTimeAgo() {
            if (date == null) return "";
            long diff = new Date().getTime() - date.getTime();
            long minutes = diff / (60 * 1000);
            long hours = diff / (60 * 60 * 1000);
            long days = diff / (24 * 60 * 60 * 1000);

            if (minutes < 1) return "Только что";
            if (minutes < 60) return minutes + " мин назад";
            if (hours < 24) return hours + " ч назад";
            if (days == 1) return "Вчера";
            if (days < 7) return days + " дн назад";
            return "Более недели назад";
        }

        public String getPreview() {
            if (content == null || content.length() <= 50) return content;
            return content.substring(0, 50) + "...";
        }

        public boolean hasAttachments() {
            return attachments != null && !attachments.isEmpty();
        }

        public int getAttachmentCount() {
            return attachments != null ? attachments.size() : 0;
        }
    }

    public static class FileAttachment {
        public Long id;
        public String fileName;
        public String fileSize;
        public String fileType;
        public String s3ObjectKey;

        public FileAttachment(Long id, String fileName, String fileSize, String fileType, String s3ObjectKey) {
            this.id = id;
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.fileType = fileType;
            this.s3ObjectKey = s3ObjectKey;
        }

        public Long getId() { return id; }
        public String getFileName() { return fileName; }
        public String getFileSize() { return fileSize; }
        public String getFileType() { return fileType; }
        public String getS3ObjectKey() { return s3ObjectKey; }

        public String getFormattedSize() {
            try {
                long size = Long.parseLong(fileSize);
                if (size < 1024) return size + " B";
                if (size < 1024 * 1024) return (size / 1024) + " KB";
                return String.format("%.1f MB", size / (1024.0 * 1024.0));
            } catch (NumberFormatException e) {
                return fileSize;
            }
        }
    }
}