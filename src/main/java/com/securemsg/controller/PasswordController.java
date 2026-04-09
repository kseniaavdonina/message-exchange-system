package com.securemsg.controller;

import com.securemsg.entity.User;
import com.securemsg.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;
import java.time.LocalDateTime;

/**
 * Контроллер для смены пароля пользователя.
 * Позволяет авторизованному пользователю изменить свой пароль
 * с проверкой сложности (12+ символов, цифра, заглавная, строчная, спецсимвол).
 */
@Controller
public class PasswordController {

    private static final Logger log = LoggerFactory.getLogger(PasswordController.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Отображает страницу смены пароля.
     *
     * @param model модель для передачи данных в представление
     * @return название шаблона change-password
     */
    @GetMapping("/change-password")
    public String showChangePasswordForm(Model model) {
        log.debug("Change password form requested");
        model.addAttribute("pageTitle", "Смена пароля");
        return "change-password";
    }

    /**
     * Обрабатывает смену пароля.
     * Выполняет проверки:
     * <ul>
     *     <li>Авторизация пользователя</li>
     *     <li>Совпадение нового пароля и подтверждения</li>
     *     <li>Длина пароля не менее 12 символов</li>
     *     <li>Наличие цифры (0-9)</li>
     *     <li>Наличие заглавной буквы (A-Z)</li>
     *     <li>Наличие строчной буквы (a-z)</li>
     *     <li>Наличие специального символа (!@#$%^&*()_+)</li>
     *     <li>Правильность текущего пароля</li>
     * </ul>
     *
     * @param currentPassword текущий пароль
     * @param newPassword новый пароль
     * @param confirmPassword подтверждение нового пароля
     * @param authentication объект аутентификации Spring Security
     * @param redirectAttributes для flash-сообщений
     * @return редирект на страницу входа или форму смены пароля
     */
    @PostMapping("/change-password")
    public String changePassword(
            @RequestParam("currentPassword") String currentPassword,
            @RequestParam("newPassword") String newPassword,
            @RequestParam("confirmPassword") String confirmPassword,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        log.info("=== CHANGE PASSWORD REQUEST ===");

        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("Unauthenticated user attempted to change password");
            redirectAttributes.addFlashAttribute("error", "Необходимо войти в систему");
            return "redirect:/login";
        }

        String email = authentication.getName();
        log.info("User: {}", email);

        if (!newPassword.equals(confirmPassword)) {
            log.warn("Password confirmation mismatch for user: {}", email);
            redirectAttributes.addFlashAttribute("error", "Новый пароль и подтверждение не совпадают");
            return "redirect:/change-password";
        }

        if (newPassword.length() < 12) {
            log.warn("Password too short for user: {}", email);
            redirectAttributes.addFlashAttribute("error", "Пароль должен содержать не менее 12 символов");
            return "redirect:/change-password";
        }

        if (!newPassword.matches(".*\\d.*")) {
            log.warn("Password missing digit for user: {}", email);
            redirectAttributes.addFlashAttribute("error", "Пароль должен содержать хотя бы одну цифру");
            return "redirect:/change-password";
        }

        if (!newPassword.matches(".*[A-Z].*")) {
            log.warn("Password missing uppercase letter for user: {}", email);
            redirectAttributes.addFlashAttribute("error", "Пароль должен содержать хотя бы одну заглавную букву");
            return "redirect:/change-password";
        }

        if (!newPassword.matches(".*[a-z].*")) {
            log.warn("Password missing lowercase letter for user: {}", email);
            redirectAttributes.addFlashAttribute("error", "Пароль должен содержать хотя бы одну строчную букву");
            return "redirect:/change-password";
        }

        if (!newPassword.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) {
            log.warn("Password missing special character for user: {}", email);
            redirectAttributes.addFlashAttribute("error", "Пароль должен содержать хотя бы один специальный символ");
            return "redirect:/change-password";
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            log.error("User not found in database: {}", email);
            redirectAttributes.addFlashAttribute("error", "Пользователь не найден");
            return "redirect:/login";
        }

        User user = userOpt.get();

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            log.warn("Current password mismatch for user: {}", email);
            redirectAttributes.addFlashAttribute("error", "Текущий пароль указан неверно");
            return "redirect:/change-password";
        }

        String encodedNewPassword = passwordEncoder.encode(newPassword);
        user.setPassword(encodedNewPassword);

        user.setTemporaryPassword(false);
        user.setTemporaryPasswordExpiresAt(null);
        user.setPasswordExpiresAt(LocalDateTime.now().plusMonths(3));

        userRepository.save(user);

        log.info("Password changed successfully for user: {}", email);
        redirectAttributes.addFlashAttribute("success", "Пароль успешно изменён. Пожалуйста, войдите снова.");
        return "redirect:/login?success=true";
    }
}