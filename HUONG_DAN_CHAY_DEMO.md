# Hướng dẫn chạy An Tâm Clinic — demo nhiều máy và thử trên 1 máy

Hệ thống có 4 cơ sở: **Trung tâm (S0)** và 3 chi nhánh **Cầu Giấy (CN1)**, **Đống Đa (CN2)**, **Ba Đình (CN3)**. Khi demo,
mỗi cơ sở chạy trên một máy riêng (CSDL + backend + giao diện), các máy nối với nhau qua **Tailscale**.

| Máy | Được làm gì |
|---|---|
| Trung tâm | Danh mục chung (thuốc, bệnh, dịch vụ, lương cơ sở, chi nhánh), báo cáo toàn chuỗi. Chọn một chi nhánh để **xem** dữ liệu của chi nhánh đó — không thêm / sửa / xoá được |
| Chi nhánh | Thêm / sửa / xoá dữ liệu của **chính chi nhánh đó**. Không xem được chi nhánh khác và chức năng Trung tâm. Vẫn làm việc khi mất mạng |

Có hai cách chạy:

- **[Phần A](#phần-a--thử-trên-1-máy)** — thử trên 1 máy: 4 "máy" chạy chung trên máy của bạn, không cần Tailscale. Nên
  chạy cách này trước để làm quen và tập kịch bản demo.
- **[Phần B](#phần-b--demo-trên-4-máy)** — demo thật trên 4 máy.

---

## Chuẩn bị chung

1. Cài **Docker Desktop** và mở lên (biểu tượng cá voi đứng yên là sẵn sàng). Cấp cho Docker ít nhất 4 GB RAM
   (Settings → Resources) nếu thử trên 1 máy.
2. Cài **Git**.
3. Lấy source code. Hai repo phải nằm **cạnh nhau** trong thư mục `source_code`:

   ```bash
   mkdir -p antam/source_code && cd antam/source_code
   git clone https://github.com/longkieu2710/antam-clinic-backend.git backend
   git clone https://github.com/longkieu2710/antam-clinic-frontend.git frontend
   cd backend && git checkout feat/che-do-trien-khai && cd ../frontend && git checkout feat/che-do-trien-khai && cd ..
   ```

4. Máy nào chạy bước **khởi tạo CSDL** (Phần A, hoặc bước B6) còn cần thư mục **`CSDL/`** của nhóm DB, đặt cạnh
   `source_code`:

   ```
   antam/
   ├── CSDL/              ← các file .sql của nhóm DB (chỉ máy khởi tạo CSDL cần)
   └── source_code/
       ├── backend/
       └── frontend/
   ```

5. Các lệnh `.sh` chạy trong **Terminal** của macOS / Linux. Máy Windows chỉ cần chạy `may.ps1` (Phần B).

Mọi lệnh bên dưới chạy trong thư mục `source_code/backend`.

---

## Phần A — Thử trên 1 máy

Dựng đủ 4 "máy" ngay trên máy của bạn. Dùng đúng cấu hình và script như khi demo thật, chỉ thay Tailscale bằng một mạng
Docker. **Không cần sửa `application.yml`**, không cần Tailscale.

### A1. Bật

```bash
cd source_code/backend
./scripts/gia_lap_4_may.sh up
```

Lần đầu mất vài phút (build backend + giao diện, rồi khởi tạo CSDL cho cả 4 máy). Xong khi thấy:

```
 Trung tâm (S0)   http://localhost:3200   nối mạng
 Cầu Giấy (S1)    http://localhost:3201   nối mạng
 Đống Đa (S2)     http://localhost:3202   nối mạng
 Ba Đình (S3)     http://localhost:3203   nối mạng
```

Mở 4 địa chỉ trên ở 4 tab trình duyệt — mỗi tab là một máy.

### A2. Các lệnh

| Lệnh | Tác dụng |
|---|---|
| `./scripts/gia_lap_4_may.sh mat-mang cn1` | Máy CN1 mất mạng (giống tắt Tailscale). Thay `cn1` bằng `hq`, `cn2`, `cn3` |
| `./scripts/gia_lap_4_may.sh co-mang cn1` | Nối lại (`co-mang all` để nối lại tất cả) |
| `./scripts/gia_lap_4_may.sh trang-thai` | Máy nào đang nối mạng |
| `./scripts/gia_lap_4_may.sh init` | Khởi tạo lại CSDL của 4 máy về dữ liệu mẫu |
| `./scripts/gia_lap_4_may.sh down` | Tắt và xoá cả 4 máy (kể cả dữ liệu) |

Tắt máy tính rồi bật lại: chạy `up` lần nữa — dữ liệu vẫn còn, không khởi tạo lại.

### A3. Kịch bản nên thử

Xem [Kịch bản demo](#kịch-bản-demo) — làm y hệt, chỉ thay "tắt Tailscale" bằng lệnh `mat-mang`, và địa chỉ
`localhost:3000` của từng máy bằng `:3200` – `:3203`.

### A4. Gặp lỗi

| Hiện tượng | Cách xử lý |
|---|---|
| `port is already allocated` / cổng 3200–3203 bận | Tắt chương trình đang dùng cổng đó (`lsof -i :3200` để xem) |
| Một máy "chưa sẵn sàng" | Docker thiếu RAM — tăng RAM cho Docker (≥ 4 GB) rồi `up` lại |
| Muốn làm lại từ đầu | `./scripts/gia_lap_4_may.sh down` rồi `up` |

---

## Phần B — Demo trên 4 máy

### B1. Phân vai

| Máy | Giá trị `may-nay` | Mở giao diện |
|---|---|---|
| Trung tâm | `hq` | http://localhost:3000 |
| Cầu Giấy | `cn1` | http://localhost:3000 |
| Đống Đa | `cn2` | http://localhost:3000 |
| Ba Đình | `cn3` | http://localhost:3000 |

Máy chạy bước **B6 (khởi tạo CSDL)** phải là máy **macOS / Linux** và có thư mục `CSDL/` — thường là máy Trung tâm.

### B2. Tailscale (mỗi máy)

1. Cài Tailscale, đăng nhập **cùng một tài khoản / tailnet** trên cả 4 máy.
2. Lấy IP Tailscale của máy (dạng `100.x.x.x`):
   - macOS: biểu tượng Tailscale trên thanh menu → dòng "This Device", hoặc
     `/Applications/Tailscale.app/Contents/MacOS/Tailscale ip -4`
   - Windows: biểu tượng Tailscale ở khay hệ thống, hoặc `tailscale ip -4` trong PowerShell
3. Gửi IP của máy mình cho cả nhóm → điền vào bảng ở bước B3.

> Không dùng IP Wi-Fi / hotspot (`192.168.x.x`, `172.20.10.x`): IP đó đổi mỗi lần kết nối lại. IP Tailscale cố định theo
> máy.

### B3. Sửa `application.yml` (mỗi máy)

Mở `source_code/backend/src/main/resources/application.yml`, sửa khối ở đầu mục `app:`:

```yaml
app:
  may-nay: cn1                 # ← chỉ dòng này khác nhau giữa các máy: hq | cn1 | cn2 | cn3
  dia-chi-may:                 # ← giống hệt nhau trên cả 4 máy
    hq:  100.101.1.10
    cn1: 100.101.1.11
    cn2: 100.101.1.12
    cn3: 100.101.1.13
```

Phải điền **đủ cả 4 IP** trên mọi máy (thiếu thì script báo `Thiếu app.dia-chi-may.cn1 …`).

### B4. Mở cổng (mỗi máy)

- **Cổng 5432** phải trống — CSDL của máy mở cổng này cho 3 máy kia. Nếu máy có cài PostgreSQL sẵn, tắt nó đi. Máy đang
  chạy cụm 4 container của thư mục `CSDL/` (lúc phát triển): `cd CSDL && docker compose stop`.
- **Cổng 3000** phải trống (giao diện).
- **Windows**: cho phép kết nối vào cổng 5432 — mở PowerShell **Run as Administrator**:

  ```powershell
  New-NetFirewallRule -DisplayName 'AnTam CSDL' -Direction Inbound -Protocol TCP -LocalPort 5432 -Action Allow
  ```

- **macOS**: nếu hiện hỏi "cho phép Docker nhận kết nối đến" → chọn Cho phép.

### B5. Bật máy (mỗi máy)

macOS / Linux:

```bash
cd source_code/backend
./scripts/may.sh up
```

Windows (PowerShell, trong thư mục `source_code\backend`):

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\may.ps1 up
```

Lần đầu mất vài phút. Xong khi thấy `Sẵn sàng: http://localhost:3000`.

Khi **cả 4 máy** đã bật, mỗi máy kiểm tra kết nối tới 3 máy kia:

```bash
./scripts/may.sh kiem-tra                                              # macOS / Linux
powershell -ExecutionPolicy Bypass -File .\scripts\may.ps1 kiem-tra    # Windows
```

Phải thấy 3 dòng xanh `… nhận kết nối` rồi mới sang B6. Báo đỏ → xem [Gặp lỗi khi demo](#b9-gặp-lỗi-khi-demo).

### B6. Khởi tạo CSDL — chạy MỘT lần, trên MỘT máy macOS / Linux

```bash
./scripts/init_may.sh
```

Script nạp schema + dữ liệu mẫu của nhóm DB vào CSDL của cả 4 máy qua Tailscale, cài replication, FDW và phần bảo mật
thông tin bệnh nhân. Mất vài phút; xong khi thấy `CSDL CỦA 4 MÁY ĐÃ SẴN SÀNG.`

Chạy lại `init_may.sh` = đưa dữ liệu cả 4 máy về dữ liệu mẫu (mất dữ liệu đã nhập).

### B7. Dùng

Mỗi máy mở http://localhost:3000.

- **Máy chi nhánh**: thanh tiêu đề ghi "Bản chi nhánh", ô chi nhánh bị khoá. Có Tiếp đón, tạo lượt khám / chữa, danh mục
  của chi nhánh, "Thuốc (bản sao)", báo cáo chi nhánh.
- **Máy Trung tâm**: ghi "Bản Trung tâm". Chọn chi nhánh ở ô "Xem chi nhánh" để xem dữ liệu chi nhánh đó (hiện tag
  "Chỉ xem", không có nút thêm / sửa / xoá). Các màn Trung tâm (thuốc, bệnh, dịch vụ, báo cáo toàn chuỗi) sửa được bình
  thường.

### B8. Các lần sau

| Việc | Lệnh (Windows: `powershell -ExecutionPolicy Bypass -File .\scripts\may.ps1 <lệnh>`) |
|---|---|
| Bật lại (dữ liệu vẫn còn) | `./scripts/may.sh up` |
| Tắt (giữ dữ liệu) | `./scripts/may.sh down` |
| Xem log khi có lỗi | `./scripts/may.sh logs` (Ctrl+C để thoát) |
| Xoá dữ liệu CSDL của máy này | `./scripts/may.sh xoa-du-lieu` — sau đó phải chạy lại `init_may.sh` |
| Đổi IP | Sửa `dia-chi-may` rồi `./scripts/may.sh up` (tự build lại phần thay đổi, nhanh) |
| Đổi vai máy (vd `cn1` → `cn2`) | `./scripts/may.sh down` **trước**, rồi mới sửa `may-nay`, rồi `./scripts/may.sh up` — nếu sửa trước, CSDL của vai cũ vẫn chạy và giữ cổng 5432 |

### B9. Gặp lỗi khi demo

| Hiện tượng | Nguyên nhân và cách xử lý |
|---|---|
| `Chưa chọn máy: sửa dòng 'may-nay:' …` | Chưa điền `may-nay` (bước B3) |
| `Thiếu app.dia-chi-may.cn1 …` | Chưa điền đủ 4 IP (bước B3) |
| `docker compose up lỗi — cổng 5432 hoặc 3000 …` | Cổng đang bị chiếm (bước B4) |
| `kiem-tra` báo đỏ một máy | 1) Máy kia đã `up` chưa, Tailscale hai máy có đang bật không. 2) IP trong `dia-chi-may` đúng chưa. 3) Firewall máy kia đã mở 5432 chưa (Windows: lệnh ở B4) |
| Máy **Windows**: `kiem-tra` báo đỏ dù Tailscale bật, IP đúng | Docker (WSL2) không ra được mạng Tailscale. Mở `%UserProfile%\.wslconfig` (tạo nếu chưa có), thêm 2 dòng `[wsl2]` và `networkingMode=mirrored`, chạy `wsl --shutdown`, mở lại Docker Desktop, rồi `up` lại |
| `init_may.sh` báo `máy cn2 … không nhận kết nối` | Máy cn2 chưa `up` hoặc không gọi được từ máy đang chạy script — xem dòng `kiem-tra` ở trên |
| `init_may.sh` báo `replication chưa đồng bộ` | CSDL của chi nhánh đó không gọi được máy Trung tâm. Chạy `kiem-tra` trên máy chi nhánh đó |
| Giao diện báo `API_UNAVAILABLE` | Backend chưa lên hoặc lỗi khởi động: `./scripts/may.sh logs` |
| Bị chặn chạy script trên Windows | Luôn chạy qua `powershell -ExecutionPolicy Bypass -File …` như trên |

---

## Kịch bản demo

Thực hiện trên 4 máy (Phần B) hoặc 4 tab (Phần A).

1. **Dữ liệu tách theo chi nhánh** — ở máy CN1 vào Bác sĩ / Bệnh nhân: chỉ thấy dữ liệu Cầu Giấy, ô chi nhánh bị khoá.
2. **Trung tâm chỉ xem** — ở máy Trung tâm chọn "Đống Đa" ở ô "Xem chi nhánh", vào Bác sĩ: thấy dữ liệu CN2, tag "Chỉ xem",
   không có nút thêm / sửa / xoá.
3. **Bảo mật thông tin bệnh nhân** — ở máy CN3 vào Tiếp đón, nhập CCCD `001095006666`: chỉ báo "đã có mã BN02 … tại chi
   nhánh CN2", **không** hiện họ tên, địa chỉ hay bệnh sử. Bấm "Lập hồ sơ với mã này" → lập hồ sơ tại CN3 với mã BN02.
   Thử nhập họ tên "Trần": không tìm ra bệnh nhân của chi nhánh khác.
4. **Danh mục chung đồng bộ** — ở máy Trung tâm vào Thuốc, thêm một thuốc → vài giây sau thuốc xuất hiện ở "Thuốc (bản
   sao)" của các chi nhánh (Logical Replication).
5. **Chi nhánh mất mạng vẫn làm việc** — tắt Tailscale ở máy CN1 (Phần A: `mat-mang cn1`):
   - Máy CN1: Tiếp đón, thêm bệnh nhân mới (mã `BN-CN1-…` do chi nhánh tự cấp), tạo lượt khám → đều được.
   - Máy Trung tâm: Tổng quan hệ thống báo CN1 mất kết nối; Doanh thu toàn chuỗi báo lỗi nêu đúng CN1 (sau khoảng 5 giây).
   - Trong lúc đó, Trung tâm thêm một thuốc nữa.
6. **Nối lại** — bật lại Tailscale ở CN1 (Phần A: `co-mang cn1`): thuốc vừa thêm tự về CN1, báo cáo toàn chuỗi chạy lại
   bình thường.

---

## Phụ lục — chạy kiểu phát triển (1 app cho mọi chi nhánh)

Dùng cụm 4 container của thư mục `CSDL/` như trước khi có demo nhiều máy:

1. Để **trống** dòng `may-nay:` trong `application.yml`.
2. `cd CSDL && docker compose up -d` (lần đầu: `cd source_code/backend/scripts && ./init_db.sh`).
3. Cụm đã khởi tạo từ trước khi có phần bảo mật bệnh nhân: chạy thêm `./init_db.sh --bo-sung` (Windows, sau
   `CSDL\init_db.ps1`: `.\scripts\bo_sung.ps1`).
4. Chạy app: `cd source_code/backend/scripts && ./docker_app.sh` → http://localhost:3000, có ô chọn chi nhánh như cũ.

Chi tiết kỹ thuật (cấu hình, quy tắc truy cập, bảo mật): `README.md` mục 1.
