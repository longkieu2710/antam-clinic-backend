package vn.antam.clinic.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import vn.antam.clinic.site.SiteRoutingDataSource;

/**
 * Đối chiếu từng entity với bảng thật ở trạm S1 (có đủ 21 bảng, kể cả bản sao danh mục):
 * tên thành phần record phải trùng tên cột (không phân biệt hoa thường) và kiểu Java phải khớp kiểu cột —
 * điều kiện để {@code .query(Xxx.class)} và {@code .paramSource(xxx)} của JdbcClient chạy đúng.
 * Bỏ qua (skipped) khi cụm CSDL chưa chạy.
 */
@SpringBootTest
class EntitySchemaTest {

    private static final String SITE = "S1";

    private static final Map<String, Class<?>> JAVA_TYPE_OF_COLUMN = Map.of(
            "character varying", String.class,
            "text", String.class,
            "date", LocalDate.class,
            "timestamp without time zone", LocalDateTime.class,
            "numeric", BigDecimal.class,
            "integer", Integer.class);

    private static Boolean siteReachable;

    @Autowired
    SiteRoutingDataSource dataSource;

    static Stream<Arguments> entities() {
        return Stream.of(
                Arguments.of("chi_nhanh", ChiNhanh.class),
                Arguments.of("khoa", Khoa.class),
                Arguments.of("nhan_vien", NhanVien.class),
                Arguments.of("nv_sodienthoai", NvSoDienThoai.class),
                Arguments.of("bac_si", BacSi.class),
                Arguments.of("y_ta", YTa.class),
                Arguments.of("luong_co_so", LuongCoSo.class),
                Arguments.of("benh_nhan", BenhNhan.class),
                Arguments.of("benh", Benh.class),
                Arguments.of("thuoc", Thuoc.class),
                Arguments.of("dich_vu", DichVu.class),
                Arguments.of("thiet_bi", ThietBi.class),
                Arguments.of("luot_kham_chua", LuotKhamChua.class),
                Arguments.of("lan_kham", LanKham.class),
                Arguments.of("chan_doan_dieu_tri", ChanDoanDieuTri.class),
                Arguments.of("lan_chua", LanChua.class),
                Arguments.of("lan_chua_hinh_thuc", LanChuaHinhThuc.class),
                Arguments.of("ho_tro", HoTro.class),
                Arguments.of("su_dung_dv", SuDungDv.class),
                Arguments.of("ke_don", KeDon.class),
                Arguments.of("su_dung_tb", SuDungTb.class));
    }

    @BeforeEach
    void requireDatabase() {
        if (siteReachable == null) {
            try (Connection ignored = dataSource.pools().get(SITE).getConnection()) {
                siteReachable = true;
            } catch (SQLException e) {
                siteReachable = false;
            }
        }
        assumeTrue(siteReachable, "Cụm CSDL chưa chạy (scripts/init_db.sh) — bỏ qua kiểm tra entity ↔ bảng");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("entities")
    void entity_matchesTableColumns(String table, Class<? extends Record> entity) throws SQLException {
        Map<String, String> columns = columnTypes(table);
        assertThat(columns).as("bảng %s không tồn tại ở %s", table, SITE).isNotEmpty();

        Map<String, Class<?>> components = Arrays.stream(entity.getRecordComponents())
                .collect(Collectors.toMap(c -> c.getName().toLowerCase(Locale.ROOT), RecordComponent::getType,
                        (a, b) -> a, LinkedHashMap::new));
        assertThat(components.keySet())
                .as("tên thành phần của %s phải trùng tên cột của %s", entity.getSimpleName(), table)
                .containsExactlyInAnyOrderElementsOf(columns.keySet());

        columns.forEach((column, sqlType) -> assertThat(components.get(column))
                .as("%s.%s (%s)", table, column, sqlType)
                .isEqualTo(JAVA_TYPE_OF_COLUMN.get(sqlType)));
    }

    /** Tên cột → kiểu dữ liệu, theo information_schema của trạm S1. */
    private Map<String, String> columnTypes(String table) throws SQLException {
        Map<String, String> columns = new LinkedHashMap<>();
        try (Connection connection = dataSource.pools().get(SITE).getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     SELECT column_name, data_type FROM information_schema.columns
                      WHERE table_schema = 'public' AND table_name = ?
                      ORDER BY ordinal_position""")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    columns.put(rs.getString(1), rs.getString(2));
                }
            }
        }
        return columns;
    }
}
