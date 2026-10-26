package vn.antam.clinic.dto.response;

import java.util.List;

/**
 * Doanh thu toàn chuỗi, tính ở S0 trên view toàn cục (postgres_fdw tới 3 chi nhánh).
 *
 * @param tongHop           số liệu gộp cả chuỗi (cùng dạng F5: theo tháng, chi tiết từng khoản)
 * @param chiNhanh          tổng của từng chi nhánh trong kỳ
 * @param theoThangChiNhanh từng chi nhánh × từng tháng
 */
public record DoanhThuToanChuoiResponse(
        DoanhThuResponse tongHop,
        List<DoanhThuChiNhanhResponse> chiNhanh,
        List<DoanhThuChiNhanhThangResponse> theoThangChiNhanh) {
}
