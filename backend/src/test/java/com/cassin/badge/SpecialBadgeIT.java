package com.cassin.badge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.AfterEach;
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

/** Phase 9 · Huy hiệu đặc biệt (một lần): Đa năng, Không bỏ cuộc, Trở lại, Cú đêm, Chim sớm. */
@IntegrationTest
class SpecialBadgeIT {
  private static final Duration ONE_DAY = Duration.ofDays(1);

  @Autowired
  MockMvc mvc;
  @Autowired
  MutableClock clock;

  ApiClient api;
  VocabClient vocab;
  BadgeClient badges;
  ApiSession nam;
  long deck;

  @BeforeEach
  void setUp() throws Exception {
    clock.reset();
    api = new ApiClient(mvc);
    vocab = new VocabClient(mvc);
    badges = new BadgeClient(mvc);
    nam = api.newLearner();
    deck = vocab.createDeck(nam, "IELTS");
  }

  @AfterEach
  void resetClock() {
    clock.reset();
  }

  @Test
  @DisplayName("Đa năng: đủ 3 kiểu lật thẻ, điền từ, xếp câu (tiến độ 1 → 2 → 3)")
  void allRounder() throws Exception {
    long senseId = vocab.createWord(nam, deck, "improve").sense(0);

    vocab.review(nam, senseId, true).andExpect(status().isOk());
    answer(senseId, "FILL_BLANK", "improve");
    answer(senseId, "FILL_BLANK", "improve"); // cùng kiểu: không tăng
    assertThat(badges.progress(nam, "ALL_ROUNDER")).isEqualTo(2);

    answer(senseId, "SENTENCE_BUILD", "We improve every day.");

    assertThat(badges.level(nam, "ALL_ROUNDER")).isEqualTo(1);
  }

  @Test
  @DisplayName("Không bỏ cuộc: quên 3 lần rồi vẫn đưa nghĩa đó lên hộp 5")
  void neverGiveUp() throws Exception {
    long senseId = vocab.createWord(nam, deck, "stubborn").sense(0);

    // Quên 3 ngày liền (mỗi lần về hộp 1, hạn ngày mai), rồi nhớ theo đúng lịch Leitner tới hộp 5.
    boolean[] remembered = { false, false, false, true, true, true, true };
    int[] daysUntilNext = { 1, 1, 1, 2, 4, 7, 0 };
    for (int i = 0; i < remembered.length; i++) {
      assertThat(badges.level(nam, "NEVER_GIVE_UP")).isZero();
      vocab.review(nam, senseId, remembered[i]).andExpect(status().isOk());
      clock.advance(ONE_DAY.multipliedBy(daysUntilNext[i]));
    }

    assertThat(badges.level(nam, "NEVER_GIVE_UP")).isEqualTo(1);
  }

  @Test
  @DisplayName("Trở lại: nghỉ 6 ngày chưa tính, nghỉ trọn 7 ngày thì đạt")
  void comeback() throws Exception {
    reviewNewWord("first");
    clock.advance(ONE_DAY.multipliedBy(7)); // học lại sau 7 ngày = nghỉ 6 ngày ở giữa
    reviewNewWord("second");
    assertThat(badges.level(nam, "COMEBACK")).isZero();

    clock.advance(ONE_DAY.multipliedBy(8)); // nghỉ trọn 7 ngày
    reviewNewWord("third");

    assertThat(badges.level(nam, "COMEBACK")).isEqualTo(1);
  }

  @Test
  @DisplayName("Cú đêm (23:30) và Chim sớm (5:00): bí mật, che tên tới khi đạt; 2 khung giờ không lẫn nhau")
  void nightOwlAndEarlyBird() throws Exception {
    badges.list(nam).andExpect(jsonPath("$[?(@.code == 'NIGHT_OWL')].name", contains(BadgeService.SECRET_NAME)));

    moveTo(LocalTime.of(23, 30));
    reviewNewWord("late");
    assertThat(badges.level(nam, "NIGHT_OWL")).isEqualTo(1);
    assertThat(badges.level(nam, "EARLY_BIRD")).isZero();
    badges.list(nam).andExpect(jsonPath("$[?(@.code == 'NIGHT_OWL')].name", contains("Cú đêm")));

    moveTo(LocalTime.of(5, 0));
    reviewNewWord("early");
    assertThat(badges.level(nam, "EARLY_BIRD")).isEqualTo(1);
  }

  // ===================== helpers =====================

  /** Tua đồng hồ tới lần gần nhất đồng hồ ở VN chỉ đúng giờ này (luôn về phía tương lai). */
  private void moveTo(LocalTime time) {
    ZonedDateTime now = ZonedDateTime.now(clock);
    ZonedDateTime target = now.with(time);
    if (!target.isAfter(now)) {
      target = target.plusDays(1);
    }
    clock.advance(Duration.between(now, target));
  }

  private void reviewNewWord(String term) throws Exception {
    vocab.review(nam, vocab.createWord(nam, deck, term).sense(0), true).andExpect(status().isOk());
  }

  private ResultActions answer(long senseId, String mode, String text) throws Exception {
    return mvc.perform(post("/api/practice/answers")
            .header(AUTHORIZATION, nam.bearer())
            .contentType(APPLICATION_JSON)
            .content("""
                {"senseId": %d, "exampleIndex": 0, "mode": "%s", "answer": "%s", "durationMs": 2000}
                """.formatted(senseId, mode, text)))
        .andExpect(status().isOk());
  }
}
