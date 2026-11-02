package vn.antam.clinic.controller;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.dto.request.ChiNhanhRequest;
import vn.antam.clinic.dto.response.ChiNhanhResponse;
import vn.antam.clinic.service.ChiNhanhService;

/**
 * Chi nhánh — danh mục chung tại S0, có bản sao ở mỗi chi nhánh (Logical Replication).
 * Frontend dùng GET để dựng dropdown "Chi nhánh đang làm việc": bản chi nhánh đọc bản sao cục bộ (không cần S0).
 */
@RestController
public class ChiNhanhController {

    private final ChiNhanhService service;

    public ChiNhanhController(ChiNhanhService service) {
        this.service = service;
    }

    @GetMapping("/api/master/chi-nhanh")
    public List<ChiNhanhResponse> list() {
        return service.list();
    }

    /** Bản sao CHI_NHANH tại chi nhánh đang chọn — bản chi nhánh không vào được /api/master/**. */
    @GetMapping("/api/chi-nhanh")
    public List<ChiNhanhResponse> listReplica() {
        return service.list();
    }

    @PutMapping("/api/master/chi-nhanh/{maCn}")
    public ChiNhanhResponse update(@PathVariable String maCn, @Valid @RequestBody ChiNhanhRequest request) {
        return service.update(maCn, request);
    }
}
