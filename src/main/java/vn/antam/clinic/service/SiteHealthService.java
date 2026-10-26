package vn.antam.clinic.service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Service;
import vn.antam.clinic.config.AppProperties;
import vn.antam.clinic.dto.response.SiteStatusResponse;
import vn.antam.clinic.site.SiteRoutingDataSource;

/**
 * Kiểm tra kết nối tới cả 4 trạm. Dùng thẳng pool của từng trạm (không đi qua X-Site / định tuyến),
 * nên không dùng repository hay transaction.
 */
@Service
public class SiteHealthService {

    private static final String HUB_COUNTS = """
            SELECT (SELECT count(*) FROM chi_nhanh), (SELECT count(*) FROM thuoc),
                   (SELECT count(*) FROM benh), (SELECT count(*) FROM dich_vu)""";
    private static final List<String> HUB_LABELS = List.of("Chi nhánh", "Thuốc", "Bệnh", "Dịch vụ");

    private static final String BRANCH_COUNTS = """
            SELECT (SELECT count(*) FROM nhan_vien), (SELECT count(*) FROM benh_nhan),
                   (SELECT count(*) FROM luot_kham_chua), (SELECT count(*) FROM thuoc)""";
    private static final List<String> BRANCH_LABELS = List.of("Nhân viên", "Bệnh nhân", "Lượt khám/chữa", "Thuốc (bản sao)");

    private final AppProperties props;
    private final SiteRoutingDataSource routing;

    public SiteHealthService(AppProperties props, SiteRoutingDataSource routing) {
        this.props = props;
        this.routing = routing;
    }

    /** Trạng thái từng trạm, kiểm tra song song. */
    public List<SiteStatusResponse> checkAll() {
        List<CompletableFuture<SiteStatusResponse>> checks = props.sites().entrySet().stream()
                .map(e -> CompletableFuture.supplyAsync(() -> check(e.getKey(), e.getValue())))
                .toList();
        return checks.stream().map(CompletableFuture::join).toList();
    }

    private SiteStatusResponse check(String id, AppProperties.Site site) {
        boolean hub = id.equals(props.hubSite());
        String role = hub ? "HUB" : "BRANCH";
        HikariDataSource pool = routing.pools().get(id);
        long start = System.nanoTime();
        try (Connection connection = pool.getConnection(); Statement st = connection.createStatement()) {
            String database;
            try (ResultSet rs = st.executeQuery("SELECT current_database()")) {
                rs.next();
                database = rs.getString(1);
            }
            long latency = (System.nanoTime() - start) / 1_000_000;
            Map<String, Long> counts = new LinkedHashMap<>();
            String error = null;
            List<String> labels = hub ? HUB_LABELS : BRANCH_LABELS;
            try (ResultSet rs = st.executeQuery(hub ? HUB_COUNTS : BRANCH_COUNTS)) {
                rs.next();
                for (int i = 0; i < labels.size(); i++) {
                    counts.put(labels.get(i), rs.getLong(i + 1));
                }
            } catch (SQLException e) {
                error = "Kết nối được nhưng chưa có bảng — đã chạy init_db.sh chưa? (" + e.getMessage() + ")";
            }
            return new SiteStatusResponse(id, role, site.name(), site.maCn(), site.url(), true, latency, database, counts, error);
        } catch (SQLException e) {
            return new SiteStatusResponse(id, role, site.name(), site.maCn(), site.url(), false, null, null, null, e.getMessage());
        }
    }
}
