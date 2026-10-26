package vn.antam.clinic.site;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.antam.clinic.config.AppProperties;

/**
 * Chọn trạm CSDL cho mỗi request, TRƯỚC khi controller/transaction chạy:
 * <ul>
 *   <li>{@code /api/master/**} (ghi danh mục chung) và {@code /api/hq/**} (báo cáo toàn chuỗi) → trạm trung tâm S0</li>
 *   <li>{@code /api/system/**} → không định tuyến (tự làm việc với từng pool)</li>
 *   <li>các {@code /api/**} còn lại → trạm chi nhánh trong header {@code X-Site} (S1, S2, S3)</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SiteFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Site";
    public static final String RESPONSE_HEADER = "X-Site-Used";

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
        String site;
        if (path.startsWith("/api/master/") || path.startsWith("/api/hq/")) {
            site = props.hubSite();
        } else {
            String header = request.getHeader(HEADER);
            site = header == null ? null : header.trim().toUpperCase(Locale.ROOT);
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
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":400,\"code\":\"MISSING_SITE\",\"message\":\"" + message.replace("\"", "'") + "\"}");
    }
}
