package com.securemsg.config;

import com.securemsg.entity.Role;
import com.securemsg.entity.User;
import com.securemsg.repository.RoleRepository;
import com.securemsg.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

/**
 * Инициализатор базы данных при первом запуске приложения.
 * Создаёт базовые роли и тестовых пользователей, если они отсутствуют.
 * Также мигрирует пароли из старого формата в BCrypt.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("=== ИНИЦИАЛИЗАЦИЯ РОЛЕЙ И ПОЛЬЗОВАТЕЛЕЙ ===");

        // 1. Создаем базовые роли
        createRoleIfNotExists("ROLE_ADMIN");
        createRoleIfNotExists("ROLE_USER");

        // 2. Создаем администраторов
        createUserIfNotExists("admin", "admin@company.org", "admin", "ROLE_ADMIN");
        createUserIfNotExists("security", "security@system.com", "security", "ROLE_ADMIN");

        // 3. Создаем обычных пользователей
        createUserIfNotExists("user", "user@sam.reshu", "password", "ROLE_USER");
        createUserIfNotExists("test1", "test1@sam.reshu", "test1", "ROLE_USER");
        createUserIfNotExists("test2", "test2@company.org", "test2", "ROLE_USER");

        // 4. Миграция паролей (перехеширование существующих)
        migratePasswords();

        log.info("=== ИНИЦИАЛИЗАЦИЯ ЗАВЕРШЕНА ===");
    }

    /**
     * Создаёт роль, если она ещё не существует.
     *
     * @param roleName имя роли (например, "ROLE_ADMIN")
     */
    private void createRoleIfNotExists(String roleName) {
        if (!roleRepository.existsByName(roleName)) {
            Role role = new Role(roleName);
            roleRepository.save(role);
            log.info("✅ Создана роль: {}", roleName);
        } else {
            log.debug("⏭️ Роль уже существует: {}", roleName);
        }
    }

    /**
     * Создаёт пользователя, если он ещё не существует.
     * Назначает указанные роли.
     *
     * @param username имя пользователя
     * @param email email пользователя
     * @param password пароль (будет закодирован BCrypt)
     * @param roleNames список имён ролей
     */
    @Transactional
    private void createUserIfNotExists(String username, String email, String password, String... roleNames) {
        if (!userRepository.existsByEmail(email)) {
            User user = new User();
            user.setUsername(username);
            user.setEmail(email);
            user.setPassword(passwordEncoder.encode(password));
            user.setEnabled(true);
            user.setCreatedAt(LocalDateTime.now());

            Set<Role> roles = new HashSet<>();
            for (String roleName : roleNames) {
                Role role = roleRepository.findByName(roleName)
                        .orElseThrow(() -> new RuntimeException("Роль не найдена: " + roleName));
                roles.add(role);
            }
            user.setRoles(roles);

            userRepository.save(user);
            log.info("✅ Создан пользователь: {} с ролями: {}", email, String.join(", ", roleNames));
        } else {
            log.debug("⏭️ Пользователь уже существует: {}", email);
        }
    }

    /**
     * Мигрирует пароли из старого формата (например, с {noop}) в BCrypt.
     * Выполняется только для паролей, которые ещё не закодированы BCrypt.
     */
    @Transactional
    public void migratePasswords() {
        List<User> allUsers = userRepository.findAll();
        int migratedCount = 0;

        for (User user : allUsers) {
            String currentPassword = user.getPassword();

            // Проверяем, не хеширован ли уже пароль (BCrypt начинается с $2a$)
            if (currentPassword != null && !currentPassword.startsWith("$2a$")) {
                String rawPassword = currentPassword.replace("{noop}", "");
                String encodedPassword = passwordEncoder.encode(rawPassword);
                user.setPassword(encodedPassword);
                userRepository.save(user);
                migratedCount++;
                log.info("🔐 Мигрирован пароль для: {}", user.getEmail());
            }
        }
        if (migratedCount > 0) {
            log.info("Мигрировано паролей: {}", migratedCount);
        } else {
            log.debug("Пароли не требуют миграции");
        }
    }
}