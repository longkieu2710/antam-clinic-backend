package vn.antam.clinic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import vn.antam.clinic.config.AppProperties;
import vn.antam.clinic.site.SiteRoutingDataSource;

/**
 * Kiểm tra cấu hình nạp đúng. Không cần DB chạy: pool chỉ mở kết nối ở lần dùng đầu tiên.
 */
@SpringBootTest
class ClinicApplicationTests {

    @Autowired
    AppProperties props;

    @Autowired
    SiteRoutingDataSource dataSource;

    @Test
    void loadsFourSites() {
        assertThat(props.hubSite()).isEqualTo("S0");
        assertThat(props.branchSites()).containsExactly("S1", "S2", "S3");
        assertThat(dataSource.pools()).containsOnlyKeys("S0", "S1", "S2", "S3");
        assertThat(props.site("S1").map(AppProperties.Site::maCn)).contains("CN1");
    }
}
