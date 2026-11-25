package com.cassin.badge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import com.cassin.support.MutableClock;
import com.cassin.support.VocabClient;
import com.cassin.support.VocabClient.CreatedWord;

/**
 * Phase 1 · Chăm chỉ: cấp từ lượt trả lời THẬT (ôn thẻ lật + điền từ), đi đủ đường
 * controller → service → publishEvent → (commit) → BadgeEventListener → DiligentRule → user_badges.
 */
@IntegrationTest
class DiligentBadgeIT {
  @Autowired
  MockMvc mvc;
  @Autowired
  MutableClock clock;

  ApiClient api;
  VocabClient vocab;
  BadgeClient badges;

  @BeforeEach
  void setUp() {
    clock.reset();
    api = new ApiClient(mvc);
    vocab = new VocabClient(mvc);
    badges = new BadgeClient(mvc);
  }

  @Test
  @DisplayName("1 lượt ôn thẻ + 29 lượt điền từ = 30 lượt → Chăm chỉ cấp I; câu sai vẫn được tính")
  void thirtyAnswersEarnLevelOne() throws Exception {
    ApiSession nam = api.newLearner();
    CreatedWord word = vocab.createWord(nam, vocab.createDeck(nam, "IELTS"), "improve");

    vocab.review(nam, word.sense(0), true).andExpect(status().isOk()); // lượt 1: ôn thẻ lật
    for (int i = 0; i < 28; i++) {                                     // lượt 2 → 29: điền từ đúng
      fillBlank(nam, word.sense(0), "improve").andExpect(status().isOk());
    }
    assertThat(badges.progress(nam, "DILIGENT")).isEqualTo(29);
    assertThat(badges.unseenCodes(nam)).isEmpty();                     // 29 < 30: chưa đạt

    fillBlank(nam, word.sense(0), "sai bét").andExpect(jsonPath("$.correct").value(false)); // lượt 30, sai

    badges.unseen(nam)
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].code").value("DILIGENT"))
        .andExpect(jsonPath("$[0].level").value(1))
        .andExpect(jsonPath("$[0].target").value(30));
    assertThat(badges.level(nam, "DILIGENT")).isEqualTo(1);
    assertThat(badges.progress(nam, "DILIGENT")).isEqualTo(30);
  }

  @Test
  @DisplayName("Lượt ôn bị từ chối (409: đã ôn hôm nay) không được tính")
  void rejectedReviewIsNotCounted() throws Exception {
    ApiSession nam = api.newLearner();
    CreatedWord word = vocab.createWord(nam, vocab.createDeck(nam, "IELTS"), "improve");

    vocab.review(nam, word.sense(0), true).andExpect(status().isOk());
    vocab.review(nam, word.sense(0), true).andExpect(status().isConflict());

    assertThat(badges.progress(nam, "DILIGENT")).isEqualTo(1);
  }

  private ResultActions fillBlank(ApiSession user, long senseId, String text) throws Exception {
    return mvc.perform(post("/api/practice/answers")
        .header(AUTHORIZATION, user.bearer())
        .contentType(APPLICATION_JSON)
        .content("""
            {"senseId": %d, "exampleIndex": 0, "mode": "FILL_BLANK", "answer": "%s", "durationMs": 2000}
            """.formatted(senseId, text)));
  }
}
