package com.cassin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.cassin.support.ApiClient;
import com.cassin.support.ApiSession;
import com.cassin.support.IntegrationTest;
import com.cassin.support.MutableClock;
import com.jayway.jsonpath.JsonPath;

/**
 * [THÊM] "Thiết bị đang đăng nhập" chạy thật: SecurityConfig + cookie + 3 câu query mới trên PostgreSQL.
 *
 *   laptop (Chrome · Windows) ── họ L      phone (Safari · iOS) ── họ P
 *
 * Mỗi request gửi CẢ HAI như trình duyệt thật: Bearer (biết user) + cookie refresh (biết thiết bị nào).
 */
@IntegrationTest
class SessionIT {

  static final String CHROME_WINDOWS =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
  static final String SAFARI_IPHONE =
      "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) "
          + "Version/18.1 Mobile/15E148 Safari/604.1";

  @Autowired
  MockMvc mvc;
  @Autowired
  MutableClock clock;

  ApiClient api;
  String email;
  ApiSession laptop;
  ApiSession phone;

  @BeforeEach
  void setUp() throws Exception {
    clock.reset();
    api = new ApiClient(mvc);
    email = ApiClient.uniqueEmail("devices");
    api.register(email, ApiClient.PASSWORD);
    laptop = api.login(email, ApiClient.PASSWORD, CHROME_WINDOWS);
    clock.advance(Duration.ofMinutes(1));
    phone = api.login(email, ApiClient.PASSWORD, SAFARI_IPHONE);
  }

  @Test
  @DisplayName("Danh sách: 2 thiết bị, mới hoạt động nhất lên đầu, đúng nhãn, đúng 'thiết bị này'")
  void list_showsBothDevices_andMarksCurrent() throws Exception {
    sessions(laptop)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].browser").value("Safari"))   // phone đăng nhập sau → hoạt động gần hơn
        .andExpect(jsonPath("$[0].os").value("iOS"))
        .andExpect(jsonPath("$[0].current").value(false))
        .andExpect(jsonPath("$[1].browser").value("Chrome"))
        .andExpect(jsonPath("$[1].os").value("Windows"))
        .andExpect(jsonPath("$[1].current").value(true));      // request này gửi cookie của laptop

    sessions(phone)
        .andExpect(jsonPath("$[0].current").value(true))
        .andExpect(jsonPath("$[1].current").value(false));
  }

  @Test
  @DisplayName("Refresh: vẫn 1 dòng, cùng id; lần hoạt động gần nhất tăng, lúc đăng nhập giữ nguyên")
  void refresh_keepsSameDevice_updatesLastActive() throws Exception {
    MvcResult before = sessions(laptop).andReturn();
    String id = read(before, "$[?(@.browser == 'Chrome')].id");
    String signedIn = read(before, "$[?(@.browser == 'Chrome')].signedInAt");

    clock.advance(Duration.ofHours(2));
    ApiSession laptop2 = ApiSession.from(email, refresh(laptop).andExpect(status().isOk()).andReturn());

    MvcResult after = sessions(laptop2).andExpect(jsonPath("$", hasSize(2))).andReturn();
    assertSame(after, "$[0].id", id);                       // laptop vừa refresh → lên đầu, CÙNG id (familyId)
    assertSame(after, "$[0].signedInAt", signedIn);         // thời điểm đăng nhập không đổi
    assertSame(after, "$[0].current", "true");
    assertThat(read(after, "$[0].lastActiveAt"))
        .isGreaterThan(read(after, "$[0].signedInAt"));     // ISO-8601 cùng định dạng → so chuỗi được
  }

  @Test
  @DisplayName("Đăng xuất thiết bị khác: điện thoại không refresh được nữa, laptop vẫn dùng tiếp")
  void revokeOne_otherDevice() throws Exception {
    String phoneId = read(sessions(laptop).andReturn(), "$[?(@.browser == 'Safari')].id");

    mvc.perform(delete("/api/auth/sessions/{id}", phoneId)
            .header(HttpHeaders.AUTHORIZATION, laptop.bearer())
            .cookie(laptop.refreshCookie()))
        .andExpect(status().isNoContent())
        .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));  // không đụng cookie của laptop

    refresh(phone).andExpect(status().isUnauthorized());
    refresh(laptop).andExpect(status().isOk());
    sessions(laptop).andExpect(jsonPath("$", hasSize(1)));
  }

  @Test
  @DisplayName("Đăng xuất chính thiết bị đang dùng: 204 + xóa cookie")
  void revokeOne_currentDevice_clearsCookie() throws Exception {
    String laptopId = read(sessions(laptop).andReturn(), "$[?(@.current == true)].id");

    mvc.perform(delete("/api/auth/sessions/{id}", laptopId)
            .header(HttpHeaders.AUTHORIZATION, laptop.bearer())
            .cookie(laptop.refreshCookie()))
        .andExpect(status().isNoContent())
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

    refresh(laptop).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Phiên của người khác / id lạ → 404 SESSION_NOT_FOUND, không thu hồi gì")
  void revokeOne_notMine_is404() throws Exception {
    ApiSession stranger = api.newLearner();
    String strangerId = read(sessions(stranger).andReturn(), "$[0].id");

    mvc.perform(delete("/api/auth/sessions/{id}", strangerId)
            .header(HttpHeaders.AUTHORIZATION, laptop.bearer())
            .cookie(laptop.refreshCookie()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    refresh(stranger).andExpect(status().isOk());           // phiên của người lạ vẫn sống

    mvc.perform(delete("/api/auth/sessions/{id}", "khong-phai-uuid")
            .header(HttpHeaders.AUTHORIZATION, laptop.bearer()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Đăng xuất mọi thiết bị khác: chỉ còn thiết bị đang dùng")
  void revokeOthers_keepsCurrentOnly() throws Exception {
    ApiSession tablet = api.login(email, ApiClient.PASSWORD, null);

    mvc.perform(delete("/api/auth/sessions")
            .header(HttpHeaders.AUTHORIZATION, laptop.bearer())
            .cookie(laptop.refreshCookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.revoked").value(2));

    refresh(phone).andExpect(status().isUnauthorized());
    refresh(tablet).andExpect(status().isUnauthorized());
    sessions(laptop)
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].current").value(true));
  }

  @Test
  @DisplayName("Không có access token → 401 (sessions KHÔNG được mở công khai như /api/auth/login)")
  void sessions_requireAccessToken() throws Exception {
    mvc.perform(get("/api/auth/sessions").cookie(laptop.refreshCookie()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    mvc.perform(delete("/api/auth/sessions").cookie(laptop.refreshCookie()))
        .andExpect(status().isUnauthorized());

    refresh(phone).andExpect(status().isOk());               // không ai bị đăng xuất
  }

  @Test
  @DisplayName("Đăng xuất / đổi mật khẩu → thiết bị tương ứng biến khỏi danh sách")
  void logoutAndPasswordChange_removeDevices() throws Exception {
    mvc.perform(post("/api/auth/logout").cookie(phone.refreshCookie())).andExpect(status().isNoContent());
    sessions(laptop).andExpect(jsonPath("$", hasSize(1)));

    ApiSession phoneAgain = api.login(email, ApiClient.PASSWORD, SAFARI_IPHONE);
    MvcResult changed = mvc.perform(put("/api/me/password")
            .header(HttpHeaders.AUTHORIZATION, laptop.bearer())
            .header(HttpHeaders.USER_AGENT, CHROME_WINDOWS)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"currentPassword": "%s", "newPassword": "matkhau-moi-456"}
                """.formatted(ApiClient.PASSWORD)))
        .andExpect(status().isNoContent())
        .andReturn();
    ApiSession laptopNew = new ApiSession(email, laptop.accessToken(),
        changed.getResponse().getCookie(RefreshCookie.NAME).getValue());

    refresh(phoneAgain).andExpect(status().isUnauthorized());
    sessions(laptopNew)
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].browser").value("Chrome"))  // phiên mới sau đổi mật khẩu vẫn ghi thiết bị
        .andExpect(jsonPath("$[0].current").value(true));
  }

  // ===== helper =====

  private ResultActions sessions(ApiSession session) throws Exception {
    return mvc.perform(get("/api/auth/sessions")
        .header(HttpHeaders.AUTHORIZATION, session.bearer())
        .cookie(session.refreshCookie()));
  }

  private ResultActions refresh(ApiSession session) throws Exception {
    return mvc.perform(post("/api/auth/refresh").cookie(session.refreshCookie()));
  }

  /** Đọc một giá trị; biểu thức lọc ($[?(...)]) trả mảng → lấy phần tử đầu. */
  private static String read(MvcResult result, String path) throws Exception {
    Object value = JsonPath.read(result.getResponse().getContentAsString(), path);
    if (value instanceof List<?> list) {
      if (list.isEmpty()) {
        throw new AssertionError("Không có giá trị nào tại " + path);
      }
      value = list.get(0);
    }
    return String.valueOf(value);
  }

  private static void assertSame(MvcResult result, String path, String expected) throws Exception {
    assertThat(read(result, path)).as(path).isEqualTo(expected);
  }
}
