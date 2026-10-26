package vn.antam.clinic.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình các trạm CSDL, đọc từ khối {@code app:} trong application.yml.
 *
 * <pre>
 * app:
 *   hub-site: S0
 *   db: { username: admin, password: password123, pool-size: 5 }
 *   sites:
 *     s0: { name: Trung tâm, url: jdbc:postgresql://localhost:5430/ql_phongkham_hub }
 *     s1: { name: Cầu Giấy, ma-cn: CN1, url: jdbc:postgresql://localhost:5431/ql_phongkham_cn1 }
 * </pre>
 *
 * Mã trạm (s0, s1...) được chuẩn hoá thành chữ in hoa: S0, S1...
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(String hubSite, Db db, Map<String, Site> sites) {

    public AppProperties {
        hubSite = hubSite == null ? "S0" : hubSite.toUpperCase(Locale.ROOT);
        db = db == null ? new Db(null, null, null) : db;
        Map<String, Site> normalized = new LinkedHashMap<>();
        if (sites != null) {
            sites.forEach((key, value) -> normalized.put(key.toUpperCase(Locale.ROOT), value));
        }
        sites = normalized;
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
