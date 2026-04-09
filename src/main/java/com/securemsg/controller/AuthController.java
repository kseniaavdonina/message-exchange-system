package com.securemsg.controller;

import com.securemsg.service.MessageStorageService;
import com.securemsg.service.MessageQueueService;
import com.securemsg.service.UserService;
import com.securemsg.service.SessionManager;
import com.securemsg.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

/**
 * Основной контроллер аутентификации и работы с почтой.
 * Обрабатывает:
 * <ul>
 *     <li>Вход в систему (/login)</li>
 *     <li>Просмотр папок входящих и отправленных писем (/dashboard)</li>
 *     <li>Просмотр конкретного письма (/email/{folder}/{id})</li>
 *     <li>Удаление писем (/email/{folder}/{id}/delete)</li>
 *     <li>Отметка о прочтении (/email/{folder}/{id}/mark/{readStatus})</li>
 *     <li>Статистику пользователя (/stats)</li>
 *     <li>Отладочную страницу (/debug)</li>
 * </ul>
 */
@Controller
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private MessageStorageService messageStorageService;

    @Autowired
    private MessageQueueService messageQueueService;

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private UserService userService;

    /**
     * Перенаправляет корневой URL на дашборд.
     *
     * @return редирект на /dashboard
     */
    @GetMapping("/")
    public String home() {
        return "redirect:/dashboard";
    }


    /**
     * Отображает дашборд с письмами пользователя.
     * Поддерживает:
     * <ul>
     *     <li>Переключение между папками "inbox" и "sent"</li>
     *     <li>Поиск по теме и содержанию писем</li>
     * </ul>
     *
     * @param folder папка (inbox или sent, по умолчанию inbox)
     * @param searchQuery поисковый запрос (опционально)
     * @param model модель для передачи данных в представление
     * @return название шаблона dashboard
     */
    @GetMapping("/dashboard")
    public String dashboard(
            @RequestParam(value = "folder", defaultValue = "inbox") String folder,
            @RequestParam(value = "search", required = false) String searchQuery,
            Model model) {

        log.info("=== DASHBOARD REQUEST ===");
        log.info("Folder: {}, Search: {}", folder, searchQuery);

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String currentUser = auth.getName();
            log.info("Current user: {}", currentUser);

            Optional<User> userOpt = userService.getUserByEmail(currentUser);
            if (userOpt.isPresent() && userOpt.get().requiresPasswordChange()) {
                log.info("User {} requires password change, redirecting", currentUser);
                return "redirect:/change-password?required=true";
            }

            messageQueueService.registerUserQueue(currentUser);

            List<MessageStorageService.EmailDTO> emails;

            if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                log.info("Searching emails with query: {}", searchQuery);
                emails = messageStorageService.searchEmails(currentUser, folder, searchQuery.trim());
            } else {
                if ("sent".equals(folder)) {
                    emails = messageStorageService.getSentForUser(currentUser);
                    log.debug("Retrieved {} sent emails", emails.size());
                } else {
                    emails = messageStorageService.getInboxForUser(currentUser);
                    log.debug("Retrieved {} inbox emails", emails.size());
                }
            }

            model.addAttribute("emails", emails);
            model.addAttribute("currentFolder", folder);
            model.addAttribute("folderName", "sent".equals(folder) ? "Отправленные" : "Входящие");
            model.addAttribute("currentUser", currentUser);
            model.addAttribute("searchQuery", searchQuery);

            log.info("Dashboard loaded successfully for user: {}", currentUser);
            return "dashboard";

        } catch (Exception e) {
            log.error("Error in dashboard: {}", e.getMessage(), e);
            model.addAttribute("error", "Ошибка загрузки почты: " + e.getMessage());
            return "dashboard";
        }
    }

    /**
     * Отображает страницу входа в систему.
     *
     * @return название шаблона login
     */
    @GetMapping("/login")
    public String login() {
        log.debug("Login page requested");
        return "login";
    }

    /**
     * Отображает содержимое письма.
     * При просмотре письма в папке "inbox" автоматически помечает его как прочитанное.
     *
     * @param folder папка (inbox или sent)
     * @param id идентификатор письма
     * @param model модель для передачи данных в представление
     * @param redirectAttributes для flash-сообщений
     * @return название шаблона email-view или редирект на дашборд
     */
    @GetMapping("/email/{folder}/{id}")
    public String viewEmail(
            @PathVariable("folder") String folder,
            @PathVariable("id") Long id,
            Model model,
            RedirectAttributes redirectAttributes) {

        log.info("=== VIEW EMAIL ===");
        log.info("Folder: {}, Email ID: {}", folder, id);

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String currentUser = auth.getName();

            log.info("User: {}", currentUser);

            MessageStorageService.EmailDTO email = messageStorageService.getEmailById(currentUser, id, folder);

            if (email == null) {
                log.warn("Email not found or access denied: id={}, folder={}, user={}", id, folder, currentUser);
                redirectAttributes.addFlashAttribute("error", "Письмо не найдено или у вас нет доступа");
                return "redirect:/dashboard?folder=" + folder;
            }

            log.info("Email found: subject={}, from={}, to={}, attachments={}",
                    email.subject, email.from, email.to,
                    email.attachments != null ? email.attachments.size() : 0);

            model.addAttribute("email", email);
            model.addAttribute("currentFolder", folder);

            return "email-view";

        } catch (Exception e) {
            log.error("Error viewing email: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Ошибка при открытии письма: " + e.getMessage());
            return "redirect:/dashboard";
        }
    }

    /**
     * Отладочная страница. Показывает статистику писем и состояние очередей.
     * Доступна только пользователям с ролью ADMIN.
     */
    @GetMapping("/debug")
    public String debug(Model model) {
        log.info("=== DEBUG PAGE REQUESTED ===");

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String currentUser = auth.getName();

            // Проверка, что пользователь ADMIN
            boolean isAdmin = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

            if (!isAdmin) {
                log.warn("Non-admin user {} attempted to access debug page", currentUser);
                return "redirect:/dashboard";
            }

            List<MessageStorageService.EmailDTO> inbox = messageStorageService.getInboxForUser(currentUser);
            List<MessageStorageService.EmailDTO> sent = messageStorageService.getSentForUser(currentUser);
            var queueInfo = messageQueueService.getQueueInfo();

            model.addAttribute("currentUser", currentUser);
            model.addAttribute("inboxCount", inbox.size());
            model.addAttribute("sentCount", sent.size());
            model.addAttribute("inboxEmails", inbox);
            model.addAttribute("sentEmails", sent);
            model.addAttribute("queueInfo", queueInfo);

        } catch (Exception e) {
            log.error("Debug error: {}", e.getMessage(), e);
            model.addAttribute("error", "Ошибка получения отладочной информации: " + e.getMessage());
        }

        return "debug";
    }

    /**
     * Удаляет письмо пользователя.
     * Вместе с письмом удаляются все вложения из MinIO.
     *
     * @param folder папка (inbox или sent)
     * @param id идентификатор письма
     * @param redirectAttributes для flash-сообщений
     * @return редирект на дашборд
     */
    @GetMapping("/email/{folder}/{id}/delete")
    public String deleteEmail(
            @PathVariable("folder") String folder,
            @PathVariable("id") Long id,
            RedirectAttributes redirectAttributes) {

        log.info("=== DELETE EMAIL ===");
        log.info("Folder: {}, Email ID: {}", folder, id);

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String currentUser = auth.getName();

            log.info("User: {}", currentUser);

            boolean deleted = messageStorageService.deleteEmail(currentUser, id, folder);

            if (deleted) {
                log.info("Email {} deleted successfully", id);
                redirectAttributes.addFlashAttribute("success", "Письмо успешно удалено");
            } else {
                log.warn("Failed to delete email {}", id);
                redirectAttributes.addFlashAttribute("error", "Не удалось удалить письмо");
            }

        } catch (Exception e) {
            log.error("Error deleting email: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Ошибка удаления: " + e.getMessage());
        }

        return "redirect:/dashboard?folder=" + folder;
    }

    /**
     * Изменяет статус прочтения письма.
     *
     * @param folder папка (inbox или sent)
     * @param id идентификатор письма
     * @param readStatus новый статус ("read" или "unread")
     * @param redirectAttributes для flash-сообщений
     * @return редирект на дашборд
     */
    @GetMapping("/email/{folder}/{id}/mark/{readStatus}")
    public String markAsRead(
            @PathVariable("folder") String folder,
            @PathVariable("id") Long id,
            @PathVariable("readStatus") String readStatus,
            RedirectAttributes redirectAttributes) {

        log.info("=== MARK EMAIL ===");
        log.info("Folder: {}, Email ID: {}, Status: {}", folder, id, readStatus);

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String currentUser = auth.getName();

            log.info("User: {}", currentUser);

            boolean read = "read".equals(readStatus);
            boolean success = messageStorageService.markAsRead(currentUser, id, read);

            if (success) {
                String message = read ? "Письмо помечено как прочитанное" : "Письмо помечено как непрочитанное";
                log.info("Email {} marked as {}", id, read ? "read" : "unread");
                redirectAttributes.addFlashAttribute("success", message);
            } else {
                log.warn("Failed to mark email {} as {}", id, read ? "read" : "unread");
                redirectAttributes.addFlashAttribute("error", "Не удалось изменить статус письма");
            }

        } catch (Exception e) {
            log.error("Error marking email: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Ошибка: " + e.getMessage());
        }

        return "redirect:/dashboard?folder=" + folder;
    }

    /**
     * Отображает страницу со статистикой пользователя.
     * Показывает количество входящих, отправленных, непрочитанных писем,
     * а также письма за последние 7 дней.
     *
     * @param model модель для передачи данных в представление
     * @return название шаблона stats
     */
    @GetMapping("/stats")
    public String userStats(Model model) {
        log.info("=== USER STATS REQUESTED ===");

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String currentUser = auth.getName();

            log.info("User: {}", currentUser);

            List<MessageStorageService.EmailDTO> inbox = messageStorageService.getInboxForUser(currentUser);
            List<MessageStorageService.EmailDTO> sent = messageStorageService.getSentForUser(currentUser);

            long unreadCount = inbox.stream()
                    .filter(email -> !email.isRead())
                    .count();

            long recentCount = inbox.stream()
                    .filter(email -> {
                        if (email.date == null) return false;
                        long diff = new java.util.Date().getTime() - email.date.getTime();
                        return diff < 7 * 24 * 60 * 60 * 1000; // 7 дней в миллисекундах
                    })
                    .count();

            log.info("Stats: inbox={}, sent={}, unread={}, recent={}",
                    inbox.size(), sent.size(), unreadCount, recentCount);

            model.addAttribute("currentUser", currentUser);
            model.addAttribute("totalInbox", inbox.size());
            model.addAttribute("totalSent", sent.size());
            model.addAttribute("unreadCount", unreadCount);
            model.addAttribute("recentCount", recentCount);
            model.addAttribute("totalEmails", inbox.size() + sent.size());

        } catch (Exception e) {
            log.error("Error getting stats: {}", e.getMessage(), e);
            model.addAttribute("error", "Ошибка получения статистики: " + e.getMessage());
        }

        return "stats";
    }

    /**
     * Вспомогательный метод для получения порта контейнера.
     * Используется для отладки при масштабировании.
     *
     * @param container имя контейнера (например, "app-2")
     * @return порт контейнера (8080 + номер)
     */
    private int getContainerPort(String container) {
        int id = Integer.parseInt(container.replace("app-", ""));
        return 8080 + id;
    }

    /**
     * Страница отказа в доступе.
     * Администратор → редирект в админ-панель
     * Обычный пользователь → видит страницу
     */
    @GetMapping("/access-denied")
    public String accessDenied(Authentication auth, Model model) {
        if (auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            return "redirect:/admin/dashboard";
        }

        // Для обычных пользователей показываем страницу
        model.addAttribute("error", "У вас нет прав для доступа к этой странице");
        return "access-denied";
    }
}