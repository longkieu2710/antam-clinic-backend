-- ============================================================================
-- BẢO MẬT THÔNG TIN BỆNH NHÂN GIỮA CÁC CHI NHÁNH — chạy tại MỖI chi nhánh S1, S2, S3
-- Bổ sung của nhóm ứng dụng, chạy SAU các script trong CSDL/ và SAU 07_bao_mat_hub.sql ở S0 (cần role nhan_ban).
-- Không sửa cấu trúc bảng nào. Chạy lại nhiều lần được.
-- ============================================================================

-- 1. Bỏ đường đọc hồ sơ bệnh nhân toàn chuỗi (CSDL/06_setup_tra_cuu_benh_nhan_branch.sql): view
--    TRA_CUU_BENH_NHAN_TOAN_CHUOI trả họ tên, CCCD, địa chỉ của bệnh nhân mọi chi nhánh, qua FDW đăng nhập S0 bằng admin.
--    Tiếp đón giờ gọi hàm tim_ma_benh_nhan ở S0 (chỉ trả mã) bằng role chi_nhanh_tra_cuu.
DROP VIEW IF EXISTS tra_cuu_benh_nhan_toan_chuoi;
DROP SCHEMA IF EXISTS hub CASCADE;
DROP SERVER IF EXISTS srv_hub CASCADE;   -- kéo theo user mapping chứa mật khẩu admin của S0

-- 2. Subscription nhận danh mục: tài khoản admin của S0 → role nhan_ban (chỉ đọc 5 bảng danh mục).
--    Giữ nguyên host / port / dbname đang dùng (tên container hoặc IP).
DO $$
DECLARE
    s record;
BEGIN
    FOR s IN SELECT subname, subconninfo FROM pg_subscription LOOP
        EXECUTE format('ALTER SUBSCRIPTION %I CONNECTION %L', s.subname,
                       regexp_replace(regexp_replace(s.subconninfo, 'user=\S+', 'user=nhan_ban'),
                                      'password=\S+', 'password=nhanban123'));
    END LOOP;
END $$;
