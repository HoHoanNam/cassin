package com.cassin.grammar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import com.cassin.common.BusinessException;
import com.cassin.grammar.dto.AdminQuestionResponse;
import com.cassin.grammar.dto.OptionRequest;
import com.cassin.grammar.dto.QuestionRequest;
import com.cassin.grammar.dto.TopicDetailResponse;
import com.cassin.grammar.dto.TopicRequest;
import com.cassin.quiz.QuizAttemptRepository;

/**
 * [THÊM] Unit test cho AdminGrammarService.
 *
 * Trọng tâm:
 *   - Luật của entity (đúng 1 đáp án đúng...) phải ra 400 với ĐÚNG mã lỗi, không phải 500.
 *   - Chủ đề / câu hỏi đã có người làm thì không xóa được (409).
 *   - Chủ đề không tồn tại → 404, kể cả khi chỉ LIỆT KÊ câu hỏi.
 *
 * GrammarService cũng được mock: test này không kiểm tra lại findTopic (GrammarServiceTest đã lo),
 * chỉ cần "findTopic trả topic" hoặc "findTopic ném 404".
 */
@ExtendWith(MockitoExtension.class)
class AdminGrammarServiceTest {
  private static final Long TOPIC_ID = 1L;
  private static final Long QUESTION_ID = 2L;

  @Mock GrammarTopicRepository topicRepository;
  @Mock QuestionRepository questionRepository;
  @Mock GrammarService grammarService;
  @Mock QuizAttemptRepository attemptRepository;

  private AdminGrammarService service;

  private final GrammarTopic topic = GrammarFixtures.topic(TOPIC_ID, "Present Perfect");

  @BeforeEach
  void setUp() {
    // Thứ tự = thứ tự field final trong AdminGrammarService.
    service = new AdminGrammarService(topicRepository, questionRepository, grammarService, attemptRepository);
  }

  // ===================== topic =====================

  @Test
  @DisplayName("Tạo chủ đề: tiêu đề toàn dấu cách → 400 INVALID_TOPIC (không phải INVALID_QUESTION), không lưu")
  void createTopic_blankTitle_isInvalidTopic() {
    // @NotBlank ở DTO sẽ chặn trước khi tới đây; test này đảm bảo nếu lọt qua thì entity vẫn chặn
    // và guard đổi lỗi thành 400 với đúng mã.
    TopicRequest request = new TopicRequest("   ", CefrLevel.B1, "Giải thích");

    assertBadRequest(() -> service.createTopic(request), "INVALID_TOPIC");
    verify(topicRepository, never()).save(any());
  }

  @Test
  @DisplayName("Sửa chủ đề: đổi nội dung, đếm số câu hỏi thật, updatedAt mới")
  void updateTopic_updatesAndCounts() {
    Instant longAgo = Instant.parse("2026-01-01T00:00:00Z");
    ReflectionTestUtils.setField(topic, "updatedAt", longAgo);
    when(grammarService.findTopic(TOPIC_ID)).thenReturn(topic);
    when(questionRepository.countByTopicId(TOPIC_ID)).thenReturn(12L);

    TopicDetailResponse response = service.updateTopic(TOPIC_ID,
        new TopicRequest("Present Perfect Continuous", CefrLevel.B2, "Bài mới"));

    assertThat(response.title()).isEqualTo("Present Perfect Continuous");
    assertThat(response.questionCount()).isEqualTo(12);
    // Bắt bug: updatedAt chỉ đổi ở @PreUpdate (lúc flush) → response mang giá trị cũ.
    assertThat(topic.getUpdatedAt()).isAfter(longAgo);
  }

  @Test
  @DisplayName("Xóa chủ đề đã có người làm quiz: 409 TOPIC_IN_USE, không xóa")
  void deleteTopic_inUse_isConflict() {
    when(grammarService.findTopic(TOPIC_ID)).thenReturn(topic);
    when(attemptRepository.existsByTopicId(TOPIC_ID)).thenReturn(true);

    assertConflict(() -> service.deleteTopic(TOPIC_ID), "TOPIC_IN_USE");
    verify(topicRepository, never()).delete(any());
  }

  @Test
  @DisplayName("Xóa chủ đề chưa ai làm: xóa được")
  void deleteTopic_unused_isDeleted() {
    when(grammarService.findTopic(TOPIC_ID)).thenReturn(topic);
    when(attemptRepository.existsByTopicId(TOPIC_ID)).thenReturn(false);

    service.deleteTopic(TOPIC_ID);

    verify(topicRepository).delete(topic);
  }

  @Test
  @DisplayName("Xóa chủ đề không tồn tại: 404, không hỏi tới lượt làm bài")
  void deleteTopic_missing_isNotFound() {
    when(grammarService.findTopic(TOPIC_ID)).thenThrow(topicNotFound());

    assertThatThrownBy(() -> service.deleteTopic(TOPIC_ID)).isInstanceOf(BusinessException.class);
    verifyNoInteractions(attemptRepository);
  }

  // ===================== listQuestions =====================

  @Test
  @DisplayName("Liệt kê câu hỏi của chủ đề KHÔNG tồn tại: 404, không trả [] (bug cũ)")
  void listQuestions_missingTopic_isNotFound() {
    when(grammarService.findTopic(TOPIC_ID)).thenThrow(topicNotFound());

    assertThatThrownBy(() -> service.listQuestions(TOPIC_ID))
        .isInstanceOfSatisfying(BusinessException.class,
            ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    verifyNoInteractions(questionRepository);
  }

  @Test
  @DisplayName("Liệt kê câu hỏi: đúng thứ tự repository trả, mỗi câu đủ 4 đáp án")
  void listQuestions_mapsAll() {
    when(grammarService.findTopic(TOPIC_ID)).thenReturn(topic);
    when(questionRepository.findByTopicIdOrderById(TOPIC_ID)).thenReturn(List.of(
        GrammarFixtures.question(topic, 1L, 0),
        GrammarFixtures.question(topic, 2L, 3)));

    List<AdminQuestionResponse> questions = service.listQuestions(TOPIC_ID);

    // [SỬA] q.id() → q.questionId(): record của bạn đặt tên field là questionId (không phải id),
    // nên accessor record tự sinh là questionId(). Gọi q.id() → lỗi biên dịch, kéo theo lỗi
    // "Cannot infer type argument(s)" ở extracting (Java không suy được kiểu từ method không tồn tại).
    assertThat(questions).extracting(q -> q.questionId()).containsExactly(1L, 2L);
    assertThat(questions).allSatisfy(q -> assertThat(q.options()).hasSize(Question.OPTION_COUNT));
  }

  // ===================== addQuestion =====================

  @Test
  @DisplayName("Thêm câu hỏi hợp lệ: lưu, trả về đủ 4 đáp án")
  void addQuestion_valid_isSaved() {
    when(grammarService.findTopic(TOPIC_ID)).thenReturn(topic);
    when(questionRepository.save(any(Question.class))).thenAnswer(inv -> inv.getArgument(0));

    AdminQuestionResponse response = service.addQuestion(TOPIC_ID, questionRequest(true, false, false, false));

    assertThat(response.content()).isEqualTo("She ___ here since 2020.");
    assertThat(response.options()).hasSize(Question.OPTION_COUNT);
    verify(questionRepository).save(any(Question.class));
  }

  @Test
  @DisplayName("Thêm câu hỏi có 2 đáp án đúng: 400 INVALID_QUESTION kèm câu báo của entity, không lưu")
  void addQuestion_twoCorrect_isBadRequest() {
    when(grammarService.findTopic(TOPIC_ID)).thenReturn(topic);

    assertThatThrownBy(() -> service.addQuestion(TOPIC_ID, questionRequest(true, true, false, false)))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(ex.getCode()).isEqualTo("INVALID_QUESTION");
          assertThat(ex.getMessage()).contains("đúng 1 đáp án đúng");
        });
    verify(questionRepository, never()).save(any());
  }

  // ===================== updateQuestion =====================

  @Test
  @DisplayName("Sửa câu hỏi: đổi đáp án đúng nhưng GIỮ NGUYÊN id 4 đáp án (bài làm cũ vẫn hợp lệ)")
  void updateQuestion_keepsOptionIds() {
    Question question = GrammarFixtures.question(topic, QUESTION_ID, 0);   // đáp án id 20..23, đúng = 20
    when(questionRepository.findById(QUESTION_ID)).thenReturn(Optional.of(question));

    AdminQuestionResponse response = service.updateQuestion(QUESTION_ID, questionRequest(false, false, true, false));

    // [THÊM] Kiểm tra cả RESPONSE: màn admin dùng optionId để hiển thị, phải đúng id cũ.
    assertThat(response.questionId()).isEqualTo(QUESTION_ID);
    assertThat(response.options())
        .extracting(o -> o.optionId(), o -> o.correct())
        .containsExactly(
            tuple(20L, false),
            tuple(21L, false),
            tuple(22L, true),
            tuple(23L, false));
    assertThat(question.getOptions())
        .extracting(o -> o.getId())
        .containsExactly(20L, 21L, 22L, 23L);
    assertThat(question.isCorrectAnswer(22L)).isTrue();
    assertThat(question.isCorrectAnswer(20L)).isFalse();
  }

  @Test
  @DisplayName("Sửa câu hỏi với đáp án trùng nhau: 400 INVALID_QUESTION, câu hỏi giữ nguyên")
  void updateQuestion_duplicateOptions_isBadRequest() {
    Question question = GrammarFixtures.question(topic, QUESTION_ID, 0);
    when(questionRepository.findById(QUESTION_ID)).thenReturn(Optional.of(question));
    QuestionRequest duplicated = new QuestionRequest("Đề mới", "Giải thích mới", List.of(
        new OptionRequest("has lived", true),
        new OptionRequest("Has Lived", false),     // trùng với đáp án đầu (không phân biệt hoa thường)
        new OptionRequest("lived", false),
        new OptionRequest("lives", false)));

    assertBadRequest(() -> service.updateQuestion(QUESTION_ID, duplicated), "INVALID_QUESTION");
    // validateOptions chạy TRƯỚC khi đổi gì → đề bài cũ còn nguyên.
    assertThat(question.getContent()).isEqualTo("Câu hỏi " + QUESTION_ID);
  }

  // ===================== deleteQuestion =====================

  @Test
  @DisplayName("Xóa câu hỏi đã có người trả lời: 409 QUESTION_IN_USE, không xóa")
  void deleteQuestion_answered_isConflict() {
    when(questionRepository.findById(QUESTION_ID))
        .thenReturn(Optional.of(GrammarFixtures.question(topic, QUESTION_ID, 0)));
    when(questionRepository.isAnswered(QUESTION_ID)).thenReturn(true);

    assertConflict(() -> service.deleteQuestion(QUESTION_ID), "QUESTION_IN_USE");
    verify(questionRepository, never()).delete(any());
  }

  @Test
  @DisplayName("Xóa câu hỏi không tồn tại: 404 QUESTION_NOT_FOUND")
  void deleteQuestion_missing_isNotFound() {
    when(questionRepository.findById(QUESTION_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.deleteQuestion(QUESTION_ID))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
          assertThat(ex.getCode()).isEqualTo("QUESTION_NOT_FOUND");
        });
  }

  // ===================== helper =====================

  /** 4 đáp án khác nhau; correct[i] = true nghĩa là đáp án thứ i đúng. */
  private static QuestionRequest questionRequest(boolean... correct) {
    String[] contents = {"has lived", "lived", "is living", "lives"};
    List<OptionRequest> options = new ArrayList<>();
    for (int i = 0; i < contents.length; i++) {
      options.add(new OptionRequest(contents[i], correct[i]));
    }
    return new QuestionRequest("She ___ here since 2020.", "Present perfect + since", options);
  }

  private static BusinessException topicNotFound() {
    return new BusinessException(HttpStatus.NOT_FOUND, "TOPIC_NOT_FOUND", "Không tìm thấy chủ đề");
  }

  private static void assertBadRequest(Runnable action, String code) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(ex.getCode()).isEqualTo(code);
        });
  }

  private static void assertConflict(Runnable action, String code) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(ex.getCode()).isEqualTo(code);
        });
  }
}
