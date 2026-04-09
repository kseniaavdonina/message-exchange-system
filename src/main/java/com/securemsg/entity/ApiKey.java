package com.securemsg.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Сущность API-ключа для интеграции с внешними системами.
 * Хранит информацию о ключах доступа к REST API.
 * <p>
 * Каждый ключ привязан к конкретному пользователю и позволяет:
 * <ul>
 *     <li>Включать или отключать доступ (enabled)</li>
 *     <li>Ограничивать срок действия (expiresAt)</li>
 *     <li>Отслеживать время последнего использования (lastUsedAt)</li>
 * </ul>
 */
@Entity
@Table(name = "api_keys")
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Значение API-ключа (уникальная строка, 64 символа)
     */
    @Column(name = "key_value", nullable = false, unique = true, length = 64)
    private String keyValue;

    /**
     * Пользователь, которому принадлежит ключ
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Название/описание ключа (например, "Интеграция с 1С")
     */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Статус ключа: true - активен, false - отключён
     */
    @Column(name = "enabled")
    private Boolean enabled = true;

    /**
     * Дата и время создания ключа
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * Дата и время последнего использования ключа
     */
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    /**
     * Дата и время истечения срока действия ключа (может быть null)
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /**
     * Автоматически устанавливает дату создания перед сохранением.
     */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // ========== Геттеры и сеттеры ==========
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getKeyValue() { return keyValue; }
    public void setKeyValue(String keyValue) { this.keyValue = keyValue; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(LocalDateTime lastUsedAt) { this.lastUsedAt = lastUsedAt; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}