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
}
