package vn.antam.clinic.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import vn.antam.clinic.config.AppProperties.Mode;
import vn.antam.clinic.site.SiteRoutingDataSource;

class DataSourceConfigTest {

    private static final AppProperties.Db DB = new AppProperties.Db("admin", "password123", 2, "chi_nhanh_tra_cuu",
            "tracuu123", null, null);

    @Test
    void dataSource_branchMode_hubPoolUsesRestrictedRole() {
        SiteRoutingDataSource routing = build(Mode.BRANCH, "S1");

        assertThat(routing.pools().get("S0").getUsername()).isEqualTo("chi_nhanh_tra_cuu");
        assertThat(routing.pools().get("S1").getUsername()).isEqualTo("admin");
        routing.destroy();
    }

    @Test
    void dataSource_hqMode_branchPoolsAreReadOnlyButHubIsNot() {
        SiteRoutingDataSource routing = build(Mode.HQ, null);

        HikariDataSource branch = routing.pools().get("S1");
        assertThat(branch.getDataSourceProperties().getProperty("options")).contains("default_transaction_read_only=on");
        assertThat(routing.pools().get("S0").getDataSourceProperties().getProperty("options")).isNull();
        assertThat(routing.pools().get("S0").getUsername()).isEqualTo("admin");
        routing.destroy();
    }

    private static SiteRoutingDataSource build(Mode mode, String localSite) {
        AppProperties props = new AppProperties(mode, localSite, "S0", DB, Map.of(
                "s0", new AppProperties.Site("Trung tâm", null, "jdbc:postgresql://localhost:5430/hub", null, null),
                "s1", new AppProperties.Site("Cầu Giấy", "CN1", "jdbc:postgresql://localhost:5431/cn1", null, null)));
        return new DataSourceConfig().dataSource(props);
    }
}
