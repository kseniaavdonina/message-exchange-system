package com.securemsg.service;

import com.securemsg.entity.Email;
import com.securemsg.entity.User;
import com.securemsg.repository.EmailRepository;
import com.securemsg.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Сервис для управления пользователями.
 * Предоставляет методы для CRUD операций с пользователями,
 * а также отправку приветственного письма при регистрации.
 */
@Service
@Transactional
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailRepository emailRepository;

    @Autowired
    private MessageQueueService messageQueueService;

    /**
     * Возвращает список всех пользователей.
     *
     * @return список пользователей
     */
    public List<User> getAllUsers() {
        log.debug("Getting all users");
        return userRepository.findAll();
    }

    /**
     * Возвращает пользователя по ID.
     *
     * @param id идентификатор пользователя
     * @return Optional с пользователем
     */
    public Optional<User> getUserById(Long id) {
        log.debug("Getting user by id: {}", id);
        return userRepository.findById(id);
    }

    /**
     * Возвращает пользователя по email.
     *
     * @param email email пользователя
     * @return Optional с пользователем
     */
    public Optional<User> getUserByEmail(String email) {
        log.debug("Getting user by email: {}", email);
        return userRepository.findByEmail(email);
    }

    /**
     * Создаёт нового пользователя.
     * После создания создаёт персональную очередь и отправляет приветственное письмо.
     *
     * @param username имя пользователя
     * @param email    email пользователя
     * @param password пароль (будет закодирован)
     * @return созданный пользователь
     * @throws RuntimeException если пользователь с таким email уже существует
     */
    public User createUser(String username, String email, String password) {
        log.info("Creating new user: {}", email);

        if (userRepository.existsByEmail(email)) {
            log.warn("User already exists with email: {}", email);
            throw new RuntimeException("User already exists with email: " + email);
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.now());

        User savedUser = userRepository.save(user);
        log.info("✅ User created: {}", email);

        // После сохранения: создаём очередь и отправляем приветствие
        messageQueueService.createUserQueue(user.getEmail());
        sendWelcomeEmail(email);

        return savedUser;
    }

    /**
     * Обновляет данные пользователя.
     *
     * @param id       идентификатор пользователя
     * @param username новое имя пользователя
     * @param email    новый email
     * @param password новый пароль (опционально)
     * @param enabled  статус учётной записи
     * @return обновлённый пользователь
     * @throws RuntimeException если пользователь не найден
     */
    public User updateUser(Long id, String username, String email, String password, boolean enabled) {
        log.info("Updating user id: {}", id);

        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            log.error("User not found with id: {}", id);
            throw new RuntimeException("User not found");
        }

        User user = userOpt.get();
        user.setUsername(username);
        user.setEmail(email);

        if (password != null && !password.trim().isEmpty()) {
            user.setPassword(passwordEncoder.encode(password));
            log.debug("Password updated for user: {}", email);
        }

        User updatedUser = userRepository.save(user);
        log.info("User updated successfully: {}", email);
        return updatedUser;
    }

    /**
     * Удаляет пользователя по ID.
     *
     * @param id идентификатор пользователя
     * @return true, если пользователь удалён
     */
    public boolean deleteUser(Long id) {
        log.info("Deleting user id: {}", id);

        if (userRepository.existsById(id)) {
            userRepository.deleteById(id);
            log.info("User deleted successfully: {}", id);
            return true;
        }

        log.warn("User not found with id: {}", id);
        return false;
    }

    /**
     * Включает или отключает пользователя.
     *
     * @param id идентификатор пользователя
     * @return true, если статус изменён
     */
    public boolean toggleUserStatus(Long id) {
        log.info("Toggling user status for id: {}", id);

        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            boolean newStatus = !user.isEnabled();
            user.setEnabled(newStatus);
            userRepository.save(user);
            log.info("User {} status toggled to: {}", user.getEmail(), newStatus);
            return true;
        }

        log.warn("User not found with id: {}", id);
        return false;
    }

    /**
     * Сбрасывает пароль пользователя.
     *
     * @param id          идентификатор пользователя
     * @param newPassword новый пароль
     * @return true, если пароль сброшен
     */
    public boolean resetPassword(Long id, String newPassword) {
        log.info("Resetting password for user id: {}", id);

        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);
            log.info("Password reset for user: {}", user.getEmail());
            return true;
        }

        log.warn("User not found with id: {}", id);
        return false;
    }

    /**
     * Проверяет, существует ли пользователь с указанным email.
     *
     * @param email email пользователя
     * @return true, если пользователь существует
     */
    public boolean userExists(String email) {
        return userRepository.existsByEmail(email);
    }

    /**
     * Возвращает общее количество пользователей.
     *
     * @return количество пользователей
     */
    public long getUserCount() {
        return userRepository.count();
    }

    /**
     * Выполняет поиск пользователей по username или email.
     *
     * @param query поисковый запрос
     * @return список пользователей, соответствующих запросу
     */
    public List<User> searchUsers(String query) {
        log.debug("Searching users with query: {}", query);
        List<User> allUsers = userRepository.findAll();
        return allUsers.stream()
                .filter(user ->
                        user.getUsername().toLowerCase().contains(query.toLowerCase()) ||
                                user.getEmail().toLowerCase().contains(query.toLowerCase()))
                .toList();
    }

    /**
     * Отправляет приветственное письмо новому пользователю.
     * Проверяет, что письмо ещё не отправлено, чтобы избежать дублирования.
     *
     * @param userEmail email получателя
     */
    private void sendWelcomeEmail(String userEmail) {
        try {
            log.info("Checking welcome email for: {}", userEmail);

            // Проверяем существование письма в БД
            List<Email> inboxEmails = emailRepository.findByRecipientEmailAndFolderOrderBySentDateDesc(
                    userEmail,
                    "INBOX"
            );

            // Ищем точное совпадение
            boolean alreadyExists = false;
            for (Email email : inboxEmails) {
                if ("security@system.com".equals(email.getSenderEmail()) &&
                        "Добро пожаловать в РИТМ".equals(email.getSubject())) {
                    alreadyExists = true;
                    log.debug("Welcome email already exists for: {} (ID: {})", userEmail, email.getId());
                    break;
                }
            }

            // Не создаём, если уже есть
            if (alreadyExists) {
                log.debug("Skipping welcome email creation for: {}", userEmail);
                return;
            }

            log.info("Creating welcome email for: {}", userEmail);

            // 1. Письмо получателю (INBOX)
            Email inboxEmail = new Email();
            inboxEmail.setSenderEmail("security@system.com");
            inboxEmail.setRecipientEmail(userEmail);
            inboxEmail.setSubject("Добро пожаловать в РИТМ");
            inboxEmail.setContent(
                    "Рады приветствовать вас в нашей безопасной почтовой системе!\n\n" +
                            "Вы успешно зарегистрированы. Теперь вы можете отправлять и получать защищенные сообщения.\n\n" +
                            "С уважением,\n" +
                            "Команда РИТМ"
            );
            inboxEmail.setFolder("INBOX");
            inboxEmail.setSentDate(LocalDateTime.now());
            inboxEmail.setRead(false);
            emailRepository.save(inboxEmail);

            // 2. Копия для отправителя (SENT)
            Email sentEmail = new Email();
            sentEmail.setSenderEmail("security@system.com");
            sentEmail.setRecipientEmail(userEmail);
            sentEmail.setSubject("Добро пожаловать в РИТМ");
            sentEmail.setContent(
                    "Рады приветствовать вас в нашей безопасной почтовой системе!\n\n" +
                            "Вы успешно зарегистрированы. Теперь вы можете отправлять и получать защищенные сообщения.\n\n" +
                            "С уважением,\n" +
                            "Команда РИТМ"
            );
            sentEmail.setFolder("SENT");
            sentEmail.setSentDate(LocalDateTime.now());
            sentEmail.setRead(true);
            emailRepository.save(sentEmail);

            log.info("✅ Welcome email sent to: {}", userEmail);

        } catch (Exception e) {
            log.error("Error creating welcome email for {}: {}", userEmail, e.getMessage(), e);
        }
    }
}