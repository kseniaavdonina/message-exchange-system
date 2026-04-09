package com.securemsg.repository;

import com.securemsg.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Репозиторий для работы с сущностью роли (Role).
 * Предоставляет методы для поиска роли по имени и проверки существования.
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    /**
     * Находит роль по её имени (например, "ROLE_ADMIN", "ROLE_USER").
     *
     * @param name название роли
     * @return Optional с ролью, если она найдена
     */
    Optional<Role> findByName(String name);

    /**
     * Проверяет, существует ли роль с указанным именем.
     *
     * @param name название роли
     * @return true, если роль существует
     */
    boolean existsByName(String name);
}