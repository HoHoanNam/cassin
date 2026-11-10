package com.cassin.scan;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Lemmatizer (cách A: quy tắc + danh sách bất quy tắc). Mỗi dòng CsvSource = "từ, dạng gốc mong đợi".
 * Giới hạn ĐÃ BIẾT cũng được chốt ở đây: đổi thuật toán làm chúng thay đổi thì test báo ngay.
 */
class LemmatizerTest {

  @ParameterizedTest(name = "{0} → {1}")
  @CsvSource({
      // số nhiều
      "books, book", "boxes, box", "watches, watch", "glasses, glass", "cities, city", "makes, make",
      // -ed / -ing: bỏ phụ âm đôi
      "stopped, stop", "running, run", "sitting, sit", "beginning, begin", "hopped, hop",
      // -ed / -ing: trả lại chữ e đã mất
      "making, make", "liked, like", "used, use", "using, use", "writing, write", "hoping, hope",
      "coming, come", "improving, improve", "realized, realize", "producing, produce", "judging, judge",
      "agreed, agree",
      // -ed / -ing: giữ nguyên gốc
      "walked, walk", "opened, open", "visited, visit", "played, play", "fixed, fix", "reading, read",
      "eating, eat", "happened, happen", "needed, need", "traveling, travel",
      // -ies / -ied, -ying ngắn
      "studies, study", "studied, study", "lying, lie", "dying, die",
      // bất quy tắc
      "went, go", "ran, run", "gave, give", "children, child", "lives, life", "fed, feed"
  })
  @DisplayName("guess: đưa về dạng gốc")
  void guess_baseForm(String word, String expected) {
    assertThat(Lemmatizer.guess(word)).isEqualTo(expected);
  }

  @ParameterizedTest(name = "{0} giữ nguyên")
  @ValueSource(strings = { "morning", "news", "bus", "thing", "teacher", "family", "quickly", "bed", "red" })
  @DisplayName("guess: từ đã là gốc / không phải biến tố → không cắt (không cắt -er, -ly)")
  void guess_keepsBaseWords(String word) {
    assertThat(Lemmatizer.guess(word)).isEqualTo(word);
  }

  @ParameterizedTest(name = "{0} → {1} (giới hạn đã biết)")
  @CsvSource({ "changing, chang", "increasing, increas", "travelled, travell" })
  @DisplayName("guess: giới hạn ĐÃ BIẾT — chỉ ảnh hưởng từ NEW, candidates vẫn chứa dạng đúng")
  void guess_knownLimitations(String word, String guessed) {
    assertThat(Lemmatizer.guess(word)).isEqualTo(guessed);
  }

  @Test
  @DisplayName("candidates: phần tử ĐẦU luôn là chính từ đó (khớp DB ưu tiên dạng nguyên văn)")
  void candidates_startWithWordItself() {
    assertThat(Lemmatizer.candidates("rose")).first().isEqualTo("rose");
    assertThat(Lemmatizer.candidates("rose")).contains("rise");
    assertThat(Lemmatizer.candidates("running")).startsWith("running").contains("run");
  }

  @Test
  @DisplayName("candidates: chứa dạng đúng cả ở những từ guess đoán sai")
  void candidates_coverGuessLimitations() {
    assertThat(Lemmatizer.candidates("changing")).contains("change");
    assertThat(Lemmatizer.candidates("increasing")).contains("increase");
    assertThat(Lemmatizer.candidates("travelled")).contains("travel");
  }

  @Test
  @DisplayName("candidates: KHÔNG cắt -er / -ly (teacher không thành teach, quickly không thành quick)")
  void candidates_noDerivationalSuffixes() {
    assertThat(Lemmatizer.candidates("teacher")).containsExactly("teacher");
    assertThat(Lemmatizer.candidates("quickly")).containsExactly("quickly");
  }
}
