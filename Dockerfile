# =============================================================================
# Image backend An Tâm Clinic (Spring Boot 4, Java 21)
#
#   docker build -t antam-clinic-backend .
#
# Chạy cùng frontend: docker-compose.yml ở thư mục source_code/ (hoặc scripts/docker_app.sh).
# Container dùng profile "docker" (application-docker.yml): gọi 4 trạm CSDL bằng tên container
# (site_s0_trungtam…, cổng 5432) → container phải nằm trong mạng Docker của cụm CSDL.
# =============================================================================

# ---- 1. Build file jar bằng Maven -------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src

# Tải dependency thành một lớp riêng: chỉ tải lại khi pom.xml thay đổi
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
# Không chạy test khi build image (chạy riêng: ./mvnw test)
RUN mvn -B -ntp -DskipTests package \
 && cp target/clinic-backend-*.jar /app.jar

# ---- 2. Image chạy: chỉ cần JRE ----------------------------------------------
FROM eclipse-temurin:21-jre

# Giờ Việt Nam: mã lượt (…-yyMMdd-NNN), "tháng hiện tại" khi xoá lượt, mốc 30 ngày bỏ dở…
# đều lấy theo giờ của JVM — để UTC mặc định sẽ lệch ngày từ 0h đến 7h sáng.
ENV TZ=Asia/Ho_Chi_Minh \
    SPRING_PROFILES_ACTIVE=docker \
    JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Ho_Chi_Minh -XX:MaxRAMPercentage=75"

RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build /app.jar app.jar
USER app

EXPOSE 8080
# Chỉ kiểm tra web server còn sống (không gọi CSDL: một trạm tắt không có nghĩa backend hỏng)
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -fsS -o /dev/null http://127.0.0.1:8080/v3/api-docs || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
