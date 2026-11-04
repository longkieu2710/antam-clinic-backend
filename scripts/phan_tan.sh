#!/usr/bin/env bash
# =============================================================================
# Chạy mô phỏng "mỗi cơ sở một máy" trên 1 máy: 4 cặp web + api (Trung tâm, CN1, CN2, CN3) cùng một source,
# mỗi cặp dùng CSDL của cơ sở mình qua một mạng LAN riêng; mạng của cụm CSDL đóng vai WAN.
#
# Cách dùng:
#   ./phan_tan.sh up                build + chạy 4 cặp web/api, gắn CSDL vào LAN của từng cơ sở
#   ./phan_tan.sh mat-mang cn1      cơ sở CN1 mất mạng: ngắt CSDL S1 và api-cn1 khỏi WAN (hq | cn1 | cn2 | cn3)
#   ./phan_tan.sh co-mang cn1       nối lại (hoặc: co-mang all)
#   ./phan_tan.sh trang-thai        cơ sở nào đang nối WAN
#   ./phan_tan.sh down              nối lại mọi cơ sở, gỡ LAN, dừng 8 container web/api (không đụng 4 container CSDL)
#   ./phan_tan.sh logs | ps
#
# Biến môi trường: HQ_PORT, CN1_PORT, CN2_PORT, CN3_PORT (mặc định 3100..3103),
# DB_NETWORK (mặc định tự dò từ container site_s0_trungtam). Dùng ../docker-compose.phan-tan.yml.
# Không sửa file nào trong thư mục CSDL/.
# =============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/../docker-compose.phan-tan.yml"
PROJECT=antam-phan-tan
SITES="hq cn1 cn2 cn3"

if [ -t 1 ]; then Y=$'\033[33m'; G=$'\033[32m'; R=$'\033[31m'; C=$'\033[36m'; N=$'\033[0m'; else Y=; G=; R=; C=; N=; fi
step() { printf '\n%s%s%s\n' "$Y" "$1" "$N"; }
ok()   { printf ' %s-> %s%s\n' "$G" "$1" "$N"; }
bad()  { printf ' %s-> %s%s\n' "$R" "$1" "$N"; }

# Thông tin từng cơ sở: container CSDL, tên service của CSDL trong CSDL/docker-compose.yml, container api, tên cơ sở
db_of()      { case "$1" in hq) echo site_s0_trungtam ;; cn1) echo site_s1_caugiay ;; cn2) echo site_s2_dongda ;; cn3) echo site_s3_badinh ;; esac; }
db_alias()   { case "$1" in hq) echo db_trung_tam ;; cn1) echo db_chinhanh_1 ;; cn2) echo db_chinhanh_2 ;; cn3) echo db_chinhanh_3 ;; esac; }
api_of()     { echo "antam_$1_api"; }
name_of()    { case "$1" in hq) echo "Trung tâm (S0)" ;; cn1) echo "Cầu Giấy (S1)" ;; cn2) echo "Đống Đa (S2)" ;; cn3) echo "Ba Đình (S3)" ;; esac; }
port_of()    { case "$1" in hq) echo "${HQ_PORT:-3100}" ;; cn1) echo "${CN1_PORT:-3101}" ;; cn2) echo "${CN2_PORT:-3102}" ;; cn3) echo "${CN3_PORT:-3103}" ;; esac; }
lan_of()     { echo "${PROJECT}_lan_$1"; }

command -v docker >/dev/null 2>&1 || { bad "Không có lệnh docker — hãy cài / mở Docker Desktop."; exit 1; }
docker info >/dev/null 2>&1 || { bad "Docker Desktop chưa chạy — mở Docker Desktop rồi chạy lại."; exit 1; }
[ -f "$COMPOSE_FILE" ] || { bad "Không thấy $COMPOSE_FILE"; exit 1; }

detect_network() {
  local c net
  for c in site_s0_trungtam site_s1_caugiay site_s2_dongda site_s3_badinh; do
    net="$(docker inspect -f '{{range $k, $v := .NetworkSettings.Networks}}{{println $k}}{{end}}' "$c" 2>/dev/null \
      | grep -v "^${PROJECT}_" | grep -v '^$' | head -n 1)"
    if [ -n "$net" ]; then echo "$net"; return 0; fi
  done
  return 1
}
WAN="${DB_NETWORK:-$(detect_network || echo csdl_default)}"

compose() { DB_NETWORK="$WAN" docker compose -f "$COMPOSE_FILE" "$@"; }

on_net() { [ -n "$(docker inspect -f "{{if index .NetworkSettings.Networks \"$2\"}}yes{{end}}" "$1" 2>/dev/null)" ]; }
exists() { docker inspect "$1" >/dev/null 2>&1; }

join_net() { # join_net <mạng> <container> [alias...]
  local net="$1" c="$2"; shift 2
  exists "$c" || return 0
  on_net "$c" "$net" && return 0
  local args=()
  for a in "$@"; do args+=(--alias "$a"); done
  docker network connect "${args[@]}" "$net" "$c"
}
leave_net() { exists "$2" && on_net "$2" "$1" && docker network disconnect "$1" "$2"; return 0; }

check_site() {
  case " $SITES " in *" $1 "*) return 0 ;; esac
  bad "Không có cơ sở '$1'. Chọn: $SITES"; exit 1
}

wait_db() {
  for _ in $(seq 1 30); do docker exec "$1" pg_isready -h 127.0.0.1 -U admin >/dev/null 2>&1 && return 0; sleep 1; done
  bad "$1 chưa nhận kết nối sau 30 giây"; return 1
}

# Đổi mạng của container CSDL xong, Docker Desktop có thể vẫn chuyển cổng localhost:543x tới IP cũ (DBeaver, backend chạy
# ngoài Docker bị "connection reset") → khởi động lại container để cổng trỏ đúng; dữ liệu nằm trên volume, không mất.
# Không làm khi ngắt WAN (mat-mang): lúc đó mất cổng là đúng tình huống đang giả lập.
restart_db() { docker restart "$1" >/dev/null && wait_db "$1"; }

wan_up() { # cơ sở nối lại WAN (giữ alias cũ để CSDL/docker-compose.yml vẫn gọi được bằng tên service)
  local db; db="$(db_of "$1")"
  if exists "$db" && ! on_net "$db" "$WAN"; then
    join_net "$WAN" "$db" "$(db_alias "$1")" && restart_db "$db"
  fi
  join_net "$WAN" "$(api_of "$1")" "api-$1"
}

# printf đếm byte, chữ có dấu lệch cột → tự đệm theo số ký tự
pad() { printf '%s%*s' "$1" $(( $2 > ${#1} ? $2 - ${#1} : 0 )) ''; }
net_word() { if on_net "$1" "$WAN"; then printf '%s' "$G$(pad nối 10)$N"; else printf '%s' "$R$(pad 'mất mạng' 10)$N"; fi; }

status() {
  printf '\n %s %s %s %s\n' "$(pad 'Cơ sở' 16)" "$(pad 'Giao diện' 23)" "$(pad 'CSDL ↔ WAN' 10)" "api ↔ WAN"
  for s in $SITES; do
    local api="(chưa chạy)"
    exists "$(api_of "$s")" && api="$(net_word "$(api_of "$s")")"
    printf ' %s %s %s %s\n' "$(pad "$(name_of "$s")" 16)" "$(pad "http://localhost:$(port_of "$s")" 23)" \
      "$(net_word "$(db_of "$s")")" "$api"
  done
}

case "${1:-}" in
  up)
    step "1. Kiểm tra 4 container CSDL (mạng WAN: $WAN)"
    for s in $SITES; do
      c="$(db_of "$s")"
      if [ "$(docker inspect -f '{{.State.Running}}' "$c" 2>/dev/null)" = "true" ]; then ok "$c đang chạy"; else
        bad "$c chưa chạy. Chạy trước:  cd CSDL && docker compose up -d   (lần đầu thêm: backend/scripts/init_db.sh)"; exit 1; fi
    done

    step "2. Build image và chạy 4 cặp web + api (lần đầu tải dependency, mất vài phút)"
    compose up -d --build || { bad "docker compose up lỗi — xem thông báo ở trên."; exit 1; }

    step "3. Gắn CSDL của từng cơ sở vào LAN của cơ sở đó"
    for s in $SITES; do
      db="$(db_of "$s")"
      if ! on_net "$db" "$(lan_of "$s")"; then
        join_net "$(lan_of "$s")" "$db" "db-$s" && restart_db "$db" || exit 1
      fi
      ok "$db ∈ $(lan_of "$s") (tên db-$s)"
    done

    step "4. Chờ 4 backend khởi động"
    for s in $SITES; do
      st=""
      for _ in $(seq 1 90); do
        st="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$(api_of "$s")" 2>/dev/null)"
        case "$st" in healthy|exited|dead) break ;; esac
        sleep 2
      done
      if [ "$st" = "healthy" ]; then ok "$(api_of "$s") sẵn sàng"; else
        bad "$(api_of "$s") chưa sẵn sàng (${st:-không rõ}). 30 dòng log cuối:"
        docker logs --tail 30 "$(api_of "$s")" 2>&1 | sed 's/^/      /'; exit 1
      fi
    done
    status
    printf '\n%sXong!%s Giả lập mất mạng:  %s mat-mang cn1   ·   nối lại:  %s co-mang cn1\n' "$C" "$N" "$0" "$0"
    ;;

  mat-mang)
    s="${2:-}"; check_site "$s"
    leave_net "$WAN" "$(db_of "$s")"
    leave_net "$WAN" "$(api_of "$s")"
    ok "$(name_of "$s") đã mất mạng: CSDL $(db_of "$s") và $(api_of "$s") không còn nối WAN ($WAN)."
    if [ "$s" = hq ]; then
      echo "    Các chi nhánh vẫn khám chữa bình thường; danh mục chung ngừng đồng bộ, báo cáo toàn chuỗi báo lỗi."
    else
      echo "    http://localhost:$(port_of "$s") vẫn tiếp đón, thêm bệnh nhân, khám, chữa, báo cáo chi nhánh;"
      echo "    chức năng cần Trung tâm (sửa thuốc, hồ sơ toàn chuỗi) và báo cáo toàn chuỗi ở bản Trung tâm sẽ báo mất kết nối."
    fi
    status
    ;;

  co-mang)
    target="${2:-}"
    if [ "$target" = all ]; then for s in $SITES; do wan_up "$s"; done; else check_site "$target"; wan_up "$target"; fi
    ok "Đã nối lại ${target}. Logical Replication tự đồng bộ bù danh mục trong vài giây."
    status
    ;;

  trang-thai) status ;;

  down)
    step "Nối lại mọi cơ sở, gỡ CSDL khỏi LAN, dừng web + api"
    for s in $SITES; do
      db="$(db_of "$s")"; changed=0
      if exists "$db" && ! on_net "$db" "$WAN"; then join_net "$WAN" "$db" "$(db_alias "$s")"; changed=1; fi
      if exists "$db" && on_net "$db" "$(lan_of "$s")"; then leave_net "$(lan_of "$s")" "$db"; changed=1; fi
      [ "$changed" = 1 ] && restart_db "$db" && ok "$db: chỉ còn mạng $WAN"
    done
    compose down
    ;;

  logs) compose logs -f --tail 100 ;;
  ps)   compose ps ;;
  *)
    echo "Dùng: $0 up | mat-mang <hq|cn1|cn2|cn3> | co-mang <hq|cn1|cn2|cn3|all> | trang-thai | down | logs | ps"
    exit 1
    ;;
esac
