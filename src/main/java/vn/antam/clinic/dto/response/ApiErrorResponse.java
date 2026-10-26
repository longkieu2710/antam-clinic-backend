package vn.antam.clinic.dto.response;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Định dạng lỗi trả về cho frontend.
 *
 * @param status      mã HTTP
 * @param code        mã lỗi ngắn để FE xử lý (DUPLICATE, FOREIGN_KEY, CHECK, BUSINESS_RULE, SITE_UNAVAILABLE...)
 * @param message     thông báo tiếng Việt hiển thị cho người dùng
 * @param detail      chi tiết kỹ thuật (DETAIL của PostgreSQL, tên ràng buộc...)
 * @param sqlState    SQLSTATE gốc của PostgreSQL (nếu có)
 * @param site        trạm CSDL đang xử lý request
 * @param fieldErrors lỗi theo từng trường (validation)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(int status, String code, String message, String detail, String sqlState, String site,
                       Map<String, String> fieldErrors) {

    public static ApiErrorResponse of(int status, String code, String message) {
        return new ApiErrorResponse(status, code, message, null, null, null, null);
    }
}
