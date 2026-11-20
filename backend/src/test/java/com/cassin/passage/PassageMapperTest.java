package com.cassin.passage;

import static com.cassin.passage.PassageFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cassin.passage.dto.*;

/** Điều quan trọng nhất của mapper: lúc ĐANG làm bài không lộ đáp án; sau khi nộp thì đủ mọi thứ. */
class PassageMapperTest {

  private final PassageMapper mapper = new PassageMapper();
  private final Passage passage = PassageFixtures.publishedWithAllTypes();

  @Test
  @DisplayName("Đề đang làm: không có đáp án, chỉ có gợi ý đã mở; MCQ có A–D, BLANK có dòng hướng dẫn")
  void toAttempt_hidesAnswers() {
    ReadingAttempt attempt = ReadingAttempt.start(PassageFixtures.user(7L), passage, NOW);
    AttemptResponse before = mapper.toAttempt(attempt);
    assertThat(before.questions().get(0).revealedHints()).isEmpty();
    assertThat(before.questions().get(0).hintsRemaining()).isEqualTo(1);

    attempt.revealNextHint(1L);
    AttemptResponse after = mapper.toAttempt(attempt);

    QuestionView blank = after.questions().get(0);
    assertThat(blank.instruction()).isEqualTo("ONE WORD ONLY");
    assertThat(blank.revealedHints()).containsExactly(new HintView("replacement", "alternative"));
    assertThat(blank.hintsRemaining()).isZero();

    QuestionView mcq = after.questions().get(2);
    assertThat(mcq.options()).extracting(OptionView::label).containsExactly('A', 'B', 'C', 'D');
    // OptionView không có field correct → kiểm bằng danh sách component của record
    assertThat(OptionView.class.getRecordComponents()).extracting(c -> c.getName())
        .containsExactly("id", "label", "content");
    assertThat(QuestionView.class.getRecordComponents()).extracting(c -> c.getName())
        .doesNotContain("evidence", "explanation", "acceptedAnswers", "answer");
  }

  @Test
  @DisplayName("Kết quả: đủ câu trả lời của học viên, đáp án đúng, bằng chứng và mọi gợi ý")
  void toResult_showsEverything() {
    ReadingAttempt attempt = ReadingAttempt.start(PassageFixtures.user(7L), passage, NOW);
    attempt.submit(Map.of(1L, new AnswerInput(null, null, "oil"), 3L, new AnswerInput(31L, null, null)), NOW);

    AttemptResultResponse result = mapper.toResult(attempt);

    assertThat(result.score()).isEqualTo(1);
    QuestionResult blank = result.questions().get(0);
    assertThat(blank.yourText()).isEqualTo("oil");
    assertThat(blank.acceptedAnswers()).containsExactly("coal");
    assertThat(blank.hints()).hasSize(1);
    assertThat(blank.evidence()).isEqualTo(PassageFixtures.EVIDENCE_1);
    QuestionResult tfng = result.questions().get(1);
    assertThat(tfng.yourTfngAnswer()).isNull();
    assertThat(tfng.correctTfngAnswer()).isEqualTo(TfngAnswer.FALSE);
    QuestionResult mcq = result.questions().get(2);
    assertThat(mcq.selectedOptionId()).isEqualTo(31L);
    assertThat(mcq.correct()).isTrue();
  }

  @Test
  @DisplayName("DTO → entity: mỗi record con của QuestionRequest tạo đúng lớp con của ReadingQuestion")
  void toNewQuestion_eachType() {
    Passage draft = PassageFixtures.draft();
    List<OptionRequest> options = List.of(new OptionRequest("expensive", false), new OptionRequest("cheap", true),
        new OptionRequest("dangerous", false), new OptionRequest("old", false));

    ReadingQuestion mcq = mapper.toNewQuestion(draft, 0,
        new McqRequest("How is it?", PassageFixtures.EVIDENCE_1, "x", null, options), NOW);
    ReadingQuestion tfng = mapper.toNewQuestion(draft, 1,
        new TfngRequest("Most homes.", PassageFixtures.EVIDENCE_2, "x", List.of(), TfngAnswer.FALSE), NOW);
    ReadingQuestion blank = mapper.toNewQuestion(draft, 2,
        new BlankRequest("A cheap replacement for ____.", PassageFixtures.EVIDENCE_1, "x",
            List.of(new HintRequest("replacement", "alternative")), AnswerKind.WORDS, 1, List.of("coal")),
        NOW);

    assertThat(mcq).isInstanceOf(McqQuestion.class);
    assertThat(tfng).isInstanceOf(TfngQuestion.class);
    assertThat(blank).isInstanceOf(BlankQuestion.class);
    assertThat(blank.getHints()).hasSize(1);
  }

  @Test
  @DisplayName("Admin: đủ đáp án từng dạng, kèm cờ locked")
  void toAdmin() {
    AdminPassageResponse response = mapper.toAdmin(passage, true);

    assertThat(response.locked()).isTrue();
    assertThat(response.questions()).extracting(AdminQuestionResponse::type)
        .containsExactly(QuestionType.BLANK, QuestionType.TFNG, QuestionType.MCQ);
    assertThat(response.questions().get(0).acceptedAnswers()).containsExactly("coal");
    assertThat(response.questions().get(1).answer()).isEqualTo(TfngAnswer.FALSE);
    assertThat(response.questions().get(2).options()).filteredOn(OptionResult::correct)
        .extracting(OptionResult::content).containsExactly("cheap");
  }
}
