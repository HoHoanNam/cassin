package com.cassin.quiz;

import static com.cassin.grammar.GrammarFixtures.optionId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import com.cassin.common.BusinessException;
import com.cassin.grammar.GrammarFixtures;
import com.cassin.grammar.GrammarService;
import com.cassin.grammar.GrammarTopic;
import com.cassin.grammar.Question;
import com.cassin.grammar.QuestionRepository;
import com.cassin.quiz.dto.AnswerResult;
import com.cassin.quiz.dto.OptionResponse;
import com.cassin.quiz.dto.QuizResultResponse;
import com.cassin.quiz.dto.QuizStartResponse;
import com.cassin.quiz.dto.SubmitQuizRequest;
import com.cassin.quiz.dto.SubmitQuizRequest.AnswerInput;
import com.cassin.user.User;
import com.cassin.user.UserRepository;

/**
 * Unit test cho QuizService: repository được thay bằng mock (không DB).
 * Luật của Question / QuizAttempt đã có QuestionTest và QuizAttemptTest lo; ở đây test phần
 * ĐIỀU PHỐI: có lưu không, kiểm tra quyền chưa, đổi lỗi của entity thành đúng mã HTTP chưa.
 */
@ExtendWith(MockitoExtension.class)
class QuizServiceTest {
  private static final Long USER_ID = 5L;
  private static final Long OTHER_USER_ID = 6L;
  private static final Long TOPIC_ID = 1L;
  private static final Long ATTEMPT_ID = 99L;

  @Mock QuizAttemptRepository attemptRepository;
  @Mock QuestionRepository questionRepository;
  @Mock UserRepository userRepository;
  @Mock GrammarService grammarService;

  private QuizService quizService;

  private final User user = new User("an@gmail.com", "hash", "An");
  private final GrammarTopic topic = GrammarFixtures.topic(TOPIC_ID, "Present Perfect");
  private final Question q1 = GrammarFixtures.question(topic, 1L, 0);   // đúng: đáp án 10
  private final Question q2 = GrammarFixtures.question(topic, 2L, 1);   // đúng: đáp án 21

  @BeforeEach
  void setUp() {
    // findOwnAttempt so attempt.getUser().getId() với userId → user cần có id.
    ReflectionTestUtils.setField(user, "id", USER_ID);
    // Thứ tự tham số = thứ tự field final trong QuizService (@RequiredArgsConstructor).
    quizService = new QuizService(attemptRepository, questionRepository, userRepository, grammarService);
  }

  // ===================== start =====================

  @Test
  @DisplayName("Bắt đầu: LƯU lượt làm bài và trả về attemptId (bắt bug quên save)")
  void start_savesAttempt_andReturnsId() {
    when(grammarService.findTopic(TOPIC_ID)).thenReturn(topic);
    when(questionRepository.findRandomByTopicId(TOPIC_ID, QuizAttempt.MAX_QUESTIONS)).thenReturn(List.of(q1, q2));
    when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
    // Giả lập DB: save(...) cấp id cho entity rồi trả về chính nó (giống INSERT với IDENTITY).
    when(attemptRepository.save(any(QuizAttempt.class))).thenAnswer(inv -> {
      QuizAttempt a = inv.getArgument(0);
      ReflectionTestUtils.setField(a, "id", ATTEMPT_ID);
      return a;
    });

    QuizStartResponse response = quizService.start(TOPIC_ID, USER_ID);

    assertThat(response.attemptId()).isEqualTo(ATTEMPT_ID);
    assertThat(response.total()).isEqualTo(2);
    assertThat(response.questions()).hasSize(2);
    assertThat(response.questions().get(0).options()).hasSize(4);
    verify(attemptRepository).save(any(QuizAttempt.class));
  }

  @Test
  @DisplayName("BẢO MẬT: đáp án trả về lúc làm bài chỉ có id + content, KHÔNG có field 'correct'")
  void optionResponse_neverExposesCorrectFlag() {
    // Đọc danh sách field của record bằng reflection. Ai đó lỡ thêm "boolean correct" vào
    // OptionResponse là test này hỏng ngay → không bao giờ lộ đáp án qua tab Network của trình duyệt.
    assertThat(Arrays.stream(OptionResponse.class.getRecordComponents()).map(RecordComponent::getName))
        .containsExactly("id", "content");
  }

  @Test
  @DisplayName("Chủ đề chưa có câu hỏi → 409 TOPIC_HAS_NO_QUESTIONS, không lưu gì")
  void start_topicWithoutQuestions() {
    when(grammarService.findTopic(TOPIC_ID)).thenReturn(topic);
    when(questionRepository.findRandomByTopicId(TOPIC_ID, QuizAttempt.MAX_QUESTIONS)).thenReturn(List.of());

    assertThatThrownBy(() -> quizService.start(TOPIC_ID, USER_ID))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(ex.getCode()).isEqualTo("TOPIC_HAS_NO_QUESTIONS");
        });
    verify(attemptRepository, never()).save(any());
  }

  @Test
  @DisplayName("Chủ đề không tồn tại → lỗi 404 của GrammarService đi thẳng ra, không lấy câu hỏi")
  void start_topicNotFound() {
    BusinessException notFound = new BusinessException(HttpStatus.NOT_FOUND, "TOPIC_NOT_FOUND", "Không tìm thấy chủ đề");
    when(grammarService.findTopic(TOPIC_ID)).thenThrow(notFound);

    assertThatThrownBy(() -> quizService.start(TOPIC_ID, USER_ID)).isSameAs(notFound);
    verify(questionRepository, never()).findRandomByTopicId(anyLong(), anyInt());
  }

  // ===================== submit =====================

  @Test
  @DisplayName("Nộp bài: chấm điểm, trả đáp án đúng + lời giải thích từng câu")
  void submit_gradesAndReturnsResult() {
    givenAttempt(QuizAttempt.start(user, topic, List.of(q1, q2)));
    SubmitQuizRequest request = new SubmitQuizRequest(List.of(
        new AnswerInput(1L, optionId(1, 0)),     // đúng
        new AnswerInput(2L, optionId(2, 0))));   // sai (đúng là 21)

    QuizResultResponse result = quizService.submit(ATTEMPT_ID, request, USER_ID);

    assertThat(result.score()).isEqualTo(1);
    assertThat(result.total()).isEqualTo(2);
    AnswerResult second = result.answers().get(1);
    assertThat(second.selectedOptionId()).isEqualTo(optionId(2, 0));
    assertThat(second.correctOptionId()).isEqualTo(optionId(2, 1));
    assertThat(second.correct()).isFalse();
    assertThat(second.explanation()).isEqualTo("Giải thích câu 2");
  }

  @Test
  @DisplayName("Lượt làm bài của người khác → 404 (không để lộ là lượt đó tồn tại)")
  void submit_otherUsersAttempt_isNotFound() {
    givenAttempt(QuizAttempt.start(user, topic, List.of(q1)));

    assertThatThrownBy(() -> quizService.submit(ATTEMPT_ID, emptyRequest(), OTHER_USER_ID))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
          assertThat(ex.getCode()).isEqualTo("ATTEMPT_NOT_FOUND");
        });
  }

  @Test
  @DisplayName("Lượt làm bài không tồn tại → 404")
  void submit_attemptNotFound() {
    when(attemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> quizService.submit(ATTEMPT_ID, emptyRequest(), USER_ID))
        .isInstanceOfSatisfying(BusinessException.class,
            ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test
  @DisplayName("Nộp lần 2: IllegalStateException của entity → 409 QUIZ_ALREADY_SUBMITTED")
  void submit_twice_isConflict() {
    QuizAttempt attempt = QuizAttempt.start(user, topic, List.of(q1));
    attempt.submit(Map.of());
    givenAttempt(attempt);

    assertThatThrownBy(() -> quizService.submit(ATTEMPT_ID, emptyRequest(), USER_ID))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(ex.getCode()).isEqualTo("QUIZ_ALREADY_SUBMITTED");
        });
  }

  @Test
  @DisplayName("Chọn đáp án của câu khác: IllegalArgumentException của entity → 400 INVALID_ANSWER")
  void submit_optionFromOtherQuestion_isBadRequest() {
    givenAttempt(QuizAttempt.start(user, topic, List.of(q1, q2)));
    SubmitQuizRequest request = new SubmitQuizRequest(List.of(new AnswerInput(1L, optionId(2, 1))));

    assertThatThrownBy(() -> quizService.submit(ATTEMPT_ID, request, USER_ID))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(ex.getCode()).isEqualTo("INVALID_ANSWER");
        });
  }

  @Test
  @DisplayName("Một câu hỏi được gửi 2 lần → 400 DUPLICATE_ANSWER, bài chưa bị nộp")
  void submit_duplicateQuestion_isBadRequest() {
    QuizAttempt attempt = QuizAttempt.start(user, topic, List.of(q1));
    givenAttempt(attempt);
    SubmitQuizRequest request = new SubmitQuizRequest(List.of(
        new AnswerInput(1L, optionId(1, 1)),
        new AnswerInput(1L, optionId(1, 0))));

    assertThatThrownBy(() -> quizService.submit(ATTEMPT_ID, request, USER_ID))
        .isInstanceOfSatisfying(BusinessException.class,
            ex -> assertThat(ex.getCode()).isEqualTo("DUPLICATE_ANSWER"));
    assertThat(attempt.isSubmitted()).isFalse();
  }

  @Test
  @DisplayName("Phần tử null trong danh sách câu trả lời → 400, không phải lỗi 500")
  void submit_nullEntry_isBadRequest() {
    givenAttempt(QuizAttempt.start(user, topic, List.of(q1)));
    // List.of(...) không cho chứa null → dùng Arrays.asList để giả lập JSON {"answers": [null]}.
    SubmitQuizRequest request = new SubmitQuizRequest(Arrays.asList((AnswerInput) null));

    assertThatThrownBy(() -> quizService.submit(ATTEMPT_ID, request, USER_ID))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(ex.getCode()).isEqualTo("INVALID_ANSWER");
        });
  }

  // ===================== getResult =====================

  @Test
  @DisplayName("Xem kết quả khi chưa nộp → 409 (không lộ đáp án đúng)")
  void getResult_notSubmitted_isConflict() {
    givenAttempt(QuizAttempt.start(user, topic, List.of(q1)));

    assertThatThrownBy(() -> quizService.getResult(ATTEMPT_ID, USER_ID))
        .isInstanceOfSatisfying(BusinessException.class,
            ex -> assertThat(ex.getCode()).isEqualTo("QUIZ_NOT_SUBMITTED"));
  }

  @Test
  @DisplayName("Xem kết quả sau khi nộp → trả đúng điểm")
  void getResult_submitted() {
    QuizAttempt attempt = QuizAttempt.start(user, topic, List.of(q1, q2));
    attempt.submit(Map.of(1L, optionId(1, 0), 2L, optionId(2, 1)));
    givenAttempt(attempt);

    QuizResultResponse result = quizService.getResult(ATTEMPT_ID, USER_ID);

    assertThat(result.score()).isEqualTo(2);
    assertThat(result.answers()).hasSize(2);
  }

  // ===================== history =====================

  @Test
  @DisplayName("Lịch sử: page âm → 0, size quá lớn → 50")
  void history_clampsPaging() {
    Page<QuizAttempt> empty = new PageImpl<>(List.of());
    when(attemptRepository.findByUserIdOrderByStartedAtDesc(eq(USER_ID), any(Pageable.class))).thenReturn(empty);

    quizService.history(-1, 1000, USER_ID);

    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(attemptRepository).findByUserIdOrderByStartedAtDesc(eq(USER_ID), pageable.capture());
    assertThat(pageable.getValue().getPageNumber()).isZero();
    assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
  }

  // ===================== helper =====================

  /** Giả lập lượt làm bài đã có trong DB với id ATTEMPT_ID. */
  private void givenAttempt(QuizAttempt attempt) {
    ReflectionTestUtils.setField(attempt, "id", ATTEMPT_ID);
    when(attemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
  }

  private static SubmitQuizRequest emptyRequest() {
    return new SubmitQuizRequest(List.of());
  }
}
