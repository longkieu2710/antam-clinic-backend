package vn.antam.clinic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Ứng dụng quản lý chuỗi phòng khám An Tâm.
 * Một backend kết nối tới 4 trạm CSDL (S0 trung tâm + S1..S3 chi nhánh),
 * chọn trạm theo header X-Site hoặc theo đường dẫn (/api/master/**, /api/hq/** → S0).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ClinicApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClinicApplication.class, args);
    }
}
