package vn.antam.clinic.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Một đợt điều trị (CHAN_DOAN_DIEU_TRI) của bệnh nhân, kèm tiến trình.
 *
 * @param soLanDaChua    số lần chữa đã thực hiện (LAN_CHUA) — cho biết đang chữa lần thứ mấy
 * @param ngayBatDau     thời gian lần khám mở đợt
 * @param maBS           bác sĩ đã khám mở đợt — mọi lần chữa của đợt do bác sĩ này thực hiện (BR2)
 * @param maKhoa         khoa của lần khám mở đợt
 * @param maCn           chi nhánh của đợt (theo khoa)
 * @param lanChuaGanNhat thời gian lần chữa gần nhất, null nếu chưa chữa lần nào
 * @param luotGanNhat    thời gian lượt gần nhất của đợt (khám mở đợt hoặc lần chữa) — lần chữa mới phải sau mốc này (BR3)
 * @param ngayHenTaiKham ngày hẹn ghi ở lượt gần nhất của đợt
 * @param quaHan         đợt còn "Đang điều trị" nhưng quá 30 ngày bệnh nhân không quay lại (BR12)
 */
public record DotDieuTriResponse(
        String maCDDT,
        String maBenh,
        String tenBenh,
        String maICD10,
        String mucDo,
        Integer soLanChuaCanThiet,
        long soLanDaChua,
        String trangThai,
        LocalDateTime ngayBatDau,
        LocalDateTime ngayKetThuc,
        String maLuotKham,
        String maBN,
        String tenBN,
        String maBS,
        String tenBS,
        String maKhoa,
        String tenKhoa,
        String maCn,
        LocalDateTime lanChuaGanNhat,
        LocalDateTime luotGanNhat,
        LocalDate ngayHenTaiKham,
        boolean quaHan) {
}
