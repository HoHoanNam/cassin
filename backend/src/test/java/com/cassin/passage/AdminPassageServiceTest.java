package com.cassin.passage;

import static com.cassin.passage.PassageFixtures.EVIDENCE_1;
import static com.cassin.passage.PassageFixtures.EVIDENCE_2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cassin.common.BusinessException;
import com.cassin.grammar.CefrLevel;
import com.cassin.passage.dto.AdminPassageResponse;
import com.cassin.passage.dto.BlankRequest;
import com.cassin.passage.dto.HintRequest;
import com.cassin.passage.dto.PassageRequest;
import com.cassin.passage.dto.QuestionRequest;
import com.cassin.passage.dto.TfngRequest;
import com.cassin.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class AdminPassageServiceTest {

  @Mock
  PassageRepository passageRepository;
  @Mock
  ReadingAttemptRepository attemptRepository;
  @Mock
  UserRepository userRepository;

  AdminPassageService service;

  @BeforeEach
  void setUp() {
    service = new AdminPassageService(passageRepository, attemptRepository, userRepository,
        new PassageMapper(), Clock.fixed(PassageFixtures.NOW, ZoneOffset.UTC));
  }

  private static PassageRequest request(QuestionRequest... questions) {
    return new PassageRequest("Solar power", PassageFixtures.TEXT, CefrLevel.B1, List.of(questions));
  }

  private static TfngRequest tfng() {
    return new TfngRequest("Most homes used solar panels.", EVIDENCE_2, "Chỉ 15%", List.of(), TfngAnswer.FALSE);
  }

  @Test
  @DisplayName("Tạo bài: bài nháp kèm câu hỏi đúng thứ tự, lưu một lần (cascade)")
  void create_savesDraftWithQuestions() {
    BlankRequest blank = new BlankRequest("A cheap replacement for ____.", EVIDENCE_1, "x",
        List.of(new HintRequest("replacement", "alternative")), AnswerKind.WORDS, 1, List.of("coal"));

    AdminPassageResponse response = service.create(request(tfng(), blank), 9L);

    assertThat(response.status()).isEqualTo(PassageStatus.DRAFT);
    assertThat(response.questions()).extracting(q -> q.position()).containsExactly(0, 1);
    assertThat(response.locked()).isFalse();
    verify(passageRepository).saveAndFlush(any(Passage.class));
  }

  @Test
  @DisplayName("Dữ liệu sai luật entity → 400 INVALID_PASSAGE kèm số câu; không lưu gì")
  void create_invalidQuestion() {
    BlankRequest noBlank = new BlankRequest("No blank here.", EVIDENCE_1, "x", null, AnswerKind.WORDS, 1, List.of("coal"));

    assertThatThrownBy(() -> service.create(request(tfng(), noBlank), 9L))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getCode()).isEqualTo("INVALID_PASSAGE");
          assertThat(ex.getMessage()).startsWith("Câu 2:");
        });
    verify(passageRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("Sửa: xóa câu cũ → flush → thêm câu mới (tránh đụng UNIQUE position)")
  void update_flushesBetweenDeleteAndInsert() {
    Passage passage = PassageFixtures.draft();
    PassageFixtures.tfng(passage, 1L);
    when(passageRepository.findById(1L)).thenReturn(Optional.of(passage));

    AdminPassageResponse response = service.update(1L, request(tfng(), tfng()));

    assertThat(response.questions()).hasSize(2);
    // Kiểm tra lock TRƯỚC khi đụng vào dữ liệu, rồi flush 2 lần: sau khi xóa câu cũ và sau khi thêm câu mới.
    InOrder order = inOrder(attemptRepository, passageRepository);
    order.verify(attemptRepository).existsByPassageId(1L);
    order.verify(passageRepository, times(2)).flush();
  }

  @Test
  @DisplayName("Bài đã có người làm → sửa / xóa đều 409 PASSAGE_LOCKED")
  void locked_whenAttemptsExist() {
    Passage passage = PassageFixtures.publishedWithAllTypes();
    when(passageRepository.findById(1L)).thenReturn(Optional.of(passage));
    when(attemptRepository.existsByPassageId(1L)).thenReturn(true);

    assertThatThrownBy(() -> service.update(1L, request(tfng())))
        .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo("PASSAGE_LOCKED"));
    assertThatThrownBy(() -> service.delete(1L))
        .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo("PASSAGE_LOCKED"));
    verify(passageRepository, never()).delete(any());
  }

  @Test
  @DisplayName("Bài đang publish bị sửa thành 0 câu → 400; sửa bài đã lưu trữ → 409 PASSAGE_STATUS")
  void update_statusRules() {
    Passage published = PassageFixtures.publishedWithAllTypes();
    when(passageRepository.findById(1L)).thenReturn(Optional.of(published));
    assertThatThrownBy(() -> service.update(1L, request()))
        .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo("INVALID_PASSAGE"));

    Passage archived = PassageFixtures.publishedWithAllTypes();
    archived.archive(PassageFixtures.NOW);
    when(passageRepository.findById(2L)).thenReturn(Optional.of(archived));
    assertThatThrownBy(() -> service.update(2L, request(tfng())))
        .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo("PASSAGE_STATUS"));
  }

  @Test
  @DisplayName("Publish bài rỗng → 400; publish hai lần → 409; bài không tồn tại → 404")
  void publish_rules() {
    when(passageRepository.findById(1L)).thenReturn(Optional.of(PassageFixtures.draft()));
    assertThatThrownBy(() -> service.publish(1L))
        .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo("INVALID_PASSAGE"));

    when(passageRepository.findById(2L)).thenReturn(Optional.of(PassageFixtures.publishedWithAllTypes()));
    assertThatThrownBy(() -> service.publish(2L))
        .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo("PASSAGE_STATUS"));

    when(passageRepository.findById(3L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.publish(3L))
        .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo("PASSAGE_NOT_FOUND"));
  }
}
