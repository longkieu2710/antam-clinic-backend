package vn.antam.clinic.config;

import java.util.LinkedHashMap;
import java.util.Map;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import vn.antam.clinic.site.SiteRoutingDataSource;

/**
 * Tạo 1 pool HikariCP cho mỗi trạm và gói chúng vào {@link SiteRoutingDataSource}.
 * Vì có bean DataSource riêng, Spring Boot không tự tạo DataSource mặc định nữa;
 * JdbcClient và transaction manager tự động dùng bean này.
 */
@Configuration(proxyBeanMethods = false)
public class DataSourceConfig {

    @Bean
    @Primary
    public SiteRoutingDataSource dataSource(AppProperties props) {
        if (props.sites().isEmpty()) {
            throw new IllegalStateException("Chưa khai báo trạm nào trong app.sites (application.yml).");
        }
        Map<String, HikariDataSource> pools = new LinkedHashMap<>();
        props.sites().forEach((id, site) -> {
            // Constructor không tham số: pool chỉ mở kết nối ở lần dùng đầu tiên,
            // nên ứng dụng vẫn khởi động được khi một trạm đang tắt.
            HikariDataSource ds = new HikariDataSource();
            ds.setPoolName("pool-" + id);
            ds.setJdbcUrl(site.url());
            // Chỉ rõ driver: nếu để DriverManager tự dò thì khi chạy bằng java -jar, request đầu tiên đi từ
            // thread ForkJoinPool (SiteHealthService) không thấy BOOT-INF/lib → "No suitable driver" cho mọi pool.
            ds.setDriverClassName("org.postgresql.Driver");
            boolean hub = id.equals(props.hubSite());
            // Bản chi nhánh gọi về S0 bằng role chỉ chạy được hàm tra cứu (scripts/sql/07_bao_mat_hub.sql)
            boolean traCuu = hub && props.mode() == AppProperties.Mode.BRANCH && props.db().traCuuUsername() != null;
            ds.setUsername(site.username() != null ? site.username()
                    : traCuu ? props.db().traCuuUsername() : props.db().username());
            ds.setPassword(site.password() != null ? site.password()
                    : traCuu ? props.db().traCuuPassword() : props.db().password());
            // Bản Trung tâm chỉ xem dữ liệu chi nhánh: PostgreSQL từ chối mọi lệnh ghi trên kết nối này,
            // kể cả khi một request ghi lọt qua SiteFilter
            if (!hub && props.mode() == AppProperties.Mode.HQ) {
                ds.addDataSourceProperty("options", "-c default_transaction_read_only=on");
            }
            ds.setMaximumPoolSize(props.db().poolSize());
            ds.setMinimumIdle(1);
            ds.setConnectionTimeout(3_000);
            // Trạm tắt → lỗi kết nối bình thường (SQLState 08xxx) thay vì lỗi khởi tạo pool.
            ds.setInitializationFailTimeout(-1);
            // Tắt JIT của PostgreSQL cho phiên của ứng dụng: foreign table (postgres_fdw) không có thống kê nên planner
            // ước lượng chi phí rất cao → bật JIT cho mọi truy vấn toàn chuỗi ở S0, mất ~1,5 giây biên dịch LLVM cho câu
            // lệnh chỉ chạy vài mili-giây. Truy vấn của ứng dụng đều nhỏ, không cần JIT. Không đổi cấu hình của CSDL.
            ds.setConnectionInitSql("SET jit = off");
            pools.put(id, ds);
        });
        return new SiteRoutingDataSource(pools);
    }
}
