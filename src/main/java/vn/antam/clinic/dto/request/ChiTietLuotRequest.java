package vn.antam.clinic.dto.request;

import java.util.List;

/** Phần chung của lượt khám và lượt chữa: y tá hỗ trợ, dịch vụ, thuốc, thiết bị. Danh sách null = không có. */
public interface ChiTietLuotRequest {

    List<YTaHoTroRequest> yTa();

    List<DichVuDungRequest> dichVu();

    List<ThuocKeRequest> thuoc();

    List<ThietBiDungRequest> thietBi();
}
