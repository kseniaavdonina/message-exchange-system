package com.securemsg.dto;

import java.io.Serializable;
import java.util.Date;

/**
 * DTO (Data Transfer Object) для передачи сообщений через JMS (ActiveMQ).
 * Используется для асинхронной доставки уведомлений о новых письмах.
 * Реализует Serializable для передачи по сети.
 */
public class EmailMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String from;
    private String to;
    private String subject;
    private String content;
    private Date timestamp;

    /**
     * Конструктор по умолчанию (необходим для десериализации JMS).
     */
    public EmailMessage() {}

    /**
     * Конструктор для создания сообщения с автоматической установкой времени.
     *
     * @param from    отправитель
     * @param to      получатель
     * @param subject тема сообщения
     * @param content текст сообщения
     */
    public EmailMessage(String from, String to, String subject, String content) {
        this.from = from;
        this.to = to;
        this.subject = subject;
        this.content = content;
        this.timestamp = new Date();
    }

    // ========== Геттеры и сеттеры ==========
    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }

    public String getTo() { return to; }
    public void setTo(String to) { this.to = to; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Date getTimestamp() { return timestamp; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }

    // ========== Вспомогательные методы ==========

    @Override
    public String toString() {
        return "EmailMessage{" +
                "from='" + from + '\'' +
                ", to='" + to + '\'' +
                ", subject='" + subject + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}