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

    private static AppProperties.Site site(String maCn) {
        return new AppProperties.Site(maCn, maCn, "jdbc:postgresql://localhost/" + maCn, null, null);
    }
}
