package com.securemsg.service;

import io.minio.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.util.UUID;

/**
 * Сервис для работы с S3-совместимым объектным хранилищем (MinIO).
 * Обеспечивает:
 * <ul>
 *     <li>Создание bucket при первом запуске</li>
 *     <li>Загрузку файлов в хранилище</li>
 *     <li>Скачивание файлов из хранилища</li>
 *     <li>Удаление файлов из хранилища</li>
 * </ul>
 */
@Service
public class S3StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

    @Autowired
    private MinioClient minioClient;

    @Value("${MINIO_BUCKET:securemsg-attachments}")
    private String bucketName;

    /**
     * Инициализация bucket при старте приложения.
     * Проверяет существование bucket и создаёт его при необходимости.
     */
    @PostConstruct
    public void init() {
        try {
            log.info("Initializing MinIO bucket: {}", bucketName);

            boolean found = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build()
            );

            if (!found) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucketName).build()
                );
                log.info("✅ Bucket created: {}", bucketName);
            } else {
                log.debug("Bucket already exists: {}", bucketName);
            }

        } catch (Exception e) {
            log.error("❌ MinIO init error: {}", e.getMessage(), e);
        }
    }

    /**
     * Загружает файл в MinIO.
     * Генерирует уникальное имя файла с помощью UUID.
     *
     * @param file загружаемый файл
     * @return уникальное имя файла в хранилище (s3ObjectKey)
     * @throws Exception в случае ошибки загрузки
     */
    public String uploadFile(MultipartFile file) throws Exception {
        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        log.debug("Uploading file: {}", fileName);

        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(fileName)
                        .stream(file.getInputStream(), file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build()
        );

        log.debug("File uploaded successfully: {}", fileName);
        return fileName;
    }

    /**
     * Скачивает файл из MinIO.
     *
     * @param objectName имя файла в хранилище (s3ObjectKey)
     * @return InputStream для чтения файла
     * @throws Exception в случае ошибки скачивания
     */
    public InputStream downloadFile(String objectName) throws Exception {
        log.debug("Downloading file: {}", objectName);

        InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build()
        );

        log.debug("File downloaded successfully: {}", objectName);
        return stream;
    }

    /**
     * Удаляет файл из MinIO.
     *
     * @param objectName имя файла в хранилище (s3ObjectKey)
     * @throws Exception в случае ошибки удаления
     */
    public void deleteFile(String objectName) throws Exception {
        log.debug("Deleting file: {}", objectName);

        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build()
        );

        log.debug("File deleted successfully: {}", objectName);
    }
}