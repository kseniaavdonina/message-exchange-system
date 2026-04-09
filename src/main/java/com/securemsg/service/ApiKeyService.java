package com.securemsg.service;

import com.securemsg.entity.ApiKey;
import com.securemsg.entity.User;
import com.securemsg.repository.ApiKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Сервис для управления API-ключами.
 * Предоставляет методы для генерации, валидации, получения, отключения и удаления ключей.
 * API-ключи используются для аутентификации внешних систем при интеграции.
 */
@Service
public class ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private UserService userService;

    /**
     * Генерирует новый API-ключ для указанного пользователя.
     *
     * @param userId идентификатор пользователя
     * @param name   название/описание ключа
     * @return созданный API-ключ
     * @throws RuntimeException если пользователь не найден
     */
    @Transactional
    public ApiKey generateApiKey(Long userId, String name) {
        log.info("Generating API key for user id: {}", userId);

        User user = userService.getUserById(userId)
                .orElseThrow(() -> {
                    log.error("User not found with id: {}", userId);
                    return new RuntimeException("User not found");
                });

        String keyValue = UUID.randomUUID().toString().replace("-", "");

        log.debug("Generated key value: {}", keyValue);

        ApiKey apiKey = new ApiKey();
        apiKey.setKeyValue(keyValue);
        apiKey.setUser(user);
        apiKey.setName(name);
        apiKey.setEnabled(true);
        apiKey.setCreatedAt(LocalDateTime.now());

        ApiKey savedKey = apiKeyRepository.save(apiKey);
        log.info("API key generated successfully for user: {}", user.getEmail());

        return savedKey;
    }

    /**
     * Проверяет валидность API-ключа.
     * Ключ считается валидным, если он существует, активен и не истёк.
     * При успешной проверке обновляется время последнего использования.
     *
     * @param keyValue значение API-ключа
     * @return Optional с ключом, если он валиден
     */
    public Optional<ApiKey> validateKey(String keyValue) {
        log.debug("Validating API key: {}", keyValue);

        Optional<ApiKey> apiKeyOpt = apiKeyRepository.findByKeyValueAndEnabledTrue(keyValue);

        if (apiKeyOpt.isPresent()) {
            ApiKey apiKey = apiKeyOpt.get();

            // Проверяем, не истёк ли ключ
            if (apiKey.getExpiresAt() != null && apiKey.getExpiresAt().isBefore(LocalDateTime.now())) {
                log.warn("API key has expired for user: {}", apiKey.getUser().getEmail());
                return Optional.empty();
            }

            // Обновляем время последнего использования
            apiKey.setLastUsedAt(LocalDateTime.now());
            apiKeyRepository.save(apiKey);
            log.debug("API key validated successfully for user: {}", apiKey.getUser().getEmail());

            return apiKeyOpt;
        }

        log.warn("Invalid or inactive API key: {}", keyValue);
        return Optional.empty();
    }

    /**
     * Возвращает список всех API-ключей, принадлежащих указанному пользователю.
     *
     * @param userId идентификатор пользователя
     * @return список ключей пользователя
     */
    public List<ApiKey> getKeysForUser(Long userId) {
        log.debug("Getting API keys for user id: {}", userId);

        return apiKeyRepository.findByUserId(userId);
    }

    /**
     * Возвращает email пользователя по значению API-ключа.
     * Используется в ApiController для аутентификации.
     *
     * @param keyValue значение API-ключа
     * @return Optional с email пользователя, если ключ валиден
     */
    public Optional<String> getUserEmailByKey(String keyValue) {
        log.debug("Getting user email by API key");
        return apiKeyRepository.findByKeyValueAndEnabledTrue(keyValue)
                .map(apiKey -> {
                    log.debug("Found user: {}", apiKey.getUser().getEmail());
                    return apiKey.getUser().getEmail();
                });
    }

    /**
     * Отключает API-ключ (делает его неактивным).
     * Отключённый ключ нельзя использовать для аутентификации.
     *
     * @param keyId идентификатор ключа
     * @return true, если ключ успешно отключён
     */
    @Transactional
    public boolean disableKey(Long keyId) {
        log.info("Disabling API key with id: {}", keyId);

        Optional<ApiKey> keyOpt = apiKeyRepository.findById(keyId);
        if (keyOpt.isPresent()) {
            ApiKey key = keyOpt.get();
            key.setEnabled(false);
            apiKeyRepository.save(key);
            log.info("API key disabled for user: {}", key.getUser().getEmail());
            return true;
        }

        log.warn("API key not found with id: {}", keyId);
        return false;
    }

    /**
     * Полностью удаляет API-ключ из базы данных.
     *
     * @param keyId идентификатор ключа
     * @return true, если ключ успешно удалён
     */
    @Transactional
    public boolean deleteKey(Long keyId) {
        log.info("Deleting API key with id: {}", keyId);

        if (apiKeyRepository.existsById(keyId)) {
            apiKeyRepository.deleteById(keyId);
            log.info("API key deleted successfully");
            return true;
        }

        log.warn("API key not found with id: {}", keyId);
        return false;
    }
}