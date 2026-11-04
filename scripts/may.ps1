# =============================================================================
# Demo nhiều máy: chạy phần của MÁY NÀY (CSDL của cơ sở + api + web) — bản Windows của may.sh.
# Máy nào là cơ sở nào đọc từ dòng app.may-nay trong src/main/resources/application.yml; địa chỉ 4 máy đọc từ
# app.dia-chi-may (IP Tailscale).
#
# Cách dùng (PowerShell, trong thư mục backend):
#   .\scripts\may.ps1 up            build + chạy, chờ api sẵn sàng
#   .\scripts\may.ps1 kiem-tra      CSDL của máy này có gọi được CSDL 3 máy kia không (thử từ TRONG container)
#   .\scripts\may.ps1 down          dừng (giữ dữ liệu)     .\scripts\may.ps1 xoa-du-lieu   dừng và xoá dữ liệu
#   .\scripts\may.ps1 logs | ps
# Bị chặn chạy script: powershell -ExecutionPolicy Bypass -File .\scripts\may.ps1 up
# Khởi tạo dữ liệu cho cả 4 máy (init_may.sh) chạy từ một máy macOS / Linux, máy Windows không cần.
# Thông báo không dấu (như CSDL/init_db.ps1) để chạy được trên PowerShell 5.1.
# =============================================================================
param([Parameter(Position = 0)][string]$Lenh = '')

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

$Root = Join-Path $PSScriptRoot '..'
$Compose = Join-Path $Root 'docker-compose.may.yml'
$Yml = Join-Path $Root 'src\main\resources\application.yml'
$Mays = @('hq', 'cn1', 'cn2', 'cn3')
$TenMay = @{ hq = 'Trung tam (S0)'; cn1 = 'Cau Giay (S1)'; cn2 = 'Dong Da (S2)'; cn3 = 'Ba Dinh (S3)' }

function Ok([string]$m) { Write-Host " -> $m" -ForegroundColor Green }
function Bad([string]$m) { Write-Host " -> $m" -ForegroundColor Red }

# Giá trị của "  khoa: gia tri" (hoặc "    khoa:" trong khối cha) trong application.yml
function Yml([string]$Khoa, [string]$Cha = '') {
    $blk = $false
    foreach ($line in (Get-Content $Yml -Encoding UTF8)) {
        if ($Cha -eq '') {
            if ($line -match "^  $Khoa\s*:(.*)$") { return ($Matches[1] -replace '#.*$', '' -replace '["'' \t]', '') }
        } else {
            if ($line -match "^  $Cha\s*:") { $blk = $true; continue }
            if ($blk -and $line -match "^    $Khoa\s*:(.*)$") { return ($Matches[1] -replace '#.*$', '' -replace '["'' \t]', '') }
            if ($blk -and $line -match '^  [^ ]') { return '' }
        }
    }
    return ''
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { Bad 'Khong co lenh docker - hay cai / mo Docker Desktop.'; exit 1 }
docker info *> $null
if ($LASTEXITCODE -ne 0) { Bad 'Docker Desktop chua chay - mo Docker Desktop roi chay lai.'; exit 1 }
if (-not (Test-Path $Yml)) { Bad "Khong thay $Yml"; exit 1 }

$May = (Yml 'may-nay').ToLower()
if ($Mays -notcontains $May) {
    Bad "Chua chon may: sua dong 'may-nay:' trong src\main\resources\application.yml thanh hq, cn1, cn2 hoac cn3."
    exit 1
}
$Cong = Yml 'cong-may' 'db'
if ($Cong -eq '') { $Cong = '5432' }
$env:CONG_DB = $Cong
$WebPort = if ($env:WEB_PORT) { $env:WEB_PORT } else { '3000' }

function Compose { docker compose -f $Compose --profile $May @args }

switch ($Lenh) {
    'up' {
        Write-Host "`nMay nay: $($TenMay[$May]) - build + chay CSDL, api, web (lan dau mat vai phut)" -ForegroundColor Yellow
        foreach ($m in $Mays) {
            if ((Yml $m 'dia-chi-may') -eq '') { Bad "Thieu app.dia-chi-may.$m (IP Tailscale cua may $($TenMay[$m])) trong application.yml"; exit 1 }
        }
        Compose up -d --build
        if ($LASTEXITCODE -ne 0) { Bad "docker compose up loi - cong $Cong hoac $WebPort co bi chiem khong?"; exit 1 }
        $api = (Compose ps -q api | Select-Object -First 1)
        $st = ''
        for ($i = 0; $i -lt 90; $i++) {
            $st = docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $api 2> $null
            if ($st -in @('healthy', 'exited', 'dead')) { break }
            Start-Sleep -Seconds 2
        }
        if ($st -ne 'healthy') { Bad "api chua san sang ($st). 30 dong log cuoi:"; docker logs --tail 30 $api; exit 1 }
        Ok "San sang: http://localhost:$WebPort   (CSDL cua may nay mo cong $Cong cho 3 may kia)"
        Write-Host "    Kiem tra ket noi toi 3 may kia:  .\scripts\may.ps1 kiem-tra"
    }
    'kiem-tra' {
        Write-Host "`nMay nay: $($TenMay[$May]) - thu ket noi CSDL cac may khac tu trong container db-$May (cong $Cong)" -ForegroundColor Yellow
        $db = (Compose ps -q "db-$May" | Select-Object -First 1)
        if (-not $db) { Bad 'CSDL cua may nay chua chay: .\scripts\may.ps1 up'; exit 1 }
        $loi = 0
        foreach ($m in $Mays) {
            $a = Yml $m 'dia-chi-may'
            if ($m -eq $May) { Ok "$($TenMay[$m]) - chinh may nay ($a)"; continue }
            docker exec $db pg_isready -q -t 5 -h $a -p $Cong
            if ($LASTEXITCODE -eq 0) { Ok "$($TenMay[$m]) - ${a}:$Cong nhan ket noi" }
            else { Bad "$($TenMay[$m]) - ${a}:$Cong KHONG goi duoc"; $loi++ }
        }
        if ($loi -gt 0) {
            Write-Host @"

    Kiem tra lan luot:
      1. May kia da chay may.sh / may.ps1 up chua, Tailscale hai may co cung tailnet va dang bat khong (tailscale status).
      2. IP trong app.dia-chi-may dung chua (tren may kia: tailscale ip -4).
      3. Firewall cho phep cong $Cong vao. Windows (PowerShell Admin):
         New-NetFirewallRule -DisplayName 'AnTam CSDL' -Direction Inbound -Protocol TCP -LocalPort $Cong -Action Allow
      4. Container khong ra duoc mang Tailscale: them vao %UserProfile%\.wslconfig
           [wsl2]
           networkingMode=mirrored
         roi chay "wsl --shutdown" va mo lai Docker Desktop.
"@
            exit 1
        }
    }
    'down' { Compose down }
    'xoa-du-lieu' { Compose down -v }
    'logs' { Compose logs -f --tail 100 }
    'ps' { Compose ps }
    default { Write-Host "Dung: .\scripts\may.ps1 up | kiem-tra | down | xoa-du-lieu | logs | ps   (may nay: $May)"; exit 1 }
}
