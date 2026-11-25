package com.cassin.badge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * Phase 7 · Bài đọc: Mọt sách (số bài khác nhau đã nộp) và Tự lực (đúng hết, không mở gợi ý).
 * Bài mẫu 3 câu giống PassageFlowIT: BLANK "coal" (có 1 gợi ý), TFNG FALSE, MCQ đáp án ở vị trí 1.
 */
@IntegrationTest
class ReadingBadgeIT {
  private static final String ADMIN_EMAIL = "admin@it.local";
  private static final String ADMIN_PASSWORD = "admin-it-12345";

  private static final String PASSAGE_JSON = """
      {
        "title": "{TITLE}", "content": "Solar power is now a cheap alternative to coal in many countries.",
        "level": "B1",
        "questions": [
          {"type": "BLANK", "prompt": "Solar power is a cheap replacement for ____.",
           "evidence": "Solar power is now a cheap alternative to coal in many countries.",
           "explanation": "alternative = replacement", "answerKind": "WORDS", "maxWords": 1,
           "acceptedAnswers": ["coal"],
           "hints": [{"questionPhrase": "replacement", "passagePhrase": "alternative"}]},
          {"type": "TFNG", "prompt": "Solar power is expensive.",
           "evidence": "Solar power is now a cheap alternative to coal in many countries.",
           "explanation": "cheap", "answer": "FALSE"},
          {"type": "MCQ", "prompt": "How is solar power described?",
           "evidence": "Solar power is now a cheap alternative to coal in many countries.",
           "explanation": "cheap", "options": [
             {"content": "expensive", "correct": false}, {"content": "cheap", "correct": true},
             {"content": "dangerous", "correct": false}, {"content": "old", "correct": false}]}
        ]
      }""";

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
  @DisplayName("3 bài khác nhau → Mọt sách cấp I; chỉ bài đúng hết KHÔNG gợi ý mới tính Tự lực")
  void threePassagesOnlyOneSelfReliant() throws Exception {
    ApiSession nam = api.newLearner();

    read(nam, publishPassage(), true, false);   // đúng hết, không gợi ý → Tự lực
    assertThat(badges.level(nam, "SELF_RELIANT")).isEqualTo(1);
    read(nam, publishPassage(), true, true);    // đúng hết nhưng mở gợi ý → không
    read(nam, publishPassage(), false, false);  // có câu sai → không

    assertThat(badges.level(nam, "BOOKWORM")).isEqualTo(1);
    assertThat(badges.progress(nam, "BOOKWORM")).isEqualTo(3);
    assertThat(badges.progress(nam, "SELF_RELIANT")).isEqualTo(1);
  }

  @Test
  @DisplayName("Đọc lại cùng một bài: Mọt sách không tăng")
  void samePassageTwice() throws Exception {
    ApiSession nam = api.newLearner();
    long passageId = publishPassage();

    read(nam, passageId, false, false);
    read(nam, passageId, true, false);

    assertThat(badges.progress(nam, "BOOKWORM")).isEqualTo(1);
    assertThat(badges.progress(nam, "SELF_RELIANT")).isEqualTo(1); // lượt 2 đúng hết, không gợi ý
  }

  /** Admin soạn + publish một bài mới (tiêu đề ngẫu nhiên). Trả id bài. */
  private long publishPassage() throws Exception {
    MvcResult created = mvc.perform(post("/api/admin/passages").header(AUTHORIZATION, admin.bearer())
            .contentType(APPLICATION_JSON)
            .content(PASSAGE_JSON.replace("{TITLE}", "Badge IT " + UUID.randomUUID())))
        .andExpect(status().isCreated())
        .andReturn();
    long passageId = VocabClient.longAt(created, "$.id");
    mvc.perform(patch("/api/admin/passages/{id}/publish", passageId).header(AUTHORIZATION, admin.bearer()))
        .andExpect(status().isOk());
    return passageId;
  }

  /** Làm bài và nộp. allCorrect = false → câu TFNG trả sai. useHint → mở gợi ý câu BLANK trước khi nộp. */
  private void read(ApiSession user, long passageId, boolean allCorrect, boolean useHint) throws Exception {
    String body = mvc.perform(post("/api/passages/{id}/attempts", passageId).header(AUTHORIZATION, user.bearer()))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    long attemptId = id(body, "$.attemptId");
    long blankId = id(body, "$.questions[0].id");
    long tfngId = id(body, "$.questions[1].id");
    long mcqId = id(body, "$.questions[2].id");
    long cheapOptionId = id(body, "$.questions[2].options[1].id");

    if (useHint) {
      mvc.perform(post("/api/reading-attempts/{a}/questions/{q}/hint", attemptId, blankId)
              .header(AUTHORIZATION, user.bearer()))
          .andExpect(status().isOk());
    }
    mvc.perform(post("/api/reading-attempts/{a}/submit", attemptId)
            .header(AUTHORIZATION, user.bearer())
            .contentType(APPLICATION_JSON)
            .content("""
                {"answers": [
                  {"questionId": %d, "text": "coal"},
                  {"questionId": %d, "tfngAnswer": "%s"},
                  {"questionId": %d, "optionId": %d}]}
                """.formatted(blankId, tfngId, allCorrect ? "FALSE" : "TRUE", mcqId, cheapOptionId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.score").value(allCorrect ? 3 : 2));
  }

  private static long id(String json, String path) {
    return ((Number) JsonPath.read(json, path)).longValue();
  }
}
