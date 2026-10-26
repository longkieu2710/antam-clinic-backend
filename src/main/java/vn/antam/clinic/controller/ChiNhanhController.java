package vn.antam.clinic.controller;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.dto.request.ChiNhanhRequest;
import vn.antam.clinic.dto.response.ChiNhanhResponse;
import vn.antam.clinic.service.ChiNhanhService;

/**
 * Chi nhánh — danh mục chung tại S0.
 * Frontend dùng GET để dựng dropdown "Chi nhánh đang làm việc".
 */
@RestController
@RequestMapping("/api/master/chi-nhanh")
public class ChiNhanhController {

    private final ChiNhanhService service;

    public ChiNhanhController(ChiNhanhService service) {
        this.service = service;
    }

    @GetMapping
    public List<ChiNhanhResponse> list() {
        return service.list();
    }

    @PutMapping("/{maCn}")
    public ChiNhanhResponse update(@PathVariable String maCn, @Valid @RequestBody ChiNhanhRequest request) {
        return service.update(maCn, request);
    }
}
