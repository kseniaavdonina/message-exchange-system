package com.securemsg.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericToStringSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Конфигурация Redis для работы с кэшем и сессиями.
 * Настраивает RedisTemplate для хранения и извлечения данных.
 */
@Configuration
public class RedisConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    /**
     * Создаёт и настраивает RedisTemplate для работы с Redis.
     * Использует StringRedisSerializer для ключей и GenericToStringSerializer для значений.
     *
     * @param connectionFactory фабрика соединений с Redis
     * @return настроенный RedisTemplate
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        log.info("Creating RedisTemplate");
        log.debug("Connection factory: {}", connectionFactory.getClass().getSimpleName());

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Настройка сериализаторов
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericToStringSerializer<>(Object.class));
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericToStringSerializer<>(Object.class));

        template.afterPropertiesSet();

        log.info("RedisTemplate created and configured successfully");
        return template;
    }
}