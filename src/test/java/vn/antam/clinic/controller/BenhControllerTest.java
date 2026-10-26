package vn.antam.clinic.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.antam.clinic.config.AppProperties;
import vn.antam.clinic.dto.response.BenhResponse;
import vn.antam.clinic.service.BenhService;

@WebMvcTest(BenhController.class)
@EnableConfigurationProperties(AppProperties.class)
class BenhControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    BenhService service;

    @Test
    void listMaster_withoutSiteHeader_isRoutedToHub() throws Exception {
        when(service.listMaster(null)).thenReturn(List.of(new BenhResponse("B01", "Viêm họng cấp tính", "J02", null, 3L)));

        mvc.perform(get("/api/master/benh"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Site-Used", "S0"))
                .andExpect(jsonPath("$[0].soLanChanDoan").value(3));
    }

    @Test
    void create_blankName_returns400() throws Exception {
        mvc.perform(post("/api/master/benh").contentType(MediaType.APPLICATION_JSON).content("""
                        {"tenBenh":"","maICD10":"J02"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.tenBenh").value("Nhập tên bệnh"));
        verifyNoInteractions(service);
    }

    @Test
    void listReplica_requiresBranchSite() throws Exception {
        mvc.perform(get("/api/benh"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_SITE"));
        verifyNoInteractions(service);
    }
}
