package vn.antam.clinic.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Cấu hình các trạm CSDL, đọc từ khối {@code app:} trong application.yml.
 *
 * <pre>
 * app:
 *   may-nay: cn1         # demo nhiều máy: hq | cn1 | cn2 | cn3 — để trống: chạy cả cụm trên 1 máy
 *   dia-chi-may: { hq: 100.64.0.10, cn1: 100.64.0.11, cn2: 100.64.0.12, cn3: 100.64.0.13 }
 *   mode: all            # khi không dùng may-nay: all | branch | hq (biến môi trường APP_MODE)
 *   local-site: S1       # chỉ dùng khi mode = branch (APP_LOCAL_SITE)
 *   hub-site: S0
 *   db: { username: admin, password: password123, pool-size: 5 }
 *   sites:
 *     s0: { name: Trung tâm, url: jdbc:postgresql://localhost:5430/ql_phongkham_hub }
 *     s1: { name: Cầu Giấy, ma-cn: CN1, url: jdbc:postgresql://localhost:5431/ql_phongkham_cn1 }
 * </pre>
 *
 * Mã trạm (s0, s1...) được chuẩn hoá thành chữ in hoa: S0, S1... và sắp theo mã.
 * <p>
 * {@code may-nay} (máy demo của một cơ sở) suy ra mọi thứ còn lại: {@code hq} → {@link Mode#HQ}; {@code cnX} →
 * {@link Mode#BRANCH} với trạm có {@code ma-cn} = CNX. URL của trạm trên chính máy này dùng {@code db.host-cuc-bo}
 * (localhost, hoặc db-cuc-bo trong Docker), trạm của máy khác dùng địa chỉ trong {@code dia-chi-may}; cổng
 * {@code db.cong-may}, tên database giữ như trong {@code sites}. Ghi nhầm may-nay thì không có CSDL cục bộ đúng tên →
 * lỗi kết nối, chứ không ghi sang CSDL của máy khác.
 * <p>
 * Ở chế độ {@link Mode#BRANCH} chỉ giữ trạm trung tâm và trạm cục bộ — các trạm khác trong file bị bỏ qua.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(String mayNay, Map<String, String> diaChiMay, Mode mode, String localSite, String hubSite,
                            Db db, Map<String, Site> sites) {

    /** Giá trị {@code may-nay} của máy Trung tâm. */
    public static final String MAY_HQ = "hq";

    /** Bản triển khai của ứng dụng: cùng một source, chọn bằng APP_MODE. */
    public enum Mode {
        /** Một bản phục vụ mọi trạm, chọn chi nhánh bằng header X-Site (cách chạy demo ban đầu). */
        ALL,
        /** Bản đặt tại một cơ sở: chỉ đọc / ghi dữ liệu của {@code local-site}; không vào chức năng Trung tâm. */
        BRANCH,
        /** Bản Trung tâm: danh mục chung + báo cáo toàn chuỗi tại S0; xem (không ghi) dữ liệu từng chi nhánh. */
        HQ
    }

    @ConstructorBinding
    public AppProperties {
        mode = mode == null ? Mode.ALL : mode;
        hubSite = hubSite == null ? "S0" : hubSite.toUpperCase(Locale.ROOT);
        localSite = localSite == null || localSite.isBlank() ? null : localSite.trim().toUpperCase(Locale.ROOT);
        mayNay = mayNay == null || mayNay.isBlank() ? null : mayNay.trim().toLowerCase(Locale.ROOT);
        Map<String, String> diaChi = new LinkedHashMap<>();
        if (diaChiMay != null) {
            diaChiMay.forEach((key, value) -> diaChi.put(key.toLowerCase(Locale.ROOT), value == null ? null : value.trim()));
        }
        diaChiMay = diaChi;
        db = db == null ? new Db(null, null, null) : db;
        // Sắp theo mã trạm (S0, S1...): thứ tự khai báo không giữ được khi biến môi trường ghi đè một trạm
        Map<String, Site> normalized = new LinkedHashMap<>();
        if (sites != null) {
            new TreeMap<>(sites).forEach((key, value) -> normalized.put(key.toUpperCase(Locale.ROOT), value));
        }
        if (mayNay != null) {
            String may = mayNay;
            String siteOfMay = may.equals(MAY_HQ) ? hubSite : normalized.entrySet().stream()
                    .filter(e -> may.equalsIgnoreCase(e.getValue().maCn()))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("app.may-nay = '" + may + "' không khớp máy nào: dùng "
                            + MAY_HQ + " hoặc ma-cn của một chi nhánh trong app.sites (cn1, cn2, cn3)."));
            mode = siteOfMay.equals(hubSite) ? Mode.HQ : Mode.BRANCH;
            localSite = mode == Mode.BRANCH ? siteOfMay : null;
        }
        if (mode == Mode.BRANCH) {
            if (localSite == null || localSite.equals(hubSite) || !normalized.containsKey(localSite)) {
                throw new IllegalStateException("APP_MODE=branch cần APP_LOCAL_SITE là một trạm chi nhánh có trong app.sites "
                        + "(đang là '" + localSite + "', các trạm đã khai báo: " + normalized.keySet() + ").");
            }
            String local = localSite;
            String hub = hubSite;
            normalized.keySet().removeIf(id -> !id.equals(local) && !id.equals(hub));
        }
        if (mode == Mode.HQ && !normalized.containsKey(hubSite)) {
            throw new IllegalStateException("APP_MODE=hq cần khai báo trạm trung tâm " + hubSite + " trong app.sites.");
        }
        if (mayNay != null) {
            String siteOfMay = mode == Mode.HQ ? hubSite : localSite;
            for (Map.Entry<String, Site> e : normalized.entrySet()) {
                Site site = e.getValue();
                String key = e.getKey().equals(hubSite) ? MAY_HQ : String.valueOf(site.maCn()).toLowerCase(Locale.ROOT);
                String host = e.getKey().equals(siteOfMay) ? db.hostCucBo() : diaChi.get(key);
                if (host == null || host.isBlank()) {
                    throw new IllegalStateException("Thiếu app.dia-chi-may." + key + " (địa chỉ máy " + site.name()
                            + ") — máy " + mayNay + " cần nối tới trạm " + e.getKey() + ".");
                }
                e.setValue(new Site(site.name(), site.maCn(), "jdbc:postgresql://" + host + ":" + db.congMay() + "/"
                        + databaseOf(site.url()), site.username(), site.password()));
            }
        }
        sites = normalized;
    }

    /** Cấu hình không dùng {@code may-nay} — giả lập trên 1 máy và test. */
    public AppProperties(Mode mode, String localSite, String hubSite, Db db, Map<String, Site> sites) {
        this(null, null, mode, localSite, hubSite, db, sites);
    }

    /** Cấu hình kiểu cũ (chế độ {@link Mode#ALL}) — dùng trong test. */
    public AppProperties(String hubSite, Db db, Map<String, Site> sites) {
        this(null, null, null, null, hubSite, db, sites);
    }

    private static String databaseOf(String url) {
        if (url == null || url.lastIndexOf('/') < 0) {
            throw new IllegalStateException("URL trạm không hợp lệ (cần dạng jdbc:postgresql://host:cổng/tên_db): " + url);
        }
        return url.substring(url.lastIndexOf('/') + 1);
    }

    /**
     * Tài khoản và kích thước pool dùng chung cho mọi trạm (có thể ghi đè từng trạm).
     *
     * @param traCuuUsername tài khoản bản chi nhánh dùng khi gọi về S0 — chỉ chạy được hàm tra cứu
     *                       (scripts/sql/07_bao_mat_hub.sql); null thì dùng {@code username}
     * @param congMay        cổng CSDL giữa các máy khi dùng {@code may-nay}
     * @param hostCucBo      host CSDL của chính máy này khi dùng {@code may-nay}
     */
    public record Db(String username, String password, Integer poolSize, String traCuuUsername, String traCuuPassword,
                     Integer congMay, String hostCucBo) {
        // Có thêm constructor 3 tham số → phải chỉ rõ constructor để bind, nếu không cả khối app.db bị bỏ qua
        @ConstructorBinding
        public Db {
            poolSize = poolSize == null ? 5 : poolSize;
            congMay = congMay == null ? 5432 : congMay;
            hostCucBo = hostCucBo == null || hostCucBo.isBlank() ? "localhost" : hostCucBo.trim();
        }

        public Db(String username, String password, Integer poolSize) {
            this(username, password, poolSize, null, null, null, null);
        }
    }

    /** Một trạm CSDL. maCn chỉ có ở trạm chi nhánh (khớp CHI_NHANH.MaCN). */
    public record Site(String name, String maCn, String url, String username, String password) {
    }

    /** Danh sách mã trạm chi nhánh (mọi trạm trừ trạm trung tâm), theo thứ tự khai báo. */
    public List<String> branchSites() {
        return sites.keySet().stream().filter(id -> !id.equals(hubSite)).toList();
    }

    public boolean isBranchSite(String siteId) {
        return siteId != null && !siteId.equals(hubSite) && sites.containsKey(siteId);
    }

    public Optional<Site> site(String siteId) {
        return Optional.ofNullable(sites.get(siteId));
    }
}
