package vn.antam.clinic.dto.response;

import java.util.List;

import vn.antam.clinic.config.AppProperties;

/**
 * Bản triển khai đang chạy — frontend đọc lúc khởi động để hiện đúng giao diện chi nhánh / Trung tâm.
 *
 * @param mode        ALL (một bản cho mọi trạm), BRANCH (bản đặt tại một cơ sở) hoặc HQ (bản Trung tâm)
 * @param localSite   trạm chi nhánh của bản BRANCH; null ở chế độ khác
 * @param branchSites trạm chi nhánh mà bản này nối tới
 */
public record AppModeResponse(AppProperties.Mode mode, String localSite, String hubSite, List<String> branchSites) {

    public static AppModeResponse from(AppProperties props) {
        return new AppModeResponse(props.mode(), props.localSite(), props.hubSite(), props.branchSites());
    }
}
