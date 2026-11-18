package com.cassin.support;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * [THÊM] Một PostgreSQL thật chạy trong Docker cho integration test.
 *
 * Container là một BEAN: Spring tự start trước khi tạo DataSource, tự stop khi context đóng.
 * Mọi lớp IT dùng chung @IntegrationTest → chung một context (Spring cache lại) → chung MỘT container.
 * 4 lớp IT = khởi động Postgres 1 lần (~3–5 giây), không phải 4 lần.
 *
 * [SỬA] Chế độ MẶC ĐỊNH (cassin.it.database=container, cần Docker). Đặt IT_DATABASE=local để dùng
 * PostgreSQL cài trên máy thay thế, xem LocalPostgresConfig.
 */
@TestConfiguration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "cassin.it.database", havingValue = "container", matchIfMissing = true)
public class PostgresContainerConfig {

  @Bean
  @ServiceConnection // thay cho @DynamicPropertySource + 3 dòng spring.datasource.*
  PostgreSQLContainer postgres() {
    // Cùng major version với máy dev (README: PostgreSQL 16). alpine: image nhỏ, pull nhanh.
    return new PostgreSQLContainer("postgres:16-alpine");
  }
}