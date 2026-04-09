package com.securemsg.entity;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Сущность пользователя системы.
 * <p>
 * Хранит учётные данные пользователя: логин (email), пароль (в зашифрованном виде),
 * статус активности, дату регистрации и назначенные роли.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Уникальное имя пользователя (логин).
     */
    @Column(name = "username", nullable = false, unique = true)
    private String username;

    /**
     * Зашифрованный пароль (BCrypt).
     */

    @Column(name = "password", nullable = false)
    private String password;

    /**
     * Уникальный email пользователя (используется для входа).
     */
    @Column(name = "email", nullable = false, unique = true)
    private String email;

    /**
     * Статус учётной записи: true — активна, false — отключена.
     */
    @Column(name = "enabled")
    private boolean enabled = true;

    /**
     * Дата и время регистрации пользователя.
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * Роли пользователя (ADMIN, USER и т.д.).
     * FetchType.EAGER — загружаем роли сразу вместе с пользователем,
     * так как они нужны для авторизации.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    /**
     * Автоматически устанавливает дату создания перед сохранением.
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /**
     * Дата истечения срока действия пароля.
     * Если null - пароль бессрочный.
     */
    @Column(name = "password_expires_at")
    private LocalDateTime passwordExpiresAt;

    @Column(name = "temporary_password")
    private boolean temporaryPassword = false;

    @Column(name = "temporary_password_expires_at")
    private LocalDateTime temporaryPasswordExpiresAt;

    // ========== Конструкторы ==========
    public User() {}

    // ========== Геттеры и сеттеры ==========
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public LocalDateTime getPasswordExpiresAt() {
        return passwordExpiresAt;
    }

    public void setPasswordExpiresAt(LocalDateTime passwordExpiresAt) {
        this.passwordExpiresAt = passwordExpiresAt;
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Set<Role> getRoles() { return roles; }
    public void setRoles(Set<Role> roles) { this.roles = roles; }

    public boolean isTemporaryPassword() { return temporaryPassword; }
    public void setTemporaryPassword(boolean temporaryPassword) { this.temporaryPassword = temporaryPassword; }

    public LocalDateTime getTemporaryPasswordExpiresAt() { return temporaryPasswordExpiresAt; }
    public void setTemporaryPasswordExpiresAt(LocalDateTime temporaryPasswordExpiresAt) { this.temporaryPasswordExpiresAt = temporaryPasswordExpiresAt; }

    // ========== Вспомогательные методы ==========

    /**
     * Добавляет роль пользователю.
     *
     * @param role добавляемая роль
     */
    public void addRole(Role role) {
        this.roles.add(role);
    }

    /**
     * Проверяет, есть ли у пользователя указанная роль.
     *
     * @param roleName название роли (например, "ROLE_ADMIN")
     * @return true, если роль присутствует
     */
    public boolean hasRole(String roleName) {
        return roles.stream().anyMatch(role -> role.getName().equals(roleName));
    }

    /**
     * Проверяет, истёк ли срок действия пароля.
     * @return true, если срок истёк или пароль просрочен
     */
    public boolean isPasswordExpired() {
        if (temporaryPassword) {
            if (temporaryPasswordExpiresAt == null) return false;
            return LocalDateTime.now().isAfter(temporaryPasswordExpiresAt);
        } else {
            if (passwordExpiresAt == null) return false;
            return LocalDateTime.now().isAfter(passwordExpiresAt);
        }
    }

    public boolean requiresPasswordChange() {
        if (temporaryPassword) return true;
        if (passwordExpiresAt != null && LocalDateTime.now().isAfter(passwordExpiresAt)) return true;
        return false;
    }
}