package vn.antam.clinic.dto.response;

import vn.antam.clinic.config.AppProperties;
import vn.antam.clinic.entity.ChiNhanh;

/**
 * Chi nhánh kèm trạm CSDL tương ứng (ghép bảng CHI_NHANH ở S0 với cấu hình app.sites).
 *
 * @param site       mã trạm (S1..S3); null nếu chi nhánh có trong CSDL nhưng chưa khai báo trạm
 * @param inDatabase false nếu không đọc được S0 — khi đó chỉ có thông tin từ cấu hình
 */
public record ChiNhanhResponse(String site, String maCn, String tenCn, String shortName, String diaChi,
                               String soDienThoai, String jdbcUrl, boolean inDatabase) {

    /**
     * @param siteId mã trạm, null nếu không có trạm nào khớp
     * @param site   cấu hình trạm, null khi {@code siteId} null
     * @param row    dòng CHI_NHANH, null khi không đọc được S0 (phải có {@code site})
     */
    public static ChiNhanhResponse from(String siteId, AppProperties.Site site, ChiNhanh row) {
        String shortName = site != null ? site.name() : null;
        String jdbcUrl = site != null ? site.url() : null;
        if (row == null) {
            return new ChiNhanhResponse(siteId, site.maCn(), shortName, shortName, null, null, jdbcUrl, false);
        }
        return new ChiNhanhResponse(siteId, row.maCn(), row.tenCn(), shortName, row.diaChi(), row.soDienThoai(),
                jdbcUrl, true);
    }
}
