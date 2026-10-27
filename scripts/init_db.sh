#!/usr/bin/env bash
# =============================================================================
# Khởi tạo cụm CSDL phân tán (4 container PostgreSQL) — bản bash của CSDL/init_db.ps1
# Chạy được trên macOS / Linux, không cần PowerShell.
#
# Cách dùng:
#   ./init_db.sh            Khởi tạo khi 4 container đã chạy (docker compose up -d)
#   ./init_db.sh --reset    Xoá sạch volume, dựng lại 4 container rồi khởi tạo
#
# Mặc định đọc các file .sql trong thư mục CSDL (ngang hàng với source_code).
# Đổi vị trí bằng biến môi trường:  CSDL_DIR=/duong/dan/CSDL ./init_db.sh
# Các bước và thứ tự file giống hệt init_db.ps1 của nhóm DB.
# =============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CSDL_DIR="${CSDL_DIR:-$SCRIPT_DIR/../../../CSDL}"
CSDL_DIR="$(cd "$CSDL_DIR" 2>/dev/null && pwd)" || { echo "Không tìm thấy thư mục CSDL. Đặt CSDL_DIR=... rồi chạy lại."; exit 1; }
DB_USER=admin

S0=site_s0_trungtam; S1=site_s1_caugiay; S2=site_s2_dongda; S3=site_s3_badinh
db_of() {
  case "$1" in
    "$S0") echo ql_phongkham_hub ;;
    "$S1") echo ql_phongkham_cn1 ;;
    "$S2") echo ql_phongkham_cn2 ;;
    "$S3") echo ql_phongkham_cn3 ;;
  esac
}

if [ -t 1 ]; then Y=$'\033[33m'; G=$'\033[32m'; R=$'\033[31m'; C=$'\033[36m'; N=$'\033[0m'; else Y=; G=; R=; C=; N=; fi
step() { printf '\n%s%s%s\n' "$Y" "$1" "$N"; }
ok()   { printf ' %s-> %s%s\n' "$G" "$1" "$N"; }
bad()  { printf ' %s-> %s%s\n' "$R" "$1" "$N"; }

ERRORS=0
# run_sql <container> <file.sql> — giống "Get-Content file | docker exec -i ... psql" trong bản .ps1,
# nhưng in ra dòng ERROR (nếu có) thay vì để trôi mất.
run_sql() {
  local c="$1" f="$CSDL_DIR/$2" err
  if [ ! -f "$f" ]; then bad "Thiếu file $2"; ERRORS=$((ERRORS + 1)); return; fi
  err="$(docker exec -i "$c" psql -q -U "$DB_USER" -d "$(db_of "$c")" -v ON_ERROR_STOP=0 < "$f" 2>&1 >/dev/null | grep -E 'ERROR|FATAL' || true)"
  if [ -n "$err" ]; then
    bad "$2 tại $c báo lỗi:"; printf '%s\n' "$err" | sed 's/^/      /'; ERRORS=$((ERRORS + 1))
  fi
}
# q <container> <sql> — chạy 1 câu SELECT, trả về giá trị
q() { docker exec -i "$1" psql -U "$DB_USER" -d "$(db_of "$1")" -tAc "$2" 2>/dev/null | tr -d '[:space:]'; }

printf '%s==========================================================%s\n' "$C" "$N"
printf '%sKHOI TAO HE CSDL PHAN TAN (KEM LOGICAL REPLICATION REALTIME)%s\n' "$C" "$N"
printf '%s==========================================================%s\n' "$C" "$N"
echo "Thư mục CSDL: $CSDL_DIR"

# --- Kiểm tra Docker -----------------------------------------------------------
command -v docker >/dev/null 2>&1 || { bad "Không có lệnh docker — hãy cài / mở Docker Desktop."; exit 1; }
docker info >/dev/null 2>&1 || { bad "Docker Desktop chưa chạy — mở Docker Desktop rồi chạy lại."; exit 1; }

if [ "${1:-}" = "--reset" ]; then
  step "[Reset] Xoá container + volume cũ, dựng lại 4 container..."
  (cd "$CSDL_DIR" && docker compose down -v && docker compose up -d) || { bad "docker compose lỗi (cổng 5430–5433 có bị chiếm không?)"; exit 1; }
fi

# --- 0. Chờ 4 container sẵn sàng ----------------------------------------------
# Kiểm tra qua TCP (127.0.0.1) để không nhầm với server tạm lúc container mới khởi tạo.
step "[Kiểm tra] Chờ 4 container PostgreSQL sẵn sàng nhận kết nối..."
running=" $(docker ps --format '{{.Names}}' | tr '\n' ' ') "
for c in "$S0" "$S1" "$S2" "$S3"; do
  case "$running" in
    *" $c "*) ;;
    *) bad "Container $c chưa chạy. Chạy 'docker compose up -d' trong thư mục CSDL, hoặc dùng: $0 --reset"; exit 1 ;;
  esac
  ready=0
  for i in $(seq 1 60); do
    if docker exec "$c" pg_isready -h 127.0.0.1 -U "$DB_USER" >/dev/null 2>&1; then ready=1; break; fi
    sleep 1
  done
  if [ "$ready" = 1 ]; then ok "$c đã sẵn sàng"; else bad "$c chưa sẵn sàng sau 60 giây"; exit 1; fi
done

# --- 1. 21 bảng tại 3 chi nhánh -------------------------------------------------
step "[Bước 1] Tạo 21 bảng cục bộ BCNF tại 3 chi nhánh (S1, S2, S3)..."
for c in "$S1" "$S2" "$S3"; do run_sql "$c" 01_init_schema_branch.sql; done
ok "Xong"

# --- 2. 5 bảng master tại S0 -----------------------------------------------------
step "[Bước 2] Tạo 5 bảng Master Data tại Trạm Trung tâm (S0)..."
run_sql "$S0" 01_init_schema_hub.sql
ok "Xong"

# --- 3. Dữ liệu master tại S0 ----------------------------------------------------
step "[Bước 3] Ban hành Master Data tại S0..."
run_sql "$S0" 04_mock_branches.sql
run_sql "$S0" 03_mock_catalogs.sql
ok "Xong"

# --- 4. Logical Replication -------------------------------------------------------
step "[Bước 4] Thiết lập Logical Replication (Publisher S0 → Subscriber S1, S2, S3)..."
run_sql "$S0" 03_setup_publisher_hub.sql
run_sql "$S1" 03_setup_subscriber_s1.sql
run_sql "$S2" 03_setup_subscriber_s2.sql
run_sql "$S3" 03_setup_subscriber_s3.sql
sleep 3
ok "Đang streaming dữ liệu danh mục sang các chi nhánh"

# --- 5. Dữ liệu khám chữa phân mảnh ---------------------------------------------
step "[Bước 5] Nạp dữ liệu khám chữa bệnh phân mảnh tại từng chi nhánh..."
run_sql "$S1" 05_seed_data_branch1.sql
run_sql "$S2" 05_seed_data_branch2.sql
run_sql "$S3" 05_seed_data_branch3.sql
ok "Xong"

# --- 6. FDW + view toàn cục tại S0 ------------------------------------------------
step "[Bước 6] Thiết lập postgres_fdw & Global Views tại S0..."
run_sql "$S0" 02_setup_distributed_hub.sql
ok "Xong"

# --- 7. View tra cứu BN toàn chuỗi tại chi nhánh --------------------------------
step "[Bước 7] Thiết lập View tra cứu bệnh nhân toàn chuỗi tại S1, S2, S3..."
for c in "$S1" "$S2" "$S3"; do run_sql "$c" 06_setup_tra_cuu_benh_nhan_branch.sql; done
ok "Xong"

# --- 8. Kiểm tra nhanh (không có trong bản .ps1) ---------------------------------
step "[Kiểm tra] Đồng bộ danh mục và view toàn cục..."
thuoc_s0="$(q "$S0" 'SELECT COUNT(*) FROM thuoc')"
sum=0
for c in "$S1" "$S2" "$S3"; do
  t="$(q "$c" 'SELECT COUNT(*) FROM thuoc')"; l="$(q "$c" 'SELECT COUNT(*) FROM luot_kham_chua')"
  if [ -n "$t" ] && [ "$t" = "$thuoc_s0" ]; then ok "$c: THUOC $t/$thuoc_s0 (đã đồng bộ), LUOT_KHAM_CHUA $l"
  else bad "$c: THUOC ${t:-?}/${thuoc_s0:-?} — replication chưa đồng bộ (chờ vài giây rồi kiểm tra lại)"; ERRORS=$((ERRORS + 1)); fi
  sum=$((sum + ${l:-0}))
done
g="$(q "$S0" 'SELECT COUNT(*) FROM luot_kham_chua')"
if [ -n "$g" ] && [ "$g" = "$sum" ]; then ok "$S0: view LUOT_KHAM_CHUA gộp $g lượt = tổng 3 chi nhánh (FDW hoạt động)"
else bad "$S0: view LUOT_KHAM_CHUA = ${g:-?}, tổng chi nhánh = $sum — kiểm tra FDW"; ERRORS=$((ERRORS + 1)); fi

echo
if [ "$ERRORS" -eq 0 ]; then
  printf '%s==========================================================%s\n' "$G" "$N"
  printf '%sHE THONG CSDL PHAN TAN DA SAN SANG!%s\n' "$G" "$N"
  printf '%s==========================================================%s\n' "$G" "$N"
else
  printf '%sXONG NHƯNG CÓ %s LỖI — xem các dòng đỏ phía trên.%s\n' "$R" "$ERRORS" "$N"
fi
cat <<INFO

Kết nối (user admin / password123):
  S0 Trung tâm  localhost:5430  ql_phongkham_hub
  S1 Cầu Giấy   localhost:5431  ql_phongkham_cn1
  S2 Đống Đa    localhost:5432  ql_phongkham_cn2
  S3 Ba Đình    localhost:5433  ql_phongkham_cn3
INFO
[ "$ERRORS" -eq 0 ]
