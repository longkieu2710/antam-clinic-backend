#!/usr/bin/env bash
# =============================================================================
# Build và chạy ứng dụng (web + api) bằng Docker, nối vào cụm CSDL đang chạy.
#
# Cách dùng:
#   ./docker_app.sh           build image (nếu code đổi) rồi chạy web + api, chờ api sẵn sàng
#   ./docker_app.sh --down    dừng và xoá 2 container web, api (không đụng tới 4 container CSDL)
#   ./docker_app.sh --logs    xem log (Ctrl+C để thoát)
#   ./docker_app.sh --ps      xem trạng thái
#
# Biến môi trường: WEB_PORT (mặc định 3000), API_PORT (mặc định 8080),
# DB_NETWORK (mặc định tự dò từ container site_s0_trungtam).
# Dùng ../docker-compose.yml. Không sửa file nào trong thư mục CSDL/.
# =============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/../docker-compose.yml"
API=antam_clinic_api
DB_CONTAINERS="site_s0_trungtam site_s1_caugiay site_s2_dongda site_s3_badinh"

if [ -t 1 ]; then Y=$'\033[33m'; G=$'\033[32m'; R=$'\033[31m'; C=$'\033[36m'; N=$'\033[0m'; else Y=; G=; R=; C=; N=; fi
step() { printf '\n%s%s%s\n' "$Y" "$1" "$N"; }
ok()   { printf ' %s-> %s%s\n' "$G" "$1" "$N"; }
bad()  { printf ' %s-> %s%s\n' "$R" "$1" "$N"; }

command -v docker >/dev/null 2>&1 || { bad "Không có lệnh docker — hãy cài / mở Docker Desktop."; exit 1; }
docker info >/dev/null 2>&1 || { bad "Docker Desktop chưa chạy — mở Docker Desktop rồi chạy lại."; exit 1; }
[ -f "$COMPOSE_FILE" ] || { bad "Không thấy $COMPOSE_FILE"; exit 1; }

# Tên mạng Docker của cụm CSDL: lấy từ container CSDL đầu tiên đang chạy
detect_network() {
  local c net
  for c in $DB_CONTAINERS; do
    net="$(docker inspect -f '{{range $k, $v := .NetworkSettings.Networks}}{{println $k}}{{end}}' "$c" 2>/dev/null | head -n 1)"
    if [ -n "$net" ]; then echo "$net"; return 0; fi
  done
  return 1
}

compose() {
  if [ -z "${DB_NETWORK:-}" ]; then DB_NETWORK="$(detect_network || echo csdl_default)"; fi
  DB_NETWORK="$DB_NETWORK" docker compose -f "$COMPOSE_FILE" "$@"
}

case "${1:-}" in
  --down) compose down; exit $? ;;
  --logs) compose logs -f --tail 100; exit $? ;;
  --ps)   compose ps; exit $? ;;
  "") ;;
  *) echo "Dùng: $0 [--down | --logs | --ps]"; exit 1 ;;
esac

# --- 1. Cụm CSDL ---------------------------------------------------------------
step "1. Kiểm tra 4 container CSDL"
running=0
for c in $DB_CONTAINERS; do
  if [ "$(docker inspect -f '{{.State.Running}}' "$c" 2>/dev/null)" = "true" ]; then
    ok "$c đang chạy"; running=$((running + 1))
  else
    bad "$c chưa chạy"
  fi
done
if [ "$running" -eq 0 ]; then
  bad "Chưa có container CSDL nào chạy. Chạy trước:  cd CSDL && docker compose up -d   (lần đầu thêm: backend/scripts/init_db.sh)"
  exit 1
fi
[ "$running" -lt 4 ] && printf ' %s-> Thiếu trạm: các màn hình của trạm đó và báo cáo toàn chuỗi sẽ báo lỗi.%s\n' "$Y" "$N"

if [ -z "${DB_NETWORK:-}" ]; then DB_NETWORK="$(detect_network)"; fi
ok "Mạng Docker của cụm CSDL: $DB_NETWORK"
export DB_NETWORK

# --- 2. Build + chạy -----------------------------------------------------------
step "2. Build image và chạy web + api (lần đầu tải dependency, mất vài phút)"
compose up -d --build || { bad "docker compose up lỗi — xem thông báo ở trên."; exit 1; }

# --- 3. Chờ api sẵn sàng ---------------------------------------------------------
step "3. Chờ backend khởi động"
status=""
for _ in $(seq 1 60); do
  status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$API" 2>/dev/null)"
  case "$status" in
    healthy) break ;;
    exited|dead) break ;;
  esac
  sleep 2
done
if [ "$status" != "healthy" ]; then
  bad "Backend chưa sẵn sàng (trạng thái: ${status:-không rõ}). 30 dòng log cuối:"
  docker logs --tail 30 "$API" 2>&1 | sed 's/^/      /'
  exit 1
fi
ok "Backend sẵn sàng"

printf '\n%sXong!%s\n' "$C" "$N"
echo "  Giao diện : http://localhost:${WEB_PORT:-3000}"
echo "  Swagger   : http://localhost:${WEB_PORT:-3000}/swagger-ui.html"
echo "  API trực tiếp: http://localhost:${API_PORT:-8080}"
echo "  Xem log   : $0 --logs      Dừng: $0 --down"
