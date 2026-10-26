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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.dto.request.ThuocRequest;
import vn.antam.clinic.dto.response.DeleteResponse;
import vn.antam.clinic.dto.response.ThuocResponse;
import vn.antam.clinic.service.ThuocService;

/**
 * Thuốc — danh mục chung.
 * <ul>
 *   <li>{@code /api/master/thuoc}: đọc/ghi tại S0; Logical Replication tự đẩy thay đổi xuống S1..S3.</li>
 *   <li>{@code /api/thuoc}: đọc bản sao ở chi nhánh đang chọn (dùng cho form kê đơn, kiểm tra đồng bộ).</li>
 * </ul>
 */
@RestController
public class ThuocController {

    private final ThuocService service;

    public ThuocController(ThuocService service) {
        this.service = service;
    }

    // ---------------------------------------------------------------- S0 (master)

    @GetMapping("/api/master/thuoc")
    public List<ThuocResponse> listMaster(@RequestParam(required = false) String q) {
        return service.listMaster(q);
    }

    @PostMapping("/api/master/thuoc")
    @ResponseStatus(HttpStatus.CREATED)
    public ThuocResponse create(@Valid @RequestBody ThuocRequest request) {
        return service.create(request);
    }

    @PutMapping("/api/master/thuoc/{maThuoc}")
    public ThuocResponse update(@PathVariable String maThuoc, @Valid @RequestBody ThuocRequest request) {
        return service.update(maThuoc, request);
    }

    @DeleteMapping("/api/master/thuoc/{maThuoc}")
    public DeleteResponse delete(@PathVariable String maThuoc) {
        return service.delete(maThuoc);
    }

    // ---------------------------------------------------------------- chi nhánh (bản sao)

    @GetMapping("/api/thuoc")
    public List<ThuocResponse> listReplica(@RequestParam(required = false) String q) {
        return service.listReplica(q);
    }
}
