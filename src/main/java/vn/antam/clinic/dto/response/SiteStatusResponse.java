package vn.antam.clinic.dto.response;

import java.util.Map;

/**
 * Trạng thái kết nối của một trạm CSDL (màn "Tổng quan hệ thống").
 *
 * @param role   HUB (S0) hoặc BRANCH (S1..S3)
 * @param counts số dòng một vài bảng chính; null nếu không kết nối được
 * @param error  lỗi kết nối, hoặc cảnh báo "chưa có bảng" khi kết nối được nhưng chưa nạp schema
 */
public record SiteStatusResponse(String site, String role, String name, String maCn, String jdbcUrl, boolean up,
                                 Long latencyMs, String database, Map<String, Long> counts, String error) {
}
