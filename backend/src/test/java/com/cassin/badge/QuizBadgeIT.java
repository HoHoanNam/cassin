package com.cassin.badge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
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
 * Phase 6 · Kiểm tra ngữ pháp: Điểm tuyệt đối (bài đúng hết) và Nhà ngữ pháp (số chủ đề khác nhau).
 * Mỗi test để admin tạo chủ đề MỚI, mỗi chủ đề 1 câu hỏi (đáp án đúng ở vị trí 1) → đề luôn đúng 1 câu.
 */
@IntegrationTest
class QuizBadgeIT {
  private static final String ADMIN_EMAIL = "admin@it.local";
  private static final String ADMIN_PASSWORD = "admin-it-12345";
  private static final int CORRECT = 1;
  private static final int WRONG = 0;

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
  @DisplayName("Bài đầu tiên đúng hết → Điểm tuyệt đối cấp I; 3 chủ đề khác nhau → Nhà ngữ pháp cấp I")
  void perfectQuizAndThreeTopics() throws Exception {
    ApiSession nam = api.newLearner();

    takeQuiz(nam, createTopic(), CORRECT);
    assertThat(badges.level(nam, "PERFECTIONIST")).isEqualTo(1);
    assertThat(badges.progress(nam, "GRAMMARIAN")).isEqualTo(1);

    takeQuiz(nam, createTopic(), WRONG);
    takeQuiz(nam, createTopic(), WRONG);

    assertThat(badges.level(nam, "GRAMMARIAN")).isEqualTo(1);
    assertThat(badges.progress(nam, "PERFECTIONIST")).isEqualTo(1); // 2 bài sai không tính
    assertThat(badges.unseenCodes(nam)).containsExactlyInAnyOrder("PERFECTIONIST", "GRAMMARIAN");
  }

  @Test
  @DisplayName("Làm lại cùng một chủ đề: Nhà ngữ pháp không tăng, Điểm tuyệt đối tính từng lượt")
  void sameTopicTwice() throws Exception {
    ApiSession nam = api.newLearner();
    long topic = createTopic();

    takeQuiz(nam, topic, CORRECT);
    takeQuiz(nam, topic, CORRECT);

    assertThat(badges.progress(nam, "GRAMMARIAN")).isEqualTo(1);
    assertThat(badges.progress(nam, "PERFECTIONIST")).isEqualTo(2);
  }

  /** Chủ đề mới có đúng 1 câu hỏi. Trả id chủ đề. */
  private long createTopic() throws Exception {
    MvcResult topic = mvc.perform(post("/api/admin/topics").header(AUTHORIZATION, admin.bearer())
            .contentType(APPLICATION_JSON)
            .content("""
                {"title": "Badge IT %s", "level": "B1", "explanation": "Chủ đề cho test huy hiệu"}
                """.formatted(UUID.randomUUID())))
        .andExpect(status().isCreated())
        .andReturn();
    long topicId = VocabClient.longAt(topic, "$.id");
    mvc.perform(post("/api/admin/topics/{topicId}/questions", topicId).header(AUTHORIZATION, admin.bearer())
            .contentType(APPLICATION_JSON)
            .content("""
                {"content": "She ___ here since 2020.", "explanation": "since → hiện tại hoàn thành",
                 "options": [
                   {"content": "lives", "correct": false}, {"content": "has lived", "correct": true},
                   {"content": "lived", "correct": false}, {"content": "is living", "correct": false}]}
                """))
        .andExpect(status().isCreated());
    return topicId;
  }

  /** Làm bài và nộp, chọn đáp án ở vị trí optionIndex (1 = đúng). */
  private void takeQuiz(ApiSession user, long topicId, int optionIndex) throws Exception {
    MvcResult started = mvc.perform(post("/api/grammar/topics/{topicId}/quiz-attempts", topicId)
            .header(AUTHORIZATION, user.bearer()))
        .andExpect(status().isCreated())
        .andReturn();
    String body = started.getResponse().getContentAsString();
    long attemptId = ((Number) JsonPath.read(body, "$.attemptId")).longValue();
    long questionId = ((Number) JsonPath.read(body, "$.questions[0].questionId")).longValue();
    long optionId = ((Number) JsonPath.read(body, "$.questions[0].options[" + optionIndex + "].id")).longValue();

    mvc.perform(post("/api/quiz-attempts/{attemptId}/submit", attemptId)
            .header(AUTHORIZATION, user.bearer())
            .contentType(APPLICATION_JSON)
            .content("""
                {"answers": [{"questionId": %d, "optionId": %d}]}
                """.formatted(questionId, optionId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.score").value(optionIndex == CORRECT ? 1 : 0));
  }
}
