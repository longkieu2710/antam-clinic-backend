-- Dữ liệu demo 3 tháng (08–10/2026) cho S1 · Cầu Giấy — để xem F2–F5 có số liệu.
-- KHÔNG thuộc bộ script của nhóm CSDL (CSDL/). Chạy sau init_db.sh, chỉ trên S1:
--   cd source_code/backend/scripts && docker exec -i site_s1_caugiay psql -U admin -d ql_phongkham_cn1 < demo_data_s1.sql
-- Cả file trong 1 transaction: chạy lần 2 sẽ báo trùng khoá và không ghi gì thêm.
-- Kết quả mong đợi: F3 tháng 09/2026 tổng quỹ lương 31.247.800 ₫; F5 01/08–31/10/2026 tổng 1.937.000 ₫.
BEGIN;
INSERT INTO khoa VALUES ('CN1-NOI', 'Khoa Nội tổng quát Cầu Giấy', 'Khám nội tổng quát', 'CN1');
INSERT INTO nhan_vien (manv, hoten, ngaysinh, gioitinh, cccd, diachi, hesoluong, ngayvaolam, loainv, makhoa) VALUES
 ('BS-CN1-02', 'Bác sĩ Trần Thị Hồng', '1984-03-02', 'Nữ', '001084005555', 'Cầu Giấy, HN', 4.00, '2019-06-01', 'BS', 'CN1-NOI'),
 ('YT-CN1-02', 'Y tá Đỗ Văn Khánh', '1995-11-11', 'Nam', '001095007777', 'Cầu Giấy, HN', 2.67, '2022-02-01', 'YT', 'CN1-NOI');
INSERT INTO bac_si VALUES ('BS-CN1-02', 'Nội tổng quát', 'BS CKI', 'CCHN-BS-002');
INSERT INTO y_ta VALUES ('YT-CN1-02', 'Cao đẳng Điều dưỡng', 'CCHN-YT-002');
INSERT INTO nv_sodienthoai VALUES ('BS-CN1-02', '0904000222'), ('YT-CN1-01', '0904000111');
INSERT INTO benh_nhan (mabn, hoten, ngaysinh, gioitinh, cccd, sodienthoai, diachi, sothebhyt, nhommau, tiensudiung) VALUES
 ('BN10', 'Phạm Minh Đức', '1988-01-20', 'Nam', '001088010101', '0911000010', 'Hà Nội', 'DN4010000000010', 'A', NULL),
 ('BN11', 'Lê Thu Hà', '1999-07-07', 'Nữ', '001099011111', '0911000011', 'Hà Nội', 'DN4010000000011', 'B', 'Penicillin'),
 ('BN12', 'Hoàng Văn Nam', '1975-12-12', 'Nam', '001075012121', '0911000012', 'Hà Nội', NULL, 'O', NULL);

INSERT INTO luot_kham_chua (maluot, thoigian, loailuot, ngayhentaikham, makhoa, mabs) VALUES
 ('C-CN1-002', '2026-09-15 10:00', 'CHUA', NULL,         'CN1-TMH', 'BS-CN1-01'),
 ('K-CN1-010', '2026-08-20 09:00', 'KHAM', '2026-08-27', 'CN1-TMH', 'BS-CN1-01'),
 ('C-CN1-010', '2026-08-27 09:00', 'CHUA', '2026-09-03', 'CN1-TMH', 'BS-CN1-01'),
 ('C-CN1-011', '2026-09-03 09:00', 'CHUA', '2026-09-10', 'CN1-TMH', 'BS-CN1-01'),
 ('K-CN1-020', '2026-09-20 14:00', 'KHAM', '2026-10-02', 'CN1-NOI', 'BS-CN1-02'),
 ('C-CN1-020', '2026-10-02 08:00', 'CHUA', NULL,         'CN1-NOI', 'BS-CN1-02'),
 ('K-CN1-030', '2026-10-05 10:00', 'KHAM', NULL,         'CN1-NOI', 'BS-CN1-02'),
 ('K-CN1-040', '2026-10-10 09:00', 'KHAM', '2026-10-17', 'CN1-TMH', 'BS-CN1-01');
INSERT INTO lan_kham VALUES
 ('K-CN1-010', 'BN10', 'Nghẹt mũi kéo dài', 'Đau nhức vùng má', 'Viêm xoang hàm'),
 ('K-CN1-020', 'BN11', 'Đau bụng, đau họng', 'Đau thượng vị, họng đỏ', 'Viêm dạ dày, viêm họng'),
 ('K-CN1-030', 'BN12', 'Khám sức khoẻ', 'Không có triệu chứng', 'Chưa phát hiện bệnh'),
 ('K-CN1-040', 'BN01', 'Đau họng tái phát', 'Họng đỏ', 'Viêm họng cấp tái phát');
INSERT INTO chan_doan_dieu_tri (macddt, maluotkham, mabenh, mucdo, solanchuacanthiet, ngayketthuc, trangthai) VALUES
 ('CDDT-CN1-010', 'K-CN1-010', 'B02', 'Trung bình', 3, NULL, 'Đang điều trị'),
 ('CDDT-CN1-020', 'K-CN1-020', 'B04', 'Nhẹ', 1, '2026-10-02 08:30', 'Đã khỏi'),
 ('CDDT-CN1-021', 'K-CN1-020', 'B01', 'Nhẹ', 1, '2026-11-01 00:00', 'Ngừng điều trị'),
 ('CDDT-CN1-040', 'K-CN1-040', 'B01', 'Nhẹ', 2, NULL, 'Đang điều trị');
UPDATE chan_doan_dieu_tri SET trangthai = 'Đã khỏi', ngayketthuc = '2026-09-15 10:30' WHERE macddt = 'CDDT-CN1-001';
INSERT INTO lan_chua VALUES
 ('C-CN1-002', 'CDDT-CN1-001', 2, 'Khỏi', 'Hết đau họng'),
 ('C-CN1-010', 'CDDT-CN1-010', 1, 'Đỡ', 'Giảm nghẹt mũi'),
 ('C-CN1-011', 'CDDT-CN1-010', 2, 'Đỡ nhiều', 'Còn chảy dịch nhẹ'),
 ('C-CN1-020', 'CDDT-CN1-020', 1, 'Khỏi', 'Hết đau thượng vị');
INSERT INTO lan_chua_hinh_thuc VALUES
 ('C-CN1-002', 'Khí dung họng'), ('C-CN1-010', 'Rửa xoang'), ('C-CN1-011', 'Rửa xoang'), ('C-CN1-011', 'Khí dung'),
 ('C-CN1-020', 'Tư vấn dinh dưỡng');
INSERT INTO ho_tro VALUES
 ('YT-CN1-01', 'C-CN1-002', 'Khí dung'), ('YT-CN1-01', 'K-CN1-010', 'Tiếp đón'), ('YT-CN1-01', 'C-CN1-010', 'Rửa xoang'),
 ('YT-CN1-02', 'K-CN1-020', 'Tiếp đón'), ('YT-CN1-02', 'C-CN1-020', 'Theo dõi'), ('YT-CN1-01', 'K-CN1-040', 'Tiếp đón');
INSERT INTO su_dung_dv VALUES
 ('K-CN1-010', 'DV01', 1, 150000, NULL), ('K-CN1-010', 'DV02', 1, 250000, 'Niêm mạc xoang phù nề'),
 ('K-CN1-020', 'DV01', 1, 150000, NULL), ('K-CN1-020', 'DV04', 1, 120000, 'Bạch cầu tăng nhẹ'),
 ('K-CN1-030', 'DV01', 1, 150000, NULL), ('K-CN1-030', 'DV04', 1, 120000, 'Bình thường'),
 ('K-CN1-040', 'DV01', 1, 150000, NULL);
INSERT INTO ke_don VALUES
 ('K-CN1-010', 'T03', 5, 'Ngày 1 gói', 25000),
 ('K-CN1-020', 'T04', 14, 'Sáng 1 viên trước ăn', 8000), ('K-CN1-020', 'T02', 10, 'Khi sốt', 2000),
 ('K-CN1-040', 'T01', 10, 'Ngày 2 viên', 5000);
INSERT INTO su_dung_tb VALUES ('C-CN1-002', 'TB-CN1-01', 1, 100000), ('C-CN1-010', 'TB-CN1-01', 1, 100000);
COMMIT;
