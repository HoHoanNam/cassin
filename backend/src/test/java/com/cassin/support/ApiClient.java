package com.cassin.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * [THÊM] Các bước lặp lại ở mọi IT: tạo tài khoản, đăng nhập.
 *
 * Mỗi test tạo người dùng MỚI với email ngẫu nhiên → các test không giẫm dữ liệu của nhau,
 * không cần xóa bảng sau mỗi test, chạy theo thứ tự nào cũng được.
 * (KHÔNG dùng @Transactional trên lớp test: nó bọc mọi thứ vào một transaction rồi rollback,
 *  làm sai đúng hành vi đang muốn kiểm tra, vd noRollbackFor của RefreshTokenService.rotate.)
 */
public final class ApiClient {

  public static final String PASSWORD = "matkhau-it-123";

  private final MockMvc mvc;

  public ApiClient(MockMvc mvc) {
    this.mvc = mvc;
  }

  public static String uniqueEmail(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@it.local";
  }

  /** Đăng ký + đăng nhập một người học mới. */
  public ApiSession newLearner() throws Exception {
    String email = uniqueEmail("learner");
    register(email, PASSWORD);
    return login(email, PASSWORD);
  }

  public void register(String email, String password) throws Exception {
    mvc.perform(post("/api/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {"email": "%s", "password": "%s", "displayName": "IT"}
            """.formatted(email, password)))
        .andExpect(status().isCreated());
  }

  public ApiSession login(String email, String password) throws Exception {
    MvcResult result = mvc.perform(post("/api/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {"email": "%s", "password": "%s"}
            """.formatted(email, password)))
        .andExpect(status().isOk())
        .andReturn();
    return ApiSession.from(email, result);
  }
}