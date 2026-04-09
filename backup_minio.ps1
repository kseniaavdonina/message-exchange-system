# ============================================================================
# backup_minio.ps1 - Скрипт резервного копирования MinIO (S3-хранилища)
# ============================================================================
# Описание:
#   Копирует все файлы из Docker-контейнера securemsg-minio в локальную папку
#   Удаляет бэкапы старше 7 дней
#
# Использование:
#   ./backup_minio.ps1
# ============================================================================

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$BackupDir = Join-Path $ScriptDir "backups\minio"

$Date = Get-Date -Format "yyyyMMdd_HHmmss"
$ContainerName = "securemsg-minio"

if (-not (Test-Path $BackupDir)) {
    New-Item -ItemType Directory -Path $BackupDir -Force
}

$TempBackup = Join-Path $BackupDir "minio_backup_$Date"
New-Item -ItemType Directory -Path $TempBackup -Force

docker cp "$ContainerName`:/data/." "$TempBackup\"

Get-ChildItem -Path $BackupDir -Directory | Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-7) } | Remove-Item -Recurse -Force

Write-Host "MinIO backup completed: minio_backup_$Date"
Write-Host "Backup saved to: $TempBackup"