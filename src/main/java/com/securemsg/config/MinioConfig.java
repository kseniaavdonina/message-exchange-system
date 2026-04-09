package com.securemsg.config;

import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация MinIO клиента для работы с S3-совместимым объектным хранилищем.
 * Используется для хранения файловых вложений писем.
 *
 * Настройки берутся из переменных окружения:
 * <ul>
 *     <li>MINIO_URL - URL MinIO сервера (по умолчанию: http://localhost:9000)</li>
 *     <li>MINIO_ACCESS_KEY - ключ доступа (по умолчанию: minioadmin)</li>
 *     <li>MINIO_SECRET_KEY - секретный ключ (по умолчанию: minioadmin123)</li>
 * </ul>
 */
@Configuration
public class MinioConfig {

    private static final Logger log = LoggerFactory.getLogger(MinioConfig.class);

    @Value("${MINIO_URL:http://localhost:9000}")
    private String url;

    @Value("${MINIO_ACCESS_KEY:minioadmin}")
    private String accessKey;

    @Value("${MINIO_SECRET_KEY:minioadmin123}")
    private String secretKey;

    /**
     * Создаёт и настраивает клиент MinIO для взаимодействия с S3-хранилищем.
     *
     * @return настроенный MinioClient
     */
    @Bean
    public MinioClient minioClient() {
        log.info("Creating MinIO client: {}", url);
        log.debug("Access key: {}", accessKey);

        MinioClient client = MinioClient.builder()
                .endpoint(url)
                .credentials(accessKey, secretKey)
                .build();

        log.info("MinIO client created successfully");
        return client;
    }
}