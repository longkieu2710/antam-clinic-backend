#!/usr/bin/env bash
# =============================================================================
# Tắt / bật lại JIT của PostgreSQL trên cả 4 container (S0–S3).
#
# Vì sao: foreign table (postgres_fdw) không có thống kê nên planner ước lượng chi phí rất cao, vượt
# jit_above_cost → mọi truy vấn qua view toàn cục ở S0 đều bị bật JIT và tốn ~1,5 giây biên dịch LLVM,
# trong khi bản thân câu lệnh chỉ chạy vài mili-giây. JIT chỉ có lợi cho truy vấn phân tích rất lớn.
# Ứng dụng Spring đã tự "SET jit = off" cho kết nối của nó; script này tắt cho MỌI client
# (psql, pgAdmin, DBeaver… khi nhóm demo truy vấn phân tán).
#
# Cách dùng:
#   ./tat_jit.sh          tắt JIT (ALTER SYSTEM SET jit = off + nạp lại cấu hình, không cần restart)
#   ./tat_jit.sh --bat    bật lại như mặc định (ALTER SYSTEM RESET jit)
#   ./tat_jit.sh --xem    chỉ xem trạng thái hiện tại
#
# Thiết lập được ghi vào postgresql.auto.conf trong volume của từng container: vẫn giữ khi restart
# container, MẤT khi chạy ./init_db.sh --reset (xoá volume) → chạy lại script này sau khi reset.
# Không sửa file nào trong thư mục CSDL/.
# =============================================================================
set -uo pipefail

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

if [ -t 1 ]; then G=$'\033[32m'; R=$'\033[31m'; Y=$'\033[33m'; N=$'\033[0m'; else G=; R=; Y=; N=; fi

mode="${1:-tat}"
case "$mode" in
  tat)   sql="ALTER SYSTEM SET jit = off"; title="Tắt JIT trên 4 trạm" ;;
  --bat) sql="ALTER SYSTEM RESET jit";     title="Bật lại JIT (mặc định của PostgreSQL) trên 4 trạm" ;;
  --xem) sql="";                           title="Trạng thái JIT trên 4 trạm" ;;
  *) echo "Dùng: $0 [--bat | --xem]"; exit 1 ;;
esac

command -v docker >/dev/null 2>&1 || { echo "${R}Không có lệnh docker — hãy cài / mở Docker Desktop.${N}"; exit 1; }
docker info >/dev/null 2>&1 || { echo "${R}Docker Desktop chưa chạy — mở Docker Desktop rồi chạy lại.${N}"; exit 1; }

printf '%s%s%s\n' "$Y" "$title" "$N"
errors=0
for c in "$S0" "$S1" "$S2" "$S3"; do
  db="$(db_of "$c")"
  if [ -n "$sql" ]; then
    # ALTER SYSTEM không chạy được trong transaction → mỗi lệnh một lần gọi psql
    if ! out="$(docker exec -i "$c" psql -U "$DB_USER" -d "$db" -v ON_ERROR_STOP=1 -tAc "$sql" 2>&1)" \
       || ! out="$(docker exec -i "$c" psql -U "$DB_USER" -d "$db" -v ON_ERROR_STOP=1 -tAc "SELECT pg_reload_conf()" 2>&1)"; then
      printf ' %s-> %s: lỗi — %s%s\n' "$R" "$c" "$out" "$N"; errors=$((errors + 1)); continue
    fi
  fi
  # mở phiên mới để đọc giá trị sau khi nạp lại cấu hình
  jit="$(docker exec -i "$c" psql -U "$DB_USER" -d "$db" -tAc "SHOW jit" 2>&1 | tr -d '[:space:]')"
  case "$jit" in
    off) printf ' %s-> %-18s jit = off%s\n' "$G" "$c" "$N" ;;
    on)  printf ' %s-> %-18s jit = on%s\n' "$Y" "$c" "$N" ;;
    *)   printf ' %s-> %-18s không đọc được: %s%s\n' "$R" "$c" "$jit" "$N"; errors=$((errors + 1)) ;;
  esac
done

if [ "$errors" -gt 0 ]; then
  echo "${R}Có $errors trạm lỗi — kiểm tra 4 container đã chạy chưa (docker ps).${N}"; exit 1
fi
