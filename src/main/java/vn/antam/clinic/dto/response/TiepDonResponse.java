package vn.antam.clinic.dto.response;

import java.util.List;

/**
 * Tra cứu khi tiếp đón.
 *
 * @param taiChiNhanh     hồ sơ tại chi nhánh khớp mã, họ tên, CCCD, thẻ BHYT hoặc số điện thoại
 * @param noiKhac         chỉ khi từ khoá là số CCCD / thẻ BHYT đầy đủ và chi nhánh chưa có hồ sơ: mã bệnh nhân và chi nhánh
 *                        đã lập hồ sơ — không kèm thông tin cá nhân
 * @param daTraToanChuoi  đã tra trên toàn chuỗi chưa (chỉ tra khi từ khoá giống số định danh)
 * @param canhBao         không tra được toàn chuỗi, hoặc có chi nhánh đang mất kết nối; null nếu không có
 */
public record TiepDonResponse(List<BenhNhanTiepDonResponse> taiChiNhanh, List<ChiMucBenhNhanResponse> noiKhac,
                              boolean daTraToanChuoi, String canhBao) {
}
