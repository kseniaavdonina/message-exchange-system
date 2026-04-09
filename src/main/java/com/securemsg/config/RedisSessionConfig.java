package com.securemsg.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * Конфигурация Redis для хранения HTTP-сессий.
 * Включает поддержку Spring Session с Redis.
 * Позволяет распределять сессии между несколькими экземплярами приложения.
 */
@Configuration
@EnableRedisHttpSession
public class RedisSessionConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisSessionConfig.class);

    /**
     * Создаёт фабрику соединений с Redis для хранения сессий.
     * Использует Lettuce-клиент для подключения.
     *
     * @return RedisConnectionFactory для подключения к Redis
     */
    @Bean
    public RedisConnectionFactory connectionFactory() {
        log.info("Creating Redis connection factory for sessions");
        log.debug("Redis host: redis, port: 6379");

        LettuceConnectionFactory factory = new LettuceConnectionFactory("redis", 6379);

        log.info("Redis connection factory created successfully");
        return factory;
    }
}