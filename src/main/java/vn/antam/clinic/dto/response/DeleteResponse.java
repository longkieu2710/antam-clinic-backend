package vn.antam.clinic.dto.response;

/**
 * Kết quả thao tác xoá.
 *
 * @param mode    HARD = đã xoá khỏi CSDL; SOFT = không xoá mà chuyển trạng thái (đối tượng đã phát sinh dữ liệu, BR9)
 * @param message thông báo cho người dùng
 */
public record DeleteResponse(String mode, String message) {

    public static DeleteResponse hard(String message) {
        return new DeleteResponse("HARD", message);
    }

    public static DeleteResponse soft(String message) {
        return new DeleteResponse("SOFT", message);
    }
}
