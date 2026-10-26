package vn.antam.clinic.site;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * DataSource "định tuyến": mỗi lần lấy kết nối sẽ chọn pool của trạm đang nằm trong {@link SiteContext}.
 * Không có trạm mặc định — quên chọn trạm sẽ báo lỗi ngay thay vì ghi nhầm vào trạm khác.
 */
public class SiteRoutingDataSource extends AbstractRoutingDataSource implements DisposableBean {

    private final Map<String, HikariDataSource> pools;

    public SiteRoutingDataSource(Map<String, HikariDataSource> pools) {
        this.pools = Collections.unmodifiableMap(pools);
        setTargetDataSources(new HashMap<Object, Object>(pools));
        setLenientFallback(false);
    }

    @Override
    protected Object determineCurrentLookupKey() {
        String site = SiteContext.get();
        if (site == null) {
            throw new IllegalStateException("Chưa chọn trạm CSDL cho request này (thiếu header X-Site).");
        }
        return site;
    }

    /** Pool của từng trạm — dùng cho màn kiểm tra kết nối (không đi qua định tuyến). */
    public Map<String, HikariDataSource> pools() {
        return pools;
    }

    @Override
    public void destroy() {
        pools.values().forEach(HikariDataSource::close);
    }
}
