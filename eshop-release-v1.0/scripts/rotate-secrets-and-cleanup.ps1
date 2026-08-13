#Requires -Version 5.1
<#
.SYNOPSIS
  验收前运维脚本：轮换 JWT/MySQL/管理员密码，关闭 bootstrap，下架测试商品。

.DESCRIPTION
  在已运行 docker compose 的机器上执行。会：
  1. 生成新密钥并写回 .env（BOOTSTRAP_ADMIN_ENABLED=false）
  2. 在 MySQL 中 ALTER 用户密码、更新 admin 的 BCrypt 密码
  3. 下架名称匹配测试/Smoke 等模式的在售商品
  4. 重建 backend 容器使 JWT/DB 密码生效

  新口令会写入 .secrets-rotated.txt（已 gitignore），请线下妥善保管。
#>
param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string]$ComposeProject = "eshop-release-v10",
    [string]$MysqlContainer = "",
    [string]$OldMysqlRootPassword = ""
)

$ErrorActionPreference = "Stop"
Set-Location $ProjectRoot

function New-AlnumPassword([int]$Len = 28) {
    $chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $bytes = New-Object byte[] $Len
    $rng.GetBytes($bytes)
    -join ($bytes | ForEach-Object { $chars[$_ % $chars.Length] })
}

function Get-EnvMap([string]$Path) {
    $map = @{}
    if (-not (Test-Path $Path)) { return $map }
    Get-Content $Path | ForEach-Object {
        if ($_ -match '^\s*#' -or $_ -notmatch '=') { return }
        $i = $_.IndexOf('=')
        $k = $_.Substring(0, $i).Trim()
        $v = $_.Substring($i + 1)
        $map[$k] = $v
    }
    return $map
}

$envPath = Join-Path $ProjectRoot ".env"
if (-not (Test-Path $envPath)) {
    throw ".env not found. Copy .env.example to .env first."
}

$old = Get-EnvMap $envPath
if (-not $OldMysqlRootPassword) {
    $OldMysqlRootPassword = $old["MYSQL_ROOT_PASSWORD"]
}
if (-not $OldMysqlRootPassword) {
    throw "Provide -OldMysqlRootPassword or set MYSQL_ROOT_PASSWORD in current .env"
}

if (-not $MysqlContainer) {
    $MysqlContainer = docker ps --format "{{.Names}}" | Where-Object { $_ -match "mysql" } | Select-Object -First 1
}
if (-not $MysqlContainer) {
    throw "MySQL container not found. Pass -MysqlContainer."
}

$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtBytes = New-Object byte[] 48
$rng.GetBytes($jwtBytes)
$jwtSecret = [Convert]::ToBase64String($jwtBytes)
$mysqlPass = New-AlnumPassword 28
$mysqlRoot = New-AlnumPassword 28
$adminPass = New-AlnumPassword 20

$py = @'
import bcrypt, sys
print(bcrypt.hashpw(sys.argv[1].encode(), bcrypt.gensalt(rounds=10)).decode())
'@
$pyPath = Join-Path $env:TEMP "eshop_bcrypt_hash.py"
Set-Content -Path $pyPath -Value $py -Encoding ASCII
python -m pip install bcrypt -q | Out-Null
$bcryptHash = (python $pyPath $adminPass).Trim()
if (-not $bcryptHash.StartsWith('$2')) {
    throw "Failed to generate BCrypt hash. Ensure Python + bcrypt is available."
}

$appPort = if ($old["APP_PORT"]) { $old["APP_PORT"] } else { "8088" }
$npm = if ($old["NPM_REGISTRY"]) { $old["NPM_REGISTRY"] } else { "https://registry.npmmirror.com" }
$db = if ($old["MYSQL_DATABASE"]) { $old["MYSQL_DATABASE"] } else { "eshop" }
$user = if ($old["MYSQL_USER"]) { $old["MYSQL_USER"] } else { "eshop" }
$cors = if ($old["CORS_ALLOWED_ORIGIN_PATTERNS"]) { $old["CORS_ALLOWED_ORIGIN_PATTERNS"] } else { "http://localhost:*,http://127.0.0.1:*" }
$tz = if ($old["TZ"]) { $old["TZ"] } else { "Asia/Shanghai" }

$nickname = ([string]([char]0x7CFB) + [char]0x7EDF + [char]0x7BA1 + [char]0x7406 + [char]0x5458)
$lines = @(
    "# Rotated $(Get-Date -Format o). Do not commit.",
    "APP_PORT=$appPort",
    "NPM_REGISTRY=$npm",
    "MYSQL_DATABASE=$db",
    "MYSQL_USER=$user",
    "MYSQL_PASSWORD=$mysqlPass",
    "MYSQL_ROOT_PASSWORD=$mysqlRoot",
    "TZ=$tz",
    "JWT_SECRET=$jwtSecret",
    "CORS_ALLOWED_ORIGIN_PATTERNS=$cors",
    "BOOTSTRAP_ADMIN_ENABLED=false",
    "BOOTSTRAP_ADMIN_USERNAME=admin",
    "BOOTSTRAP_ADMIN_PASSWORD=$adminPass",
    "BOOTSTRAP_ADMIN_NICKNAME=$nickname",
    "ORDER_TIMEOUT_ENABLED=true",
    "ORDER_TIMEOUT_MINUTES=30",
    "ORDER_TIMEOUT_BATCH_SIZE=100",
    "ORDER_TIMEOUT_INITIAL_DELAY_MS=15000",
    "ORDER_TIMEOUT_SCAN_DELAY_MS=60000"
)
$utf8 = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllLines($envPath, $lines, $utf8)

$secretNote = Join-Path $ProjectRoot ".secrets-rotated.txt"
@"
# Generated $(Get-Date -Format o) - DO NOT COMMIT
JWT_SECRET=$jwtSecret
MYSQL_PASSWORD=$mysqlPass
MYSQL_ROOT_PASSWORD=$mysqlRoot
BOOTSTRAP_ADMIN_PASSWORD=$adminPass
ADMIN_BCRYPT=$bcryptHash
"@ | Set-Content -Path $secretNote -Encoding UTF8

Write-Host "Off-saling test products and rotating DB credentials..."
$sql = @"
UPDATE product
SET status = 'OFF_SALE', updated_at = CURRENT_TIMESTAMP
WHERE status = 'ON_SALE'
  AND (
       name LIKE '%测试%'
    OR name LIKE '%Test%'
    OR name LIKE '%test%'
    OR name LIKE '%TEST%'
    OR name LIKE '%Smoke%'
    OR name LIKE '%smoke%'
    OR name LIKE 'Smoke Product%'
    OR name LIKE '%乱码%'
    OR name LIKE '%placeholder%'
    OR name LIKE '%PLACEHOLDER%'
    OR name LIKE '%临时%'
    OR name LIKE '%demo-test%'
  );
UPDATE sys_user SET password_hash='$bcryptHash' WHERE username='admin';
ALTER USER 'eshop'@'%' IDENTIFIED BY '$mysqlPass';
ALTER USER 'root'@'%' IDENTIFIED BY '$mysqlRoot';
ALTER USER 'root'@'localhost' IDENTIFIED BY '$mysqlRoot';
FLUSH PRIVILEGES;
SELECT COUNT(*) AS on_sale FROM product WHERE status='ON_SALE';
"@

docker exec -i $MysqlContainer mysql -uroot "-p$OldMysqlRootPassword" --default-character-set=utf8mb4 $db -e $sql

Write-Host "Recreating backend with new JWT/MySQL password..."
docker compose -p $ComposeProject up -d --force-recreate --no-deps backend

Write-Host ""
Write-Host "Done."
Write-Host "New secrets saved to: $secretNote"
Write-Host "Admin username: admin"
Write-Host "Admin password: $adminPass"
Write-Host "BOOTSTRAP_ADMIN_ENABLED=false"
Write-Host "Remember: all existing JWT sessions are invalid after backend restart."
