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
import vn.antam.clinic.dto.request.DichVuRequest;
import vn.antam.clinic.dto.response.DeleteResponse;
import vn.antam.clinic.dto.response.DichVuResponse;
import vn.antam.clinic.service.DichVuService;

/**
 * Dịch vụ y tế — danh mục chung.
 * <ul>
 *   <li>{@code /api/master/dich-vu}: đọc/ghi tại S0; Logical Replication tự đẩy thay đổi xuống S1..S3.</li>
 *   <li>{@code /api/dich-vu}: đọc bản sao ở chi nhánh đang chọn.</li>
 * </ul>
 */
@RestController
public class DichVuController {

    private final DichVuService service;

    public DichVuController(DichVuService service) {
        this.service = service;
    }

    @GetMapping("/api/master/dich-vu")
    public List<DichVuResponse> listMaster(@RequestParam(required = false) String q) {
        return service.listMaster(q);
    }

    @PostMapping("/api/master/dich-vu")
    @ResponseStatus(HttpStatus.CREATED)
    public DichVuResponse create(@Valid @RequestBody DichVuRequest request) {
        return service.create(request);
    }

    @PutMapping("/api/master/dich-vu/{maDV}")
    public DichVuResponse update(@PathVariable String maDV, @Valid @RequestBody DichVuRequest request) {
        return service.update(maDV, request);
    }

    @DeleteMapping("/api/master/dich-vu/{maDV}")
    public DeleteResponse delete(@PathVariable String maDV) {
        return service.delete(maDV);
    }

    @GetMapping("/api/dich-vu")
    public List<DichVuResponse> listReplica(@RequestParam(required = false) String q) {
        return service.listReplica(q);
    }
}
