package com.cassin.vocab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** [V10] Đọc / kiểm tra câu ví dụ có đánh dấu [từ đích]. */
class ExampleMarkupTest {

  @Test
  @DisplayName("Câu có đánh dấu: tách đúng câu hiển thị, từ đích và câu đã che")
  void parse_markedSentence() {
    ExampleMarkup m = ExampleMarkup.parse("Her English has [improved] a lot.").orElseThrow();

    assertThat(m.plain()).isEqualTo("Her English has improved a lot.");
    assertThat(m.target()).isEqualTo("improved");
    assertThat(m.blanked()).isEqualTo("Her English has ____ a lot.");
  }

  @Test
  @DisplayName("Đánh dấu dạng đã chia (children) chứ không phải từ gốc (child): che đúng chỗ")
  void parse_inflectedForm() {
    ExampleMarkup m = ExampleMarkup.parse("The [children] are playing outside.").orElseThrow();

    assertThat(m.target()).isEqualTo("children");
    assertThat(m.blanked()).isEqualTo("The ____ are playing outside.");
  }

  @Test
  @DisplayName("Câu không đánh dấu: empty (vẫn hợp lệ, chỉ không dùng cho Fill in blank)")
  void parse_unmarked_isEmpty() {
    assertThat(ExampleMarkup.parse("Turn on the light.")).isEmpty();
    assertThat(ExampleMarkup.parse(null)).isEmpty();
  }

  @Test
  @DisplayName("plainOf: bỏ dấu ngoặc để hiển thị; câu không đánh dấu giữ nguyên")
  void plainOf_stripsBrackets() {
    assertThat(ExampleMarkup.plainOf("Turn on the [light].")).isEqualTo("Turn on the light.");
    assertThat(ExampleMarkup.plainOf("Turn on the light.")).isEqualTo("Turn on the light.");
  }

  @Test
  @DisplayName("validate: chấp nhận câu thường và câu có đúng 1 đánh dấu")
  void validate_acceptsValid() {
    assertThatCode(() -> ExampleMarkup.validate("Turn on the light.")).doesNotThrowAnyException();
    assertThatCode(() -> ExampleMarkup.validate("Turn on the [light].")).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("validate: ngoặc lẻ, 2 đánh dấu, đánh dấu rỗng → IllegalArgumentException")
  void validate_rejectsMalformed() {
    assertThatThrownBy(() -> ExampleMarkup.validate("Turn on the [light.")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ExampleMarkup.validate("Turn on the light].")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ExampleMarkup.validate("[Turn] on the [light].")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ExampleMarkup.validate("Turn on the [].")).isInstanceOf(IllegalArgumentException.class);
  }

  // ===================== [THÊM] Thành ngữ & cụm động từ: [ ] bao NHIỀU chữ =====================

  @Test
  @DisplayName("[THÊM] Thành ngữ nhiều chữ: cả cụm là từ đích, che bằng MỘT chỗ trống")
  void parse_multiWordIdiom() {
    ExampleMarkup m = ExampleMarkup.parse("He told a joke to [break the ice].").orElseThrow();

    assertThat(m.plain()).isEqualTo("He told a joke to break the ice.");
    assertThat(m.target()).isEqualTo("break the ice");
    assertThat(m.start()).isEqualTo("He told a joke to ".length());
    assertThat(m.blanked()).isEqualTo("He told a joke to ____.");
  }

  @Test
  @DisplayName("[THÊM] Cụm động từ đã chia (gave up, không phải give up): đáp án là đúng dạng trong câu")
  void parse_inflectedPhrasalVerb() {
    ExampleMarkup m = ExampleMarkup.parse("He [gave up] smoking last year.").orElseThrow();

    assertThat(m.target()).isEqualTo("gave up");
    assertThat(m.blanked()).isEqualTo("He ____ smoking last year.");
  }

  @Test
  @DisplayName("[THÊM] Thành ngữ đứng đầu câu: start = 0, giữ dấu câu phía sau")
  void parse_idiomAtSentenceStart() {
    ExampleMarkup m = ExampleMarkup.parse("[Better late than never]!").orElseThrow();

    assertThat(m.start()).isZero();
    assertThat(m.blanked()).isEqualTo("____!");
    assertThat(ExampleMarkup.plainOf("[Better late than never]!")).isEqualTo("Better late than never!");
  }

  @Test
  @DisplayName("[THÊM] validate: một đánh dấu nhiều chữ là hợp lệ; hai cụm đánh dấu vẫn bị từ chối")
  void validate_multiWord() {
    assertThatCode(() -> ExampleMarkup.validate("She [let the cat out of the bag] about the party."))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> ExampleMarkup.validate("[Give up] or [carry on]?"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
