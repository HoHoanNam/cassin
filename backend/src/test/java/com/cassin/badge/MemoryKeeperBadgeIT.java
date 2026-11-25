package com.cassin.badge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.cassin.support.ApiClient;
import com.cassin.support.ApiSession;
import com.cassin.support.BadgeClient;
import com.cassin.support.IntegrationTest;
import com.cassin.support.MutableClock;
import com.cassin.support.VocabClient;

/**
 * Phase 3 · Nhớ lâu: đưa 5 nghĩa lên hộp 5 bằng lịch Leitner thật.
 *
 *   ngày 0  : thẻ mới, nhớ → hộp 2, hạn +2
 *   ngày 2  : nhớ → hộp 3, hạn +4
 *   ngày 6  : nhớ → hộp 4, hạn +7
 *   ngày 13 : nhớ → hộp 5  ← lúc này mới tính
 */
@IntegrationTest
class MemoryKeeperBadgeIT {
  private static final int[] REVIEW_DAYS = { 0, 2, 6, 13 };

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

  @AfterEach
  void resetClock() {
    clock.reset();
  }

  @Test
  @DisplayName("5 nghĩa lên hộp 5 → Nhớ lâu cấp I; trước lượt ôn cuối thì chưa")
  void fiveSensesInBoxFive() throws Exception {
    ApiSession nam = api.newLearner();
    long deck = vocab.createDeck(nam, "IELTS");
    List<Long> senses = new ArrayList<>();
    for (String term : new String[] { "alpha", "bravo", "charlie", "delta", "echo" }) {
      senses.add(vocab.createWord(nam, deck, term).sense(0));
    }

    int today = 0;
    for (int day : REVIEW_DAYS) {
      clock.advance(Duration.ofDays(day - today));
      today = day;
      assertThat(badges.level(nam, "MEMORY_KEEPER")).isZero();
      for (long senseId : senses) {
        vocab.review(nam, senseId, true).andExpect(status().isOk());
      }
    }

    assertThat(badges.progress(nam, "MEMORY_KEEPER")).isEqualTo(5);
    assertThat(badges.level(nam, "MEMORY_KEEPER")).isEqualTo(1);
    vocab.stats(nam).andExpect(jsonPath("$.boxes[4].total").value(5)); // khớp biểu đồ hộp ở trang Ôn tập
  }
}
