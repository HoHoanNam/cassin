package com.cassin.passage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Luật chấm câu điền từ: Java thuần nên test thẳng, không cần mock. */
class BlankAnswerRulesTest {

  @Test
  @DisplayName("Chuẩn hóa: bỏ khoảng trắng thừa, chữ thường, bỏ dấu câu ở hai đầu, đổi nháy cong")
  void normalize() {
    assertThat(BlankAnswerRules.normalize("  Solar   Power. ")).isEqualTo("solar power");
    assertThat(BlankAnswerRules.normalize("“Coal”")).isEqualTo("coal");
    assertThat(BlankAnswerRules.normalize("15%")).isEqualTo("15%");
    assertThat(BlankAnswerRules.normalize(null)).isEmpty();
  }

  @ParameterizedTest(name = "{0} max={1} \"{2}\" → {3}")
  @CsvSource(nullValues = "OK", value = {
      "WORDS,               2, solar power,       OK",
      "WORDS,               2, clean solar power, OVER_WORD_LIMIT",
      "WORDS,               1, well-known,        OK",
      "WORDS,               2, 15 panels,         NUMBER_NOT_ALLOWED",
      "NUMBER,              , 1990,              OK",
      "NUMBER,              , '1,000',           OK",
      "NUMBER,              , fifteen,           NUMBER_REQUIRED",
      "NUMBER,              , 15 homes,          NUMBER_REQUIRED",
      "WORDS_AND_OR_NUMBER, 2, 15% of homes,      OK",
      "WORDS_AND_OR_NUMBER, 1, 3.5 metres,        OK",
      "WORDS_AND_OR_NUMBER, 1, 15 20,             TOO_MANY_NUMBERS",
      "WORDS_AND_OR_NUMBER, 1, big roof,          OVER_WORD_LIMIT",
  })
  @DisplayName("Kiểm tra định dạng theo loại đáp án và số từ tối đa")
  void check(AnswerKind kind, Integer maxWords, String answer, RejectedReason expected) {
    assertThat(BlankAnswerRules.check(kind, maxWords, answer)).isEqualTo(expected);
  }

  @Test
  @DisplayName("Dòng hướng dẫn theo đúng câu chữ IELTS")
  void instruction() {
    assertThat(BlankAnswerRules.instruction(AnswerKind.WORDS, 1)).isEqualTo("ONE WORD ONLY");
    assertThat(BlankAnswerRules.instruction(AnswerKind.WORDS, 3)).isEqualTo("NO MORE THAN THREE WORDS");
    assertThat(BlankAnswerRules.instruction(AnswerKind.NUMBER, null)).isEqualTo("A NUMBER");
    assertThat(BlankAnswerRules.instruction(AnswerKind.WORDS_AND_OR_NUMBER, 2))
        .isEqualTo("NO MORE THAN TWO WORDS AND/OR A NUMBER");
  }
}
