package vn.antam.clinic.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.UnknownHostException;
import java.sql.SQLTransientConnectionException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.CannotCreateTransactionException;
import vn.antam.clinic.config.AppProperties;
import vn.antam.clinic.service.TongQuanService;

@WebMvcTest(TongQuanController.class)
@EnableConfigurationProperties(AppProperties.class)
class TongQuanControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    TongQuanService service;

    @Test
    void tongQuan_missingSite_returns400() throws Exception {
        mvc.perform(get("/api/tong-quan"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_SITE"));
        verifyNoInteractions(service);
    }

    @Test
    void unknownApi_returns404WithRebuildHint() throws Exception {
        mvc.perform(get("/api/khong-co-api").header("X-Site", "S1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("API_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(containsString("bản build cũ")));
    }

    @Test
    void tongQuan_withSite_callsService() throws Exception {
        mvc.perform(get("/api/tong-quan").header("X-Site", "S1")).andExpect(status().isOk());

        verify(service).tongQuan();
    }

    @Test
    void tongQuan_siteDown_returns503() throws Exception {
        // Trạm tắt: method @Transactional không mở được kết nối (Docker: tên container không còn phân giải)
        when(service.tongQuan()).thenThrow(new CannotCreateTransactionException("Could not open JDBC Connection for transaction",
                new SQLTransientConnectionException("pool-S3 - Connection is not available", "08001",
                        new UnknownHostException("site_s3_badinh"))));

        mvc.perform(get("/api/tong-quan").header("X-Site", "S3"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SITE_UNAVAILABLE"))
                .andExpect(jsonPath("$.site").value("S3"));
    }
}
