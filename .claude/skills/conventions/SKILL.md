---
name: conventions
description: Quy ước bắt buộc của backend An Tâm Clinic (Spring Boot 4, Java 21, JdbcClient + SQL thuần trên cụm PostgreSQL phân tán 4 trạm). Auto-loads khi viết, sửa hoặc review code Java / Spring / Maven trong backend/. Ưu tiên hơn các skill chung (spring-boot, jpa-patterns, testing-patterns) khi mâu thuẫn.
metadata:
  version: "1.1.0"
  domain: backend
  triggers: Spring Boot, Java, Maven, Spring Framework, REST API, JdbcClient, controller, service, repository, entity, dto
  role: guide
  scope: conventions
  output-format: code
---

# Quy ước backend An Tâm Clinic

Áp dụng cho MỌI code sinh ra hoặc review trong `backend/`. Các skill chung (`spring-boot`, `jpa-patterns`,
`testing-patterns`, `logging-patterns`) dùng ví dụ JPA / `pl.piomin.services` — chỉ lấy ý tưởng, **khi mâu thuẫn
thì theo file này**.

## Bối cảnh dự án

- Đồ án môn Các hệ thống CSDL: 1 backend nối tới 4 trạm PostgreSQL — S0 trung tâm (danh mục chung, view toàn cục
  qua postgres_fdw) và S1..S3 chi nhánh (Logical Replication nhận danh mục từ S0).
- Schema do thư mục `CSDL/` quản lý (`01_init_schema_*.sql`), dựng bằng `scripts/init_db.sh`. Ứng dụng KHÔNG tạo /
  sửa bảng — không Flyway, không `ddl-auto`.
- Chọn trạm theo request: `SiteFilter` đặt `SiteContext` → `SiteRoutingDataSource` chọn pool.
  `/api/master/**`, `/api/hq/**` → S0; `/api/system/**` không định tuyến; `/api/**` còn lại → header `X-Site` (S1..S3).

## Quy tắc bắt buộc

- **JdbcClient + SQL thuần** — KHÔNG JPA / Hibernate / Spring Data. SQL phải nhìn thấy được (đây là đồ án CSDL).
- **Group ID** `vn.antam`, package gốc `vn.antam.clinic`.
- **Không Lombok** — constructor, getter viết tay; logger dùng `LoggerFactory.getLogger(...)`.
- **Constructor injection** — không `@Autowired` trên field (trừ field trong test).
- **Record** cho entity và DTO.
- `@Valid @RequestBody` trên mọi POST / PUT; thông báo validation bằng tiếng Việt.
- Lỗi trả về theo `ApiErrorResponse` (FE phụ thuộc `code`, `sqlState`, `fieldErrors`) — KHÔNG bật Problem Details.
- Giữ đường dẫn `/api/...` (không thêm `/v1`) vì `SiteFilter` định tuyến theo tiền tố đường dẫn.
- Tests: luôn có cả trường hợp đúng và sai; tên `method_condition_expectedResult`.

## Cấu trúc package (chia theo tầng)

```
vn/antam/clinic/
├── controller/      @RestController — chỉ HTTP: nhận request, @Valid, gọi service
├── service/         @Service — nghiệp vụ, @Transactional, Request → Entity, Entity → Response
├── repository/      @Repository — nơi DUY NHẤT dùng JdbcClient / viết SQL
├── entity/          record ánh xạ đúng 1 bảng, tên thành phần = tên cột
├── dto/request/     record dữ liệu FE gửi lên + annotation validation
├── dto/response/    record dữ liệu trả về FE (kể cả ApiErrorResponse, DeleteResponse)
├── config/          @Configuration, @ConfigurationProperties (AppProperties, DataSourceConfig, WebConfig)
├── site/            cơ chế định tuyến trạm (SiteContext, SiteFilter, SiteRoutingDataSource)
├── exception/       BusinessException, GlobalExceptionHandler
└── util/            hàm tiện ích tĩnh, không phải bean (Codes)
```

Đặt tên `<TinhNang><Tang>`: `ThuocController`, `ThuocService`, `ThuocRepository`, `ThuocRequest`, `ThuocResponse`,
entity `Thuoc`. Test đặt cùng package với class được test (`service/BacSiServiceTest`).

## Quy tắc phụ thuộc giữa các tầng

| Tầng | Được dùng | KHÔNG được dùng |
|---|---|---|
| `controller` | `service`, `dto` | `repository`, `entity`, `JdbcClient`, `@Transactional` |
| `service` | `repository`, `entity`, `dto`, `exception`, `config` | `JdbcClient`, SQL |
| `repository` | `entity`, `dto/response` (chỉ cho truy vấn JOIN / tổng hợp), `JdbcClient` | nghiệp vụ, `BusinessException` |
| `entity` | — | validation, Jackson |
| `dto/request` | annotation validation | `entity` |
| `dto/response` | `entity` qua `static from(...)` | — |

Mọi tính năng đều đủ 3 tầng controller → service → repository, kể cả khi service chỉ chuyển tiếp.

## Entity và repository với JdbcClient

```java
// entity: tên thành phần trùng tên cột (so khớp không phân biệt hoa thường: hangSX ↔ hangsx)
public record Thuoc(String maThuoc, String tenThuoc, String hoatChat, String donViTinh, BigDecimal donGia, String hangSX) {}

// đọc 1 bảng → entity
jdbc.sql("SELECT mathuoc, tenthuoc, hoatchat, donvitinh, dongia, hangsx FROM thuoc").query(Thuoc.class).list();

// ghi → paramSource(entity), tên tham số :maThuoc... = tên thành phần record
jdbc.sql("INSERT INTO thuoc (...) VALUES (:maThuoc, :tenThuoc, :hoatChat, :donViTinh, :donGia, :hangSX)")
    .paramSource(thuoc).update();
```

- **Ghi** dữ liệu luôn qua entity. **Đọc 1 bảng** trả entity. **Đọc có JOIN / hàm tổng hợp** (vd bác sĩ kèm
  tenKhoa, soLuot) trả thẳng record trong `dto/response` (projection) — không ghép trong service (tránh N+1).
- Bảng cha–con ISA (NHAN_VIEN → BAC_SI / Y_TA): mỗi bảng 1 repository; service gọi cả hai trong 1 transaction.
  Xoá con trước cha.
- Mỗi bảng trong `CSDL/01_init_schema_branch.sql` có đúng 1 entity — đủ cả 21 bảng, kể cả bảng thuộc tính đa trị
  (`NvSoDienThoai`, `LanChuaHinhThuc`) và bảng liên kết khoá kép (`KeDon`, `HoTro`...). Tên record = tên bảng dạng
  PascalCase. Kiểu: VARCHAR/TEXT → `String`, DATE → `LocalDate`, TIMESTAMP → `LocalDateTime`, NUMERIC → `BigDecimal`,
  INT → `Integer`. `entity/EntitySchemaTest` đối chiếu entity với DB thật (tự bỏ qua khi cụm chưa chạy).
- Thuộc tính đa trị khi ghi lại toàn bộ (vd số điện thoại) repository bảng cha có thể nhận `List<String>` thay vì
  danh sách entity.
- Hàm `update` / `delete` trả `int` số dòng; service ném `BusinessException.notFound(...)` khi bằng 0.

## Transaction trong cụm phân tán

- `@Transactional` đặt trên **phương thức ghi** của service. Đọc thông thường: `@Transactional(readOnly = true)`.
  KHÔNG đặt `@Transactional` ở mức class.
- Mỗi transaction chỉ chạm 1 trạm: kết nối được lấy lúc bắt đầu transaction theo `SiteContext`, đổi trạm giữa
  chừng không có tác dụng.
- Phương thức "đọc chấp nhận lỗi" (vẫn trả dữ liệu dự phòng khi một trạm tắt — `ChiNhanhService.list`,
  `ThuocService.listMaster`) phải là `@Transactional(propagation = Propagation.NOT_SUPPORTED)`:
  nếu có transaction, S0 tắt sẽ lỗi trước khi vào `try`, và query FDW lỗi làm PostgreSQL huỷ cả transaction.
- Phương thức đọc **trạm khác** bằng `SiteContext.callOn(...)` (vd kiểm tra toàn chuỗi ở S0 rồi ghi ở chi nhánh) thì
  KHÔNG gắn `@Transactional` nào, kể cả `NOT_SUPPORTED`: khi có đồng bộ transaction, Spring giữ kết nối đầu tiên cho cả
  phương thức nên `callOn` vẫn chạy trên trạm cũ. Đọc liên trạm trước, rồi ghi trong `TransactionTemplate`
  (mẫu: `LuotKhamService`).
- SQL chạy cả ở S0 (view toàn cục qua postgres_fdw) không dùng truy vấn con theo từng dòng; tổng hợp bằng CTE
  `GROUP BY` để mỗi bảng chỉ quét qua FDW một lần.

## Test

- Service: `@ExtendWith(MockitoExtension.class)`, mock repository, đặt `SiteContext.set("S1")` trong `@BeforeEach`
  và `SiteContext.clear()` trong `@AfterEach`.
- Controller: `@WebMvcTest(XxxController.class)` (package `org.springframework.boot.webmvc.test.autoconfigure`) +
  `@EnableConfigurationProperties(AppProperties.class)` để `SiteFilter` chạy; service dùng `@MockitoBean`.
- Không dùng Testcontainers: cụm 4 trạm có replication + FDW được dựng bằng `scripts/init_db.sh`; kiểm thử tích hợp
  bằng cách chạy app và gọi API trên cụm đó.
- Chạy `./mvnw test` trước khi báo xong.

## Thêm một màn hình mới (thứ tự)

1. `entity/` — đã có sẵn cho cả 21 bảng; chỉ sửa khi schema trong `CSDL/` đổi (chạy `EntitySchemaTest` để kiểm tra).
2. `repository/` — SQL đọc / ghi.
3. `dto/request/`, `dto/response/` — kèm validation và `static from(...)`.
4. `service/` — nghiệp vụ + transaction.
5. `controller/` — `/api/...` (chi nhánh), `/api/master/...` (danh mục S0), `/api/hq/...` (báo cáo toàn chuỗi).
6. Test service + controller, cập nhật README.
