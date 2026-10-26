package vn.antam.clinic.dto.request;

import java.time.LocalDateTime;

/** Đánh dấu đợt "Ngừng điều trị" (bệnh nhân bỏ dở — BR12). @param ngayKetThuc null = thời điểm hiện tại */
public record NgungDieuTriRequest(LocalDateTime ngayKetThuc, String lyDo) {
}
