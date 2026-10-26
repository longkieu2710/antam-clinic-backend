package vn.antam.clinic.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.antam.clinic.config.AppProperties;
import vn.antam.clinic.exception.BusinessException;
import vn.antam.clinic.service.LuongCoSoService;

@WebMvcTest(LuongCoSoController.class)
@EnableConfigurationProperties(AppProperties.class)
class LuongCoSoControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    LuongCoSoService service;

    @Test
    void update_appliedLevel_returns409HistoryLocked() throws Exception {
        when(service.update(eq(LocalDate.of(2024, 7, 1)), any()))
                .thenThrow(new BusinessException(HttpStatus.CONFLICT, "HISTORY_LOCKED", "đã áp dụng"));

        mvc.perform(put("/api/master/luong-co-so/2024-07-01").contentType(MediaType.APPLICATION_JSON).content("""
                        {"ngayApDung":"2024-07-01","mucLuong":2340000}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HISTORY_LOCKED"));
    }

    @Test
    void delete_invalidDatePath_returns400() throws Exception {
        mvc.perform(delete("/api/master/luong-co-so/2024-13-01"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void create_nonPositiveAmount_returns400() throws Exception {
        mvc.perform(post("/api/master/luong-co-so").contentType(MediaType.APPLICATION_JSON).content("""
                        {"mucLuong":0}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.ngayApDung").value("Chọn ngày áp dụng"))
                .andExpect(jsonPath("$.fieldErrors.mucLuong").value("Mức lương phải > 0"));
        verifyNoInteractions(service);
    }
}
