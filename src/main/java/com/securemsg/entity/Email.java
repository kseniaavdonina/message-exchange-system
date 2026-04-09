package com.securemsg.entity;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Сущность письма в системе обмена сообщениями.
 * <p>
 * Каждое письмо сохраняется в двух экземплярах:
 * <ul>
 *     <li>Для получателя (folder = "INBOX")</li>
 *     <li>Для отправителя (folder = "SENT")</li>
 * </ul>
 * Вложения хранятся отдельно в таблице attachments и связаны с письмом.
 */
@Entity
@Table(name = "emails")
public class Email {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Email отправителя.
     */
    @Column(name = "sender_email", nullable = false)
    private String senderEmail;

    /**
     * Email получателя.
     */
    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    /**
     * Тема письма.
     */
    @Column(name = "subject")
    private String subject;

    /**
     * Текст письма.
     */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /**
     * Дата и время отправки письма.
     */
    @Column(name = "sent_date")
    private LocalDateTime sentDate;

    /**
     * Папка: "INBOX" (входящие) или "SENT" (отправленные).
     */
    @Column(name = "folder", nullable = false)
    private String folder;

    /**
     * Статус прочтения: true — прочитано, false — не прочитано.
     */
    @Column(name = "is_read")
    private Boolean read = false;

    /**
     * Список вложений письма.
     * При удалении письма вложения удаляются каскадно.
     */
    @OneToMany(mappedBy = "email", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Attachment> attachments = new ArrayList<>();

    /**
     * Автоматически устанавливает дату отправки и статус прочтения перед сохранением.
     */
    @PrePersist
    protected void onCreate() {
        if (sentDate == null) {
            sentDate = LocalDateTime.now();
        }
        if (read == null) {
            read = false;
        }
    }

    // ========== Конструкторы ==========
    public Email() {}

    /**
     * Создаёт новое письмо с автоматической установкой даты отправки,
     * папкой "INBOX" и статусом "не прочитано".
     *
     * @param senderEmail   email отправителя
     * @param recipientEmail email получателя
     * @param subject       тема письма
     * @param content       текст письма
     */
    public Email(String senderEmail, String recipientEmail, String subject, String content) {
        this.senderEmail = senderEmail;
        this.recipientEmail = recipientEmail;
        this.subject = subject;
        this.content = content;
        this.sentDate = LocalDateTime.now();
        this.folder = "INBOX";
        this.read = false;
    }

    // ========== Геттеры и сеттеры ==========
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSenderEmail() { return senderEmail; }
    public void setSenderEmail(String senderEmail) { this.senderEmail = senderEmail; }

    public String getRecipientEmail() { return recipientEmail; }
    public void setRecipientEmail(String recipientEmail) { this.recipientEmail = recipientEmail; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public LocalDateTime getSentDate() { return sentDate; }
    public void setSentDate(LocalDateTime sentDate) { this.sentDate = sentDate; }

    public String getFolder() { return folder; }
    public void setFolder(String folder) { this.folder = folder; }

    public Boolean isRead() { return read != null ? read : false; }
    public void setRead(Boolean read) { this.read = read != null ? read : false; }

    public List<Attachment> getAttachments() { return attachments; }
    public void setAttachments(List<Attachment> attachments) { this.attachments = attachments; }

    // ========== Вспомогательные методы ==========

    /**
     * Добавляет вложение к письму и устанавливает двустороннюю связь.
     *
     * @param attachment вложение для добавления
     */
    public void addAttachment(Attachment attachment) {
        attachments.add(attachment);
        attachment.setEmail(this);
    }
}