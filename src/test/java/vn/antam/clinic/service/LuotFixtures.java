package vn.antam.clinic.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import vn.antam.clinic.config.AppProperties;
import vn.antam.clinic.dto.response.BacSiResponse;
import vn.antam.clinic.dto.response.DotDieuTriResponse;
import vn.antam.clinic.dto.response.LuotResponse;
import vn.antam.clinic.entity.ChanDoanDieuTri;

/** Dữ liệu dựng sẵn cho test lượt khám / chữa. */
final class LuotFixtures {

    private LuotFixtures() {
    }

    static AppProperties props() {
        return new AppProperties("S0", null, Map.of(
                "s0", new AppProperties.Site("Trung tâm", null, "jdbc:postgresql://localhost:5430/hub", null, null),
                "s1", new AppProperties.Site("Cầu Giấy", "CN1", "jdbc:postgresql://localhost:5431/cn1", null, null)));
    }

    /** Transaction manager giả: TransactionTemplate.execute chạy thẳng callback. */
    static PlatformTransactionManager txManager() {
        PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
        lenient().when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        return tm;
    }

    static BacSiResponse bacSi(String maNV, String maKhoa, String trangThai) {
        return new BacSiResponse(maNV, "BS " + maNV, null, "Nam", null, null, new BigDecimal("4.0"), LocalDate.of(2020, 1, 1),
                trangThai, maKhoa, "Khoa " + maKhoa, null, null, null, List.of(), 0);
    }

    static DotDieuTriResponse dot(String maCDDT, String maBenh, String maCn, String trangThai, LocalDateTime luotGanNhat) {
        return new DotDieuTriResponse(maCDDT, maBenh, "Bệnh " + maBenh, null, "Nhẹ", 3, 1, trangThai,
                luotGanNhat.minusDays(3), null, "K-1", "BN01", "Nguyễn Văn An", "BS-CN1-01", "BS A", "CN1-TMH",
                "Khoa TMH", maCn, luotGanNhat, luotGanNhat, null, false);
    }

    static DotDieuTriResponse dotDang(String maCDDT, String maBenh, String maCn, LocalDateTime luotGanNhat) {
        return dot(maCDDT, maBenh, maCn, ChanDoanDieuTri.DANG_DIEU_TRI, luotGanNhat);
    }

    static LuotResponse luot(String maLuot, String loai, LocalDateTime thoiGian, String maCDDT, Integer lanThu) {
        return new LuotResponse(maLuot, thoiGian, loai, null, null, "CN1-TMH", "Khoa TMH", "BS-CN1-01", "BS A", "BN01",
                "Nguyễn Văn An", null, null, null, maCDDT, lanThu, null, null, "Viêm họng", null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
