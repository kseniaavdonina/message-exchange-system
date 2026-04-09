package com.securemsg.controller;

import com.securemsg.entity.User;
import com.securemsg.repository.UserRepository;
import com.securemsg.service.MessageQueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;


import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Контроллер административной панели.
 * Обеспечивает управление пользователями: просмотр, добавление, редактирование,
 * удаление, включение/отключение, сброс паролей.
 * Доступен только пользователям с ролью ADMIN.
 */

@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MessageQueueService messageQueueService;

    /**
     * Проверяет, имеет ли текущий пользователь роль администратора.
     *
     * @return true, если пользователь аутентифицирован и имеет роль ADMIN или ROLE_ADMIN
     */
    private boolean isAdminUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            log.debug("isAdminUser: auth is null or not authenticated");
            return false;
        }

        log.debug("isAdminUser: Checking authorities for user: {}", auth.getName());
        for (GrantedAuthority authority : auth.getAuthorities()) {
            log.debug("  Authority: {}", authority.getAuthority());
        }

        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") ||
                        a.getAuthority().equals("ADMIN"));

        log.debug("isAdminUser: Result = {}", isAdmin);
        return isAdmin;
    }

    /**
     * Отображает главную страницу административной панели с дашбордом.
     * Показывает общую статистику: количество пользователей, активных пользователей,
     * список последних 5 зарегистрированных пользователей.
     *
     * @param model модель для передачи данных в представление
     * @return название шаблона admin/dashboard или редирект на пользовательский дашборд
     */
    @GetMapping("/dashboard")
    public String adminDashboard(Model model) {
        log.info("=== ADMIN DASHBOARD METHOD CALLED ===");
        log.info("Processing /admin/dashboard request");

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String currentUser = auth.getName();
            log.info("Current user: {}", currentUser);
            log.info("User authorities: {}", auth.getAuthorities());

            if (!isAdminUser()) {
                log.warn("User is not admin, redirecting to regular dashboard");
                return "redirect:/dashboard";
            }

            log.info("User is admin, loading admin dashboard data...");

            List<User> allUsers = userRepository.findAll();
            long totalUsers = userRepository.count();
            long activeUsers = userRepository.findAll().stream()
                    .filter(User::isEnabled)
                    .count();

            // Последние 5 пользователей по дате создания
            List<User> recentUsers = userRepository.findAll().stream()
                    .sorted((u1, u2) -> {
                        if (u2.getCreatedAt() == null) return -1;
                        if (u1.getCreatedAt() == null) return 1;
                        return u2.getCreatedAt().compareTo(u1.getCreatedAt());
                    })
                    .limit(5)
                    .collect(Collectors.toList());

            log.info("Total users: {}, Active users: {}, Recent users: {}", totalUsers, activeUsers, recentUsers.size());

            model.addAttribute("currentUser", currentUser);
            model.addAttribute("users", recentUsers);
            model.addAttribute("allUsers", allUsers);
            model.addAttribute("totalUsers", totalUsers);
            model.addAttribute("activeUsers", activeUsers);
            model.addAttribute("inactiveUsers", totalUsers - activeUsers);

            log.info("Returning view: admin/dashboard");
            return "admin/dashboard";

        } catch (Exception e) {
            log.error("ERROR in admin dashboard: {}", e.getMessage(), e);
            model.addAttribute("error", "Ошибка загрузки админ-панели: " + e.getMessage());
            return "redirect:/dashboard";
        }
    }

    /**
     * Отображает список пользователей с возможностью поиска.
     *
     * @param search поисковый запрос (опционально)
     * @param model модель для передачи данных в представление
     * @return название шаблона admin/users
     */
    @GetMapping("/users")
    public String manageUsers(
            @RequestParam(required = false) String search,
            Model model) {

        log.info("=== ОСНОВНОЙ МЕТОД manageUsers() ===");

        if (!isAdminUser()) {
            return "redirect:/dashboard";
        }

        List<User> users;

        if (search != null && !search.trim().isEmpty()) {
            String searchTerm = search.toLowerCase().trim();
            users = userRepository.findAll().stream()
                    .filter(user ->
                            user.getUsername().toLowerCase().contains(searchTerm) ||
                                    user.getEmail().toLowerCase().contains(searchTerm))
                    .collect(Collectors.toList());

            log.info("Поиск по запросу: '{}', найдено: {}", search, users.size());
        } else {
            users = userRepository.findAll();
            log.info("Показаны все пользователи: {}", users.size());
        }

        log.info("Пользователей для отображения: {}", users.size());

        long activeUsers = users.stream().filter(User::isEnabled).count();

        model.addAttribute("searchQuery", search);
        model.addAttribute("users", users);
        model.addAttribute("totalUsers", users.size());
        model.addAttribute("activeUsers", activeUsers);
        model.addAttribute("inactiveUsers", users.size() - activeUsers);

        return "admin/users";
    }

    /**
     * Отображает форму добавления нового пользователя.
     *
     * @param model модель для передачи данных в представление
     * @return название шаблона admin/add-user
     */
    @GetMapping("/users/add")
    public String addUserForm(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String currentUser = auth.getName();

        if (!isAdminUser()) {
            return "redirect:/dashboard";
        }

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("user", new User());

        return "admin/add-user";
    }

    /**
     * Обрабатывает создание нового пользователя.
     * Выполняет проверку уникальности email и username,
     * валидацию сложности пароля (12+ символов, цифра, заглавная, строчная, спецсимвол),
     * создаёт персональную очередь в ActiveMQ.
     *
     * @param username имя пользователя
     * @param email email пользователя (используется как логин)
     * @param password пароль (проверяется на сложность)
     * @param enabled статус учётной записи
     * @param redirectAttributes для flash-сообщений
     * @return редирект на список пользователей
     */
    @PostMapping("/users/add")
    public String addUser(
            @RequestParam String username,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam(defaultValue = "true") boolean enabled,
            RedirectAttributes redirectAttributes) {

        log.info("=== ADD USER ===");
        log.info("Creating user with email: {}", email);

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String currentUser = auth.getName();

            if (!isAdminUser()) {
                return "redirect:/dashboard";
            }

            if (userRepository.existsByEmail(email)) {
                redirectAttributes.addFlashAttribute("error", "Пользователь с email " + email + " уже существует");
                return "redirect:/admin/users/add";
            }

            if (userRepository.existsByUsername(username)) {
                redirectAttributes.addFlashAttribute("error", "Пользователь с именем " + username + " уже существует");
                return "redirect:/admin/users/add";
            }

            if (password.length() < 12) {
                redirectAttributes.addFlashAttribute("error", "Пароль должен содержать не менее 12 символов");
                return "redirect:/admin/users/add";
            }

            if (!password.matches(".*\\d.*")) {
                redirectAttributes.addFlashAttribute("error", "Пароль должен содержать хотя бы одну цифру");
                return "redirect:/admin/users/add";
            }

            if (!password.matches(".*[A-Z].*")) {
                redirectAttributes.addFlashAttribute("error", "Пароль должен содержать хотя бы одну заглавную букву");
                return "redirect:/admin/users/add";
            }

            if (!password.matches(".*[a-z].*")) {
                redirectAttributes.addFlashAttribute("error", "Пароль должен содержать хотя бы одну строчную букву");
                return "redirect:/admin/users/add";
            }

            if (!password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) {
                redirectAttributes.addFlashAttribute("error", "Пароль должен содержать хотя бы один специальный символ");
                return "redirect:/admin/users/add";
            }

            User newUser = new User();
            newUser.setUsername(username);
            newUser.setEmail(email);
            newUser.setPassword(passwordEncoder.encode(password));
            newUser.setEnabled(enabled);
            newUser.setCreatedAt(LocalDateTime.now());

            newUser.setTemporaryPassword(true);
            newUser.setTemporaryPasswordExpiresAt(LocalDateTime.now().plusHours(72));
            newUser.setPasswordExpiresAt(null);

            userRepository.save(newUser);
            messageQueueService.createUserQueue(email);

            log.info("User {} created successfully", email);
            redirectAttributes.addFlashAttribute("success", "Пользователь " + username + " успешно создан");
            return "redirect:/admin/users";

        } catch (Exception e) {
            log.error("Error creating user: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Ошибка создания пользователя: " + e.getMessage());
            return "redirect:/admin/users/add";
        }
    }

    /**
     * Отображает форму редактирования пользователя.
     *
     * @param id идентификатор пользователя
     * @param model модель для передачи данных
     * @param redirectAttributes для flash-сообщений
     * @return название шаблона admin/edit-user или редирект
     */
    @GetMapping("/users/edit/{id}")
    public String editUserForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.info("=== EDIT USER FORM ===");
        log.info("Editing user with id: {}", id);

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String currentUser = auth.getName();

            if (!isAdminUser()) {
                return "redirect:/dashboard";
            }

            Optional<User> userOpt = userRepository.findById(id);

            if (userOpt.isEmpty()) {
                log.warn("User with id {} not found", id);
                redirectAttributes.addFlashAttribute("error", "Пользователь не найден");
                return "redirect:/admin/users";
            }

            model.addAttribute("user", userOpt.get());
            model.addAttribute("currentUser", currentUser);

            return "admin/edit-user";

        } catch (Exception e) {
            log.error("Error editing user: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Ошибка: " + e.getMessage());
            return "redirect:/admin/users";
        }
    }

    /**
     * Обрабатывает обновление данных пользователя.
     * Проверяет уникальность email и username, при необходимости обновляет пароль.
     *
     * @param id идентификатор пользователя
     * @param username новое имя пользователя
     * @param email новый email
     * @param password новый пароль (опционально)
     * @param enabled статус учётной записи
     * @param redirectAttributes для flash-сообщений
     * @return редирект на список пользователей
     */
    @PostMapping("/users/edit/{id}")
    public String updateUser(
            @PathVariable Long id,
            @RequestParam String username,
            @RequestParam String email,
            @RequestParam(required = false) String password,
            @RequestParam(defaultValue = "true") boolean enabled,
            RedirectAttributes redirectAttributes) {

        log.info("=== UPDATE USER ===");
        log.info("Updating user id: {}, new email: {}", id, email);

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String currentUser = auth.getName();

            if (!isAdminUser()) {
                return "redirect:/dashboard";
            }

            Optional<User> userOpt = userRepository.findById(id);
            if (userOpt.isEmpty()) {
                log.warn("User with id {} not found", id);
                redirectAttributes.addFlashAttribute("error", "Пользователь не найден");
                return "redirect:/admin/users";
            }

            User user = userOpt.get();

            // Проверка уникальности email
            if (!user.getEmail().equals(email) && userRepository.existsByEmail(email)) {
                log.warn("Email {} already exists", email);
                redirectAttributes.addFlashAttribute("error", "Пользователь с email " + email + " уже существует");
                return "redirect:/admin/users/edit/" + id;
            }

            // Проверка уникальности username
            if (!user.getUsername().equals(username) && userRepository.existsByUsername(username)) {
                log.warn("Username {} already exists", username);
                redirectAttributes.addFlashAttribute("error", "Пользователь с именем " + username + " уже существует");
                return "redirect:/admin/users/edit/" + id;
            }

            user.setUsername(username);
            user.setEmail(email);
            user.setEnabled(enabled);
            if (password != null && !password.trim().isEmpty()) {
                user.setPassword(passwordEncoder.encode(password));
                log.info("Password updated for user: {}", email);
            }

            userRepository.save(user);

            log.info("User {} updated successfully", email);
            redirectAttributes.addFlashAttribute("success",
                    "Пользователь " + username + " успешно обновлен");

            return "redirect:/admin/users";

        } catch (Exception e) {
            log.error("Error updating user: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка обновления пользователя: " + e.getMessage());
            return "redirect:/admin/users/edit/" + id;
        }
    }

    /**
     * Удаляет пользователя из системы.
     * Запрещено удалять собственную учётную запись и системных пользователей.
     *
     * @param id идентификатор пользователя
     * @param redirectAttributes для flash-сообщений
     * @return редирект на список пользователей
     */
    @GetMapping("/users/delete/{id}")
    public String deleteUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("=== DELETE USER ===");
        log.info("Deleting user id: {}", id);

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String currentUser = auth.getName();

            if (!isAdminUser()) {
                return "redirect:/dashboard";
            }

            Optional<User> userOpt = userRepository.findById(id);

            if (userOpt.isEmpty()) {
                log.warn("User with id {} not found", id);
                redirectAttributes.addFlashAttribute("error", "Пользователь не найден");
                return "redirect:/admin/users";
            }

            User user = userOpt.get();

            // Защита от удаления себя
            if (user.getEmail().equals(currentUser)) {
                log.warn("User {} attempted to delete themselves", currentUser);
                redirectAttributes.addFlashAttribute("error", "Нельзя удалить свою учетную запись");
                return "redirect:/admin/users";
            }

            // Защита системных пользователей
            if (user.getEmail().equals("security@system.com") ||
                    user.getEmail().equals("admin@company.org")) {
                log.warn("Attempted to delete system user: {}", user.getEmail());
                redirectAttributes.addFlashAttribute("error", "Нельзя удалить системного пользователя");
                return "redirect:/admin/users";
            }

            String username = user.getUsername();
            userRepository.delete(user);
            log.info("User {} deleted successfully", username);
            redirectAttributes.addFlashAttribute("success",
                    "Пользователь " + username + " успешно удален");

            return "redirect:/admin/users";

        } catch (Exception e) {
            log.error("Error deleting user: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка удаления пользователя: " + e.getMessage());
            return "redirect:/admin/users";
        }
    }

    /**
     * Включает или отключает учётную запись пользователя.
     * Запрещено отключать собственную учётную запись и системных пользователей.
     *
     * @param id идентификатор пользователя
     * @param redirectAttributes для flash-сообщений
     * @return редирект на список пользователей
     */
    @GetMapping("/users/toggle/{id}")
    public String toggleUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("=== TOGGLE USER ===");
        log.info("Toggling user id: {}", id);

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String currentUser = auth.getName();

            if (!isAdminUser()) {
                return "redirect:/dashboard";
            }

            Optional<User> userOpt = userRepository.findById(id);

            if (userOpt.isEmpty()) {
                log.warn("User with id {} not found", id);
                redirectAttributes.addFlashAttribute("error", "Пользователь не найден");
                return "redirect:/admin/users";
            }

            User user = userOpt.get();

            if (user.getEmail().equals(currentUser)) {
                log.warn("User {} attempted to toggle themselves", currentUser);
                redirectAttributes.addFlashAttribute("error", "Нельзя отключить свою учетную запись");
                return "redirect:/admin/users";
            }

            if (user.getEmail().equals("security@system.com") ||
                    user.getEmail().equals("admin@company.org")) {
                log.warn("Attempted to toggle system user: {}", user.getEmail());
                redirectAttributes.addFlashAttribute("error", "Нельзя отключать системного пользователя");
                return "redirect:/admin/users";
            }

            boolean newStatus = !user.isEnabled();
            user.setEnabled(newStatus);
            userRepository.save(user);

            String status = newStatus ? "включена" : "отключена";
            log.info("User {} toggled to: {}", user.getUsername(), status);
            redirectAttributes.addFlashAttribute("success",
                    "Учетная запись " + user.getUsername() + " " + status);
            return "redirect:/admin/users";

        } catch (Exception e) {
            log.error("Error toggling user: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка изменения статуса: " + e.getMessage());
            return "redirect:/admin/users";
        }
    }

    /**
     * Отображает форму сброса пароля пользователя.
     *
     * @param id идентификатор пользователя
     * @param model модель для передачи данных
     * @param redirectAttributes для flash-сообщений
     * @return название шаблона admin/reset-password
     */
    @GetMapping("/users/reset-password/{id}")
    public String resetPasswordForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.info("=== RESET PASSWORD FORM ===");
        log.info("Reset password for user id: {}", id);

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (!isAdminUser()) {
                return "redirect:/dashboard";
            }

            Optional<User> userOpt = userRepository.findById(id);
            if (userOpt.isEmpty()) {
                log.warn("User with id {} not found", id);
                redirectAttributes.addFlashAttribute("error", "Пользователь не найден");
                return "redirect:/admin/users";
            }

            model.addAttribute("user", userOpt.get());
            return "admin/reset-password";

        } catch (Exception e) {
            log.error("Error showing reset password form: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Ошибка: " + e.getMessage());
            return "redirect:/admin/users";
        }
    }

    /**
     * Генерирует новый случайный пароль для пользователя и сохраняет его.
     * Пароль возвращается в JSON для отображения в модальном окне.
     *
     * @param id идентификатор пользователя
     * @return Map с паролем или ошибкой
     */
    @PostMapping("/users/reset-password/{id}")
    public String resetPassword(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.info("=== RESET PASSWORD (FORM) ===");
        log.info("Generating new password for user id: {}", id);

        try {
            if (!isAdminUser()) {
                redirectAttributes.addFlashAttribute("error", "Доступ запрещён");
                return "redirect:/admin/users";
            }

            Optional<User> userOpt = userRepository.findById(id);
            if (userOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Пользователь не найден");
                return "redirect:/admin/users";
            }

            User user = userOpt.get();
            String newPassword = generateRandomPassword();
            user.setPassword(passwordEncoder.encode(newPassword));

            user.setTemporaryPassword(true);
            user.setTemporaryPasswordExpiresAt(LocalDateTime.now().plusHours(72));
            user.setPasswordExpiresAt(null);

            userRepository.save(user);

            log.info("New password generated for user: {}", user.getEmail());

            // Показываем пароль на той же странице
            model.addAttribute("user", user);
            model.addAttribute("newPassword", newPassword);
            model.addAttribute("success", "Пароль успешно сброшен!");

            return "admin/reset-password";

        } catch (Exception e) {
            log.error("Error resetting password: {}", e.getMessage(), e);
            model.addAttribute("error", "Ошибка сброса пароля: " + e.getMessage());
            return "admin/reset-password";
        }
    }

    /**
     * Генерирует случайный пароль длиной 12 символов,
     * содержащий латинские заглавные, латинские строчные буквы, цифры и спецсимволы.
     *
     * @return сгенерированный пароль
     */
    private String generateRandomPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz0123456789!@#$%^&*";
        StringBuilder password = new StringBuilder();
        Random random = new Random();

        // 4 обязательных символа (разных типов)
        password.append((char)('A' + random.nextInt(26)));  // заглавная
        password.append((char)('a' + random.nextInt(26)));  // строчная
        password.append((char)('0' + random.nextInt(10)));  // цифра
        password.append(chars.charAt(52 + random.nextInt(10))); // спецсимвол

        for (int i = 4; i < 12; i++) {
            password.append(chars.charAt(random.nextInt(chars.length())));
        }

        char[] array = password.toString().toCharArray();
        for (int i = array.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char temp = array[i];
            array[i] = array[j];
            array[j] = temp;
        }

        return new String(array);
    }

    /**
     * Возвращает статистику пользователей в формате JSON.
     *
     * @return Map с полями totalUsers, enabledUsers, disabledUsers, success
     */
    @GetMapping("/api/stats")
    @ResponseBody
    public java.util.Map<String, Object> getUserStats() {
        log.debug("=== GET USER STATS API ===");

        Map<String, Object> result = new java.util.HashMap<>();

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String currentUser = auth.getName();

            if (!isAdminUser()) {
                log.warn("Non-admin user {} attempted to access stats", currentUser);
                result.put("error", "Access denied");
                return result;
            }

            long totalUsers = userRepository.count();
            long enabledUsers = userRepository.findAll().stream()
                    .filter(User::isEnabled)
                    .count();

            result.put("totalUsers", totalUsers);
            result.put("enabledUsers", enabledUsers);
            result.put("disabledUsers", totalUsers - enabledUsers);
            result.put("success", true);

            log.debug("Stats returned: total={}, enabled={}", totalUsers, enabledUsers);

        } catch (Exception e) {
            log.error("Error getting user stats: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
            result.put("success", false);
        }

        return result;
    }

    /**
     * Выполняет поиск пользователей по username или email (без учёта регистра).
     * Используется для AJAX-подсказок в интерфейсе администратора.
     *
     * @param query поисковый запрос (часть username или email)
     * @return список пользователей, соответствующих запросу, в формате JSON
     */
    @GetMapping("/api/users/search")
    @ResponseBody
    public List<Map<String, Object>> searchUsersApi(@RequestParam String query) {
        log.debug("=== API Search: Searching users for: {}", query);

        if (!isAdminUser()) {
            return new ArrayList<>();
        }

        List<User> users = userRepository.findAll();

        if (query == null || query.trim().isEmpty()) {
            return convertUsersToMap(users);
        }

        String searchTerm = query.toLowerCase().trim();
        List<User> filteredUsers = users.stream()
                .filter(user ->
                        user.getUsername().toLowerCase().contains(searchTerm) ||
                                user.getEmail().toLowerCase().contains(searchTerm))
                .collect(Collectors.toList());

        log.debug("Found {} users for query: {}", filteredUsers.size(), query);
        return convertUsersToMap(filteredUsers);
    }

    /**
     * Преобразует список пользователей в список Map для JSON-ответа.
     *
     * @param users список пользователей
     * @return список Map с полями id, username, email, enabled, createdAt
     */
    private List<Map<String, Object>> convertUsersToMap(List<User> users) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (User user : users) {
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("id", user.getId());
            userMap.put("username", user.getUsername());
            userMap.put("email", user.getEmail());
            userMap.put("enabled", user.isEnabled());
            userMap.put("createdAt", user.getCreatedAt());
            result.add(userMap);
        }
        return result;
    }

    /**
     * Возвращает список всех пользователей в формате JSON (используется AJAX на странице users.html).
     *
     * @return список пользователей с полями id, username, email, enabled, createdAt
     */
    @GetMapping("/api/users")
    @ResponseBody
    public List<Map<String, Object>> getUsersApi() {
        log.debug("=== API: Getting all users ===");

        if (!isAdminUser()) {
            return new ArrayList<>();
        }

        List<User> users = userRepository.findAll();
        log.debug("API returning {} users", users.size());

        return convertUsersToMap(users);
    }

    /**
     * Возвращает количество пользователей в формате JSON.
     *
     * @return Map с полями count и success
     */
    @GetMapping("/api/users/count")
    @ResponseBody
    public Map<String, Object> getUserCount() {
        log.debug("=== API: Getting user count ===");

        Map<String, Object> result = new HashMap<>();
        try {
            long count = userRepository.count();
            result.put("count", count);
            result.put("success", true);
            log.debug("User count: {}", count);
        } catch (Exception e) {
            log.error("Error getting user count: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }
}