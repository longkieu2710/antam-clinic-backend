package vn.antam.clinic.exception;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import vn.antam.clinic.dto.response.ApiErrorResponse;
import vn.antam.clinic.site.SiteContext;

/**
 * Đổi mọi lỗi thành {@link ApiErrorResponse} với thông báo tiếng Việt.
 * Lỗi từ PostgreSQL được phân loại theo SQLSTATE — chính là minh chứng các ràng buộc trong CSDL đang hoạt động.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Tên foreign server của nhóm CSDL: srv_chinhanh_1..3 ↔ trạm S1..S3. */
    private static final Pattern FDW_SERVER = Pattern.compile("srv_chinhanh_(\\d)");

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> business(BusinessException ex) {
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), null, null, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> fields.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION", "Dữ liệu chưa hợp lệ, kiểm tra các trường được đánh dấu.", null, null, fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> unreadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "BAD_JSON", "Dữ liệu gửi lên sai định dạng (ngày phải là yyyy-MM-dd, số phải là số).", null, null, null);
    }

    // CannotCreateTransactionException: method @Transactional không mở được kết nối khi trạm tắt
    // (chạy bằng Docker, container đã dừng thì tên site_sX… không còn phân giải được → UnknownHostException)
    @ExceptionHandler({DataAccessException.class, TransactionSystemException.class, CannotCreateTransactionException.class})
    public ResponseEntity<ApiErrorResponse> database(Exception ex) {
        // Rollback lỗi (thường vì kết nối đã bị Hikari đóng sau lỗi 08xxx) đè lên lỗi gốc → báo theo lỗi gốc
        if (ex instanceof TransactionSystemException tse && tse.getApplicationException() != null) {
            Throwable app = tse.getApplicationException();
            if (app instanceof BusinessException be) {
                return business(be);
            }
            if (app instanceof Exception e && !(e instanceof TransactionSystemException)) {
                return database(e);
            }
        }
        SQLException sql = findSqlException(ex);
        String state = sql != null ? sql.getSQLState() : null;
        String dbMessage = null;
        String detail = null;
        if (sql instanceof PSQLException psql && psql.getServerErrorMessage() != null) {
            ServerErrorMessage server = psql.getServerErrorMessage();
            dbMessage = server.getMessage();
            detail = joinNonNull(server.getDetail(), server.getConstraint() != null ? "Ràng buộc: " + server.getConstraint() : null);
        } else if (sql != null) {
            dbMessage = sql.getMessage();
        }

        // postgres_fdw ở S0 không tới được một chi nhánh: 'could not connect to server "srv_chinhanh_3"'
        Matcher fdw = FDW_SERVER.matcher(nvl(dbMessage, "") + " " + rootMessage(ex));
        if (fdw.find() && (state == null || state.startsWith("08"))) {
            String branch = "S" + fdw.group(1);
            return build(HttpStatus.SERVICE_UNAVAILABLE, "CHAIN_UNAVAILABLE",
                    "Trạm " + nvl(SiteContext.get(), "S0") + " không kết nối được tới chi nhánh " + branch
                            + " qua postgres_fdw (" + fdw.group(0) + ") — kiểm tra container của trạm " + branch + ".",
                    nvl(dbMessage, rootMessage(ex)), state, null);
        }
        if (ex instanceof CannotGetJdbcConnectionException || ex instanceof CannotCreateTransactionException
                || (state != null && state.startsWith("08"))
                || (state == null && sql != null && "Connection is closed".equals(sql.getMessage()))) {
            return build(HttpStatus.SERVICE_UNAVAILABLE, "SITE_UNAVAILABLE",
                    "Không kết nối được trạm CSDL " + nvl(SiteContext.get(), "") + ". Kiểm tra container Docker của trạm này.",
                    nvl(dbMessage, rootMessage(ex)), state, null);
        }
        if (state == null) {
            log.error("Lỗi CSDL không rõ SQLSTATE", ex);
            return build(HttpStatus.INTERNAL_SERVER_ERROR, "DB_ERROR", "Lỗi CSDL: " + rootMessage(ex), null, null, null);
        }
        return switch (state) {
            case "23505" -> build(HttpStatus.CONFLICT, "DUPLICATE", "Dữ liệu bị trùng — giá trị này đã tồn tại (vi phạm khoá chính / UNIQUE).", detail, state, null);
            case "23503" -> build(HttpStatus.CONFLICT, "FOREIGN_KEY", "Không thực hiện được: còn dữ liệu liên quan, hoặc mã tham chiếu không tồn tại (vi phạm khoá ngoại).", detail, state, null);
            case "23514" -> build(HttpStatus.BAD_REQUEST, "CHECK", "Giá trị không hợp lệ (vi phạm ràng buộc CHECK).", detail, state, null);
            case "23502" -> build(HttpStatus.BAD_REQUEST, "NOT_NULL", "Thiếu dữ liệu bắt buộc (NOT NULL).", nvl(detail, dbMessage), state, null);
            case "22001" -> build(HttpStatus.BAD_REQUEST, "TOO_LONG", "Dữ liệu vượt quá độ dài cho phép.", dbMessage, state, null);
            // RAISE EXCEPTION trong trigger / function của nhóm DB: dùng nguyên thông báo của DB
            case "P0001" -> build(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE", nvl(dbMessage, "Vi phạm quy tắc nghiệp vụ."), detail, state, null);
            default -> {
                log.error("Lỗi CSDL SQLSTATE {}", state, ex);
                yield build(HttpStatus.INTERNAL_SERVER_ERROR, "DB_ERROR", "Lỗi CSDL: " + nvl(dbMessage, rootMessage(ex)), detail, state, null);
            }
        };
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> unexpected(Exception ex) {
        // Gọi /api/... mà backend không có controller nào khớp: Spring rơi về bộ phục vụ file tĩnh và báo
        // "No static resource api/...". Hay gặp nhất khi FE đã cập nhật nhưng backend đang chạy là bản build cũ.
        if (ex instanceof NoResourceFoundException nrf && nrf.getResourcePath().startsWith("api/")) {
            return build(HttpStatus.NOT_FOUND, "API_NOT_FOUND", "Backend đang chạy không có API /" + nrf.getResourcePath()
                    + ". Nếu vừa cập nhật code, backend đang chạy là bản build cũ: dừng backend, build lại (IntelliJ: Build → "
                    + "Rebuild Project, hoặc ./mvnw clean spring-boot:run) rồi chạy lại.", null, null, null);
        }
        // Lỗi HTTP chuẩn của Spring MVC (404 không có route, 405 sai method...) giữ nguyên mã trạng thái
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            return build(status, "HTTP_" + status.value(), ex.getMessage(), null, null, null);
        }
        if (ex instanceof TypeMismatchException) {
            return build(HttpStatus.BAD_REQUEST, "BAD_PARAM", "Tham số không hợp lệ: " + ex.getMessage(), null, null, null);
        }
        log.error("Lỗi không mong muốn", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL", "Lỗi hệ thống: " + rootMessage(ex), null, null, null);
    }

    private static ResponseEntity<ApiErrorResponse> build(HttpStatus status, String code, String message, String detail,
                                                  String sqlState, Map<String, String> fields) {
        ApiErrorResponse body = new ApiErrorResponse(status.value(), code, message, detail, sqlState, SiteContext.get(), fields);
        return ResponseEntity.status(status).body(body);
    }

    private static SQLException findSqlException(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql) {
                return sql;
            }
        }
        return null;
    }

    private static String rootMessage(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getMessage();
    }

    private static String joinNonNull(String a, String b) {
        if (a == null) {
            return b;
        }
        return b == null ? a : a + " — " + b;
    }

    private static String nvl(String value, String fallback) {
        return value != null ? value : fallback;
    }
}
