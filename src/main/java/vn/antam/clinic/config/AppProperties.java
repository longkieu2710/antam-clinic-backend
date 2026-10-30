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
 *   mode: all            # all | branch | hq (biến môi trường APP_MODE)
 *   local-site: S1       # chỉ dùng khi mode = branch (APP_LOCAL_SITE)
 *   hub-site: S0
 *   db: { username: admin, password: password123, pool-size: 5 }
 *   sites:
 *     s0: { name: Trung tâm, url: jdbc:postgresql://localhost:5430/ql_phongkham_hub }
 *     s1: { name: Cầu Giấy, ma-cn: CN1, url: jdbc:postgresql://localhost:5431/ql_phongkham_cn1 }
 * </pre>
 *
 * Mã trạm (s0, s1...) được chuẩn hoá thành chữ in hoa: S0, S1... và sắp theo mã.
 * Ở chế độ {@link Mode#BRANCH} chỉ giữ trạm trung tâm và trạm cục bộ — các trạm khác trong file bị bỏ qua.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Mode mode, String localSite, String hubSite, Db db, Map<String, Site> sites) {

    /** Bản triển khai của ứng dụng: cùng một source, chọn bằng APP_MODE. */
    public enum Mode {
        /** Một bản phục vụ mọi trạm, chọn chi nhánh bằng header X-Site (cách chạy demo ban đầu). */
        ALL,
        /** Bản đặt tại một cơ sở: dữ liệu chi nhánh luôn ở {@code local-site}; S0 chỉ cần khi dùng chức năng chung. */
        BRANCH,
        /** Bản Trung tâm: chỉ phục vụ danh mục chung và báo cáo toàn chuỗi tại S0. */
        HQ
    }

    @ConstructorBinding
    public AppProperties {
        mode = mode == null ? Mode.ALL : mode;
        hubSite = hubSite == null ? "S0" : hubSite.toUpperCase(Locale.ROOT);
        localSite = localSite == null || localSite.isBlank() ? null : localSite.trim().toUpperCase(Locale.ROOT);
        db = db == null ? new Db(null, null, null) : db;
        // Sắp theo mã trạm (S0, S1...): thứ tự khai báo không giữ được khi biến môi trường ghi đè một trạm
        Map<String, Site> normalized = new LinkedHashMap<>();
        if (sites != null) {
            new TreeMap<>(sites).forEach((key, value) -> normalized.put(key.toUpperCase(Locale.ROOT), value));
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
        sites = normalized;
    }

    /** Cấu hình kiểu cũ (chế độ {@link Mode#ALL}) — dùng trong test. */
    public AppProperties(String hubSite, Db db, Map<String, Site> sites) {
        this(null, null, hubSite, db, sites);
    }

    /** Tài khoản và kích thước pool dùng chung cho mọi trạm (có thể ghi đè từng trạm). */
    public record Db(String username, String password, Integer poolSize) {
        public Db {
            poolSize = poolSize == null ? 5 : poolSize;
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
