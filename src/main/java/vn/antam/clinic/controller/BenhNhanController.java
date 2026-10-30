package vn.antam.clinic.controller;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.dto.request.BenhNhanRequest;
import vn.antam.clinic.dto.response.BenhNhanResponse;
import vn.antam.clinic.dto.response.BenhNhanToanChuoiResponse;
import vn.antam.clinic.dto.response.DeleteResponse;
import vn.antam.clinic.service.BenhNhanService;

/** Bệnh nhân của chi nhánh đang chọn (header X-Site), kèm tra cứu toàn chuỗi. */
@RestController
@RequestMapping("/api/benh-nhan")
public class BenhNhanController {

    private final BenhNhanService service;

    public BenhNhanController(BenhNhanService service) {
        this.service = service;
    }

    @GetMapping
    public List<BenhNhanResponse> search(@RequestParam(required = false) String q) {
        return service.search(q);
    }

    /** Tra cứu theo CCCD / thẻ BHYT / số điện thoại / mã (khớp đúng) hoặc họ tên trên cả 3 chi nhánh. */
    @GetMapping("/toan-chuoi")
    public List<BenhNhanToanChuoiResponse> searchToanChuoi(@RequestParam(required = false) String q) {
        return service.searchToanChuoi(q);
    }

    /** Tra cứu như /toan-chuoi nhưng chỉ trong chi nhánh — tiếp đón dùng khi mất kết nối toàn chuỗi. */
    @GetMapping("/cuc-bo")
    public List<BenhNhanToanChuoiResponse> searchCucBo(@RequestParam(required = false) String q) {
        return service.searchCucBo(q);
    }

    @GetMapping("/{maBN}")
    public BenhNhanResponse get(@PathVariable String maBN) {
        return service.get(maBN);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BenhNhanResponse create(@Valid @RequestBody BenhNhanRequest request) {
        return service.create(request);
    }

    @PutMapping("/{maBN}")
    public BenhNhanResponse update(@PathVariable String maBN, @Valid @RequestBody BenhNhanRequest request) {
        return service.update(maBN, request);
    }

    @DeleteMapping("/{maBN}")
    public DeleteResponse delete(@PathVariable String maBN) {
        return service.delete(maBN);
    }
}
