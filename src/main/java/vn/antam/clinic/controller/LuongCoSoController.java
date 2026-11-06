package vn.antam.clinic.controller;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.dto.request.LuongCoSoRequest;
import vn.antam.clinic.dto.response.DeleteResponse;
import vn.antam.clinic.dto.response.LuongCoSoResponse;
import vn.antam.clinic.service.LuongCoSoService;

/**
 * Lương cơ sở — danh mục chung.
 * <ul>
 *   <li>{@code /api/master/luong-co-so}: đọc/ghi tại S0; Logical Replication tự đẩy thay đổi xuống S1..S3.</li>
 *   <li>{@code /api/luong-co-so}: đọc bản sao ở chi nhánh đang chọn (chỉ xem; dùng được khi mất kết nối S0).</li>
 * </ul>
 */
@RestController
public class LuongCoSoController {

    private final LuongCoSoService service;

    public LuongCoSoController(LuongCoSoService service) {
        this.service = service;
    }

    // ---------------------------------------------------------------- S0 (master)

    @GetMapping("/api/master/luong-co-so")
    public List<LuongCoSoResponse> list() {
        return service.list();
    }

    @PostMapping("/api/master/luong-co-so")
    @ResponseStatus(HttpStatus.CREATED)
    public LuongCoSoResponse create(@Valid @RequestBody LuongCoSoRequest request) {
        return service.create(request);
    }

    /** @param ngayApDung dạng yyyy-MM-dd */
    @PutMapping("/api/master/luong-co-so/{ngayApDung}")
    public LuongCoSoResponse update(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ngayApDung,
                                    @Valid @RequestBody LuongCoSoRequest request) {
        return service.update(ngayApDung, request);
    }

    @DeleteMapping("/api/master/luong-co-so/{ngayApDung}")
    public DeleteResponse delete(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ngayApDung) {
        return service.delete(ngayApDung);
    }

    // ---------------------------------------------------------------- chi nhánh (bản sao)

    /** Cùng truy vấn với bản gốc — SiteFilter định tuyến theo đường dẫn, nên ở đây đọc bảng LUONG_CO_SO của chi nhánh. */
    @GetMapping("/api/luong-co-so")
    public List<LuongCoSoResponse> listReplica() {
        return service.list();
    }
}
