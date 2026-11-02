# =============================================================================
# Bản Windows của "init_db.sh --bo-sung": chạy bước bổ sung của nhóm ứng dụng (scripts/sql/07_*, 08_*) trên cụm
# 4 container chạy trên 1 máy. Chạy SAU CSDL\init_db.ps1 — ứng dụng cần bước này (role + hàm tra cứu ở S0, giới hạn
# chờ FDW).
#   powershell -ExecutionPolicy Bypass -File .\scripts\bo_sung.ps1
# =============================================================================
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "`n[Buoc 8] Bo sung: role + ham tra cuu o S0, go view tra cuu toan chuoi o chi nhanh, gioi han cho FDW..." -ForegroundColor Yellow
$loi = 0
$buoc = @(
    @('site_s0_trungtam', 'ql_phongkham_hub', '07_bao_mat_hub.sql'),
    @('site_s0_trungtam', 'ql_phongkham_hub', '08_gioi_han_cho_fdw_hub.sql'),
    @('site_s1_caugiay', 'ql_phongkham_cn1', '07_bao_mat_chi_nhanh.sql'),
    @('site_s2_dongda', 'ql_phongkham_cn2', '07_bao_mat_chi_nhanh.sql'),
    @('site_s3_badinh', 'ql_phongkham_cn3', '07_bao_mat_chi_nhanh.sql')
)
foreach ($b in $buoc) {
    $out = Get-Content (Join-Path $PSScriptRoot "sql\$($b[2])") -Encoding UTF8 -Raw |
        docker exec -i $b[0] psql -q -U admin -d $b[1] -v ON_ERROR_STOP=0 2>&1 | Select-String -Pattern 'ERROR|FATAL'
    if ($out) { Write-Host " -> $($b[2]) tai $($b[0]) bao loi:" -ForegroundColor Red; $out | ForEach-Object { Write-Host "      $_" }; $loi++ }
}
if ($loi -eq 0) { Write-Host ' -> Xong' -ForegroundColor Green } else { exit 1 }
