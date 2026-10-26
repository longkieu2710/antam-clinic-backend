package vn.antam.clinic.exception;

import org.springframework.http.HttpStatus;

/** Lỗi nghiệp vụ do ứng dụng tự phát hiện (không tìm thấy, đang được sử dụng...). */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public BusinessException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static BusinessException notFound(String what, String id) {
        return new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy " + what + " '" + id + "'.");
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
