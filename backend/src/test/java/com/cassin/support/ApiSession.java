package com.cassin.support;

import org.springframework.test.web.servlet.MvcResult;

import com.cassin.auth.RefreshCookie;
import com.jayway.jsonpath.JsonPath; // có sẵn trong spring-boot-starter-test

import jakarta.servlet.http.Cookie;

/**
 * [THÊM] Một phiên đăng nhập trong test: access token (header) + refresh token (cookie).
 * Đọc thẳng từ response của /login, /refresh, /me/password.
 */
public record ApiSession(String email, String accessToken, String refreshToken) {

  public static ApiSession from(String email, MvcResult result) throws Exception {
    String body = result.getResponse().getContentAsString();
    Cookie cookie = result.getResponse().getCookie(RefreshCookie.NAME); // MockMvc tự tách Set-Cookie
    return new ApiSession(email, JsonPath.read(body, "$.accessToken"), cookie == null ? null : cookie.getValue());
  }

  /** Header Authorization cho các API cần đăng nhập. */
  public String bearer() {
    return "Bearer " + accessToken;
  }

  /** Cookie refresh để gửi lên /api/auth/refresh, /logout. */
  public Cookie refreshCookie() {
    return new Cookie(RefreshCookie.NAME, refreshToken);
  }
}