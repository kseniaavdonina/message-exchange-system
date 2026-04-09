package com.securemsg.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Кастомный обработчик отказа в доступе (403 Forbidden).
 * Перенаправляет пользователя на страницу /access-denied с сообщением об ошибке.
 * Используется Spring Security, когда у пользователя недостаточно прав.
 */
@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {
    private static final Logger log = LoggerFactory.getLogger(CustomAccessDeniedHandler.class);

    /**
     * Обрабатывает ситуацию, когда у пользователя нет прав для доступа к ресурсу.
     * Логирует попытку несанкционированного доступа и перенаправляет на страницу ошибки.
     *
     * @param request HTTP запрос
     * @param response HTTP ответ
     * @param accessDeniedException исключение о нарушении доступа
     * @throws IOException в случае ошибки ввода/вывода
     * @throws ServletException в случае ошибки сервлета
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {

        String requestURI = request.getRequestURI();
        String remoteAddr = request.getRemoteAddr();

        log.warn("Access denied for URI: {} from IP: {}", requestURI, remoteAddr);
        log.debug("Exception details: {}", accessDeniedException.getMessage());

        request.setAttribute("error", "У вас нет прав для доступа к этой странице");
        request.getRequestDispatcher("/access-denied").forward(request, response);
    }
}