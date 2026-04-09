package com.securemsg.repository;

import com.securemsg.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с сущностью API-ключа.
 * Предоставляет методы для поиска ключей по значению, пользователю и проверки существования.
 */
@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {
    /**
     * Находит API-ключ по его значению, учитывая только активные ключи.
     * Используется при аутентификации внешних систем.
     *
     * @param keyValue значение API-ключа
     * @return Optional с ключом, если найден и активен
     */
    Optional<ApiKey> findByKeyValueAndEnabledTrue(String keyValue);

    /**
     * Возвращает список всех API-ключей, принадлежащих указанному пользователю.
     *
     * @param userId идентификатор пользователя
     * @return список ключей пользователя
     */
    List<ApiKey> findByUserId(Long userId);

    /**
     * Проверяет, существует ли API-ключ с указанным значением.
     *
     * @param keyValue значение API-ключа
     * @return true, если ключ существует
     */
    boolean existsByKeyValue(String keyValue);
}