package vn.antam.clinic.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;

/** Sinh mã kế tiếp theo tiền tố, ví dụ BS-CN1-01 → BS-CN1-02, T04 → T05. */
public final class Codes {

    private Codes() {
    }

    /**
     * @param prefix  tiền tố cố định (vd "BS-CN1-", "T")
     * @param existing các mã đang có (chỉ tính mã bắt đầu bằng prefix và phần sau là số)
     * @param width   số chữ số tối thiểu
     */
    public static String next(String prefix, Collection<String> existing, int width) {
        int max = 0;
        for (String code : existing) {
            if (code != null && code.startsWith(prefix)) {
                String tail = code.substring(prefix.length());
                if (!tail.isEmpty() && tail.chars().allMatch(Character::isDigit)) {
                    max = Math.max(max, Integer.parseInt(tail));
                }
            }
        }
        return prefix + String.format("%0" + width + "d", max + 1);
    }

    private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd");

    /**
     * Tiền tố mã lượt khám / chữa (BR5) — mã chứa khoa và bác sĩ như đề bài yêu cầu, vd
     * {@code K-CN1-TMH-BS-CN1-01-261024-}; nối thêm số thứ tự 3 chữ số bằng {@link #next}.
     *
     * @param loai "K" (khám) hoặc "C" (chữa)
     */
    public static String luotPrefix(String loai, String maKhoa, String maBS, LocalDateTime thoiGian) {
        return loai + "-" + maKhoa + "-" + maBS + "-" + YYMMDD.format(thoiGian) + "-";
    }

    /** Chuỗi rỗng / toàn khoảng trắng → null, để không vi phạm UNIQUE với giá trị ''. */
    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
