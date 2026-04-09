package com.securemsg.config;

import com.securemsg.filter.ApiKeyFilter;
import com.securemsg.handler.CustomAuthenticationSuccessHandler;
import com.securemsg.handler.CustomLogoutSuccessHandler;
import com.securemsg.service.CustomUserDetailsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Конфигурация безопасности Spring Security.
 * Настраивает:
 * <ul>
 *     <li>Аутентификацию пользователей по email и паролю</li>
 *     <li>Авторизацию с разделением на роли (USER, ADMIN)</li>
 *     <li>Доступ к URL в зависимости от роли</li>
 *     <li>Форму входа и выхода</li>
 *     <li>CSRF защиту</li>
 *     <li>Фильтр для API-ключей</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private CustomAuthenticationSuccessHandler successHandler;

    @Autowired
    @Lazy
    private ApiKeyFilter apiKeyFilter;

    @Autowired
    private CustomLogoutSuccessHandler logoutSuccessHandler;

    /**
     * Создаёт кодировщик паролей BCrypt.
     * Используется для безопасного хэширования паролей пользователей.
     *
     * @return PasswordEncoder на основе BCrypt
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        log.debug("Creating BCryptPasswordEncoder");
        return new BCryptPasswordEncoder();
    }

    /**
     * Создаёт провайдер аутентификации с использованием кастомного UserDetailsService
     * и BCrypt кодировщика паролей.
     *
     * @return DaoAuthenticationProvider
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        log.debug("Creating DaoAuthenticationProvider");

        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /**
     * Настраивает менеджер аутентификации с использованием кастомного провайдера.
     */
    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        log.debug("Configuring AuthenticationManagerBuilder");
        auth.authenticationProvider(authenticationProvider());
    }

    /**
     * Основная конфигурация безопасности HTTP.
     * Определяет правила доступа к URL, настройки формы входа, выхода и CSRF.
     *
     * @param http объект HttpSecurity для настройки
     * @throws Exception в случае ошибки конфигурации
     */
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        log.info("=== CONFIGURING HTTP SECURITY ===");

        // Добавляем фильтр для API-ключей перед стандартной аутентификацией
        http.addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class);
        log.debug("ApiKeyFilter registered before UsernamePasswordAuthenticationFilter");

        http
                // CSRF настройки
                .csrf()
                    .ignoringAntMatchers("/api/v1/**", "/admin/users/reset-password/**")
                    .and()

                // Правила авторизации
                .authorizeRequests()

                // Публичные страницы
                    .antMatchers("/", "/login", "/error").permitAll()
                    .antMatchers("/css/**", "/js/**", "/images/**", "/videos/**").permitAll()
                    .antMatchers("/instance-info").permitAll()
                    .antMatchers("/api/v1/**").permitAll()

                    // Административные страницы (только ADMIN)
                    .antMatchers("/admin/**").hasRole("ADMIN")
                    //.antMatchers("/admin/**").permitAll()

                    // Страница смены пароля (только аутентифицированные)
                    .antMatchers("/change-password").authenticated()

                    // Отладочная страница (только ADMIN для безопасности)
                    .antMatchers("/debug").hasRole("ADMIN")

                    // Пользовательские страницы (только аутентифицированные)
                    .antMatchers("/dashboard", "/compose", "/send", "/email/**",
                            "/download/**", "/queue/**", "/stats").authenticated()

                    // Все остальные запросы требуют аутентификации
                    .anyRequest().authenticated()
                    .and()

                // Настройка формы входа
                .formLogin()
                    .loginPage("/login")
                    .loginProcessingUrl("/login")
                    .usernameParameter("username")
                    .passwordParameter("password")
                    .successHandler(successHandler)
                    .failureUrl("/login?error=true")
                    .permitAll()
                    .and()

                // Настройка выхода
                .logout()
                    .logoutUrl("/logout")
                    .logoutSuccessHandler(logoutSuccessHandler)
                    .logoutSuccessUrl("/login?logout=true")
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID")
                    .permitAll()
                    .and()

                // Обработка ошибок доступа
                .exceptionHandling()
                    .accessDeniedPage("/access-denied");
        log.info("HTTP Security configured successfully");
        log.debug("Access rules:");
        log.debug("  - /admin/** : ADMIN only");
        log.debug("  - /debug : ADMIN only");
        log.debug("  - /change-password : authenticated users");
        log.debug("  - /dashboard, /compose, /send, /email/**, /download/**, /queue/**, /stats : authenticated users");
        log.debug("  - /api/v1/** : public (with API key filter)");
        log.debug("  - /, /login, /error, /instance-info, static resources : public");
    }
}