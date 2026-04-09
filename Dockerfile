# ============================================================================
# Dockerfile для сборки образа приложения РИТМ (Secure Message System)
# ============================================================================
# Основа: Eclipse Temurin 17 (OpenJDK 17) на Alpine Linux
# ============================================================================

FROM eclipse-temurin:17-jre-alpine

# ========== РАБОЧАЯ ДИРЕКТОРИЯ ==========
WORKDIR /app

# ========== КОПИРОВАНИЕ JAR-ФАЙЛА ==========
# Копируем собранный jar-файл из target-директории
COPY target/*.jar app.jar

# ========== ОТЛАДКА (проверка содержимого jar) ==========
# Проверяем, что jar содержит шаблоны Thymeleaf (опционально)
RUN jar tf app.jar | grep -q "templates/" && echo "✅ Templates found in jar" || echo "⚠️ No templates found in jar"

# ========== ЗАПУСК ПРИЛОЖЕНИЯ ==========
# JAVA_OPTS можно передать через docker run -e "JAVA_OPTS=..."
# Пример: -Xmx512m -Xms256m -XX:+UseG1GC
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# ========== ПРОБРОС ПОРТА ==========
EXPOSE 8081