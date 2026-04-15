# ============================================================================
# scale.ps1 - Скрипт автоматического масштабирования контейнеров приложения
# ============================================================================
# Описание:
#   - Слушает Redis-канал scale-channel для команд CREATE
#   - Мониторит загрузку контейнеров через API
#   - Автоматически создаёт новые контейнеры при достижении лимита (100 пользователей)
#   - Удаляет пустые контейнеры (кроме app-1)
#   - Очищает "фантомные" контейнеры в Redis
#
# Использование:
#   ./scale.ps1
# ============================================================================

# ========== ЗАГОЛОВОК ==========
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "    SESSION AUTO-SCALING" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "[SCALER] Starting Redis listener..." -ForegroundColor Cyan

# ========== ФОНТОВЫЙ СЛУШАТЕЛЬ REDIS ==========
$job = Start-Job -Name RedisListener -ScriptBlock {
    docker exec -i securemsg-redis redis-cli --raw SUBSCRIBE scale-channel | ForEach-Object {
        if ($_ -match "CREATE:(app-\d+)") {
            $containerName = $matches[1]
            $id = $containerName -replace 'app-', ''
            $port = 8080 + $id
            $dockerName = "securemsg-app-$id"

            Write-Host "[SCALER] 🚀 Creating $dockerName..."

            docker run -d `
              --name $dockerName `
              --network securemsg-network `
              -p ${port}:8081 `
              --restart unless-stopped `
              --memory="512M" `
              --memory-reservation="256M" `
              -e "SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/securemsg_db" `
              -e "SPRING_DATASOURCE_USERNAME=postgres" `
              -e "SPRING_DATASOURCE_PASSWORD=postgres" `
              -e "MINIO_URL=http://minio:9000" `
              -e "MINIO_ACCESS_KEY=minioadmin" `
              -e "MINIO_SECRET_KEY=minioadmin123" `
              -e "MINIO_BUCKET=securemsg-attachments" `
              -e "SPRING_ACTIVEMQ_BROKER_URL=tcp://activemq:61616" `
              -e "SPRING_ACTIVEMQ_USER=admin" `
              -e "SPRING_ACTIVEMQ_PASSWORD=admin" `
              -e "INSTANCE_ID=$id" `
              -e "INSTANCE_NAME=app-$id" `
              -e "JAVA_OPTS=-Xmx512m -Xms256m -XX:+UseG1GC" `
              -e "SPRING_PROFILES_ACTIVE=docker" `
              securemsg-app-1

            Write-Host "[SCALER] ✅ $containerName created!"
        }
    }
}

Write-Host "[SCALER] ✅ Redis listener started in background" -ForegroundColor Green

# ========== ОСНОВНАЯ ЛОГИКА МАСШТАБИРОВАНИЯ ==========
try {
    # Получаем статистику контейнеров через API
    $response = Invoke-RestMethod -Uri "http://localhost:8081/api/session/containers" -Method Get -ErrorAction Stop
    $stats = $response

    Write-Host "[INFO] Current load (from Redis):" -ForegroundColor Yellow
    $totalUsers = 0
    $stats.PSObject.Properties | Sort-Object Name | ForEach-Object {
        Write-Host "   $($_.Name): $($_.Value)/100 users"
        $totalUsers += [int]$_.Value
    }

    # Получаем запущенные контейнеры из Docker
    $realContainers = docker ps --filter "name=securemsg-app" --format "{{.Names}}" | ForEach-Object { $_ -replace 'securemsg-', '' }

    Write-Host "[INFO] Running containers (Docker):" -ForegroundColor Yellow
    if ($realContainers) {
        $realContainers | ForEach-Object { Write-Host "   $_" }
    } else {
        Write-Host "   (none)" -ForegroundColor Gray
    }

    # ========== ОЧИСТКА ФАНТОМНЫХ КОНТЕЙНЕРОВ ==========
    $phantomContainers = @()
    $stats.PSObject.Properties | ForEach-Object {
        $containerName = $_.Name
        if ($realContainers -notcontains "securemsg-$containerName") {
            $phantomContainers += $containerName
        }
    }

    if ($phantomContainers.Count -gt 0) {
        Write-Host "[CLEAN] 👻 Phantom containers in Redis: $($phantomContainers -join ', ')" -ForegroundColor Magenta

        foreach ($container in $phantomContainers) {
            Write-Host "   Removing $container from Redis..." -ForegroundColor Yellow
            docker exec securemsg-redis redis-cli SREM active_containers $container
            docker exec securemsg-redis redis-cli DEL "container:$container"
        }

        Write-Host "[CLEAN] ✅ Phantom containers removed from Redis" -ForegroundColor Green
    }

    # ========== ПРОВЕРКА НАЛИЧИЯ СВОБОДНЫХ МЕСТ ==========
    $freeContainerExists = $false
    $stats.PSObject.Properties | ForEach-Object {
        if ([int]$_.Value -lt 100 -and $realContainers -contains "securemsg-$($_.Name)") {
            $freeContainerExists = $true
        }
    }

    # ========== ПРОВЕРКА НЕОБХОДИМОСТИ НОВОГО КОНТЕЙНЕРА ==========
    $needNew = $false
    if (-not $freeContainerExists -and $totalUsers -gt 0) {
        Write-Host "[SCALE] ⚠️ All containers full! Need new one." -ForegroundColor Yellow
        $needNew = $true
    }

    # ========== СОЗДАНИЕ НОВОГО КОНТЕЙНЕРА ==========
    if ($needNew) {
        $nextId = $stats.PSObject.Properties.Count + 1
        Write-Host "[SCALE] 🚀 Creating app-$nextId..." -ForegroundColor Green

        $NEW_PORT = 8080 + $nextId
        $NEW_NAME = "securemsg-app-$nextId"

        docker run -d `
          --name $NEW_NAME `
          --network securemsg-network `
          -p ${NEW_PORT}:8081 `
          --restart unless-stopped `
          --memory="512M" `
          --memory-reservation="256M" `
          -e "SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/securemsg_db" `
          -e "SPRING_DATASOURCE_USERNAME=postgres" `
          -e "SPRING_DATASOURCE_PASSWORD=postgres" `
          -e "MINIO_URL=http://minio:9000" `
          -e "MINIO_ACCESS_KEY=minioadmin" `
          -e "MINIO_SECRET_KEY=minioadmin123" `
          -e "MINIO_BUCKET=securemsg-attachments" `
          -e "SPRING_ACTIVEMQ_BROKER_URL=tcp://activemq:61616" `
          -e "SPRING_ACTIVEMQ_USER=admin" `
          -e "SPRING_ACTIVEMQ_PASSWORD=admin" `
          -e "INSTANCE_ID=$nextId" `
          -e "INSTANCE_NAME=app-$nextId" `
          -e "JAVA_OPTS=-Xmx512m -Xms256m -XX:+UseG1GC" `
          -e "SPRING_PROFILES_ACTIVE=docker" `
          securemsg-app-1

        Write-Host "[SCALE] ✅ app-$nextId created!" -ForegroundColor Green

        # Перезагружаем Nginx, чтобы он подхватил новый контейнер
        Write-Host "[SCALE] 🔄 Reloading Nginx to pick up new container..." -ForegroundColor Yellow
        docker exec securemsg-nginx nginx -s reload
        Write-Host "[SCALE] ✅ Nginx reloaded" -ForegroundColor Green
    }

    # ========== УДАЛЕНИЕ ПУСТЫХ КОНТЕЙНЕРОВ ==========
    $emptyContainers = @()
    $stats.PSObject.Properties | ForEach-Object {
        if ([int]$_.Value -eq 0 -and $_.Name -ne "app-1") {
            if ($realContainers -contains $_.Name) {
                $emptyContainers += $_.Name
            }
        }
    }

    if ($emptyContainers.Count -gt 0) {
        Write-Host "[SCALE] 🗑️ Empty containers: $($emptyContainers -join ', ')" -ForegroundColor Yellow

        foreach ($container in $emptyContainers) {
            $id = $container -replace 'app-', ''
            Write-Host "   Removing $container..." -ForegroundColor Red
            docker stop "securemsg-app-$id" 2>$null
            docker rm "securemsg-app-$id" 2>$null
            docker exec securemsg-redis redis-cli SREM active_containers $container
            docker exec securemsg-redis redis-cli DEL "container:$container"
        }

        Write-Host "[SCALE] ✅ Empty containers removed" -ForegroundColor Green

        # Перезагружаем Nginx после удаления контейнера
        Write-Host "[SCALE] 🔄 Reloading Nginx to update upstream..." -ForegroundColor Yellow
        docker exec securemsg-nginx nginx -s reload
        Write-Host "[SCALE] ✅ Nginx reloaded" -ForegroundColor Green
    }

    if (-not $needNew -and $emptyContainers.Count -eq 0 -and $phantomContainers.Count -eq 0) {
        Write-Host "[SCALE] ✅ All good, scaling not needed" -ForegroundColor Green
    }

} catch {
    Write-Host "[ERROR] Error: $_" -ForegroundColor Red
    Write-Host "   Check that Redis and app-1 are running" -ForegroundColor Yellow
    Write-Host "   Make sure API endpoint /api/session/containers is accessible" -ForegroundColor Yellow
}

# ========== ПОДДЕРЖАНИЕ СКРИПТА В РАБОТЕ ==========
while ($true) {
    Start-Sleep -Seconds 60
}
