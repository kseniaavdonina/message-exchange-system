package com.securemsg.handler;

import com.securemsg.service.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;

/**
 * Кастомный обработчик успешной аутентификации.
 * Выполняет:
 * <ul>
 *     <li>Привязку пользователя к контейнеру (SessionManager)</li>
 *     <li>Перенаправление в зависимости от роли пользователя</li>
 *     <li>Логирование успешного входа</li>
 * </ul>
 */
@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(CustomAuthenticationSuccessHandler.class);

    @Autowired
    private SessionManager sessionManager;

    /**
     * Обрабатывает успешный вход пользователя в систему.
     * Привязывает пользователя к контейнеру и перенаправляет:
     * <ul>
     *     <li>ADMIN → /admin/dashboard</li>
     *     <li>USER → /dashboard</li>
     * </ul>
     *
     * @param request HTTP запрос
     * @param response HTTP ответ
     * @param authentication объект аутентификации с данными пользователя
     * @throws IOException в случае ошибки ввода/вывода
     * @throws ServletException в случае ошибки сервлета
     */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        String username = authentication.getName();
        log.info("=== LOGIN SUCCESS ===");
        log.info("User: {}", username);

        // Привязываем пользователя к контейнеру
        String container = sessionManager.assignUserToContainer(username);
        log.info("✅ User {} assigned to container: {}", username, container);

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        log.debug("Authorities: {}", authorities);

        boolean isAdmin = authorities.stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        log.info("Is admin: {}", isAdmin);

        String redirectUrl;
        if (isAdmin) {
            redirectUrl = "/admin/dashboard";
            log.info("Redirecting admin to: {}", redirectUrl);
        } else {
            redirectUrl = "/dashboard";
            log.info("Redirecting user to: {}", redirectUrl);
        }

        response.sendRedirect(redirectUrl);
    }
}