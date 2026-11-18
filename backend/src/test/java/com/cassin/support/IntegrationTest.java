package com.cassin.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc; // Boot 4: package mới
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * [THÊM] Gắn lên mọi lớp *IT. Gom 4 annotation thành 1 để:
 *   - không lớp nào quên @ActiveProfiles hay @Import
 *   - cấu hình GIỐNG HỆT nhau → Spring dùng lại 1 context, 1 container cho cả bộ IT
 *     (khác 1 annotation thôi là Spring dựng context mới, thêm vài giây mỗi lần)
 *
 * @SpringBootTest     : dựng toàn bộ app (security, JPA, Flyway...), không mock gì
 * @AutoConfigureMockMvc: gửi request HTTP giả qua ĐỦ filter chain (JwtAuthenticationFilter...)
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Import({ PostgresContainerConfig.class, TestClockConfig.class })
public @interface IntegrationTest {
}