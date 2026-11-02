package vn.antam.clinic.site;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.antam.clinic.config.AppProperties;
import vn.antam.clinic.config.AppProperties.Mode;

/**
 * Chọn trạm CSDL cho mỗi request, TRƯỚC khi controller/transaction chạy, và áp quy tắc truy cập của từng bản triển khai:
 * <ul>
 *   <li>{@code /api/system/**} → không định tuyến (tự làm việc với từng pool)</li>
 *   <li>{@code /api/master/**} (danh mục chung) và {@code /api/hq/**} (báo cáo toàn chuỗi) → trạm trung tâm S0;
 *       bản chi nhánh (BRANCH) không được vào → 403 {@code HQ_ONLY}</li>
 *   <li>các {@code /api/**} còn lại (dữ liệu chi nhánh):
 *     <ul>
 *       <li>ALL → trạm chi nhánh trong header {@code X-Site} (S1, S2, S3)</li>
 *       <li>BRANCH → luôn là {@code app.local-site}; X-Site khác trạm đó bị từ chối để không ghi nhầm chi nhánh</li>
 *       <li>HQ → chỉ xem: GET theo X-Site; thêm / sửa / xoá → 403 {@code READ_ONLY}
 *           (pool chi nhánh của bản này còn đặt default_transaction_read_only)</li>
 *     </ul>
 *   </li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SiteFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Site";
    public static final String RESPONSE_HEADER = "X-Site-Used";

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final AppProperties props;

    public SiteFilter(AppProperties props) {
        this.props = props;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = pathOf(request);
        return !path.startsWith("/api/") || path.startsWith("/api/system/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = pathOf(request);
        String header = request.getHeader(HEADER);
        String requested = header == null || header.isBlank() ? null : header.trim().toUpperCase(Locale.ROOT);
        String site;
        if (path.startsWith("/api/master/") || path.startsWith("/api/hq/")) {
            if (props.mode() == Mode.BRANCH) {
                writeError(response, HttpServletResponse.SC_FORBIDDEN, "HQ_ONLY",
                        "Chức năng của Trung tâm — bản chi nhánh (" + props.localSite() + ") không được xem hay sửa.");
                return;
            }
            site = props.hubSite();
        } else if (props.mode() == Mode.HQ) {
            if (!READ_METHODS.contains(request.getMethod())) {
                writeError(response, HttpServletResponse.SC_FORBIDDEN, "READ_ONLY",
                        "Trung tâm chỉ xem dữ liệu chi nhánh — thêm / sửa / xoá do bản chi nhánh tại cơ sở đó thực hiện.");
                return;
            }
            site = requested;
            if (!props.isBranchSite(site)) {
                writeMissingSite(response, header);
                return;
            }
        } else if (props.mode() == Mode.BRANCH) {
            if (requested != null && !requested.equals(props.localSite())) {
                writeError(response, HttpServletResponse.SC_BAD_REQUEST, "WRONG_SITE",
                        "Bản này chỉ phục vụ chi nhánh " + props.localSite() + " — không nhận X-Site: " + header.trim() + ".");
                return;
            }
            site = props.localSite();
        } else {
            site = requested;
            if (!props.isBranchSite(site)) {
                writeMissingSite(response, header);
                return;
            }
        }
        SiteContext.set(site);
        response.setHeader(RESPONSE_HEADER, site);
        try {
            chain.doFilter(request, response);
        } finally {
            SiteContext.clear();
        }
    }

    private static String pathOf(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    private void writeMissingSite(HttpServletResponse response, String header) throws IOException {
        String allowed = String.join(", ", props.branchSites());
        String message = header == null
                ? "Thiếu header X-Site. Chọn chi nhánh đang làm việc (" + allowed + ")."
                : "Header X-Site không hợp lệ: '" + header + "'. Giá trị hợp lệ: " + allowed + ".";
        writeError(response, HttpServletResponse.SC_BAD_REQUEST, "MISSING_SITE", message);
    }

    private static void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":" + status + ",\"code\":\"" + code + "\",\"message\":\""
                + message.replace("\\", "\\\\").replace("\"", "'") + "\"}");
    }
}
