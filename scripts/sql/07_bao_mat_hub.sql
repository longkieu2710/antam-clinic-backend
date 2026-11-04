-- ============================================================================
-- BẢO MẬT THÔNG TIN BỆNH NHÂN GIỮA CÁC CHI NHÁNH — chạy tại Trạm Trung tâm S0
-- Bổ sung của nhóm ứng dụng, chạy SAU các script trong thư mục CSDL/. Không sửa cấu trúc bảng nào.
--
-- Chi nhánh không đọc được hồ sơ bệnh nhân của chi nhánh khác (họ tên, CCCD, địa chỉ, bệnh sử). Thay vào đó:
--   * role chi_nhanh_tra_cuu — tài khoản backend chi nhánh dùng khi gọi về S0: KHÔNG được SELECT bảng / view nào,
--     chỉ được EXECUTE 3 hàm bên dưới.
--   * 3 hàm SECURITY DEFINER: chạy bằng quyền của chủ sở hữu (admin), đọc phân mảnh s1 / s2 / s3 qua postgres_fdw,
--     chỉ trả phần tối thiểu (mã bệnh nhân, mã bệnh đang điều trị).
--   * role nhan_ban — chỉ dùng cho Logical Replication (đọc 5 bảng danh mục), thay tài khoản admin trong subscription
--     của chi nhánh (07_bao_mat_chi_nhanh.sql).
-- Mỗi chi nhánh được truy vấn riêng trong khối BEGIN … EXCEPTION: chi nhánh đang tắt không làm hỏng cả câu trả lời mà
-- trả một dòng ket_noi = false để ứng dụng cảnh báo.
-- Chạy lại nhiều lần được.
-- ============================================================================

-- 1. Role
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'chi_nhanh_tra_cuu') THEN
        CREATE ROLE chi_nhanh_tra_cuu LOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nhan_ban') THEN
        CREATE ROLE nhan_ban LOGIN REPLICATION;
    END IF;
END $$;
ALTER ROLE chi_nhanh_tra_cuu PASSWORD 'tracuu123';
ALTER ROLE nhan_ban PASSWORD 'nhanban123';

-- Subscription cần đọc 5 bảng của publication pub_master_catalogs (lần đồng bộ đầu / refresh)
GRANT SELECT ON chi_nhanh, luong_co_so, benh, thuoc, dich_vu TO nhan_ban;

-- 2. Tiếp đón: nhập ĐÚNG số CCCD hoặc thẻ BHYT → mã bệnh nhân + chi nhánh đã lập hồ sơ. Không tìm theo tên / SĐT.
CREATE OR REPLACE FUNCTION tim_ma_benh_nhan(p_so_dinh_danh text)
RETURNS TABLE (mabn varchar, macn varchar, ket_noi boolean)
LANGUAGE plpgsql STABLE SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
DECLARE
    cn record;
BEGIN
    IF p_so_dinh_danh IS NULL OR length(trim(p_so_dinh_danh)) < 6 THEN
        RETURN;
    END IF;
    FOR cn IN SELECT * FROM (VALUES ('s1', 'CN1'), ('s2', 'CN2'), ('s3', 'CN3')) AS t(sch, ma) LOOP
        BEGIN
            RETURN QUERY EXECUTE format(
                'SELECT b.mabn::varchar, %L::varchar, true FROM %I.benh_nhan b WHERE b.cccd = $1 OR b.sothebhyt = $1',
                cn.ma, cn.sch) USING trim(p_so_dinh_danh);
        EXCEPTION WHEN OTHERS THEN
            RETURN QUERY SELECT NULL::varchar, cn.ma::varchar, false;
        END;
    END LOOP;
END $$;

-- 3. BR4 toàn chuỗi: trong các bệnh vừa chẩn đoán, bệnh nào bệnh nhân đang điều trị dở ở chi nhánh khác (chỉ trả mã bệnh)
CREATE OR REPLACE FUNCTION benh_dang_dieu_tri_noi_khac(p_mabn text, p_mabenh text[], p_tru_macn text)
RETURNS TABLE (mabenh varchar, ket_noi boolean)
LANGUAGE plpgsql STABLE SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
DECLARE
    cn record;
BEGIN
    FOR cn IN SELECT * FROM (VALUES ('s1', 'CN1'), ('s2', 'CN2'), ('s3', 'CN3')) AS t(sch, ma) LOOP
        CONTINUE WHEN cn.ma = p_tru_macn;
        BEGIN
            RETURN QUERY EXECUTE format(
                'SELECT DISTINCT cd.mabenh::varchar, true FROM %1$I.chan_doan_dieu_tri cd '
                    || 'JOIN %1$I.lan_kham lk ON lk.maluot = cd.maluotkham '
                    || 'WHERE lk.mabn = $1 AND cd.mabenh = ANY ($2) AND cd.trangthai = %2$L',
                cn.sch, 'Đang điều trị') USING p_mabn, p_mabenh;
        EXCEPTION WHEN OTHERS THEN
            RETURN QUERY SELECT NULL::varchar, false;
        END;
    END LOOP;
END $$;

-- 4. Sinh mã bệnh nhân: các mã đã cấp với một tiền tố (BN-CN1-…) trên cả chuỗi — mã không phải thông tin nhạy cảm
CREATE OR REPLACE FUNCTION ma_benh_nhan_theo_tien_to(p_tien_to text)
RETURNS TABLE (mabn varchar, ket_noi boolean)
LANGUAGE plpgsql STABLE SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
DECLARE
    cn record;
BEGIN
    FOR cn IN SELECT * FROM (VALUES ('s1', 'CN1'), ('s2', 'CN2'), ('s3', 'CN3')) AS t(sch, ma) LOOP
        BEGIN
            RETURN QUERY EXECUTE format(
                'SELECT b.mabn::varchar, true FROM %I.benh_nhan b WHERE starts_with(b.mabn, $1)', cn.sch)
                USING p_tien_to;
        EXCEPTION WHEN OTHERS THEN
            RETURN QUERY SELECT NULL::varchar, false;
        END;
    END LOOP;
END $$;

-- 5. Quyền: hàm mới mặc định cho PUBLIC chạy → thu lại, chỉ cấp cho chi_nhanh_tra_cuu
REVOKE ALL ON FUNCTION tim_ma_benh_nhan(text) FROM PUBLIC;
REVOKE ALL ON FUNCTION benh_dang_dieu_tri_noi_khac(text, text[], text) FROM PUBLIC;
REVOKE ALL ON FUNCTION ma_benh_nhan_theo_tien_to(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION tim_ma_benh_nhan(text) TO chi_nhanh_tra_cuu;
GRANT EXECUTE ON FUNCTION benh_dang_dieu_tri_noi_khac(text, text[], text) TO chi_nhanh_tra_cuu;
GRANT EXECUTE ON FUNCTION ma_benh_nhan_theo_tien_to(text) TO chi_nhanh_tra_cuu;
