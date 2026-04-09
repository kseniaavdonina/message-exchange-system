package com.securemsg.handler;

import com.securemsg.service.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Кастомный обработчик успешного выхода из системы.
 * Выполняет:
 * <ul>
 *     <li>Удаление пользователя из контейнера (SessionManager)</li>
 *     <li>Перенаправление на страницу входа с параметром logout</li>
 * </ul>
 */
@Component
public class CustomLogoutSuccessHandler implements LogoutSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(CustomLogoutSuccessHandler.class);

    @Autowired
    private SessionManager sessionManager;

    /**
     * Обрабатывает успешный выход пользователя из системы.
     * Удаляет пользователя из контейнера и перенаправляет на страницу входа.
     *
     * @param request HTTP запрос
     * @param response HTTP ответ
     * @param authentication объект аутентификации (может быть null, если сессия уже недействительна)
     * @throws IOException в случае ошибки ввода/вывода
     * @throws ServletException в случае ошибки сервлета
     */
    @Override
    public void onLogoutSuccess(HttpServletRequest request,
                                HttpServletResponse response,
                                Authentication authentication) throws IOException, ServletException {

        log.info("=== LOGOUT SUCCESS ===");

        if (authentication != null) {
            String username = authentication.getName();
            log.info("User logging out: {}", username);

            sessionManager.removeUserFromContainer(username);
            log.info("👋 User {} removed from container on logout", username);
        } else {
            log.debug("Authentication is null, no user to remove from container");
        }

        log.info("Redirecting to login page");
        response.sendRedirect("/login?logout=true");
    }
}