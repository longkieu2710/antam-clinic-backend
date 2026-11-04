package vn.antam.clinic.service;

import org.springframework.stereotype.Service;
import vn.antam.clinic.config.AppProperties;
import vn.antam.clinic.dto.response.AppModeResponse;

/** Chế độ triển khai (APP_MODE / APP_LOCAL_SITE) — chỉ đọc cấu hình, không chạm CSDL. */
@Service
public class AppModeService {

    private final AppProperties props;

    public AppModeService(AppProperties props) {
        this.props = props;
    }

    public AppModeResponse mode() {
        return AppModeResponse.from(props);
    }
}
