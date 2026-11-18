package com.cassin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import jakarta.servlet.http.Cookie;

import com.cassin.support.ApiClient;
import com.cassin.support.ApiSession;
import com.cassin.support.IntegrationTest;
import com.cassin.support.MutableClock;

/**
 * [THÊM] Luồng đăng nhập + refresh token chạy THẬT: HTTP → Security filter → Service → PostgreSQL.
 *
 * Unit test (RefreshTokenServiceTest) đã kiểm tra từng nhánh với repository giả.
 * Ở đây kiểm tra những thứ unit test không thấy được:
 *   - câu UPDATE ... WHERE revoked_at IS NULL (revokeIfActive) chạy đúng trên Postgres
 *   - noRollbackFor: lệnh "thu hồi cả họ" THỰC SỰ được lưu dù sau đó ném 401
 *   - header Set-Cookie đúng thuộc tính, JwtAuthenticationFilter đọc được token vừa cấp
 */
@IntegrationTest
class AuthFlowIT {

  @Autowired
  MockMvc mvc;
  @Autowired
  MutableClock clock;

  ApiClient api;

  @BeforeEach
  void setUp() {
    clock.reset(); // test trước có thể đã tua đồng hồ
    api = new ApiClient(mvc);
  }

  @Test
  @DisplayName("Đăng nhập: cookie refresh HttpOnly + access token gọi được /api/me")
  void login_setsSecureCookie_andAccessTokenWorks() throws Exception {
    String email = ApiClient.uniqueEmail("login");
    api.register(email, ApiClient.PASSWORD);

    mvc.perform(post("/api/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {"email": "%s", "password": "%s"}
            """.formatted(email, ApiClient.PASSWORD)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.expiresIn").value(900)) // 15m của profile "it"
        .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
            containsString(RefreshCookie.NAME + "="),
            containsString("HttpOnly"),
            containsString("SameSite=Strict"),
            containsString("Path=/api/auth"))));

    ApiSession session = api.login(email, ApiClient.PASSWORD);
    mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, session.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(email));
  }

  @Test
  @DisplayName("Không có token → 401 cùng định dạng ErrorResponse")
  void me_withoutToken_returnsUnifiedError() throws Exception {
    mvc.perform(get("/api/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  @DisplayName("Refresh: đổi cookie A lấy cặp token MỚI, access token mới dùng được")
  void refresh_rotatesToken() throws Exception {
    ApiSession a = api.newLearner();

    ApiSession b = ApiSession.from(a.email(), refresh(a).andExpect(status().isOk()).andReturn());

    assertThat(b.refreshToken()).isNotBlank().isNotEqualTo(a.refreshToken());
    mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, b.bearer()))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Hai tab gửi cùng cookie trong 10 giây → tab sau nhận 409 REFRESH_RACE")
  void refresh_sameTokenWithinRaceWindow_returns409() throws Exception {
    ApiSession a = api.newLearner();
    refresh(a).andExpect(status().isOk()); // tab 1 thắng, A bị thu hồi, họ còn token B sống

    clock.advance(Duration.ofSeconds(5)); // vẫn trong cửa sổ 10 giây
    refresh(a)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("REFRESH_RACE"));
  }

  @Test
  @DisplayName("Dùng lại token cũ sau 10 giây → 401 và THU HỒI CẢ HỌ (token B cũng chết)")
  void refresh_reusedToken_revokesWholeFamily() throws Exception {
    ApiSession a = api.newLearner();
    ApiSession b = ApiSession.from(a.email(), refresh(a).andExpect(status().isOk()).andReturn());

    clock.advance(Duration.ofSeconds(11)); // ra khỏi cửa sổ chạy đua → coi là bị đánh cắp
    refresh(a)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("REFRESH_REUSED"));

    // Điểm mấu chốt của IT này: nếu noRollbackFor bị mất, transaction rollback lệnh revokeFamily
    // → B vẫn sống → dòng dưới trả 200 thay vì 401.
    refresh(b)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("REFRESH_REUSED"));
  }

  @Test
  @DisplayName("Đăng xuất: xóa cookie (Max-Age=0), refresh token không dùng được nữa")
  void logout_clearsCookie_andRevokesSession() throws Exception {
    ApiSession a = api.newLearner();

    mvc.perform(post("/api/auth/logout").cookie(a.refreshCookie()))
        .andExpect(status().isNoContent())
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

    refresh(a).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Đổi mật khẩu: thiết bị khác bị đăng xuất, thiết bị đang đổi giữ phiên")
  void changePassword_revokesOtherSessions_keepsCurrentOne() throws Exception {
    ApiSession laptop = api.newLearner();
    ApiSession phone = api.login(laptop.email(), ApiClient.PASSWORD);

    MvcResult changed = mvc.perform(put("/api/me/password")
        .header(HttpHeaders.AUTHORIZATION, laptop.bearer())
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {"currentPassword": "%s", "newPassword": "matkhau-moi-456"}
            """.formatted(ApiClient.PASSWORD)))
        .andExpect(status().isNoContent())
        .andReturn();
    String laptopCookie = changed.getResponse().getCookie(RefreshCookie.NAME).getValue();

    refresh(phone).andExpect(status().isUnauthorized()); // điện thoại bị đá ra
    mvc.perform(post("/api/auth/refresh")
        .cookie(new Cookie(RefreshCookie.NAME, laptopCookie)))
        .andExpect(status().isOk()); // laptop vẫn dùng tiếp
  }

  @Test
  @DisplayName("Không gửi cookie → 401 REFRESH_INVALID")
  void refresh_withoutCookie_returns401() throws Exception {
    mvc.perform(post("/api/auth/refresh"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("REFRESH_INVALID"));
  }

  private ResultActions refresh(ApiSession session) throws Exception {
    return mvc.perform(post("/api/auth/refresh").cookie(session.refreshCookie()));
  }
}