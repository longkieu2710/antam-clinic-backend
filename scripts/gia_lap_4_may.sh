#!/usr/bin/env bash
# =============================================================================
# Thử demo 4 máy ngay trên 1 máy, trước khi chạy thật: dùng đúng docker-compose.may.yml và init_may.sh như 4 máy thật,
# chỉ thay Tailscale bằng một mạng Docker (tailnet-gia). Không đụng cụm CSDL/ đang chạy và không cần sửa application.yml.
#
#   Trung tâm http://localhost:3200 · Cầu Giấy :3201 · Đống Đa :3202 · Ba Đình :3203
#
# Cách dùng:
#   ./gia_lap_4_may.sh up               build + chạy 4 máy; lần đầu tự khởi tạo CSDL (init_may.sh)
#   ./gia_lap_4_may.sh mat-mang cn1     máy cn1 mất mạng (giống tắt Tailscale)      — hq | cn1 | cn2 | cn3
#   ./gia_lap_4_may.sh co-mang cn1      nối lại (hoặc: co-mang all)
#   ./gia_lap_4_may.sh trang-thai       máy nào đang nối mạng
#   ./gia_lap_4_may.sh init             khởi tạo lại CSDL của 4 máy (về dữ liệu mẫu)
#   ./gia_lap_4_may.sh down             dừng và xoá cả 4 máy (kể cả dữ liệu)
# Cần khoảng 2–3 GB RAM cho Docker Desktop (4 JVM, mỗi cái giới hạn 384 MB).
# =============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$SCRIPT_DIR/.."
NET=tailnet-gia
MAYS="hq cn1 cn2 cn3"

if [ -t 1 ]; then Y=$'\033[33m'; G=$'\033[32m'; R=$'\033[31m'; C=$'\033[36m'; N=$'\033[0m'; else Y=; G=; R=; C=; N=; fi
step() { printf '\n%s%s%s\n' "$Y" "$1" "$N"; }
ok()   { printf ' %s-> %s%s\n' "$G" "$1" "$N"; }
bad()  { printf ' %s-> %s%s\n' "$R" "$1" "$N"; }

ten_may() { case "$1" in hq) echo "Trung tâm (S0)" ;; cn1) echo "Cầu Giấy (S1)" ;; cn2) echo "Đống Đa (S2)" ;; cn3) echo "Ba Đình (S3)" ;; esac; }
cong_web() { case "$1" in hq) echo 3200 ;; cn1) echo 3201 ;; cn2) echo 3202 ;; cn3) echo 3203 ;; esac; }

compose() { # compose <máy> [lệnh compose...]
  local m="$1"; shift
  MAY="$m" WEB_PORT="$(cong_web "$m")" docker compose -p "gl-$m" \
    -f "$ROOT/docker-compose.may.yml" -f "$ROOT/docker-compose.gia-lap.yml" --profile "$m" "$@"
}
container() { compose "$1" ps -q "$2" 2>/dev/null | head -n 1; }   # container <máy> <service>
# printf đếm byte, chữ có dấu lệch cột → tự đệm theo số ký tự
pad() { printf '%s%*s' "$1" $(( $2 > ${#1} ? $2 - ${#1} : 0 )) ''; }
on_net() { [ -n "$(docker inspect -f "{{if index .NetworkSettings.Networks \"$NET\"}}yes{{end}}" "$1" 2>/dev/null)" ]; }

check_may() {
  case " $MAYS " in *" $1 "*) return 0 ;; esac
  bad "Không có máy '$1'. Chọn: $MAYS"; exit 1
}

khoi_tao() {
  step "Khởi tạo CSDL của 4 máy qua mạng $NET (init_may.sh, mất vài phút)"
  IP_HQ=may-hq IP_CN1=may-cn1 IP_CN2=may-cn2 IP_CN3=may-cn3 CONG_DB=5432 PSQL_NETWORK="$NET" "$SCRIPT_DIR/init_may.sh"
}

status() {
  printf '\n'
  for m in $MAYS; do
    local db api net
    db="$(container "$m" "db-$m")"; api="$(container "$m" api)"
    if [ -z "$db" ]; then net="${R}chưa chạy${N}"
    elif on_net "$db" && on_net "$api"; then net="${G}nối mạng${N}"
    else net="${R}mất mạng${N}"; fi
    printf ' %s http://localhost:%s   %s\n' "$(pad "$(ten_may "$m")" 16)" "$(cong_web "$m")" "$net"
  done
}

command -v docker >/dev/null 2>&1 || { bad "Không có lệnh docker — hãy cài / mở Docker Desktop."; exit 1; }
docker info >/dev/null 2>&1 || { bad "Docker Desktop chưa chạy — mở Docker Desktop rồi chạy lại."; exit 1; }

case "${1:-}" in
  up)
    docker network inspect "$NET" >/dev/null 2>&1 || docker network create "$NET" >/dev/null
    for m in $MAYS; do
      step "Máy $(ten_may "$m") — build + chạy (lần đầu mất vài phút)"
      compose "$m" up -d --build || { bad "docker compose up lỗi ở máy $m — cổng $(cong_web "$m") có bị chiếm không?"; exit 1; }
    done
    step "Chờ 4 backend khởi động"
    for m in $MAYS; do
      api="$(container "$m" api)"; st=""
      for _ in $(seq 1 90); do
        st="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$api" 2>/dev/null)"
        case "$st" in healthy|exited|dead) break ;; esac
        sleep 2
      done
      if [ "$st" = "healthy" ]; then ok "máy $m sẵn sàng"; else
        bad "máy $m chưa sẵn sàng (${st:-không rõ}). 30 dòng log cuối:"; docker logs --tail 30 "$api" 2>&1 | sed 's/^/      /'; exit 1
      fi
    done
    da_co="$(docker exec "$(container hq db-hq)" psql -U admin -d ql_phongkham_hub -tAc "SELECT to_regclass('public.thuoc') IS NOT NULL" 2>/dev/null | tr -d '[:space:]')"
    if [ "$da_co" = "t" ]; then ok "CSDL đã khởi tạo từ trước (khởi tạo lại: $0 init)"; else khoi_tao || exit 1; fi
    status
    printf '\n%sXong!%s Thử mất mạng:  %s mat-mang cn1   ·   nối lại:  %s co-mang cn1\n' "$C" "$N" "$0" "$0"
    ;;

  init) khoi_tao ;;

  mat-mang)
    m="${2:-}"; check_may "$m"
    for c in "$(container "$m" "db-$m")" "$(container "$m" api)"; do
      [ -n "$c" ] && on_net "$c" && docker network disconnect "$NET" "$c"
    done
    ok "$(ten_may "$m") đã mất mạng (CSDL và api không còn nối $NET)."
    status
    ;;

  co-mang)
    target="${2:-}"
    [ "$target" = all ] && list="$MAYS" || { check_may "$target"; list="$target"; }
    for m in $list; do
      db="$(container "$m" "db-$m")"; api="$(container "$m" api)"
      [ -n "$db" ] && ! on_net "$db" && docker network connect --alias "may-$m" "$NET" "$db"
      [ -n "$api" ] && ! on_net "$api" && docker network connect "$NET" "$api"
    done
    ok "Đã nối lại $target. Logical Replication tự đồng bộ bù danh mục trong vài giây."
    status
    ;;

  trang-thai) status ;;

  down)
    for m in $MAYS; do compose "$m" down -v >/dev/null 2>&1 && ok "đã xoá máy $m"; done
    docker network rm "$NET" >/dev/null 2>&1
    ;;

  *)
    echo "Dùng: $0 up | mat-mang <máy> | co-mang <máy|all> | trang-thai | init | down"
    exit 1
    ;;
esac
