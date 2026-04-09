# ============================================================================
# backup.ps1 - Скрипт резервного копирования базы данных PostgreSQL
# ============================================================================
# Описание:
#   Создаёт дамп базы данных securemsg_db из Docker-контейнера securemsg-postgres
#   Удаляет бэкапы старше 7 дней
#
# Использование:
#   ./backup.ps1
# ============================================================================

# ========== НАСТРОЙКИ ==========
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$BackupDir = Join-Path $ScriptDir "backups\postgres"

$Date = Get-Date -Format "yyyyMMdd_HHmmss"
$DbName = "securemsg_db"
$DbUser = "postgres"
$ContainerName = "securemsg-postgres"

# ========== СОЗДАНИЕ ПАПКИ ДЛЯ БЭКАПОВ ==========
if (-not (Test-Path $BackupDir)) {
    New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
    Write-Host "Folder created: $BackupDir" -ForegroundColor Yellow
}

# ========== СОЗДАНИЕ БЭКАПА ==========
Write-Host "Creating backup of database $DbName..." -ForegroundColor Cyan

try {
    docker exec $ContainerName pg_dump -U $DbUser $DbName > "$BackupDir\backup_$Date.sql"
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Backup created: backup_$Date.sql" -ForegroundColor Green
    } else {
        Write-Host "Error creating backup" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "Error: $_" -ForegroundColor Red
    exit 1
}

# ========== УДАЛЕНИЕ СТАРЫХ БЭКАПОВ (СТАРШЕ 7 ДНЕЙ) ==========
$oldBackups = Get-ChildItem -Path $BackupDir -Filter "backup_*.sql" | Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-7) }

if ($oldBackups.Count -gt 0) {
    Write-Host "Deleting old backups (older than 7 days):" -ForegroundColor Yellow
    $oldBackups | ForEach-Object {
        Remove-Item $_.FullName
        Write-Host "   Deleted: $($_.Name)" -ForegroundColor Gray
    }
} else {
    Write-Host "No old backups found" -ForegroundColor Gray
}

Write-Host "Backup completed!" -ForegroundColor Green