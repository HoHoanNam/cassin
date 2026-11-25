package com.cassin.badge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.cassin.support.ApiClient;
import com.cassin.support.ApiSession;
import com.cassin.support.BadgeClient;
import com.cassin.support.IntegrationTest;
import com.cassin.support.VocabClient;
import com.jayway.jsonpath.JsonPath;

/**
 * Phase 8 · Mắt tinh: huy hiệu về tay NGƯỜI BÁO LỖI dù thao tác là của admin.
 * Chỉ báo lỗi được từ hệ thống (từ của mình thì tự sửa) → admin tạo bộ + từ hệ thống trước.
 */
@IntegrationTest
class SharpEyeBadgeIT {
  private static final String ADMIN_EMAIL = "admin@it.local";
  private static final String ADMIN_PASSWORD = "admin-it-12345";

  @Autowired
  MockMvc mvc;

  ApiClient api;
  BadgeClient badges;
  ApiSession admin;

  @BeforeEach
  void setUp() throws Exception {
    api = new ApiClient(mvc);
    badges = new BadgeClient(mvc);
    admin = api.login(ADMIN_EMAIL, ADMIN_PASSWORD);
  }

  @Test
  @DisplayName("Báo lỗi bị từ chối: không tính. Được xác nhận: người báo nhận Mắt tinh cấp I, admin thì không")
  void resolvedReportEarnsReporterTheBadge() throws Exception {
    ApiSession nam = api.newLearner();
    List<Long> senses = systemWordSenses();

    long rejected = report(nam, senses.get(0));
    review(rejected, "REJECTED", "Nghĩa này đúng");
    assertThat(badges.level(nam, "SHARP_EYE")).isZero();

    long resolved = report(nam, senses.get(1));
    review(resolved, "RESOLVED", "Đã sửa, cảm ơn bạn");

    assertThat(badges.level(nam, "SHARP_EYE")).isEqualTo(1);
    assertThat(badges.progress(nam, "SHARP_EYE")).isEqualTo(1);
    assertThat(badges.unseenCodes(nam)).containsExactly("SHARP_EYE");
    assertThat(badges.level(admin, "SHARP_EYE")).isZero();
  }

  /** Admin tạo bộ hệ thống + một từ 2 nghĩa (tên ngẫu nhiên: từ hệ thống không được trùng). Trả id 2 nghĩa. */
  private List<Long> systemWordSenses() throws Exception {
    MvcResult deck = mvc.perform(post("/api/admin/decks").header(AUTHORIZATION, admin.bearer())
            .contentType(APPLICATION_JSON)
            .content("""
                {"deckName": "Badge IT %s"}
                """.formatted(UUID.randomUUID().toString().substring(0, 8))))
        .andExpect(status().isCreated())
        .andReturn();
    long deckId = VocabClient.longAt(deck, "$.id");
    String term = "sharp" + UUID.randomUUID().toString().substring(0, 8);
    MvcResult word = mvc.perform(post("/api/admin/decks/{deckId}/words", deckId).header(AUTHORIZATION, admin.bearer())
            .contentType(APPLICATION_JSON)
            .content(VocabClient.wordJson(term, 0)))
        .andExpect(status().isCreated())
        .andReturn();
    List<Number> ids = JsonPath.read(word.getResponse().getContentAsString(), "$.senses[*].id");
    return ids.stream().map(Number::longValue).toList();
  }

  private long report(ApiSession user, long senseId) throws Exception {
    MvcResult created = mvc.perform(post("/api/reports").header(AUTHORIZATION, user.bearer())
            .contentType(APPLICATION_JSON)
            .content("""
                {"senseId": %d, "reason": "WRONG_DEFINITION", "message": "Nghĩa chưa đúng"}
                """.formatted(senseId)))
        .andExpect(status().isCreated())
        .andReturn();
    return VocabClient.longAt(created, "$.id");
  }

  private void review(long reportId, String status, String note) throws Exception {
    mvc.perform(patch("/api/admin/reports/{id}", reportId).header(AUTHORIZATION, admin.bearer())
            .contentType(APPLICATION_JSON)
            .content("""
                {"status": "%s", "adminNote": "%s"}
                """.formatted(status, note)))
        .andExpect(status().isOk());
  }
}
