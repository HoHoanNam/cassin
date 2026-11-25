package com.cassin.badge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.cassin.support.ApiClient;
import com.cassin.support.ApiSession;
import com.cassin.support.BadgeClient;
import com.cassin.support.IntegrationTest;
import com.cassin.support.VocabClient;

/** Phase 4 · Thợ rèn câu: chỉ điền từ + xếp câu được tính, thẻ lật thì không. */
@IntegrationTest
class WordsmithBadgeIT {
  @Autowired
  MockMvc mvc;

  ApiClient api;
  VocabClient vocab;
  BadgeClient badges;

  @BeforeEach
  void setUp() {
    api = new ApiClient(mvc);
    vocab = new VocabClient(mvc);
    badges = new BadgeClient(mvc);
  }

  @Test
  @DisplayName("15 điền từ + 15 xếp câu → Thợ rèn câu cấp I; lượt ôn thẻ lật không được tính")
  void thirtySentenceAnswers() throws Exception {
    ApiSession nam = api.newLearner();
    long senseId = vocab.createWord(nam, vocab.createDeck(nam, "IELTS"), "improve").sense(0);

    vocab.review(nam, senseId, true).andExpect(status().isOk()); // thẻ lật: Chăm chỉ +1, Thợ rèn câu +0
    for (int i = 0; i < 15; i++) {
      answer(nam, senseId, "FILL_BLANK", "improve").andExpect(status().isOk());
    }
    for (int i = 0; i < 14; i++) {
      answer(nam, senseId, "SENTENCE_BUILD", "We improve every day.").andExpect(status().isOk());
    }
    assertThat(badges.progress(nam, "WORDSMITH")).isEqualTo(29);
    assertThat(badges.level(nam, "WORDSMITH")).isZero();

    answer(nam, senseId, "SENTENCE_BUILD", "every day We improve").andExpect(status().isOk()); // câu 30, sai

    assertThat(badges.level(nam, "WORDSMITH")).isEqualTo(1);
    assertThat(badges.progress(nam, "WORDSMITH")).isEqualTo(30);
    assertThat(badges.progress(nam, "DILIGENT")).isEqualTo(31); // Chăm chỉ đếm cả lượt thẻ lật
  }

  private ResultActions answer(ApiSession user, long senseId, String mode, String text) throws Exception {
    return mvc.perform(post("/api/practice/answers")
        .header(AUTHORIZATION, user.bearer())
        .contentType(APPLICATION_JSON)
        .content("""
            {"senseId": %d, "exampleIndex": 0, "mode": "%s", "answer": "%s", "durationMs": 2000}
            """.formatted(senseId, mode, text)));
  }
}
