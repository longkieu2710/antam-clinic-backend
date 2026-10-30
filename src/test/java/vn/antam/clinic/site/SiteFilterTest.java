package vn.antam.clinic.site;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;
import vn.antam.clinic.config.AppProperties;
import vn.antam.clinic.config.AppProperties.Mode;

class SiteFilterTest {

    private final AtomicReference<String> routedTo = new AtomicReference<>();
    private final FilterChain chain = (req, res) -> routedTo.set(SiteContext.get());

    @Test
    void doFilter_allModeWithBranchHeader_routesToHeaderSite() throws Exception {
        MockHttpServletResponse res = run(Mode.ALL, null, "/api/bac-si", "s2");

        assertThat(routedTo.get()).isEqualTo("S2");
        assertThat(res.getHeader(SiteFilter.RESPONSE_HEADER)).isEqualTo("S2");
    }

    @Test
    void doFilter_allModeWithoutHeader_returns400MissingSite() throws Exception {
        MockHttpServletResponse res = run(Mode.ALL, null, "/api/bac-si", null);

        assertThat(res.getStatus()).isEqualTo(400);
        assertThat(res.getContentAsString()).contains("MISSING_SITE");
        assertThat(routedTo.get()).isNull();
    }

    @Test
    void doFilter_branchModeWithoutHeader_routesToLocalSite() throws Exception {
        run(Mode.BRANCH, "S1", "/api/bac-si", null);

        assertThat(routedTo.get()).isEqualTo("S1");
    }

    @Test
    void doFilter_branchModeWithOtherBranchHeader_returns400WrongSite() throws Exception {
        MockHttpServletResponse res = run(Mode.BRANCH, "S1", "/api/bac-si", "S2");

        assertThat(res.getStatus()).isEqualTo(400);
        assertThat(res.getContentAsString()).contains("WRONG_SITE");
        assertThat(routedTo.get()).isNull();
    }

    @Test
    void doFilter_headerWithBackslash_stillReturnsValidJson() throws Exception {
        MockHttpServletResponse res = run(Mode.BRANCH, "S1", "/api/bac-si", "S2\\");

        assertThat(res.getStatus()).isEqualTo(400);
        assertThat(new ObjectMapper().readTree(res.getContentAsString()).get("code").asString())
                .isEqualTo("WRONG_SITE");
    }

    @Test
    void doFilter_branchModeMasterPath_routesToHub() throws Exception {
        run(Mode.BRANCH, "S1", "/api/master/thuoc", "S1");

        assertThat(routedTo.get()).isEqualTo("S0");
    }

    @Test
    void doFilter_hqModeBranchPath_returns404NotServed() throws Exception {
        MockHttpServletResponse res = run(Mode.HQ, null, "/api/bac-si", "S1");

        assertThat(res.getStatus()).isEqualTo(404);
        assertThat(res.getContentAsString()).contains("NOT_SERVED");
        assertThat(routedTo.get()).isNull();
    }

    @Test
    void doFilter_hqModeHqPath_routesToHub() throws Exception {
        run(Mode.HQ, null, "/api/hq/doanh-thu", null);

        assertThat(routedTo.get()).isEqualTo("S0");
    }

    @Test
    void doFilter_systemPath_isNotRouted() throws Exception {
        MockHttpServletResponse res = run(Mode.HQ, null, "/api/system/mode", null);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(routedTo.get()).isNull();
    }

    private MockHttpServletResponse run(Mode mode, String localSite, String path, String header) throws Exception {
        AppProperties props = new AppProperties(mode, localSite, "S0", null, Map.of(
                "s0", new AppProperties.Site("Trung tâm", null, "jdbc:postgresql://localhost:5430/hub", null, null),
                "s1", new AppProperties.Site("Cầu Giấy", "CN1", "jdbc:postgresql://localhost:5431/cn1", null, null),
                "s2", new AppProperties.Site("Đống Đa", "CN2", "jdbc:postgresql://localhost:5432/cn2", null, null)));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", path);
        if (header != null) {
            req.addHeader(SiteFilter.HEADER, header);
        }
        MockHttpServletResponse res = new MockHttpServletResponse();
        new SiteFilter(props).doFilter(req, res, chain);
        return res;
    }
}
