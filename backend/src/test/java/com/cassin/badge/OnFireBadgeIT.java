package com.cassin.badge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

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
 * Phase 2 · Lửa bền: tua đồng hồ từng ngày, mỗi ngày ôn MỘT thẻ mới (thẻ mới luôn đến hạn ngay hôm đó).
 */
@IntegrationTest
class OnFireBadgeIT {
  private static final Duration ONE_DAY = Duration.ofDays(1);

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
    clock.reset(); // IT khác dùng chung bean đồng hồ: trả về "bây giờ"
  }

  @Test
  @DisplayName("Ôn 3 ngày liền → Lửa bền cấp I, tiến độ khớp chuỗi ngày ở trang Ôn tập")
  void threeDaysInARow() throws Exception {
    ApiSession nam = api.newLearner();
    long deck = vocab.createDeck(nam, "IELTS");

    reviewNewWord(nam, deck, "one");
    clock.advance(ONE_DAY);
    reviewNewWord(nam, deck, "two");
    assertThat(badges.level(nam, "ON_FIRE")).isZero();
    clock.advance(ONE_DAY);
    reviewNewWord(nam, deck, "three");

    assertThat(badges.level(nam, "ON_FIRE")).isEqualTo(1);
    assertThat(badges.progress(nam, "ON_FIRE")).isEqualTo(3);
    assertThat(badges.unseenCodes(nam)).contains("ON_FIRE");
    vocab.stats(nam).andExpect(jsonPath("$.streakDays").value(3)); // cùng một con số với trang Ôn tập
  }

  @Test
  @DisplayName("Bỏ trọn một ngày: chuỗi đứt, tiến độ về 1 nhưng cấp đã đạt vẫn giữ")
  void brokenStreakKeepsEarnedLevel() throws Exception {
    ApiSession nam = api.newLearner();
    long deck = vocab.createDeck(nam, "IELTS");
    for (String term : new String[] { "one", "two", "three" }) {
      reviewNewWord(nam, deck, term);
      clock.advance(ONE_DAY);
    }
    clock.advance(ONE_DAY); // bỏ trọn một ngày

    reviewNewWord(nam, deck, "four");

    assertThat(badges.progress(nam, "ON_FIRE")).isEqualTo(1);
    assertThat(badges.level(nam, "ON_FIRE")).isEqualTo(1);
  }

  private void reviewNewWord(ApiSession user, long deckId, String term) throws Exception {
    long senseId = vocab.createWord(user, deckId, term).sense(0);
    vocab.review(user, senseId, true).andExpect(status().isOk());
  }
}
