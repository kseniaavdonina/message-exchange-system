package com.securemsg.dto;

import com.securemsg.entity.User;
import java.time.LocalDateTime;

/**
 * DTO (Data Transfer Object) для передачи данных пользователя.
 * Используется в API и административной панели для
 * безопасной передачи данных без раскрытия пароля и других чувствительных полей.
 */
public class UserDTO {
    private Long id;
    private String username;
    private String email;
    private boolean enabled;
    private LocalDateTime createdAt;

    /**
     * Конструктор, создающий DTO из сущности User.
     * Исключает поле password для безопасности.
     *
     * @param user сущность пользователя
     */
    public UserDTO(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.enabled = user.isEnabled();
        this.createdAt = user.getCreatedAt();
    }

    // ========== Геттеры ==========
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public boolean isEnabled() { return enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // ========== Вспомогательные методы ==========

    @Override
    public String toString() {
        return "UserDTO{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", enabled=" + enabled +
                ", createdAt=" + createdAt +
                '}';
    }
}