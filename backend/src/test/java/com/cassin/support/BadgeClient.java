package com.cassin.support;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

/**
 * Đọc huy hiệu của một người trong integration test, cùng kiểu VocabClient.
 *
 * Các IT của từng danh mục chỉ cần hỏi "huy hiệu X đang ở cấp mấy, tiến độ bao nhiêu":
 *   assertThat(badges.level(nam, "DILIGENT")).isEqualTo(1);
 * Tìm theo MÃ trong danh sách, không theo vị trí: mỗi phase thêm rule là danh sách dài thêm.
 */
public final class BadgeClient {

  private final MockMvc mvc;

  public BadgeClient(MockMvc mvc) {
    this.mvc = mvc;
  }

  /** GET /api/badges */
  public ResultActions list(ApiSession user) throws Exception {
    return mvc.perform(get("/api/badges").header(AUTHORIZATION, user.bearer()));
  }

  /** GET /api/badges/unseen */
  public ResultActions unseen(ApiSession user) throws Exception {
    return mvc.perform(get("/api/badges/unseen").header(AUTHORIZATION, user.bearer()));
  }

  /** Cấp hiện tại (0 = chưa đạt cấp nào). */
  public int level(ApiSession user, String code) throws Exception {
    return field(user, code, "level").intValue();
  }

  /** Tiến độ hiện tại (vd số lượt trả lời). Huy hiệu bí mật chưa đạt luôn trả 0. */
  public long progress(ApiSession user, String code) throws Exception {
    return field(user, code, "progress").longValue();
  }

  /** Mã các huy hiệu chưa xem, đúng thứ tự API trả (mỗi huy hiệu một dòng, cấp cao nhất). */
  public List<String> unseenCodes(ApiSession user) throws Exception {
    String body = unseen(user).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    return JsonPath.read(body, "$[*].code");
  }

  private Number field(ApiSession user, String code, String name) throws Exception {
    String body = list(user).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    List<Number> values = JsonPath.read(body, "$[?(@.code == '" + code + "')]." + name);
    if (values.isEmpty()) {
      throw new AssertionError("Không có huy hiệu " + code + " trong /api/badges (chưa có BadgeRule?)");
    }
    return values.get(0);
  }
}
