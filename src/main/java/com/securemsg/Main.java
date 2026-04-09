package com.securemsg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Главный класс приложения Secure Message System.
 * Точка входа в Spring Boot приложение.
 */
@SpringBootApplication
public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    /**
     * Главный метод, запускающий Spring Boot приложение.
     *
     * @param args аргументы командной строки
     */
    public static void main(String[] args) {
        log.info("=== Starting Secure Message Application ===");
        SpringApplication.run(Main.class, args);
        log.info("=== Secure Message Application started successfully ===");
    }
}