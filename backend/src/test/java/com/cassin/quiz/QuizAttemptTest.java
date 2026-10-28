package com.cassin.quiz;

import static com.cassin.grammar.GrammarFixtures.optionId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cassin.grammar.GrammarFixtures;
import com.cassin.grammar.GrammarTopic;
import com.cassin.grammar.Question;
import com.cassin.user.User;

/**
 * Unit test cho vòng đời của một tờ bài làm: start (phát tờ, chốt đề) → submit (thu bài, chấm điểm).
 */
class QuizAttemptTest {

  private final User user = new User("an@gmail.com", "hash", "An");
  private final GrammarTopic topic = GrammarFixtures.topic(1L, "Present Perfect");

  // 3 câu hỏi; đáp án đúng lần lượt ở vị trí 0, 1, 2
  private final Question q1 = GrammarFixtures.question(topic, 1L, 0);
  private final Question q2 = GrammarFixtures.question(topic, 2L, 1);
  private final Question q3 = GrammarFixtures.question(topic, 3L, 2);

  // ===================== start =====================

  @Test
  @DisplayName("Bắt đầu: mỗi câu một dòng trống đúng thứ tự, chưa có điểm, chưa nộp")
  void start_createsBlankAnswerPerQuestion() {
    QuizAttempt attempt = QuizAttempt.start(user, topic, List.of(q1, q2, q3));

    assertThat(attempt.getTotal()).isEqualTo(3);
    assertThat(attempt.getScore()).isNull();
    assertThat(attempt.isSubmitted()).isFalse();
    assertThat(attempt.getStartedAt()).isNotNull();
    assertThat(attempt.getAnswers())
        .extracting(a -> a.getPosition(), a -> a.getQuestion(), a -> a.getSelectedOption(), a -> a.isCorrect())
        .containsExactly(
            tuple(0, q1, null, false),
            tuple(1, q2, null, false),
            tuple(2, q3, null, false));
    // Mỗi dòng biết nó thuộc tờ bài làm nào (quan hệ 2 chiều được nối đúng).
    assertThat(attempt.getAnswers()).allSatisfy(a -> assertThat(a.getAttempt()).isSameAs(attempt));
  }

  @Test
  @DisplayName("Không có câu hỏi hoặc quá 10 câu → bị chặn")
  void start_rejectsEmptyOrTooManyQuestions() {
    List<Question> eleven = new ArrayList<>();
    for (long id = 1; id <= QuizAttempt.MAX_QUESTIONS + 1; id++) {
      eleven.add(GrammarFixtures.question(topic, id, 0));
    }

    assertThatThrownBy(() -> QuizAttempt.start(user, topic, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> QuizAttempt.start(user, topic, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> QuizAttempt.start(user, topic, eleven))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tối đa");
  }

  @Test
  @DisplayName("Câu hỏi của chủ đề khác lọt vào đề → bị chặn")
  void start_rejectsQuestionFromOtherTopic() {
    GrammarTopic otherTopic = GrammarFixtures.topic(2L, "Past Simple");
    Question foreign = GrammarFixtures.question(otherTopic, 9L, 0);

    assertThatThrownBy(() -> QuizAttempt.start(user, topic, List.of(q1, foreign)))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("không thuộc chủ đề");
  }

  @Test
  @DisplayName("Một câu hỏi xuất hiện 2 lần trong đề → bị chặn")
  void start_rejectsDuplicateQuestion() {
    assertThatThrownBy(() -> QuizAttempt.start(user, topic, List.of(q1, q2, q1)))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("bị lặp");
  }

  @Test
  @DisplayName("User hoặc chủ đề null → bị chặn")
  void start_rejectsNullUserOrTopic() {
    assertThatThrownBy(() -> QuizAttempt.start(null, topic, List.of(q1)))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> QuizAttempt.start(user, null, List.of(q1)))
        .isInstanceOf(NullPointerException.class);
  }

  // ===================== submit =====================

  @Test
  @DisplayName("Nộp bài: chấm đúng / sai / bỏ trống, điểm = số câu đúng")
  void submit_gradesCorrectWrongAndBlank() {
    QuizAttempt attempt = QuizAttempt.start(user, topic, List.of(q1, q2, q3));
    Map<Long, Long> selections = new HashMap<>();
    selections.put(1L, optionId(1, 0));   // câu 1: chọn đúng
    selections.put(2L, optionId(2, 3));   // câu 2: chọn sai (đúng là vị trí 1)
    // câu 3: không gửi → bỏ trống

    attempt.submit(selections);

    assertThat(attempt.getScore()).isEqualTo(1);
    assertThat(attempt.isSubmitted()).isTrue();
    assertThat(attempt.getSubmittedAt()).isNotNull();
    assertThat(attempt.getAnswers())
        .extracting(a -> a.getSelectedOption() == null ? null : a.getSelectedOption().getId(), a -> a.isCorrect())
        .containsExactly(
            tuple(optionId(1, 0), true),
            tuple(optionId(2, 3), false),
            tuple(null, false));
  }

  @Test
  @DisplayName("Gửi optionId = null (bỏ trống có chủ ý) hoặc selections = null → tính sai, không lỗi")
  void submit_nullSelections_countAsBlank() {
    QuizAttempt attempt = QuizAttempt.start(user, topic, List.of(q1, q2));
    Map<Long, Long> selections = new HashMap<>();
    selections.put(1L, null);             // HashMap cho phép value null (Map.of thì không)

    attempt.submit(selections);
    assertThat(attempt.getScore()).isZero();

    QuizAttempt attempt2 = QuizAttempt.start(user, topic, List.of(q1, q2));
    attempt2.submit(null);
    assertThat(attempt2.getScore()).isZero();
    assertThat(attempt2.isSubmitted()).isTrue();
  }

  @Test
  @DisplayName("Nộp 2 lần → bị chặn, điểm lần đầu giữ nguyên")
  void submit_twice_isRejected() {
    QuizAttempt attempt = QuizAttempt.start(user, topic, List.of(q1));
    attempt.submit(Map.of(1L, optionId(1, 0)));

    assertThatThrownBy(() -> attempt.submit(Map.of(1L, optionId(1, 1))))
        .isInstanceOf(IllegalStateException.class).hasMessage("Bài này đã được nộp");
    assertThat(attempt.getScore()).isEqualTo(1);
  }

  @Test
  @DisplayName("Gửi câu hỏi KHÔNG có trong đề (tự chọn câu dễ) → bị chặn, bài chưa bị nộp")
  void submit_questionNotInAttempt_isRejected() {
    QuizAttempt attempt = QuizAttempt.start(user, topic, List.of(q1, q2));

    assertThatThrownBy(() -> attempt.submit(Map.of(99L, optionId(99, 0))))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("không có trong đề");
    // Kiểm tra nằm TRƯỚC vòng chấm điểm → chưa có gì bị thay đổi.
    assertThat(attempt.isSubmitted()).isFalse();
    assertThat(attempt.getScore()).isNull();
  }

  @Test
  @DisplayName("Chọn đáp án của câu khác cho câu này → bị chặn, bài chưa bị nộp")
  void submit_optionFromOtherQuestion_isRejected() {
    QuizAttempt attempt = QuizAttempt.start(user, topic, List.of(q1, q2));

    // Câu 1 nhưng gửi id đáp án ĐÚNG của câu 2
    assertThatThrownBy(() -> attempt.submit(Map.of(1L, optionId(2, 1))))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("không thuộc câu hỏi");
    // Lỗi xảy ra giữa vòng chấm, nên vài dòng trong RAM có thể đã được chấm dở.
    // Không sao: service đổi lỗi này thành BusinessException → @Transactional rollback,
    // DB không lưu gì. Điều quan trọng là bài KHÔNG bị đánh dấu đã nộp.
    assertThat(attempt.isSubmitted()).isFalse();
  }

  @Test
  @DisplayName("getAnswers() chỉ đọc: bên ngoài không tự thêm/xóa dòng được")
  void getAnswers_isReadOnly() {
    QuizAttempt attempt = QuizAttempt.start(user, topic, List.of(q1));

    assertThatThrownBy(() -> attempt.getAnswers().clear()).isInstanceOf(UnsupportedOperationException.class);
  }
}
