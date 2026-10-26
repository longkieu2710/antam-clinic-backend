package vn.antam.clinic.site;

import java.util.function.Supplier;

/**
 * Giữ mã trạm (S0..S3) của request hiện tại trong ThreadLocal.
 * {@link SiteFilter} đặt giá trị ở đầu request; {@link SiteRoutingDataSource} đọc để chọn pool kết nối.
 */
public final class SiteContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private SiteContext() {
    }

    public static String get() {
        return CURRENT.get();
    }

    public static void set(String siteId) {
        CURRENT.set(siteId);
    }

    public static void clear() {
        CURRENT.remove();
    }

    /**
     * Chạy một đoạn code trên trạm chỉ định rồi trả lại trạm cũ.
     * Chỉ dùng NGOÀI transaction: kết nối được lấy lúc bắt đầu transaction nên đổi trạm giữa chừng không có tác dụng.
     */
    public static <T> T callOn(String siteId, Supplier<T> action) {
        String previous = CURRENT.get();
        CURRENT.set(siteId);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
