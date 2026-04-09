package com.securemsg.filter;

import com.securemsg.service.ApiKeyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Фильтр для аутентификации API-запросов по ключу.
 * Проверяет наличие и валидность API-ключа в заголовке X-API-Key.
 * Эндпоинт /api/v1/health доступен без ключа.
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyFilter.class);

    @Autowired
    private ApiKeyService apiKeyService;

    /**
     * Основной метод фильтрации запросов.
     * Проверяет API-ключ для всех запросов к /api/v1/*, кроме /api/v1/health.
     *
     * @param request HTTP запрос
     * @param response HTTP ответ
     * @param filterChain цепочка фильтров
     * @throws ServletException в случае ошибки сервлета
     * @throws IOException в случае ошибки ввода/вывода
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        log.debug("ApiKeyFilter processing request: {}", path);

        // Health check доступен без ключа
        if (path.equals("/api/v1/health")) {
            log.debug("Health check endpoint - skipping API key validation");
            filterChain.doFilter(request, response);
            return;
        }

        // Остальные API-запросы требуют ключ
        if (path.startsWith("/api/v1/")) {
            String apiKey = request.getHeader("X-API-Key");

            if (apiKey == null || apiKey.isEmpty()) {
                log.warn("API key missing for request: {}", path);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"API key is missing. Please provide X-API-Key header.\"}");
                return;
            }

            boolean isValid = apiKeyService.validateKey(apiKey).isPresent();

            if (!isValid) {
                log.warn("Invalid or expired API key for request: {}", path);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Invalid or expired API key.\"}");
                return;
            }
            log.debug("API key validated successfully for request: {}", path);
        }

        filterChain.doFilter(request, response);
    }
}