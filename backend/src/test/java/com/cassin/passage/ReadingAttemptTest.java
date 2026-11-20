package com.cassin.passage;

import static com.cassin.passage.PassageFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Một lượt làm bài: bắt đầu → mở gợi ý → nộp.
 * Bài mẫu: câu id 1 BLANK ("coal", 1 gợi ý) · id 2 TFNG (FALSE) · id 3 MCQ (đúng: option 31).
 */
class ReadingAttemptTest {

  private final Passage passage = PassageFixtures.publishedWithAllTypes();

  @Test
  @DisplayName("Bắt đầu: tạo sẵn một câu trả lời trống cho mỗi câu hỏi, đúng thứ tự")
  void start_createsEmptyAnswers() {
    ReadingAttempt attempt = ReadingAttempt.start(PassageFixtures.user(7L), passage, NOW);

    assertThat(attempt.getTotal()).isEqualTo(3);
    assertThat(attempt.getAnswers()).extracting(ReadingAnswer::getPosition).containsExactly(0, 1, 2);
    assertThat(attempt.getScore()).isNull();
    assertThat(attempt.isOwnedBy(7L)).isTrue();
    assertThat(attempt.isOwnedBy(8L)).isFalse();
  }

  @Test
  @DisplayName("Không bắt đầu được bài chưa publish")
  void start_requiresPublished() {
    Passage draft = PassageFixtures.draft();
    PassageFixtures.tfng(draft, 1L);

    assertThatThrownBy(() -> ReadingAttempt.start(PassageFixtures.user(7L), draft, NOW))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("Gợi ý mở lần lượt và được đếm; hết gợi ý thì rỗng, không đếm thêm")
  void revealNextHint() {
    ReadingAttempt attempt = ReadingAttempt.start(PassageFixtures.user(7L), passage, NOW);

    assertThat(attempt.revealNextHint(1L)).get()
        .extracting(ReadingHint::getQuestionPhrase, ReadingHint::getPassagePhrase)
        .containsExactly("replacement", "alternative");
    assertThat(attempt.revealNextHint(1L)).isEmpty();
    assertThat(attempt.answerOf(1L).getHintsUsed()).isEqualTo(1);
    assertThat(attempt.revealNextHint(2L)).isEmpty(); // câu TFNG không có gợi ý
    assertThatThrownBy(() -> attempt.revealNextHint(99L)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Nộp: chấm từng câu, tính điểm và số câu đúng không cần gợi ý")
  void submit_gradesAll() {
    ReadingAttempt attempt = ReadingAttempt.start(PassageFixtures.user(7L), passage, NOW);
    attempt.revealNextHint(1L);

    attempt.submit(Map.of(
        1L, new AnswerInput(null, null, " Coal. "),
        2L, new AnswerInput(null, TfngAnswer.FALSE, null),
        3L, new AnswerInput(30L, null, null)), NOW);

    assertThat(attempt.getScore()).isEqualTo(2);
    assertThat(attempt.correctWithoutHints()).isEqualTo(1); // câu 1 đúng nhưng đã dùng gợi ý
    assertThat(attempt.getSubmittedAt()).isEqualTo(NOW);
    assertThat(attempt.answerOf(1L).getTextAnswer()).isEqualTo("Coal.");
    assertThat(attempt.answerOf(2L).getTfngAnswer()).isEqualTo(TfngAnswer.FALSE);
    assertThat(attempt.answerOf(3L).getSelectedOption().getContent()).isEqualTo("expensive");
  }

  @Test
  @DisplayName("Câu không gửi = bỏ trống; câu điền từ sai định dạng lưu lại lý do")
  void submit_blankAndRejected() {
    ReadingAttempt attempt = ReadingAttempt.start(PassageFixtures.user(7L), passage, NOW);

    attempt.submit(Map.of(1L, new AnswerInput(null, null, "black coal")), NOW);

    assertThat(attempt.getScore()).isZero();
    assertThat(attempt.answerOf(1L).getRejectedReason()).isEqualTo(RejectedReason.OVER_WORD_LIMIT);
    assertThat(attempt.answerOf(2L).getTfngAnswer()).isNull();
  }

  @Test
  @DisplayName("Đã nộp thì không nộp lại, không mở gợi ý; id câu lạ → lỗi dữ liệu")
  void submit_guards() {
    ReadingAttempt attempt = ReadingAttempt.start(PassageFixtures.user(7L), passage, NOW);
    assertThatThrownBy(() -> attempt.submit(Map.of(99L, AnswerInput.empty()), NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(attempt.isSubmitted()).isFalse(); // lỗi xảy ra trước khi chấm

    attempt.submit(Map.of(), NOW);
    assertThatThrownBy(() -> attempt.submit(Map.of(), NOW)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> attempt.revealNextHint(1L)).isInstanceOf(IllegalStateException.class);
  }
}
