package vn.antam.clinic.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import vn.antam.clinic.config.AppProperties.Mode;

class AppPropertiesTest {

    private static final Map<String, AppProperties.Site> SITES = Map.of(
            "s0", site(null), "s1", site("CN1"), "s2", site("CN2"), "s3", site("CN3"));

    @Test
    void constructor_noMode_defaultsToAllAndKeepsEverySite() {
        AppProperties props = new AppProperties("S0", null, SITES);

        assertThat(props.mode()).isEqualTo(Mode.ALL);
        assertThat(props.sites()).containsOnlyKeys("S0", "S1", "S2", "S3");
    }

    @Test
    void constructor_branchMode_keepsOnlyHubAndLocalSite() {
        AppProperties props = new AppProperties(Mode.BRANCH, " s2 ", "S0", null, SITES);

        assertThat(props.localSite()).isEqualTo("S2");
        assertThat(props.sites()).containsOnlyKeys("S0", "S2");
        assertThat(props.branchSites()).containsExactly("S2");
    }

    @Test
    void constructor_branchModeWithoutLocalSite_throws() {
        assertThatThrownBy(() -> new AppProperties(Mode.BRANCH, null, "S0", null, SITES))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_LOCAL_SITE");
    }

    @Test
    void constructor_branchModeLocalSiteIsHub_throws() {
        assertThatThrownBy(() -> new AppProperties(Mode.BRANCH, "S0", "S0", null, SITES))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void constructor_hqModeWithoutHub_throws() {
        assertThatThrownBy(() -> new AppProperties(Mode.HQ, null, "S0", null, Map.of("s1", site("CN1"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_MODE=hq");
    }

    @Test
    void bind_lowercaseModeAndEnvOverride_bindsBranchWithOverriddenUrl() {
        ConfigurationPropertySource yml = new MapConfigurationPropertySource(Map.of(
                "app.mode", "all",
                "app.sites.s0.url", "jdbc:postgresql://site_s0_trungtam:5432/hub",
                "app.sites.s1.ma-cn", "CN1",
                "app.sites.s1.url", "jdbc:postgresql://site_s1_caugiay:5432/cn1",
                "app.sites.s2.url", "jdbc:postgresql://site_s2_dongda:5432/cn2"));
        SystemEnvironmentPropertySource env = new SystemEnvironmentPropertySource("env", Map.of(
                "APP_MODE", "branch",
                "APP_LOCAL_SITE", "s1",
                "APP_SITES_S1_URL", "jdbc:postgresql://db-cn1:5432/cn1"));
        List<ConfigurationPropertySource> sources = List.of(ConfigurationPropertySources.from(env).iterator().next(), yml);

        AppProperties props = new Binder(sources).bind("app", AppProperties.class).get();

        assertThat(props.mode()).isEqualTo(Mode.BRANCH);
        assertThat(props.localSite()).isEqualTo("S1");
        assertThat(props.sites().keySet()).as("S0 trước dù S1 được ghi đè từ biến môi trường").containsExactly("S0", "S1");
        assertThat(props.sites().get("S1").url()).isEqualTo("jdbc:postgresql://db-cn1:5432/cn1");
        assertThat(props.sites().get("S1").maCn()).isEqualTo("CN1");
    }

    @Test
    void constructor_mayNayCn2_derivesBranchModeWithLocalDbAndHubAddress() {
        AppProperties props = new AppProperties("CN2", Map.of("hq", "100.64.0.10", "cn1", "100.64.0.11"), null, null, "S0",
                null, sites());

        assertThat(props.mode()).isEqualTo(Mode.BRANCH);
        assertThat(props.localSite()).isEqualTo("S2");
        assertThat(props.sites()).containsOnlyKeys("S0", "S2");
        assertThat(props.sites().get("S0").url()).isEqualTo("jdbc:postgresql://100.64.0.10:5432/ql_phongkham_hub");
        assertThat(props.sites().get("S2").url()).as("CSDL của chính máy này").isEqualTo("jdbc:postgresql://localhost:5432/ql_phongkham_cn2");
    }

    @Test
    void constructor_mayNayHq_derivesHqModeWithEveryBranchAddress() {
        AppProperties props = new AppProperties("hq", Map.of("hq", "x", "cn1", "100.64.0.11", "cn2", "100.64.0.12",
                "cn3", "100.64.0.13"), Mode.ALL, null, "S0", new AppProperties.Db(null, null, null, null, null, 6543, "db-cuc-bo"),
                sites());

        assertThat(props.mode()).isEqualTo(Mode.HQ);
        assertThat(props.sites()).containsOnlyKeys("S0", "S1", "S2", "S3");
        assertThat(props.sites().get("S0").url()).isEqualTo("jdbc:postgresql://db-cuc-bo:6543/ql_phongkham_hub");
        assertThat(props.sites().get("S3").url()).isEqualTo("jdbc:postgresql://100.64.0.13:6543/ql_phongkham_cn3");
    }

    @Test
    void constructor_mayNayHqMissingBranchAddress_throws() {
        assertThatThrownBy(() -> new AppProperties("hq", Map.of("cn1", "100.64.0.11"), null, null, "S0", null, sites()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.dia-chi-may.cn2");
    }

    @Test
    void constructor_unknownMayNay_throws() {
        assertThatThrownBy(() -> new AppProperties("cn9", Map.of(), null, null, "S0", null, sites()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cn9");
    }

    @Test
    void bind_mayNayFromYaml_overridesModeAndUrls() {
        ConfigurationPropertySource yml = new MapConfigurationPropertySource(Map.of(
                "app.may-nay", "cn1",
                "app.dia-chi-may.hq", "100.64.0.10",
                "app.dia-chi-may.cn1", "",
                "app.mode", "all",
                "app.db.username", "admin",
                "app.db.password", "password123",
                "app.db.tra-cuu-username", "chi_nhanh_tra_cuu",
                "app.sites.s0.url", "jdbc:postgresql://localhost:5430/ql_phongkham_hub",
                "app.sites.s1.ma-cn", "CN1",
                "app.sites.s1.url", "jdbc:postgresql://localhost:5431/ql_phongkham_cn1"));

        AppProperties props = new Binder(yml).bind("app", AppProperties.class).get();

        assertThat(props.mode()).isEqualTo(Mode.BRANCH);
        assertThat(props.sites().get("S0").url()).isEqualTo("jdbc:postgresql://100.64.0.10:5432/ql_phongkham_hub");
        assertThat(props.sites().get("S1").url()).isEqualTo("jdbc:postgresql://localhost:5432/ql_phongkham_cn1");
        assertThat(props.db().password()).as("khối app.db phải được bind").isEqualTo("password123");
        assertThat(props.db().traCuuUsername()).isEqualTo("chi_nhanh_tra_cuu");
        assertThat(props.db().congMay()).isEqualTo(5432);
    }

    @Test
    void bind_emptyMayNayEnvOverFileValue_runsSingleMachineMode() {
        ConfigurationPropertySource yml = new MapConfigurationPropertySource(Map.of(
                "app.may-nay", "hq",
                "app.dia-chi-may.hq", "172.20.10.11",
                "app.sites.s0.url", "jdbc:postgresql://localhost:5430/ql_phongkham_hub",
                "app.sites.s1.ma-cn", "CN1",
                "app.sites.s1.url", "jdbc:postgresql://localhost:5431/ql_phongkham_cn1"));
        SystemEnvironmentPropertySource env = new SystemEnvironmentPropertySource("env", Map.of("APP_MAY_NAY", ""));
        List<ConfigurationPropertySource> sources = List.of(ConfigurationPropertySources.from(env).iterator().next(), yml);

        AppProperties props = new Binder(sources).bind("app", AppProperties.class).get();

        assertThat(props.mayNay()).as("docker-compose.yml / phan-tan đặt APP_MAY_NAY rỗng").isNull();
        assertThat(props.mode()).isEqualTo(Mode.ALL);
        assertThat(props.sites().get("S1").url()).isEqualTo("jdbc:postgresql://localhost:5431/ql_phongkham_cn1");
    }

    private static Map<String, AppProperties.Site> sites() {
        return Map.of(
                "s0", new AppProperties.Site("Trung tâm", null, "jdbc:postgresql://localhost:5430/ql_phongkham_hub", null, null),
                "s1", new AppProperties.Site("Cầu Giấy", "CN1", "jdbc:postgresql://localhost:5431/ql_phongkham_cn1", null, null),
                "s2", new AppProperties.Site("Đống Đa", "CN2", "jdbc:postgresql://localhost:5432/ql_phongkham_cn2", null, null),
                "s3", new AppProperties.Site("Ba Đình", "CN3", "jdbc:postgresql://localhost:5433/ql_phongkham_cn3", null, null));
    }

    private static AppProperties.Site site(String maCn) {
        return new AppProperties.Site(maCn, maCn, "jdbc:postgresql://localhost/" + maCn, null, null);
    }
}
