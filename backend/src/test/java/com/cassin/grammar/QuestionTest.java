package com.cassin.grammar;

import static com.cassin.grammar.GrammarFixtures.optionId;
import static com.cassin.grammar.GrammarFixtures.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test cho rule của Question (aggregate root của Question + QuestionOption):
 * "đúng 4 đáp án, đúng 1 đáp án đúng, không trùng nhau".
 * Không Spring, không DB: chỉ tạo object và gọi method.
 */
class QuestionTest {

  private final GrammarTopic topic = GrammarFixtures.topic(1L, "Present Perfect");

  // ===================== create =====================

  @Test
  @DisplayName("Tạo câu hỏi hợp lệ: 4 đáp án đúng thứ tự 0..3, nội dung được cắt khoảng trắng")
  void create_validQuestion() {
    List<OptionInput> inputs = List.of(
        new OptionInput("  has lived ", true),
        new OptionInput("lived", false),
        new OptionInput("lives", false),
        new OptionInput("is living", false));

    Question q = Question.create(topic, "  She ___ in Hanoi since 2020. ", " Có since ", inputs);

    assertThat(q.getContent()).isEqualTo("She ___ in Hanoi since 2020.");
    assertThat(q.getExplanation()).isEqualTo("Có since");
    assertThat(q.getTopic()).isSameAs(topic);
    assertThat(q.getOptions())
        .extracting(o -> o.getPosition(), o -> o.getContent(), o -> o.isCorrect())
        .containsExactly(
            tuple(0, "has lived", true),
            tuple(1, "lived", false),
            tuple(2, "lives", false),
            tuple(3, "is living", false));
  }

  @Test
  @DisplayName("Số đáp án khác 4 (hoặc null) → bị chặn")
  void create_rejectsWrongOptionCount() {
    List<OptionInput> three = new ArrayList<>(options("x", 0).subList(0, 3));
    List<OptionInput> five = new ArrayList<>(options("x", 0));
    five.add(new OptionInput("thừa", false));

    assertThatThrownBy(() -> Question.create(topic, "Đề", "Giải thích", three))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("đúng 4 đáp án");
    assertThatThrownBy(() -> Question.create(topic, "Đề", "Giải thích", five))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("đúng 4 đáp án");
    assertThatThrownBy(() -> Question.create(topic, "Đề", "Giải thích", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("0 đáp án đúng hoặc 2 đáp án đúng → bị chặn")
  void create_rejectsNotExactlyOneCorrect() {
    List<OptionInput> none = List.of(
        new OptionInput("a", false), new OptionInput("b", false),
        new OptionInput("c", false), new OptionInput("d", false));
    List<OptionInput> two = List.of(
        new OptionInput("a", true), new OptionInput("b", true),
        new OptionInput("c", false), new OptionInput("d", false));

    assertThatThrownBy(() -> Question.create(topic, "Đề", "Giải thích", none))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("đang có 0");
    assertThatThrownBy(() -> Question.create(topic, "Đề", "Giải thích", two))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("đang có 2");
  }

  @Test
  @DisplayName("Hai đáp án trùng nhau (khác hoa thường, thừa khoảng trắng) → bị chặn")
  void create_rejectsDuplicateOptionsIgnoringCase() {
    List<OptionInput> duplicated = List.of(
        new OptionInput("has lived", true),
        new OptionInput("lived", false),
        new OptionInput("  Has Lived ", false),
        new OptionInput("lives", false));

    assertThatThrownBy(() -> Question.create(topic, "Đề", "Giải thích", duplicated))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("trùng");
  }

  @Test
  @DisplayName("Đề bài / giải thích / nội dung đáp án rỗng, hoặc đáp án null → bị chặn")
  void create_rejectsBlankText() {
    List<OptionInput> blankOption = List.of(
        new OptionInput("a", true), new OptionInput("   ", false),
        new OptionInput("c", false), new OptionInput("d", false));
    List<OptionInput> nullOption = new ArrayList<>(options("x", 0));
    nullOption.set(2, null);

    assertThatThrownBy(() -> Question.create(topic, "  ", "Giải thích", options("x", 0)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Question.create(topic, "Đề", null, options("x", 0)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Question.create(topic, "Đề", "Giải thích", blankOption))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Question.create(topic, "Đề", "Giải thích", nullOption))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Chủ đề null → bị chặn (fail fast)")
  void create_rejectsNullTopic() {
    assertThatThrownBy(() -> Question.create(null, "Đề", "Giải thích", options("x", 0)))
        .isInstanceOf(NullPointerException.class);
  }

  // ===================== update =====================

  @Test
  @DisplayName("Sửa câu hỏi: GIỮ NGUYÊN id 4 đáp án (bài làm cũ vẫn hợp lệ), đổi được đáp án đúng")
  void update_keepsOptionIds_andChangesCorrectAnswer() {
    Question q = GrammarFixtures.question(topic, 1L, 0);   // đáp án đúng ở vị trí 0

    q.update("Đề mới", "Giải thích mới", options("Mới", 3)); // đổi sang vị trí 3

    assertThat(q.getContent()).isEqualTo("Đề mới");
    assertThat(q.getOptions()).extracting(o -> o.getId()).containsExactly(10L, 11L, 12L, 13L);
    assertThat(q.isCorrectAnswer(optionId(1, 0))).isFalse();
    assertThat(q.isCorrectAnswer(optionId(1, 3))).isTrue();
    assertThat(q.getOptions().get(0).getContent()).isEqualTo("Mới 0");
  }

  @Test
  @DisplayName("Sửa với đáp án không hợp lệ: bị chặn, câu hỏi KHÔNG bị đổi gì")
  void update_invalidOptions_leavesQuestionUnchanged() {
    Question q = GrammarFixtures.question(topic, 1L, 0);
    List<OptionInput> twoCorrect = List.of(
        new OptionInput("a", true), new OptionInput("b", true),
        new OptionInput("c", false), new OptionInput("d", false));

    assertThatThrownBy(() -> q.update("Đề mới", "Giải thích mới", twoCorrect))
        .isInstanceOf(IllegalArgumentException.class);

    // validateOptions chạy TRƯỚC applyText → đề bài cũ còn nguyên.
    assertThat(q.getContent()).isEqualTo("Câu hỏi 1");
    assertThat(q.isCorrectAnswer(optionId(1, 0))).isTrue();
  }

  // ===================== findOption / hasOption =====================

  @Test
  @DisplayName("Đáp án của câu khác: hasOption = false, findOption ném lỗi (không lặng lẽ trả false)")
  void findOption_foreignOption() {
    Question q1 = GrammarFixtures.question(topic, 1L, 0);
    long optionOfQuestion2 = optionId(2, 0);

    assertThat(q1.hasOption(optionId(1, 2))).isTrue();
    assertThat(q1.hasOption(optionOfQuestion2)).isFalse();
    assertThatThrownBy(() -> q1.findOption(optionOfQuestion2))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("không thuộc câu hỏi");
    assertThatThrownBy(() -> q1.isCorrectAnswer(optionOfQuestion2))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("getOptions() chỉ đọc: bên ngoài không thêm/xóa đáp án được")
  void getOptions_isReadOnly() {
    Question q = GrammarFixtures.question(topic, 1L, 0);

    assertThatThrownBy(() -> q.getOptions().clear()).isInstanceOf(UnsupportedOperationException.class);
  }
}
