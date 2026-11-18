package com.cassin.support;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource; // pool có sẵn qua spring-boot-starter-data-jpa

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * [THÊM] Chạy integration test trên PostgreSQL CÀI TRÊN MÁY (không cần Docker).
 *
 * Bật bằng biến môi trường IT_DATABASE=local (xem application-it.yml). Dùng một database RIÊNG,
 * mặc định cassin_it, KHÔNG phải DB dev "cassin":
 *
 *   mỗi lần chạy:  flyway clean (xóa sạch bảng)  →  flyway migrate (V1 → V14 + dữ liệu seed)
 *
 * nên IT vẫn bắt đầu từ một DB sạch giống Testcontainers. Để tránh xóa nhầm DB đang dùng,
 * tên database trong URL BẮT BUỘC kết thúc bằng "_it", nếu không sẽ dừng ngay với lỗi rõ ràng.
 *
 * Chuẩn bị một lần (psql, pgAdmin...):  CREATE DATABASE cassin_it OWNER cassin;
 */
@TestConfiguration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "cassin.it.database", havingValue = "local")
public class LocalPostgresConfig {

  @Bean
  DataSource dataSource(
      @Value("${cassin.it.local.url}") String url,
      @Value("${cassin.it.local.username}") String username,
      @Value("${cassin.it.local.password}") String password) {
    requireTestDatabase(url);
    HikariDataSource dataSource = new HikariDataSource();
    dataSource.setJdbcUrl(url);
    dataSource.setUsername(username);
    dataSource.setPassword(password);
    return dataSource;
  }

  /**
   * Thay bước "migrate" mặc định của Spring Boot bằng "clean rồi migrate".
   * Flyway mặc định cấm clean (cleanDisabled = true) để chống xóa nhầm DB thật,
   * nên tạo một bản Flyway riêng chỉ để clean, có bật cho phép.
   */
  @Bean
  FlywayMigrationStrategy cleanThenMigrate() {
    return flyway -> {
      Flyway.configure()
          .configuration(flyway.getConfiguration())
          .cleanDisabled(false)
          .load()
          .clean();
      flyway.migrate();
    };
  }

  private static void requireTestDatabase(String url) {
    String database = url.replaceFirst("\\?.*$", "").replaceFirst("^.*/", "");
    if (!database.endsWith("_it")) {
      throw new IllegalStateException(
          "IT_DATABASE=local sẽ XÓA SẠCH database '" + database + "' mỗi lần chạy. "
              + "Chỉ dùng database riêng có tên kết thúc bằng _it (vd cassin_it). URL hiện tại: " + url);
    }
  }
}
