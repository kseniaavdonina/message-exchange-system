package com.securemsg.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Сущность вложения, прикреплённого к письму.
 * <p>
 * Файлы хранятся в S3-совместимом хранилище (MinIO).
 * В базе данных хранятся только метаданные:
 * <ul>
 *     <li>Имя файла</li>
 *     <li>Размер</li>
 *     <li>MIME-тип</li>
 *     <li>Ключ в S3 (s3ObjectKey)</li>
 *     <li>Дата загрузки</li>
 * </ul>
 * Сам файл в базе данных НЕ хранится.
 */
@Entity
@Table(name = "attachments")
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Письмо, к которому прикреплено вложение.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "email_id", nullable = false)
    private Email email;

    /**
     * Оригинальное имя файла.
     */
    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    /**
     * Размер файла в байтах.
     */
    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    /**
     * MIME-тип файла (например, "image/png", "application/pdf").
     */
    @Column(name = "file_type", length = 100)
    private String fileType;

    /**
     * Уникальный ключ объекта в S3-хранилище (MinIO).
     */
    @Column(name = "s3_object_key", nullable = false, length = 500)
    private String s3ObjectKey;

    /**
     * Дата и время загрузки файла.
     */
    @Column(name = "upload_date")
    private LocalDateTime uploadDate;

    /**
     * Автоматически устанавливает дату загрузки перед сохранением.
     */
    @PrePersist
    protected void onCreate() {
        uploadDate = LocalDateTime.now();
    }

    // ========== Конструкторы ==========
    public Attachment() {}

    public Attachment(Email email, String fileName, Long fileSize,
                      String fileType, String s3ObjectKey) {
        this.email = email;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.fileType = fileType;
        this.s3ObjectKey = s3ObjectKey;
    }

    // ========== Геттеры и сеттеры ==========
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Email getEmail() { return email; }
    public void setEmail(Email email) { this.email = email; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public String getS3ObjectKey() { return s3ObjectKey; }
    public void setS3ObjectKey(String s3ObjectKey) { this.s3ObjectKey = s3ObjectKey; }

    public LocalDateTime getUploadDate() { return uploadDate; }
    public void setUploadDate(LocalDateTime uploadDate) { this.uploadDate = uploadDate; }

    /**
     * Возвращает отформатированный размер файла для отображения пользователю.
     *
     * @return строка с размером (например, "1.5 MB", "256 KB", "1024 B")
     */
    public String getFormattedSize() {
        if (fileSize < 1024) return fileSize + " B";
        if (fileSize < 1024 * 1024) return (fileSize / 1024) + " KB";
        return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
    }
}