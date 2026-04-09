package com.securemsg.repository;

import com.securemsg.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Репозиторий для работы с сущностью пользователя (User).
 * Предоставляет методы для поиска пользователей по email и username,
 * а также проверки существования.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    /**
     * Находит пользователя по email.
     * Используется при аутентификации (Spring Security).
     *
     * @param email email пользователя
     * @return Optional с пользователем, если найден
     */
    Optional<User> findByEmail(String email);

    /**
     * Находит пользователя по имени пользователя (username).
     *
     * @param username имя пользователя
     * @return Optional с пользователем, если найден
     */
    Optional<User> findByUsername(String username);

    /**
     * Проверяет, существует ли пользователь с указанным email.
     * Используется при создании и обновлении пользователей.
     *
     * @param email email пользователя
     * @return true, если пользователь существует
     */
    boolean existsByEmail(String email);

    /**
     * Проверяет, существует ли пользователь с указанным именем.
     * Используется при создании и обновлении пользователей.
     *
     * @param username имя пользователя
     * @return true, если пользователь существует
     */
    boolean existsByUsername(String username);
}