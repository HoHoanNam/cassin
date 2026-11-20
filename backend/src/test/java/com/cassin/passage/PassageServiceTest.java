package com.cassin.passage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.cassin.common.BusinessException;
import com.cassin.passage.dto.AnswerRequest;
import com.cassin.passage.dto.PassageSummary;
import com.cassin.passage.dto.SubmitRequest;
import com.cassin.user.UserRepository;

/** Bài mẫu: câu id 1 BLANK ("coal", 1 gợi ý) · id 2 TFNG (FALSE) · id 3 MCQ (đúng: option 31). */
@ExtendWith(MockitoExtension.class)
class PassageServiceTest {

  @Mock
  PassageRepository passageRepository;
  @Mock
  ReadingAttemptRepository attemptRepository;
  @Mock
  UserRepository userRepository;

  PassageService service;
  Passage passage;

  @BeforeEach
  void setUp() {
    service = new PassageService(passageRepository, attemptRepository, userRepository,
        new PassageMapper(), Clock.fixed(PassageFixtures.NOW, ZoneOffset.UTC));
    passage = PassageFixtures.publishedWithAllTypes();
  }

  private ReadingAttempt attemptOf(long userId) {
    ReadingAttempt attempt = ReadingAttempt.start(PassageFixtures.user(userId), passage, PassageFixtures.NOW);
    when(attemptRepository.findById(5L)).thenReturn(Optional.of(attempt));
    return attempt;
  }

  private static void assertCode(Throwable ex, String code) {
    assertThat(ex).isInstanceOf(BusinessException.class);
    assertThat(((BusinessException) ex).getCode()).isEqualTo(code);
  }

  @Test
  @DisplayName("Danh sách: điểm cao nhất lấy bằng MỘT truy vấn cho cả trang; bài chưa làm → null")
  void list_attachesBestScores() {
    when(passageRepository.findByStatus(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(passage)));
    when(attemptRepository.findBestScores(7L, List.of(1L))).thenReturn(List.of(new PassageBestScore(1L, 2)));

    List<PassageSummary> content = service.list(null, 0, 20, 7L).content();

    assertThat(content).singleElement().satisfies(s -> {
      assertThat(s.bestScore()).isEqualTo(2);
      assertThat(s.questionCount()).isEqualTo(3);
    });
  }

  @Test
  @DisplayName("Làm bài: bài chưa publish → 404; đang có lượt dở → trả lại lượt đó, không tạo mới")
  void start_rules() {
    Passage draft = PassageFixtures.draft();
    when(passageRepository.findById(2L)).thenReturn(Optional.of(draft));
    assertCode(catchThrowable(() -> service.start(2L, 7L)), "PASSAGE_NOT_FOUND");

    when(passageRepository.findById(1L)).thenReturn(Optional.of(passage));
    ReadingAttempt open = ReadingAttempt.start(PassageFixtures.user(7L), passage, PassageFixtures.NOW);
    when(attemptRepository.findFirstByUserIdAndPassageIdAndSubmittedAtIsNullOrderByStartedAtDesc(7L, 1L))
        .thenReturn(Optional.of(open));

    StartedAttempt started = service.start(1L, 7L);

    assertThat(started.created()).isFalse();
    verify(attemptRepository, never()).save(any());
  }

  @Test
  @DisplayName("Làm bài lần đầu: tạo lượt mới, đề không kèm đáp án")
  void start_createsAttempt() {
    when(passageRepository.findById(1L)).thenReturn(Optional.of(passage));
    when(attemptRepository.findFirstByUserIdAndPassageIdAndSubmittedAtIsNullOrderByStartedAtDesc(anyLong(), anyLong()))
        .thenReturn(Optional.empty());
    when(userRepository.getReferenceById(7L)).thenReturn(PassageFixtures.user(7L));

    StartedAttempt started = service.start(1L, 7L);

    assertThat(started.created()).isTrue();
    assertThat(started.attempt().questions()).hasSize(3);
    verify(attemptRepository).save(any(ReadingAttempt.class));
  }

  @Test
  @DisplayName("Lượt làm của người khác → 404 ATTEMPT_NOT_FOUND (giống như không tồn tại)")
  void otherUsersAttempt_is404() {
    attemptOf(8L);

    assertCode(catchThrowable(() -> service.getAttempt(5L, 7L)), "ATTEMPT_NOT_FOUND");
    assertCode(catchThrowable(() -> service.revealHint(5L, 1L, 7L)), "ATTEMPT_NOT_FOUND");
    assertCode(catchThrowable(() -> service.submit(5L, new SubmitRequest(List.of()), 7L)), "ATTEMPT_NOT_FOUND");
  }

  @Test
  @DisplayName("Gợi ý: lần 1 trả cặp đồng nghĩa, lần 2 → 409 NO_MORE_HINTS; câu lạ → 400")
  void revealHint_rules() {
    attemptOf(7L);

    assertThat(service.revealHint(5L, 1L, 7L).hint().passagePhrase()).isEqualTo("alternative");
    assertCode(catchThrowable(() -> service.revealHint(5L, 1L, 7L)), "NO_MORE_HINTS");
    assertCode(catchThrowable(() -> service.revealHint(5L, 99L, 7L)), "INVALID_ANSWER");
  }

  @Test
  @DisplayName("Nộp: chấm và trả kết quả; gửi trùng câu → 400; nộp lại → 409; xem kết quả khi chưa nộp → 409")
  void submit_rules() {
    attemptOf(7L);
    assertCode(catchThrowable(() -> service.result(5L, 7L)), "ATTEMPT_NOT_SUBMITTED");

    SubmitRequest duplicate = new SubmitRequest(List.of(
        new AnswerRequest(2L, null, TfngAnswer.FALSE, null), new AnswerRequest(2L, null, TfngAnswer.TRUE, null)));
    assertCode(catchThrowable(() -> service.submit(5L, duplicate, 7L)), "INVALID_ANSWER");

    SubmitRequest answers = new SubmitRequest(List.of(
        new AnswerRequest(1L, null, null, "coal"),
        new AnswerRequest(2L, null, TfngAnswer.FALSE, null),
        new AnswerRequest(3L, 31L, null, null)));
    assertThat(service.submit(5L, answers, 7L).score()).isEqualTo(3);

    assertCode(catchThrowable(() -> service.submit(5L, answers, 7L)), "ATTEMPT_SUBMITTED");
    assertCode(catchThrowable(() -> service.getAttempt(5L, 7L)), "ATTEMPT_SUBMITTED");
    assertThat(service.result(5L, 7L).correctWithoutHints()).isEqualTo(3);
  }

  @Test
  @DisplayName("Lựa chọn của câu khác → 400 INVALID_ANSWER, lượt làm vẫn chưa nộp")
  void submit_foreignOption() {
    ReadingAttempt attempt = attemptOf(7L);

    assertCode(catchThrowable(() -> service.submit(5L,
        new SubmitRequest(List.of(new AnswerRequest(3L, 99L, null, null))), 7L)), "INVALID_ANSWER");
    assertThat(attempt.isSubmitted()).isFalse();
  }

  private static Throwable catchThrowable(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
    return org.assertj.core.api.Assertions.catchThrowable(call);
  }
}
