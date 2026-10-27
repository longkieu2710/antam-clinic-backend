# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository. The backend repo is the main one: it also holds the shared docs (`README.md`, in Vietnamese), `scripts/` and `docker-compose.yml`. The frontend repo must sit next to it in `../frontend/`, which has its own `CLAUDE.md`.

## What this is

A web app (Spring Boot backend and React frontend) for a university distributed-database project ("An Tâm Clinic", a chain of private clinics). The app sits on a **cluster of 4 PostgreSQL 16 containers**: S0 is the hub (Trung tâm), and S1, S2 and S3 are the branches CN1, CN2 and CN3. The DB team owns the schema and SQL scripts, which live **outside both repos** in `../../CSDL/`. Requirements and UI mockups are in `../../requirements/` (`02_ui-prototype.html` holds the planned screens and APIs for the menu items that aren't built yet).

UI text, API error messages, code comments and docs are all in **Vietnamese**. Keep new user-facing strings in Vietnamese.

## Commands

```bash
# DB cluster (needs Docker Desktop; reads ../../CSDL/*.sql; override with CSDL_DIR=...)
cd scripts && ./init_db.sh            # load schema + data into 4 running containers
cd scripts && ./init_db.sh --reset    # docker compose down -v / up -d, then load

# Backend (Java 21, Maven wrapper, run from this folder) → :8080, Swagger at /swagger-ui.html
./mvnw spring-boot:run
./mvnw test
./mvnw test -Dtest=LuongServiceTest   # single test class (or -Dtest=Class#method)
SPRING_PROFILES_ACTIVE=docker ./mvnw spring-boot:run   # when the backend runs inside the DB Docker network

# Frontend (Node 24 via nvm, see .nvmrc) → :5173, proxies /api, /swagger-ui and /v3 to :8080
cd ../frontend && npm run dev
cd ../frontend && npm run typecheck && npm run lint    # tsc -b, oxlint
# Single jar with the FE bundled in: build the FE into backend/src/main/resources/static, then package
cd ../frontend && npm run build && cd ../backend && ./mvnw clean package

# Docker: web (nginx :3000) + api (:8080) from ./docker-compose.yml, joined to the DB cluster's network
cd scripts && ./docker_app.sh           # detects the DB network, builds, starts, waits for api health; --down / --logs / --ps
docker compose up -d --build            # same, from this folder (DB_NETWORK=<name> if it isn't csdl_default)
```

Backend tests don't need a running DB, because Hikari pools connect lazily. The frontend has no test suite.

DB connections use user `admin` / `password123`: S0 is `localhost:5430/ql_phongkham_hub`, and S1, S2 and S3 are on `:5431`, `:5432` and `:5433` (`ql_phongkham_cn1..3`). A local Postgres install will take port 5432, which S2 needs.

## Docker images

- `Dockerfile` (this repo): `maven:3.9-eclipse-temurin-21` (`dependency:go-offline` layer, then `package -DskipTests`) → `eclipse-temurin:21-jre`, non-root, profile `docker`. It sets `TZ` and `-Duser.timezone=Asia/Ho_Chi_Minh` because visit codes, "current month" delete checks and the 30-day rule use the JVM clock. The healthcheck is `/v3/api-docs` on purpose (no DB call), so one site being down doesn't mark the api unhealthy. `.dockerignore` allowlists `pom.xml` + `src/` and excludes `src/main/resources/static/`, so docs, scripts and the compose file never enter the build context.
- `docker-compose.yml` builds `api` from `.` and `web` from `../frontend`. It doesn't create the DB: `api` joins the external network of the DB team's compose (`csdl_default`, overridable with `DB_NETWORK`).
- The frontend image (nginx) is described in `../frontend/CLAUDE.md`. A new backend path prefix besides `/api`, `/v3` and `/swagger-ui` must be added to both the nginx `location` regex and the vite proxy there.
- In Docker, a stopped DB container's name stops resolving (`UnknownHostException`), and `@Transactional` methods then fail with `CannotCreateTransactionException`. `GlobalExceptionHandler` maps that to 503 `SITE_UNAVAILABLE`.

## Architecture: how a request picks its database site

```
FE (header X-Site: S1|S2|S3) → SiteFilter → SiteContext (ThreadLocal) → SiteRoutingDataSource → that site's Hikari pool
```

- `site/SiteFilter` routes by path **before** any controller or `@Transactional` code runs:
  - `/api/master/**` (shared catalogs) and `/api/hq/**` (chain-wide reports) always go to the hub S0.
  - `/api/system/**` is not routed. `SystemController` uses `SiteRoutingDataSource.pools()` directly to check every site.
  - Any other `/api/**` path needs a valid branch in `X-Site`; otherwise the filter returns 400 `MISSING_SITE`. Every response carries `X-Site-Used`.
- `SiteRoutingDataSource` has **no default site**: if no site is set, it throws instead of writing to the wrong DB. One pool per site is built from `app.sites` in `application.yml` (`config/AppProperties`; site keys are upper-cased; `ma-cn` must match `CHI_NHANH.MaCN`).
- **Each transaction touches exactly one site** (there is no 2PC). Use `SiteContext.callOn(site, ...)` only *outside* a transaction, because the connection is bound when the transaction starts.
- When a site is down, the app still starts (`initializationFailTimeout = -1`). Connection failures surface as SQLSTATE `08xxx` and map to 503 `SITE_UNAVAILABLE`.

## Architecture: data distribution in the DB

- **Branch sites (S1–S3)** each hold 21 BCNF tables (`../../CSDL/01_init_schema_branch.sql`) with that branch's own data (horizontal fragmentation). Examples: `NHAN_VIEN` is the ISA parent of `BAC_SI` / `Y_TA`, the multi-valued `NV_SODIENTHOAI`, and `LUOT_KHAM_CHUA` with `LoaiLuot` = `KHAM`/`CHUA`. Branches have no triggers and no reporting views, so business rules (BR2, BR3, BR4, BR6, BR11) must be enforced in the app, and branch reports must be written as explicit joins.
- **Hub S0** holds 5 master tables: `CHI_NHANH`, `LUONG_CO_SO`, `BENH`, `THUOC`, `DICH_VU`. **Logical Replication** pushes them to the branches within about 1–2 s. Write master data **only at S0** (through `/api/master/**`). Branches read their replica copies.
- S0 also exposes **global views over `postgres_fdw`** with the same names as the branch tables (`KE_DON`, `LUOT_KHAM_CHUA`, `BENH_NHAN`, …), plus `V_CHI_PHI_LUOT` and `V_TIEN_TRINH_DOT`. That is how chain-wide reads work. Each branch has `TRA_CUU_BENH_NHAN_TOAN_CHUOI` for cross-branch patient lookup.
- Branches don't enforce FKs on replicated rows. Before deleting master data at S0, check its usage through the S0 global views (see `ThuocService.delete` → `ThuocRepository.countKeDonByThuoc`). If a branch is down, refuse the delete.
- Branches can't be added, deleted or renamed, because the FDW setup and global views are fixed to 3 branches.
- Take table and column names from `../../CSDL/01_init_schema_branch.sql` / `01_init_schema_hub.sql`, **not** from `../../CSDL/thiet-ke-phan-tan.md`, which describes an older global schema. Identifiers are unquoted, so in SQL they're lowercase (`nhan_vien.manv`).

## Backend conventions (`vn.antam.clinic`)

The full rules are in `.claude/skills/conventions/SKILL.md`. Follow that file whenever it disagrees with generic Spring skills.

- Data access is plain SQL through `JdbcClient` with named params. **No JPA, no Lombok.** `spring.sql.init.mode=never`, because the app never creates tables.
- Packages are **layered**: `controller/` (HTTP only), `service/` (business rules, `@Transactional`), `repository/` (the only place with SQL), `entity/` (one record per table, all 21 tables; component names = column names, so `.query(Xxx.class)` / `.paramSource(entity)` map automatically), `dto/request/`, `dto/response/`, `config/`, `site/`, `exception/`, `util/`. Every feature has controller → service → repository, even when the service only forwards the call.
- Reads with JOINs or aggregates return a `dto/response` projection straight from the repository. Don't assemble them in the service.
- Transactions:
  - Put `@Transactional` on write methods and `readOnly = true` on reads.
  - Reads that must survive a site being down (FDW fallbacks) use `Propagation.NOT_SUPPORTED`.
  - Never put `@Transactional` at class level.
  - **A method that reads another site with `SiteContext.callOn(...)` must have no `@Transactional` at all, not even `NOT_SUPPORTED`.** While transaction synchronization is active, Spring keeps the first connection for the whole method, so a `callOn("S0", …)` would still run on the branch. Do the cross-site reads first, then write inside a `TransactionTemplate` (see `LuotKhamService`, which also retries the transaction when a freshly generated code collides).
- `config/DataSourceConfig` runs `SET jit = off` on every pooled connection. Foreign tables have no statistics, so the planner turns on JIT for every chain-wide query, which adds about 1.5 s of LLVM compile time to a few-ms query. The DB configuration itself is untouched.
- Chain-wide reports (`/api/hq/**`, `ToanChuoiService`) reuse the branch services (`HoSoBenhNhanService`, `DoanhThuService`, `ThongKeBenhService`): at S0 the global views have the same names as the branch tables. Visit and episode codes contain the branch code, so they are unique chain-wide. Avoid per-row correlated subqueries in SQL that also runs at S0; aggregate in a CTE with `GROUP BY` so each FDW table is scanned once. When an FDW query fails, `ToanChuoiService` checks every site and returns 503 `CHAIN_UNAVAILABLE` naming the site that is down.
- Visits (`LuotKhamService`, `LuotChuaService`, `ChiTietLuotService`, `LuotService`):
  - Branches have no triggers, so the app enforces BR1–BR4, BR6, BR7, BR10–BR13 in the same transaction as the writes, including the ISA rule that each `LUOT_KHAM_CHUA` row has exactly one `LAN_KHAM` or `LAN_CHUA`.
  - Codes: visits use `Codes.luotPrefix` → `K-{MaKhoa}-{MaBS}-yyMMdd-NNN` / `C-…`; episodes use `CDDT-{MaCN}-NNN`.
  - A patient without a record at the branch is copied from the S0 `BENH_NHAN` view, keeping the same code.
  - BR4 is checked chain-wide via S0. If S0 is down, it falls back to the local check and returns a warning in `canhBao`.
  - Deleting a visit is allowed only within the current month, and only for the last treatment of an episode or for an exam whose episodes have no treatments.
- Path prefixes: `/api/...` for branch data, `/api/master/...` for S0 catalogs, `/api/hq/...` for chain-wide reports. Branch reports live under `/api/bao-cao/...`.
- Errors:
  - Throw `exception/BusinessException` for app rules, for example 409 `IN_USE`, 409 `HISTORY_LOCKED`, 422 `NO_BASE_SALARY`, 503 `CHAIN_UNAVAILABLE`.
  - Let DB constraint violations propagate. `exception/GlobalExceptionHandler` maps SQLSTATEs to Vietnamese `ApiErrorResponse` JSON: `23505` → 409 DUPLICATE, `23503` → 409 FOREIGN_KEY, `23514` → 400 CHECK, `P0001` → 422, `08xxx` → 503. Bad query params give 400 `BAD_PARAM`.
- Helpers in `util/Codes`:
  - `blankToNull`: normalize optional strings with it.
  - `next(prefix, existing, width)`: auto-generates IDs such as `BS-CN1-02`, `YT-CN1-03`, `TB-CN1-02`, `CN1-K03`, `B05`, `DV05` and `T05`.
  - Patient IDs (`BN13`) are unique across the chain. They are generated from `TRA_CUU_BENH_NHAN_TOAN_CHUOI`, so creating a patient needs every site up.
- Deletes return `DeleteResponse` HARD or SOFT (BR9). A doctor, nurse or device with history gets its status set to "Ngừng hoạt động". Patients with visits, departments with staff or visits, and catalog rows used at any branch are refused with 409 `IN_USE` (usage is checked through the S0 global views; if a branch is down, the delete is refused).
- Status strings are constants on the entities: `ChanDoanDieuTri.DANG_DIEU_TRI` / `DA_KHOI` / `NGUNG_DIEU_TRI`, `ThietBi.HOAT_DONG_TOT` / `NGUNG_HOAT_DONG` and `DichVu.LOAI_KHAM_BENH`.
- `LUONG_CO_SO` is history. Levels with `NgayApDung` ≤ today can't be edited or deleted. F3 salary is computed live from visit data in `LuongRepository`. **No salary table exists, so salaries can't be locked ("chốt lương").** Bonuses are 1,000,000 ₫ per "Đã khỏi" episode ending in the month (credited to the doctor of the opening visit) and 200,000 ₫ per `HO_TRO` row in the month.
- Tests:
  - Service tests use Mockito; controller tests use `@WebMvcTest` + `@EnableConfigurationProperties(AppProperties.class)` + `@MockitoBean`. Test names follow `method_condition_expected`.
  - 159 tests, no DB needed. Service tests that use `TransactionTemplate` take the mock transaction manager from `LuotFixtures.txManager()`. `EntitySchemaTest` skips itself when the cluster is down.
  - Run with `-Duser.timezone=Asia/Ho_Chi_Minh` if the JVM default zone is `Asia/Saigon`, which PostgreSQL rejects.
- `config/WebConfig` serves the built SPA from `classpath:/static/` with an `index.html` fallback for client routes.

## Frontend

The frontend is a separate repo next to this one, in `../frontend/`. Its conventions (the `Base*` wrappers, `api()` + `X-Site`, TanStack Query keys, the shared visit-form blocks, antd v6 selectors) are in `../frontend/CLAUDE.md`. Read that file before editing frontend code. When an API changes, update `../frontend/src/api/types.ts` and `endpoints.ts` so they match the Java records.

## Note on `.claude/agents/` and generic skills

The agents in `.claude/agents/` and any generic Spring skills are templates copied from elsewhere. Several of their rules contradict this project: JPA, group ID `pl.piomin.services`, Testcontainers, JSON logging, ProblemDetails. Where they disagree, follow `.claude/skills/conventions/SKILL.md`, the existing code and this file.
