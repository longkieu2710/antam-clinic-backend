#!/usr/bin/env bash
# =============================================================================
# Demo nhiều máy: chạy phần của MÁY NÀY (CSDL của cơ sở + api + web). Máy nào là cơ sở nào đọc từ dòng app.may-nay
# trong src/main/resources/application.yml; địa chỉ 4 máy đọc từ app.dia-chi-may (IP Tailscale).
#
# Cách dùng:
#   ./may.sh up            build + chạy, chờ api sẵn sàng (lần đầu: sau khi cả 4 máy cùng up, chạy init_may.sh MỘT lần)
#   ./may.sh kiem-tra      CSDL của máy này có gọi được CSDL 3 máy kia không (thử từ TRONG container, như FDW / replication)
#   ./may.sh down          dừng (giữ dữ liệu)       ./may.sh xoa-du-lieu   dừng và xoá dữ liệu CSDL của máy này
#   ./may.sh logs | ps
#
# Bản Windows: may.ps1 (cùng lệnh). Biến môi trường: WEB_PORT (mặc định 3000).
# =============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$SCRIPT_DIR/.."
COMPOSE_FILE="$ROOT/docker-compose.may.yml"
YML="$ROOT/src/main/resources/application.yml"
MAYS="hq cn1 cn2 cn3"

if [ -t 1 ]; then Y=$'\033[33m'; G=$'\033[32m'; R=$'\033[31m'; C=$'\033[36m'; N=$'\033[0m'; else Y=; G=; R=; C=; N=; fi
step() { printf '\n%s%s%s\n' "$Y" "$1" "$N"; }
ok()   { printf ' %s-> %s%s\n' "$G" "$1" "$N"; }
bad()  { printf ' %s-> %s%s\n' "$R" "$1" "$N"; }

# yml <khoá> [khối cha] — giá trị của "  khoá: giá trị" trong application.yml (bỏ chú thích, dấu nháy, khoảng trắng)
yml() {
  awk -v k="$1" -v p="${2:-}" '
    function val(line) { sub(/^[^:]*:/, "", line); sub(/#.*/, "", line); gsub(/[ \t"\047]/, "", line); return line }
    p == "" && $0 ~ "^  " k ":" { print val($0); exit }
    p != "" && $0 ~ "^  " p ":" { blk = 1; next }
    blk && $0 ~ "^    " k ":" { print val($0); exit }
    blk && $0 ~ "^  [^ ]" { exit }
  ' "$YML"
}

ten_may() { case "$1" in hq) echo "Trung tâm (S0)" ;; cn1) echo "Cầu Giấy (S1)" ;; cn2) echo "Đống Đa (S2)" ;; cn3) echo "Ba Đình (S3)" ;; esac; }

command -v docker >/dev/null 2>&1 || { bad "Không có lệnh docker — hãy cài / mở Docker Desktop."; exit 1; }
docker info >/dev/null 2>&1 || { bad "Docker Desktop chưa chạy — mở Docker Desktop rồi chạy lại."; exit 1; }
[ -f "$YML" ] || { bad "Không thấy $YML"; exit 1; }

MAY="$(yml may-nay | tr '[:upper:]' '[:lower:]')"
case " $MAYS " in
  *" $MAY "*) ;;
  *) bad "Chưa chọn máy: sửa dòng 'may-nay:' trong src/main/resources/application.yml thành hq, cn1, cn2 hoặc cn3."; exit 1 ;;
esac
CONG_DB="$(yml cong-may db)"; CONG_DB="${CONG_DB:-5432}"
export CONG_DB

compose() { docker compose -f "$COMPOSE_FILE" --profile "$MAY" "$@"; }

case "${1:-}" in
  up)
    step "Máy này: $(ten_may "$MAY") — build + chạy CSDL, api, web (lần đầu mất vài phút)"
    for m in $MAYS; do
      a="$(yml "$m" dia-chi-may)"
      if [ -z "$a" ]; then bad "Thiếu app.dia-chi-may.$m (IP Tailscale của máy $(ten_may "$m")) trong application.yml"; exit 1; fi
    done
    compose up -d --build || { bad "docker compose up lỗi — cổng $CONG_DB hoặc ${WEB_PORT:-3000} có bị chiếm không?"; exit 1; }
    api="$(compose ps -q api)"
    st=""
    for _ in $(seq 1 90); do
      st="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$api" 2>/dev/null)"
      case "$st" in healthy|exited|dead) break ;; esac
      sleep 2
    done
    if [ "$st" != "healthy" ]; then
      bad "api chưa sẵn sàng (${st:-không rõ}). 30 dòng log cuối:"; docker logs --tail 30 "$api" 2>&1 | sed 's/^/      /'; exit 1
    fi
    ok "Sẵn sàng: http://localhost:${WEB_PORT:-3000}   (CSDL của máy này mở cổng $CONG_DB cho 3 máy kia)"
    echo "    Kiểm tra kết nối tới 3 máy kia:  $0 kiem-tra"
    ;;

  kiem-tra)
    step "Máy này: $(ten_may "$MAY") — thử kết nối CSDL các máy khác từ trong container db-$MAY (cổng $CONG_DB)"
    db="$(compose ps -q "db-$MAY")"
    [ -n "$db" ] || { bad "CSDL của máy này chưa chạy: $0 up"; exit 1; }
    loi=0
    for m in $MAYS; do
      a="$(yml "$m" dia-chi-may)"
      if [ "$m" = "$MAY" ]; then ok "$(ten_may "$m") — chính máy này ($a)"; continue; fi
      if docker exec "$db" pg_isready -q -t 5 -h "$a" -p "$CONG_DB"; then ok "$(ten_may "$m") — $a:$CONG_DB nhận kết nối"
      else bad "$(ten_may "$m") — $a:$CONG_DB KHÔNG gọi được"; loi=$((loi + 1)); fi
    done
    if [ "$loi" -gt 0 ]; then
      cat <<HINT

    Kiểm tra lần lượt:
      1. Máy kia đã chạy may.sh / may.ps1 up chưa, Tailscale hai máy có cùng tailnet và đang bật không (tailscale status).
      2. IP trong app.dia-chi-may đúng chưa (trên máy kia: tailscale ip -4).
      3. Firewall máy kia cho phép cổng $CONG_DB vào (Windows: thêm Inbound Rule TCP $CONG_DB).
      4. Máy Windows: container không ra được mạng Tailscale → bật networkingMode=mirrored trong %UserProfile%\\.wslconfig,
         chạy "wsl --shutdown" rồi mở lại Docker Desktop.
HINT
      exit 1
    fi
    ;;

  down)         compose down ;;
  xoa-du-lieu)  compose down -v ;;
  logs)         compose logs -f --tail 100 ;;
  ps)           compose ps ;;
  *)
    echo "Dùng: $0 up | kiem-tra | down | xoa-du-lieu | logs | ps   (máy này: ${MAY})"
    exit 1
    ;;
esac
