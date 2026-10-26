package vn.antam.clinic.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Bệnh nhân của chi nhánh, kèm số liệu tổng hợp từ LAN_KHAM / CHAN_DOAN_DIEU_TRI (đọc thẳng từ truy vấn).
 *
 * @param soLanKham        số lần khám (LAN_KHAM) tại chi nhánh này
 * @param lanKhamGanNhat   thời gian lần khám gần nhất, null nếu chưa khám
 * @param soDotDangDieuTri số đợt điều trị đang ở trạng thái "Đang điều trị"
 */
public record BenhNhanResponse(
        String maBN,
        String hoTen,
        LocalDate ngaySinh,
        String gioiTinh,
        String cccd,
        String soDienThoai,
        String diaChi,
        String soTheBHYT,
        String nhomMau,
        String tienSuDiUng,
        long soLanKham,
        LocalDateTime lanKhamGanNhat,
        long soDotDangDieuTri) {
}
