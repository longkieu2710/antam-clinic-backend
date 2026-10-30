# An Tâm Clinic — source code ứng dụng (FE + BE)

Ứng dụng web cho Đề tài 4 (quản lý chuỗi phòng khám tư nhân), chạy trên **cụm CSDL phân tán** trong `CSDL/`.
Tech stack chi tiết: `requirements/tech-stack.md` · Thiết kế màn hình: `requirements/02_ui-prototype.html` (`requirements/` và `CSDL/` nằm ngang hàng với `source_code/`).

```
source_code/
├── backend/                  repo antam-clinic-backend · Java 21 · Spring Boot 4.1 · JdbcClient (SQL thuần) · springdoc
│   ├── README.md, CLAUDE.md  tài liệu chung của cả ứng dụng (file này)
│   ├── docker-compose.yml    web (nginx + React) + api (Spring Boot), nối vào mạng Docker của cụm DB
│   ├── docker-compose.phan-tan.yml  4 cặp web + api theo cơ sở (Trung tâm, CN1–CN3) trên 1 máy (mục 1.2)
│   ├── Dockerfile            image backend
│   ├── scripts/init_db.sh    dựng 4 container PostgreSQL (S0–S3) + nạp schema, dữ liệu, replication, FDW
│   ├── scripts/tat_jit.sh    tắt / bật JIT của PostgreSQL trên 4 container (truy vấn toàn chuỗi nhanh hơn)
│   ├── scripts/docker_app.sh build + chạy web và api bằng Docker (mục 1.1)
│   ├── scripts/phan_tan.sh   chạy 4 cặp web + api theo cơ sở, giả lập mất mạng từng cơ sở (mục 1.2)
│   └── .claude/              skills/ (quy ước dự án: skills/conventions/SKILL.md) · agents/ (spring-boot-engineer, code-reviewer...)
└── frontend/                 repo antam-clinic-frontend · React 19 · Vite 8 · TypeScript · Ant Design 6 · TanStack Query · React Router
    ├── README.md, CLAUDE.md  bản ngắn: cách chạy FE + quy ước FE
    └── Dockerfile, nginx/    build bằng Node rồi phục vụ bằng nginx
```

Hai repo phải nằm cạnh nhau trong `source_code/` (script và compose của backend gọi sang `../frontend`). Các đường dẫn trong file này tính từ `source_code/`.

## 1. Chạy lần đầu (macOS)

Cần có: Docker Desktop, JDK 21 (`brew install --cask temurin@21`), Node 24 qua nvm.

```bash
# 1) Cụm DB (bỏ qua nếu 4 container đã chạy)
cd source_code/backend/scripts && ./init_db.sh

# 2) Backend → http://localhost:8080 · Swagger: http://localhost:8080/swagger-ui.html
cd source_code/backend
./mvnw spring-boot:run          # lần đầu tự tải Maven + thư viện, mất vài phút

# 3) Frontend (terminal khác) → http://localhost:5173
cd source_code/frontend
nvm use                         # đọc .nvmrc = 24 (chưa có thì: nvm install)
npm install                     # chỉ cần lần đầu / khi package.json đổi
npm run dev
```

Mở http://localhost:5173 → trang **Tổng quan hệ thống** hiện 4 thẻ S0–S3; thẻ nào đỏ là trạm đó không kết nối được.

**IntelliJ IDEA:** File → Open → chọn `backend/pom.xml` → Open as Project · Project SDK = Temurin 21 · chạy `ClinicApplication`.
**VS Code:** mở thư mục `frontend/`.

### 1.1. Chạy bằng Docker (không cần cài JDK, Node)

Mỗi repo có `Dockerfile` riêng; `backend/docker-compose.yml` chạy cả hai. Cụm DB vẫn do `CSDL/docker-compose.yml` quản lý — compose của ứng dụng không tạo DB, chỉ cho container `api` nối vào mạng Docker của 4 container DB.

```
trình duyệt ──► web (nginx, cổng 3000) ──/api, /v3, /swagger-ui──► api (Spring Boot, cổng 8080, profile docker)
                 └ phục vụ bản build React                          └ mạng csdl_default ──► site_s0_trungtam … site_s3_badinh :5432
```

```bash
# 1) Cụm DB như trên (4 container đang chạy và đã init)
# 2) Build + chạy web và api → http://localhost:3000 · Swagger: http://localhost:3000/swagger-ui.html
cd source_code/backend/scripts && ./docker_app.sh   # tự dò mạng của cụm DB, chờ api sẵn sàng
#    hoặc:  cd source_code/backend && docker compose up -d --build
./docker_app.sh --logs                               # xem log (Ctrl+C để thoát)
./docker_app.sh --ps                                 # trạng thái
./docker_app.sh --down                               # dừng web + api (4 container DB giữ nguyên)
```

| | `backend/Dockerfile` | `frontend/Dockerfile` |
|---|---|---|
| Build | `maven:3.9-eclipse-temurin-21`: tải dependency thành lớp riêng (chỉ tải lại khi `pom.xml` đổi) → `mvn package -DskipTests` | `node:24-alpine`: `npm ci` → `tsc -b` + `vite build --outDir dist` |
| Chạy | `eclipse-temurin:21-jre`, user thường, giờ `Asia/Ho_Chi_Minh`, profile `docker` | `nginx:stable-alpine`: phục vụ SPA (F5 ở route con không 404), chuyển `/api` `/v3` `/swagger-ui` sang `API_UPSTREAM` (mặc định `http://api:8080`) |
| Healthcheck | `GET /v3/api-docs` (không gọi DB: một trạm tắt không làm api "hỏng") | `GET /` |

- Lần build đầu tải image và thư viện, mất vài phút; các lần sau chỉ build lại phần đổi. Sửa code xong chạy lại `./docker_app.sh` (hoặc `docker compose up -d --build`).
- Biến môi trường (đặt trước lệnh, hoặc ghi vào `backend/.env`): `WEB_PORT` (mặc định 3000), `API_PORT` (8080), `DB_NETWORK` (mặc định `csdl_default` = tên thư mục `CSDL` + `_default`; script tự dò).
- Giờ JVM phải là giờ Việt Nam: mã lượt (`…-yyMMdd-NNN`), "tháng hiện tại" khi xoá lượt, mốc 30 ngày bỏ dở đều tính theo giờ JVM — image đã đặt sẵn.
- Backend tắt: nginx trả JSON `502 API_UNAVAILABLE` nên giao diện vẫn hiện thông báo dễ hiểu. Một trạm DB tắt: API trả `503 SITE_UNAVAILABLE` / `CHAIN_UNAVAILABLE` như khi chạy ngoài Docker.
- `docker-compose.yml` và `scripts/` nằm trong repo backend; `Dockerfile` + `nginx/` của frontend nằm trong repo frontend. Compose build frontend từ `../frontend`, nên cần clone 2 repo cạnh nhau như cây thư mục ở trên.

### 1.2. Triển khai theo cơ sở: mỗi cơ sở một FE + BE + CSDL

Cùng một source FE và BE, chọn bản bằng biến môi trường của **backend** (frontend đọc `GET /api/system/mode` lúc mở
trang rồi tự hiện đúng giao diện — không cần build FE riêng cho từng cơ sở):

| Biến | Bản | Dữ liệu chi nhánh | Giao diện |
|---|---|---|---|
| (không đặt) / `APP_MODE=all` | một bản cho mọi trạm — như trước | chi nhánh trong `X-Site` | đủ mọi màn, có dropdown chọn chi nhánh |
| `APP_MODE=branch APP_LOCAL_SITE=S1` | bản đặt tại cơ sở CN1 | luôn S1 (`X-Site` khác → 400 `WRONG_SITE`); chỉ mở pool S0 + S1 | khoá vào CN1; có thuốc / bệnh / dịch vụ và hồ sơ toàn chuỗi (cần kết nối S0); không có lương cơ sở, chi nhánh, báo cáo toàn chuỗi |
| `APP_MODE=hq` | bản Trung tâm | không phục vụ (404 `NOT_SERVED`) | chỉ các màn làm việc với S0 + tổng quan hệ thống |

Bản chi nhánh khi **mất kết nối Trung tâm** vẫn: tiếp đón (tự tra cứu trong chi nhánh), thêm bệnh nhân (mã
`BN-CN1-0001` do chi nhánh cấp), tạo lượt khám / chữa, kê đơn, danh mục chi nhánh, báo cáo chi nhánh. Không làm được:
sửa danh mục chung, hồ sơ toàn chuỗi, tạo lượt cho bệnh nhân chưa có hồ sơ tại chi nhánh.

Chạy thử cả 4 cơ sở trên 1 máy (cần cụm DB đang chạy như mục 1):

```bash
cd source_code/backend/scripts
./phan_tan.sh up              # Trung tâm :3100 · Cầu Giấy :3101 · Đống Đa :3102 · Ba Đình :3103
./phan_tan.sh mat-mang cn1    # CN1 mất mạng: ngắt CSDL S1 + api-cn1 khỏi mạng chung (hq | cn1 | cn2 | cn3)
./phan_tan.sh trang-thai      # cơ sở nào đang nối mạng
./phan_tan.sh co-mang cn1     # nối lại (co-mang all) — Logical Replication tự đồng bộ bù
./phan_tan.sh down            # nối lại mọi cơ sở, dừng 8 container web/api (4 container DB giữ nguyên)
```

- Mỗi cơ sở có một mạng Docker riêng ("LAN": web, api và CSDL của cơ sở, tên `db-cn1`…); mạng của cụm DB
  (`csdl_default`) đóng vai "WAN". `mat-mang` chỉ gỡ khỏi WAN nên api của cơ sở vẫn tới CSDL của mình.
- Luôn dừng bằng `./phan_tan.sh down` (không `docker compose down` trực tiếp): script phải gỡ 4 container DB khỏi LAN
  và nối lại WAN trước. Chạy song song được với bản một-cho-tất-cả ở :3000.
- 4 JVM trên 1 máy: mỗi api giới hạn heap 384 MB, cả cụm cần khoảng 2–3 GB RAM cho Docker Desktop.
- Trong lúc một cơ sở "mất mạng", cổng `localhost:543x` của CSDL đó có thể không vào được (DBeaver báo connection reset,
  `EntitySchemaTest` tự bỏ qua). Khi nối lại, gắn / gỡ LAN, script khởi động lại container CSDL đó (2–3 giây, dữ liệu giữ
  nguyên) vì Docker Desktop không tự chuyển cổng sang IP mới của container.
- Bản chi nhánh chờ tối đa 3 giây (`connectionTimeout` của pool) rồi mới báo lỗi ở các thao tác cần S0.
- Triển khai thật trên nhiều máy còn cần (chưa làm, thuộc nhóm DB): đổi tên container trong `CSDL/02_*`, `03_setup_subscriber_*`,
  `06_*` sang hostname/IP, mở `listen_addresses` / `pg_hba.conf`, đặt `max_slot_wal_keep_size` ở S0 (chi nhánh offline lâu làm
  S0 giữ WAL), `connect_timeout` cho các FDW server, và VPN giữa các cơ sở (mật khẩu FDW / subscription đang ghi rõ).

## 2. Lệnh thường dùng

| Việc | Lệnh |
|---|---|
| Test backend (unit service, `@WebMvcTest` controller, context Spring — không cần DB) | `cd backend && ./mvnw test` |
| Tắt JIT của PostgreSQL trên cả 4 container (truy vấn toàn chuỗi qua FDW nhanh hơn ~1,5 giây cho mọi client: psql, pgAdmin…) | `cd backend/scripts && ./tat_jit.sh` · bật lại: `./tat_jit.sh --bat` · xem: `./tat_jit.sh --xem` |
| Kiểm tra kiểu + lint frontend | `cd frontend && npm run typecheck && npm run lint` |
| Đóng gói 1 file jar chứa cả FE | `cd frontend && npm run build` → `cd ../backend && ./mvnw clean package` |
| Chạy bản đóng gói → http://localhost:8080 | `java -jar backend/target/clinic-backend-0.0.1-SNAPSHOT.jar` |
| Chạy cả FE + BE bằng Docker → http://localhost:3000 | `cd backend/scripts && ./docker_app.sh` (mục 1.1) · dừng: `./docker_app.sh --down` |
| Backend chạy trong mạng Docker của cụm DB | thêm `SPRING_PROFILES_ACTIVE=docker` (dùng tên container, xem `application-docker.yml`; image backend đã bật sẵn) |

`npm run build` ghi vào `backend/src/main/resources/static/` (đã có trong `.gitignore`), Spring Boot phục vụ luôn FE ở cổng 8080.

## 3. Ứng dụng chọn trạm CSDL thế nào

```
FE ──(header X-Site: S1|S2|S3)──► SiteFilter ──► SiteContext ──► SiteRoutingDataSource ──► pool Hikari của trạm
```

| Đường dẫn API | Trạm |
|---|---|
| `/api/master/**`, `/api/hq/**` | luôn S0 (danh mục chung, báo cáo toàn chuỗi) |
| `/api/**` khác | bản `all`: chi nhánh trong header `X-Site` (thiếu header → 400 `MISSING_SITE`) · bản `branch`: luôn `APP_LOCAL_SITE` · bản `hq`: 404 `NOT_SERVED` (mục 1.2) |
| `/api/system/**` | không định tuyến (tự kiểm tra các trạm đã khai báo; `/api/system/mode` cho biết bản đang chạy) |

- Danh sách trạm, mã chi nhánh, URL JDBC: `backend/src/main/resources/application.yml` (`app.sites`).
- Response có header `X-Site-Used` cho biết request đã chạy ở trạm nào.
- Mỗi transaction chỉ chạm 1 trạm. Ghi danh mục ở S0 → Logical Replication đẩy xuống S1–S3 sau ~1–2 giây.
- Lỗi PostgreSQL được đổi thành JSON tiếng Việt (`exception/GlobalExceptionHandler`): `23505` trùng → 409, `23503` khoá ngoại → 409, `23514` CHECK → 400, `P0001` (RAISE trong trigger) → 422, mất kết nối trạm → 503.

## 4. Đã làm

**Tiếp đón & khám chữa (theo `X-Site`)**

| Màn hình | API |
|---|---|
| Tổng quan chi nhánh — lượt hôm nay, đợt đang điều trị, doanh thu tháng, lịch hẹn, đợt quá hạn, bệnh nhiều nhất | `GET /api/tong-quan` |
| Tiếp đón — tra cứu bệnh nhân toàn chuỗi, chi nhánh đang có hồ sơ; mất kết nối thì tra cứu trong chi nhánh | `GET /api/benh-nhan/toan-chuoi?q=` · `GET /api/hq/benh-nhan/chi-nhanh?ma=BN01,BN02` · `GET /api/benh-nhan/cuc-bo?q=` |
| Tạo lượt khám — chẩn đoán mở đợt, y tá, dịch vụ, thuốc, thiết bị | `POST /api/luot-kham` |
| Tạo lượt chữa — chọn đợt đang mở, "Khỏi" kết thúc đợt; ngừng điều trị | `GET /api/benh-nhan/{maBN}/dot-dang-dieu-tri` · `POST /api/luot-chua` · `PUT /api/dot-dieu-tri/{maCDDT}/ngung` |
| Danh sách lượt — lọc, xem chi tiết, xoá lượt nhập nhầm | `GET /api/luot?tu=&den=&loai=&khoa=&bs=&q=` · `GET/DELETE /api/luot/{maLuot}` |

**Danh mục chi nhánh (F1 — thêm / sửa / xoá theo `X-Site`)**

| Màn hình | API |
|---|---|
| Bác sĩ — tìm, lọc khoa, CRUD | `GET/POST /api/bac-si` · `GET/PUT/DELETE /api/bac-si/{maNV}` |
| Y tá — tìm, lọc khoa, CRUD, đếm lượt hỗ trợ | `GET/POST /api/y-ta` · `GET/PUT/DELETE /api/y-ta/{maNV}` |
| Bệnh nhân — CRUD, tra cứu toàn chuỗi khi thêm mới | `GET/POST /api/benh-nhan` · `GET/PUT/DELETE /api/benh-nhan/{maBN}` · `GET /api/benh-nhan/toan-chuoi?q=` |
| Khoa — CRUD | `GET/POST /api/khoa` · `GET/PUT/DELETE /api/khoa/{maKhoa}` |
| Thiết bị — CRUD | `GET/POST /api/thiet-bi` · `GET/PUT/DELETE /api/thiet-bi/{maTB}` |

**Danh mục chung (F1 — ghi tại S0, Logical Replication đẩy xuống chi nhánh)**

| Màn hình | API |
|---|---|
| Thuốc — CRUD + so bản sao ở chi nhánh | `GET/POST /api/master/thuoc` · `PUT/DELETE /api/master/thuoc/{ma}` · `GET /api/thuoc` |
| Bệnh (ICD-10) — CRUD | `GET/POST /api/master/benh` · `PUT/DELETE /api/master/benh/{ma}` · `GET /api/benh` |
| Dịch vụ y tế — CRUD | `GET/POST /api/master/dich-vu` · `PUT/DELETE /api/master/dich-vu/{ma}` · `GET /api/dich-vu` |
| Lương cơ sở — thêm mức mới, sửa / xoá mức chưa áp dụng | `GET/POST /api/master/luong-co-so` · `PUT/DELETE /api/master/luong-co-so/{yyyy-MM-dd}` |
| Chi nhánh — xem, sửa thông tin | `GET /api/master/chi-nhanh` · `PUT /api/master/chi-nhanh/{maCn}` |
| Tổng quan hệ thống | `GET /api/system/sites` (S0–S3) |

**Báo cáo chi nhánh (F2–F5)**

| Màn hình | API |
|---|---|
| F2 · Thống kê bệnh theo tháng (giảm dần theo số lần mắc) | `GET /api/bao-cao/thong-ke-benh?thang=yyyy-MM` |
| F3 · Lương bác sĩ & y tá trong tháng | `GET /api/bao-cao/luong?thang=yyyy-MM` |
| F4 · Hồ sơ bệnh nhân: tình trạng hiện tại, lịch sử khám/chữa, chi phí từng lượt | `GET /api/benh-nhan/{maBN}/ho-so` |
| F5 · Doanh thu theo kỳ: tiền khám, dịch vụ, thuốc, thiết bị | `GET /api/bao-cao/doanh-thu?tu=yyyy-MM-dd&den=yyyy-MM-dd` |

**Báo cáo toàn chuỗi (chạy ở S0, view toàn cục qua postgres_fdw — cần cả 4 trạm đang chạy)**

| Màn hình | API |
|---|---|
| Doanh thu toàn chuỗi — theo chi nhánh, theo tháng × chi nhánh, từng khoản | `GET /api/hq/doanh-thu?tu=&den=` |
| Hồ sơ bệnh nhân toàn chuỗi — mỗi lượt / đợt gắn chi nhánh phát sinh | `GET /api/hq/benh-nhan?q=` · `GET /api/hq/benh-nhan/{maBN}/ho-so` |
| Thống kê bệnh toàn chuỗi — số lần mắc tại từng chi nhánh | `GET /api/hq/thong-ke-benh?thang=yyyy-MM` |

Bỏ trống `thang` / `tu`, `den` → lấy tháng hiện tại.

Ghi chú nghiệp vụ:
- **Mã lượt** (BR5, đề bài yêu cầu chứa khoa và bác sĩ): `K-{MaKhoa}-{MaBS}-yyMMdd-NNN` cho lượt khám, `C-…` cho lượt chữa, vd `K-CN1-TMH-BS-CN1-01-261024-001`; mã đợt `CDDT-CN1-NNN`. Dữ liệu mẫu cũ (`K-CN1-001`) giữ nguyên. Hai người lưu cùng lúc mà trùng mã thì backend sinh lại mã, tối đa 3 lần.
- **Lượt khám** (1 transaction tại chi nhánh): bệnh nhân chưa có hồ sơ ở chi nhánh thì chép bản ghi từ view `BENH_NHAN` ở S0 (giữ mã) → `LUOT_KHAM_CHUA` → `LAN_KHAM` → mỗi chẩn đoán một đợt "Đang điều trị" → `HO_TRO` → `SU_DUNG_DV` / `KE_DON` / `SU_DUNG_TB`. Kiểm tra:
  - BR6: khoa, bác sĩ, y tá, thiết bị phải của chi nhánh. Bác sĩ phải đang làm việc và đúng khoa; thiết bị không được "Ngừng hoạt động".
  - BR1: trong một lượt không chẩn đoán trùng bệnh. BR13: không chẩn đoán thì không mở đợt và kết luận ghi "Không phát hiện bệnh".
  - BR4: bệnh nhân đang có đợt cùng bệnh chưa kết thúc ở bất kỳ chi nhánh nào thì chặn (kiểm tra qua view toàn cục ở S0). S0 tắt → chỉ kiểm tra tại chi nhánh và trả cảnh báo.
  - BR7: đơn giá áp dụng lấy từ danh mục tại lúc lưu. Ngày hẹn phải sau ngày khám; thời gian không được ở tương lai.
- **Lượt chữa**:
  - BR2: bác sĩ, khoa lấy theo lần khám mở đợt.
  - BR3: chỉ thêm vào đợt "Đang điều trị", thời gian phải sau lượt gần nhất của đợt. Lần thứ = số lần đã chữa + 1.
  - Có ít nhất 1 hình thức chữa (`LAN_CHUA_HINH_THUC`).
  - BR11: không tính dịch vụ loại "Khám bệnh".
  - BR10: tình trạng "Khỏi" → đợt "Đã khỏi" + `NgayKetThuc`, và bác sĩ được thưởng ở F3. Bác sĩ được chỉnh số lần chữa cần thiết.
- **BR12**: đợt "Đang điều trị" mà bệnh nhân quá 30 ngày không quay lại được đánh dấu "Quá 30 ngày" (tổng quan, tạo lượt chữa, hồ sơ). CSDL không có job tự chuyển, nên có nút **Ngừng điều trị** — bác sĩ không được thưởng cho đợt đó.
- **Xoá lượt nhập nhầm**:
  - Chỉ xoá được lượt trong tháng hiện tại, để lương và doanh thu các tháng trước không đổi.
  - Lượt chữa: phải là lần chữa cuối của đợt. Nếu chính lần đó kết thúc đợt thì đợt mở lại "Đang điều trị".
  - Lượt khám: các đợt mở từ lượt đó phải chưa có lần chữa. Khoá ngoại CASCADE xoá kèm đợt, y tá, chi phí.
- **Mã tự sinh** khi để trống: `BS-CN1-02`, `YT-CN1-03`, `TB-CN1-02`, `CN1-K03` (khoa), `B05`, `DV05`, `T05`. Mã bệnh nhân mới dạng `BN-CN1-0001` mang mã chi nhánh cấp nó, nên không trùng toàn chuỗi mà vẫn sinh được khi mất kết nối: có mạng thì tính số tiếp theo trên view `TRA_CUU_BENH_NHAN_TOAN_CHUOI`, mất mạng thì trên bảng `BENH_NHAN` của chi nhánh. Mã cũ (`BN01`…, `BN000001` trong bộ CSV) giữ nguyên.
- **Bệnh nhân đã khám ở chi nhánh khác**: ô "Tra cứu toàn chuỗi" trong hộp thoại thêm mới → "Dùng thông tin này" điền sẵn thông tin và dùng lại đúng mã BN.
- **Xoá (BR9)**: bác sĩ / y tá / thiết bị đã có lịch sử → chuyển "Ngừng hoạt động" (SOFT), chưa có → xoá hẳn (HARD). Bệnh nhân đã có lượt khám, khoa còn nhân viên hoặc lượt, thuốc / bệnh / dịch vụ đã dùng ở bất kỳ chi nhánh nào (kiểm tra qua view toàn cục ở S0) → từ chối 409 `IN_USE`.
- **Lương cơ sở** là dữ liệu lịch sử: mức đã có hiệu lực (ngày áp dụng ≤ hôm nay) không sửa / xoá được (409 `HISTORY_LOCKED`); đổi lương = thêm mức mới.
- **F2** — một lần mắc = một đợt `CHAN_DOAN_DIEU_TRI` có lần khám mở đợt hoặc lần chữa trong tháng. Các lượt của cùng đợt chỉ tính 1 lần; mắc lại sau khi khỏi là đợt mới.
- **F3** — lương = hệ số × lương cơ sở có hiệu lực cuối tháng + 1.000.000 ₫ × số đợt "Đã khỏi" kết thúc trong tháng (bác sĩ khám mở đợt) + 200.000 ₫ × số lượt y tá hỗ trợ trong tháng. Tính trực tiếp từ dữ liệu, **chưa chốt lương** được vì schema không có bảng lương. Chưa có mức lương cơ sở nào → 422 `NO_BASE_SALARY`.
- **F4** — chi phí từng lượt = dịch vụ + thuốc + thiết bị, theo đơn giá áp dụng lưu ở `SU_DUNG_DV`, `KE_DON`, `SU_DUNG_TB` (BR7).
- **F5** — tiền khám = dịch vụ loại "Khám bệnh"; lần chữa trong đợt điều trị không thu tiền khám (BR11). Kỳ tính theo thời gian của lượt, `den` tính cả ngày cuối.
- Chi nhánh: không có thêm / xoá / đổi mã (FDW và view toàn cục cố định 3 chi nhánh).

**Kiểm thử**: `./mvnw test` chạy 159 test (service bằng Mockito, controller bằng `@WebMvcTest`). Các API và luồng giao diện đã được thử với cụm 4 trạm chạy thật và dữ liệu mẫu 3 tháng: tiếp đón bệnh nhân của chi nhánh khác → khám → chữa → khỏi → xoá lượt → ngừng điều trị → hồ sơ toàn chuỗi. Dữ liệu mẫu trong `CSDL/` hiện ít, nên báo cáo F2, F3, F5 sẽ gần như trống nếu không nạp thêm lượt khám/chữa. Muốn xem thử có số liệu, nạp `backend/scripts/demo_data_s1.sql` vào S1 (cách chạy ở đầu file; không thuộc script của nhóm CSDL).

Mọi mục trong menu đã có màn hình thật (đối chiếu thiết kế: `requirements/02_ui-prototype.html`).

**Hiệu năng truy vấn toàn chuỗi**:
- Ứng dụng tắt JIT của PostgreSQL cho các kết nối của mình (`SET jit = off` trong `DataSourceConfig`). Foreign table không có thống kê nên planner ước lượng chi phí rất cao và bật JIT, làm mỗi báo cáo toàn chuỗi mất thêm khoảng 1,5 giây biên dịch.
- Các truy vấn hồ sơ / đợt dùng CTE `GROUP BY` thay vì truy vấn con theo từng dòng, nên ở S0 mỗi bảng chỉ quét qua FDW một lần.
- Ứng dụng không đổi cấu hình của CSDL. Muốn tắt JIT cho mọi client (psql, pgAdmin khi demo truy vấn phân tán) thì chạy `backend/scripts/tat_jit.sh`. Script ghi `ALTER SYSTEM SET jit = off` vào từng container, không sửa `CSDL/`, và mất khi `init_db.sh --reset` → chạy lại sau khi reset.

## 5. Cấu trúc backend và thêm một màn hình mới

Package `vn.antam.clinic` chia theo tầng; quy tắc đầy đủ trong `backend/.claude/skills/conventions/SKILL.md`.

```
controller/      chỉ HTTP: nhận request, @Valid, gọi service          (không dùng JdbcClient / entity)
service/         nghiệp vụ, @Transactional, Request → Entity → Response (không viết SQL)
repository/      nơi DUY NHẤT dùng JdbcClient / SQL
entity/          1 record = 1 bảng, tên thành phần = tên cột
dto/request/     dữ liệu FE gửi lên + validation
dto/response/    dữ liệu trả về FE (kể cả ApiErrorResponse, DeleteResponse)
config/ · site/ · exception/ · util/   cấu hình · định tuyến trạm · xử lý lỗi · tiện ích
```

Làm theo mẫu bác sĩ (chi nhánh: `BacSiController` → `BacSiService` → `NhanVienRepository` + `BacSiRepository`) hoặc thuốc (S0: `ThuocController` → `ThuocService` → `ThuocRepository`):

1. **Backend** — theo thứ tự:
   1. `entity/Xxx` — đã có sẵn cho cả 21 bảng; tên thành phần trùng tên cột trong `CSDL/01_init_schema_*.sql` nên `JdbcClient` tự ánh xạ (`.query(Xxx.class)` khi đọc, `.paramSource(xxx)` khi ghi). Nếu schema đổi thì sửa entity tương ứng — `EntitySchemaTest` sẽ báo khi lệch (cần cụm DB đang chạy, không thì test tự bỏ qua).
   2. `repository/XxxRepository` — SQL thuần. Đọc 1 bảng trả entity; đọc có JOIN / `count` trả thẳng `XxxResponse`.
   3. `dto/request/XxxRequest` (validation, thông báo tiếng Việt) và `dto/response/XxxResponse` (`static from(entity, ...)`).
   4. `service/XxxService` — `@Transactional` trên phương thức ghi; đọc dùng `readOnly = true`; đọc cần chạy được khi một trạm tắt dùng `Propagation.NOT_SUPPORTED`.
   5. `controller/XxxController` — `/api/...` cho chi nhánh, `/api/master/...` cho danh mục S0, `/api/hq/...` cho báo cáo toàn chuỗi.
   6. Test: `service/XxxServiceTest` (Mockito) và `controller/XxxControllerTest` (`@WebMvcTest`), chạy `./mvnw test`.
2. **Frontend** — thêm kiểu vào `src/api/types.ts`, hàm gọi vào `src/api/endpoints.ts` (truyền `site` với API chi nhánh), trang trong `src/pages/`, route trong `src/App.tsx`, đánh dấu `done: true` cho mục trong `src/layout/menu.tsx`. Giao diện chỉ dùng component `Base*` (xem mục 5.1).

### 5.1. Component dùng chung phía FE (`frontend/src/components/common`)

Màn hình **không dùng trực tiếp thẻ Ant Design**. Mỗi component Ant Design được bọc một lần thành `Base*` (đặt sẵn mặc định chung), chia thư mục theo nhóm chức năng:

```
components/common/
├── index.ts      gom export của mọi nhóm — màn hình import từ đây
├── provider/     cấu hình chung       BaseProvider (tiếng Việt + theme, dùng trong main.tsx)
├── layout/       khung & bố cục       BaseLayout, BaseHeader, BaseSider, BaseContent, BaseMenu, BaseRow, BaseCol, BaseSpace, BaseDivider,
│                                      BaseTabs
├── form/         form & ô nhập liệu   BaseForm, BaseFormItem, useBaseForm, BaseInput, BaseSearchInput, BaseNumberInput,
│                                      BaseMoneyInput, BaseSelect, BaseTagsInput, BaseTextArea, BaseDatePicker,
│                                      BaseDateTimePicker, BaseMonthPicker, BaseDateRangePicker, BaseCheckableTags,
│                                      BaseSegmented, BaseFormList, useBaseWatch
├── table/        bảng & thành phần    BaseTable, BaseColumns<T>, BaseEditButton, BaseDeleteButton (nút trên từng dòng),
│                                      BaseSummaryRow, BaseSummaryCell (dòng tổng cuối bảng)
├── button/       nút bấm              BaseButton
├── typography/   chữ                  BaseTitle, BaseText, BaseParagraph
├── display/      hiển thị thông tin   BaseTag, BaseBadge, BaseTooltip, BaseCard, BaseDescriptions, BaseStatistic,
│                                      BaseProgress, BaseEmpty
├── feedback/     phản hồi người dùng  BaseAlert, BaseErrorAlert, BaseModal, BaseFormModal, BaseDrawer, BaseResult, BaseSkeleton,
│                                      useNotify
└── icons/        icon                 AddIcon, EditIcon, DeleteIcon… (tên XxxOutlined → XxxIcon)
```

```tsx
import { BaseButton, BaseTable, BaseFormModal, AddIcon, useNotify } from '../components/common'
```

- Một số component có sẵn hành vi chung: `BaseTable` phân trang 10 dòng (`unit="bác sĩ"` → "12 bác sĩ"); `BaseFormModal` = hộp thoại + form + hiện lỗi backend kèm SQLSTATE; `BaseSelect` nhận thẳng mảng chuỗi; `BaseMoneyInput` hiện 25.000; `BaseDatePicker` định dạng DD/MM/YYYY, `disableFuture`.
- Màn hình CRUD dùng hook `src/hooks/useCrudMutations.ts`: gói sẵn mutation lưu / xoá, đổ lỗi `fieldErrors` của backend vào ô form, thông báo xoá hẳn / chuyển "Ngừng hoạt động" và làm mới các query liên quan (xem `YTaPage.tsx`).
- Form lượt khám / chữa dùng chung các khối trong `src/pages/luot/`: `LuotItemsEditor` (3 thẻ dịch vụ · thuốc · thiết bị bằng `BaseFormList`), `LuotSummaryCard` (tóm tắt tiền ghim bên phải), `LuotDetailDrawer` (chi tiết + xoá lượt), `useLuotCatalogs` (danh mục của chi nhánh). Form dài đặt `submitOnEnter={false}` để nhấn Enter khi đang gõ không lưu nhầm.
- `BaseMonthPicker` (chọn tháng, trả về ngày đầu tháng) và `BaseDateRangePicker` (chỉ gọi `onChange` khi chọn đủ 2 ngày) dùng cho các trang báo cáo.
- Cần component Ant Design chưa có (Checkbox, Radio…) → tạo `BaseXxx.tsx` trong nhóm phù hợp (vd `form/BaseCheckbox.tsx`), export trong `index.ts` của nhóm đó, rồi mới dùng ở màn hình.
- Muốn đổi giao diện cho cả ứng dụng (cỡ bảng, chữ trên nút, định dạng ngày, màu chủ đạo…) → chỉ sửa file `Base*` tương ứng / `THEME` trong `provider/BaseProvider.tsx`.
- `npm run lint` báo lỗi nếu file ngoài `components/common` import `antd` hoặc `@ant-design/icons` (luật `no-restricted-imports` trong `.oxlintrc.json`).

## 6. Sự cố thường gặp

| Hiện tượng | Cách xử lý |
|---|---|
| Thẻ trạm đỏ "Connection refused" | container chưa chạy: `docker ps`, hoặc chạy lại `backend/scripts/init_db.sh` |
| `./mvnw: Permission denied` | `chmod +x backend/mvnw` |
| `release version 21 not supported` | IntelliJ / terminal đang dùng JDK khác: `/usr/libexec/java_home -V`, chọn 21 |
| Port 8080 đang bận | `lsof -i :8080` hoặc đổi `server.port` trong `application.yml` |
| FE báo "Không gọi được backend" | backend chưa chạy ở 8080 (Vite proxy `/api` → `localhost:8080`) |
| Báo cáo toàn chuỗi / tiếp đón báo 503 `CHAIN_UNAVAILABLE` | một trạm đang tắt — thông báo nêu rõ trạm nào; báo cáo toàn chuỗi cần cả 4 trạm |
| Tạo lượt khám có cảnh báo "chưa kiểm tra được BR4 ở chi nhánh khác" | S0 đang tắt — lượt vẫn được lưu, BR4 chỉ được kiểm tra tại chi nhánh hiện tại |
| Docker: `network csdl_default declared as external, but could not be found` | cụm DB chưa chạy (`cd CSDL && docker compose up -d`) hoặc mạng có tên khác: dùng `./docker_app.sh` (tự dò), hoặc `DB_NETWORK=<tên> docker compose up -d` — xem tên bằng `docker network ls` |
| Docker: `Bind for 0.0.0.0:8080 failed: port is already allocated` | backend đang chạy ngoài Docker (IntelliJ): dừng nó, hoặc `API_PORT=8081 ./docker_app.sh` |
| Docker: giao diện báo `API_UNAVAILABLE` | container api chưa lên hoặc lỗi khi khởi động: `./docker_app.sh --ps`, `docker logs antam_clinic_api` |
| Docker: sửa code nhưng bản chạy không đổi | chưa build lại image: `./docker_app.sh` hoặc `docker compose up -d --build` |
| Docker: `unable to prepare context: path "../frontend" not found` | repo frontend chưa nằm cạnh repo backend — clone vào `source_code/frontend` |
