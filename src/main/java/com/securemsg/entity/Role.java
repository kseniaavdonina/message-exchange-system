package com.securemsg.entity;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Сущность роли пользователя в системе.
 * <p>
 * Роли определяют права доступа:
 * <ul>
 *     <li>ROLE_ADMIN — доступ к административной панели</li>
 *     <li>ROLE_USER — обычный пользователь</li>
 * </ul>
 * Связь с пользователями — ManyToMany (один пользователь может иметь несколько ролей).
 */
@Entity
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Название роли (например, "ROLE_ADMIN", "ROLE_USER").
     * Должно быть уникальным.
     */
    @Column(unique = true, nullable = false, length = 50)
    private String name;

    /**
     * Пользователи, которым назначена данная роль.
     * Обратная сторона связи ManyToMany (mappedBy = "roles" в User).
     */
    @ManyToMany(mappedBy = "roles")
    private Set<User> users = new HashSet<>();

    // ========== Конструкторы ==========
    public Role() {}

    /**
     * Создаёт роль с указанным именем.
     *
     * @param name название роли (например, "ROLE_ADMIN")
     */
    public Role(String name) {
        this.name = name;
    }

    // ========== Геттеры и сеттеры ==========
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<User> getUsers() {
        return users;
    }

    public void setUsers(Set<User> users) {
        this.users = users;
    }

    // ========== Вспомогательные методы ==========

    /**
     * Возвращает название роли для удобного отображения.
     *
     * @return название роли
     */
    @Override
    public String toString() {
        return name;
    }
}