package com.securemsg.service;

import com.securemsg.entity.Role;
import com.securemsg.entity.User;
import com.securemsg.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Кастомная реализация UserDetailsService для Spring Security.
 * Загружает данные пользователя из базы данных по email.
 * Назначает роли пользователя для авторизации.
 */
@Service
@Transactional
public class CustomUserDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(CustomUserDetailsService.class);

    @Autowired
    private UserRepository userRepository;

    /**
     * Загружает пользователя по email для аутентификации.
     *
     * @param email email пользователя (используется как логин)
     * @return UserDetails для Spring Security
     * @throws UsernameNotFoundException если пользователь не найден или отключён
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        log.info("=== AUTHENTICATION ATTEMPT ===");
        log.info("Email: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("User not found: {}", email);
                    return new UsernameNotFoundException("User not found with email: " + email);
                });

        log.info("User found: {}", user.getEmail());
        log.debug("Password hash: {}", user.getPassword());
        log.info("Enabled: {}", user.isEnabled());

        if (!user.isEnabled()) {
            log.warn("User is disabled: {}", email);
            throw new UsernameNotFoundException("User is disabled: " + email);
        }

        if (user.isPasswordExpired()) {
            log.warn("Password expired for user: {}", email);
            throw new UsernameNotFoundException("Срок действия пароля истёк. Обратитесь к администратору.");
        }

        List<GrantedAuthority> authorities = new ArrayList<>();

        Set<Role> roles = user.getRoles();
        if (roles != null && !roles.isEmpty()) {
            for (Role role : roles) {
                authorities.add(new SimpleGrantedAuthority(role.getName()));
                log.debug("Granted role from DB: {}", role.getName());
            }
        } else {
            log.warn("No roles in DB for user: {}, using hardcoded check", email);
            if (isAdminByEmail(user)) {
                authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                log.debug("Granted role from hardcode: ROLE_ADMIN");
            } else {
                authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                log.debug("Granted role from hardcode: ROLE_USER");
            }
        }

        log.info("Authentication successful for user: {}", email);
        log.debug("Authorities: {}", authorities);

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                user.isEnabled(),
                true, true, true,
                authorities
        );
    }

    /**
     * Запасной метод для хардкода (используется, если у пользователя нет ролей в БД).
     * Определяет, является ли пользователь администратором по email.
     *
     * @param user объект пользователя
     * @return true, если пользователь имеет права администратора
     */
    private boolean isAdminByEmail(User user) {
        String email = user.getEmail();
        return email.equals("admin@company.org") ||
                email.equals("security@system.com");
    }
}