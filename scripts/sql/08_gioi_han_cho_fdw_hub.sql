-- ============================================================================
-- GIỚI HẠN THỜI GIAN CHỜ CỦA postgres_fdw — chạy tại Trạm Trung tâm S0, SAU 02_setup_distributed_hub.sql
-- Bổ sung của nhóm ứng dụng; chỉ thêm tuỳ chọn kết nối cho 3 foreign server, không sửa cấu trúc nào. Chạy lại được.
--
-- S0 giữ sẵn kết nối FDW tới từng chi nhánh. Khi một chi nhánh mất mạng (tắt Tailscale, rút dây), gói tin bị rơi mà
-- không có phản hồi: câu lệnh qua FDW chờ tới khi TCP tự bỏ cuộc — nhiều phút — làm treo báo cáo toàn chuỗi và hàm tra
-- cứu (07_bao_mat_hub.sql). Các tuỳ chọn libpq dưới đây cắt kết nối chết sau vài giây để S0 báo lỗi ngay:
--   connect_timeout   — mở kết nối mới tối đa 5 giây
--   tcp_user_timeout  — dữ liệu đã gửi mà 5 giây không được xác nhận thì coi kết nối đã chết
--   keepalives_*      — kết nối đang rảnh: dò sau 10 giây, mỗi 5 giây, 2 lần không trả lời thì đóng
-- ============================================================================
DO $$
DECLARE
    srv text;
    opt record;
BEGIN
    FOREACH srv IN ARRAY ARRAY['srv_chinhanh_1', 'srv_chinhanh_2', 'srv_chinhanh_3'] LOOP
        CONTINUE WHEN NOT EXISTS (SELECT 1 FROM pg_foreign_server WHERE srvname = srv);
        FOR opt IN SELECT * FROM (VALUES ('connect_timeout', '5'), ('tcp_user_timeout', '5000'), ('keepalives_idle', '10'),
                                         ('keepalives_interval', '5'), ('keepalives_count', '2')) AS t(ten, gia_tri) LOOP
            IF EXISTS (SELECT 1 FROM pg_foreign_server s, unnest(s.srvoptions) o
                       WHERE s.srvname = srv AND o LIKE opt.ten || '=%') THEN
                EXECUTE format('ALTER SERVER %I OPTIONS (SET %I %L)', srv, opt.ten, opt.gia_tri);
            ELSE
                EXECUTE format('ALTER SERVER %I OPTIONS (ADD %I %L)', srv, opt.ten, opt.gia_tri);
            END IF;
        END LOOP;
    END LOOP;
END $$;
