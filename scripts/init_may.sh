#!/usr/bin/env bash
# =============================================================================
# Demo nhiều máy: khởi tạo CSDL của CẢ 4 máy, chạy MỘT lần từ một máy bất kỳ có Docker và vào được Tailscale
# (nên chạy từ máy macOS / Linux; máy Windows không cần chạy script này).
# Trước đó cả 4 máy đã chạy: scripts/may.sh up (Windows: may.ps1 up) — CSDL trống, đang mở cổng.
#
# Đọc địa chỉ 4 máy và cổng từ src/main/resources/application.yml (app.dia-chi-may, app.db.cong-may). Chạy đúng các
# file SQL của nhóm DB (thư mục CSDL/) theo thứ tự như init_db.sh, gửi qua mạng tới từng máy bằng một container psql
# dùng một lần, và đổi tên container trong SQL (FDW, subscription: site_s0_trungtam…, cổng 5432) thành địa chỉ máy.
# Không sửa file nào trong CSDL/. Không chạy 06 (view tra cứu toàn chuỗi) vì bước bảo mật 07 gỡ nó.
# Chạy lại được (các file SQL tự xoá dữ liệu cũ) — dữ liệu khám chữa của 4 máy về lại dữ liệu mẫu.
#
# Ghi đè khi cần: IP_HQ IP_CN1 IP_CN2 IP_CN3 CONG_DB CSDL_DIR, PSQL_NETWORK (mạng Docker cho container psql — chỉ dùng
# khi giả lập 4 máy trên 1 máy).
# =============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
YML="$SCRIPT_DIR/../src/main/resources/application.yml"
SQL_DIR="$SCRIPT_DIR/sql"
CSDL_DIR="${CSDL_DIR:-$SCRIPT_DIR/../../../CSDL}"
CSDL_DIR="$(cd "$CSDL_DIR" 2>/dev/null && pwd)" || { echo "Không tìm thấy thư mục CSDL. Đặt CSDL_DIR=... rồi chạy lại."; exit 1; }
PSQL_IMAGE=postgres:16-alpine
DB_USER=admin
DB_PASS=password123

if [ -t 1 ]; then Y=$'\033[33m'; G=$'\033[32m'; R=$'\033[31m'; C=$'\033[36m'; N=$'\033[0m'; else Y=; G=; R=; C=; N=; fi
step() { printf '\n%s%s%s\n' "$Y" "$1" "$N"; }
ok()   { printf ' %s-> %s%s\n' "$G" "$1" "$N"; }
bad()  { printf ' %s-> %s%s\n' "$R" "$1" "$N"; }

# yml <khoá> [khối cha] — xem may.sh
yml() {
  awk -v k="$1" -v p="${2:-}" '
    function val(line) { sub(/^[^:]*:/, "", line); sub(/#.*/, "", line); gsub(/[ \t"\047]/, "", line); return line }
    p == "" && $0 ~ "^  " k ":" { print val($0); exit }
    p != "" && $0 ~ "^  " p ":" { blk = 1; next }
    blk && $0 ~ "^    " k ":" { print val($0); exit }
    blk && $0 ~ "^  [^ ]" { exit }
  ' "$YML"
}

IP_HQ="${IP_HQ:-$(yml hq dia-chi-may)}"
IP_CN1="${IP_CN1:-$(yml cn1 dia-chi-may)}"
IP_CN2="${IP_CN2:-$(yml cn2 dia-chi-may)}"
IP_CN3="${IP_CN3:-$(yml cn3 dia-chi-may)}"
CONG_DB="${CONG_DB:-$(yml cong-may db)}"; CONG_DB="${CONG_DB:-5432}"
NET_ARGS=()
[ -n "${PSQL_NETWORK:-}" ] && NET_ARGS=(--network "$PSQL_NETWORK")

ip_of() { case "$1" in hq) echo "$IP_HQ" ;; cn1) echo "$IP_CN1" ;; cn2) echo "$IP_CN2" ;; cn3) echo "$IP_CN3" ;; esac; }
db_of() { case "$1" in hq) echo ql_phongkham_hub ;; cn1) echo ql_phongkham_cn1 ;; cn2) echo ql_phongkham_cn2 ;; cn3) echo ql_phongkham_cn3 ;; esac; }

# Tên container trong SQL của nhóm DB → địa chỉ máy (FDW: host '…', port '5432'; subscription: host=… port=5432)
doi_dia_chi() {
  sed -e "s/host 'site_s0_trungtam', port '5432'/host '$IP_HQ', port '$CONG_DB'/g" \
      -e "s/host 'site_s1_caugiay', port '5432'/host '$IP_CN1', port '$CONG_DB'/g" \
      -e "s/host 'site_s2_dongda', port '5432'/host '$IP_CN2', port '$CONG_DB'/g" \
      -e "s/host 'site_s3_badinh', port '5432'/host '$IP_CN3', port '$CONG_DB'/g" \
      -e "s/host=site_s0_trungtam port=5432/host=$IP_HQ port=$CONG_DB/g" "$1"
}

psql_may() { # psql_may <máy> [tham số psql...] — đọc SQL từ stdin
  local m="$1"; shift
  docker run --rm -i -e PGPASSWORD="$DB_PASS" ${NET_ARGS[@]+"${NET_ARGS[@]}"} "$PSQL_IMAGE" \
    psql -h "$(ip_of "$m")" -p "$CONG_DB" -U "$DB_USER" -d "$(db_of "$m")" "$@"
}

ERRORS=0
run_sql() { # run_sql <máy> <file.sql> [thư mục]
  local m="$1" f="${3:-$CSDL_DIR}/$2" err
  if [ ! -f "$f" ]; then bad "Thiếu file $2"; ERRORS=$((ERRORS + 1)); return; fi
  err="$(doi_dia_chi "$f" | psql_may "$m" -q -v ON_ERROR_STOP=0 2>&1 >/dev/null | grep -E 'ERROR|FATAL' || true)"
  if [ -n "$err" ]; then
    bad "$2 tại máy $m báo lỗi:"; printf '%s\n' "$err" | sed 's/^/      /'; ERRORS=$((ERRORS + 1))
  fi
}
q() { psql_may "$1" -tAc "$2" 2>/dev/null </dev/null | tr -d '[:space:]'; }

command -v docker >/dev/null 2>&1 || { bad "Không có lệnh docker — hãy cài / mở Docker Desktop."; exit 1; }
docker info >/dev/null 2>&1 || { bad "Docker Desktop chưa chạy — mở Docker Desktop rồi chạy lại."; exit 1; }

printf '%sKHỞI TẠO CSDL CHO DEMO 4 MÁY%s\n' "$C" "$N"
echo "Thư mục CSDL: $CSDL_DIR"
echo "Trung tâm $IP_HQ · Cầu Giấy $IP_CN1 · Đống Đa $IP_CN2 · Ba Đình $IP_CN3 · cổng $CONG_DB"

step "[Kiểm tra] 4 máy có nhận kết nối CSDL không..."
for m in hq cn1 cn2 cn3; do
  a="$(ip_of "$m")"
  [ -n "$a" ] || { bad "Thiếu địa chỉ máy $m (app.dia-chi-may.$m trong application.yml)"; exit 1; }
  if docker run --rm ${NET_ARGS[@]+"${NET_ARGS[@]}"} "$PSQL_IMAGE" pg_isready -q -t 5 -h "$a" -p "$CONG_DB"; then ok "máy $m ($a) sẵn sàng"
  else bad "máy $m ($a:$CONG_DB) không nhận kết nối — máy đó đã chạy may.sh up chưa, Tailscale / firewall?"; exit 1; fi
done

step "[Bước 1] Tạo 21 bảng cục bộ BCNF tại 3 chi nhánh..."
for m in cn1 cn2 cn3; do run_sql "$m" 01_init_schema_branch.sql; done

step "[Bước 2-3] Tạo 5 bảng Master Data và ban hành dữ liệu danh mục tại Trung tâm..."
run_sql hq 01_init_schema_hub.sql
run_sql hq 04_mock_branches.sql
run_sql hq 03_mock_catalogs.sql

step "[Bước 4] Logical Replication: Trung tâm phát hành, 3 chi nhánh đăng ký..."
run_sql hq 03_setup_publisher_hub.sql
run_sql cn1 03_setup_subscriber_s1.sql
run_sql cn2 03_setup_subscriber_s2.sql
run_sql cn3 03_setup_subscriber_s3.sql
sleep 5

step "[Bước 5] Nạp dữ liệu khám chữa bệnh tại từng chi nhánh..."
run_sql cn1 05_seed_data_branch1.sql
run_sql cn2 05_seed_data_branch2.sql
run_sql cn3 05_seed_data_branch3.sql

step "[Bước 6] postgres_fdw + view toàn cục tại Trung tâm (nối tới 3 máy chi nhánh)..."
run_sql hq 02_setup_distributed_hub.sql

step "[Bước 7] Bổ sung: bảo mật thông tin bệnh nhân, giới hạn chờ FDW (scripts/sql/07_*, 08_*)..."
run_sql hq 07_bao_mat_hub.sql "$SQL_DIR"
run_sql hq 08_gioi_han_cho_fdw_hub.sql "$SQL_DIR"
for m in cn1 cn2 cn3; do run_sql "$m" 07_bao_mat_chi_nhanh.sql "$SQL_DIR"; done

step "[Kiểm tra] Đồng bộ danh mục và view toàn cục..."
thuoc_hq="$(q hq 'SELECT COUNT(*) FROM thuoc')"
sum=0
for m in cn1 cn2 cn3; do
  t="$(q "$m" 'SELECT COUNT(*) FROM thuoc')"; l="$(q "$m" 'SELECT COUNT(*) FROM luot_kham_chua')"
  if [ -n "$t" ] && [ "$t" = "$thuoc_hq" ]; then ok "máy $m: THUOC $t/$thuoc_hq (đã đồng bộ), LUOT_KHAM_CHUA $l"
  else bad "máy $m: THUOC ${t:-?}/${thuoc_hq:-?} — replication chưa đồng bộ (CSDL máy $m có gọi được $IP_HQ:$CONG_DB không?)"; ERRORS=$((ERRORS + 1)); fi
  sum=$((sum + ${l:-0}))
done
g="$(q hq 'SELECT COUNT(*) FROM luot_kham_chua')"
if [ -n "$g" ] && [ "$g" = "$sum" ]; then ok "Trung tâm: view LUOT_KHAM_CHUA gộp $g lượt = tổng 3 chi nhánh (FDW qua mạng hoạt động)"
else bad "Trung tâm: view LUOT_KHAM_CHUA = ${g:-?}, tổng chi nhánh = $sum — CSDL máy Trung tâm có gọi được 3 máy kia không?"; ERRORS=$((ERRORS + 1)); fi

echo
if [ "$ERRORS" -eq 0 ]; then printf '%sCSDL CỦA 4 MÁY ĐÃ SẴN SÀNG.%s\n' "$G" "$N"
else printf '%sXONG NHƯNG CÓ %s LỖI — xem các dòng đỏ phía trên.%s\n' "$R" "$ERRORS" "$N"; fi
[ "$ERRORS" -eq 0 ]
