package com.cassin.passage;

import java.time.Instant;
import java.util.List;

import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;

import com.cassin.passage.dto.*;

/**
 * Chỗ DUY NHẤT đổi qua lại giữa entity của module bài đọc và DTO.
 *   DTO → entity : toNewQuestion (QuestionRequest sealed → switch không cần default)
 *   entity → DTO : các method to...
 *
 * Mapper đọc quan hệ LAZY (questions, options, hints...) → phải gọi BÊN TRONG transaction của service.
 */
@Component
public class PassageMapper {

  // ─────────────── DTO → entity ───────────────

  public ReadingQuestion toNewQuestion(Passage passage, int position, QuestionRequest request, Instant now) {
    QuestionContent content = request.toContent();
    return switch (request) {
      case McqRequest m -> McqQuestion.create(passage, position, content, m.toChoices(), now);
      case TfngRequest t -> TfngQuestion.create(passage, position, content, t.answer(), now);
      case BlankRequest b -> BlankQuestion.create(passage, position, content,
          b.answerKind(), b.maxWords(), b.acceptedAnswers(), now);
    };
  }

  // ─────────────── học viên ───────────────

  public PassageSummary toSummary(Passage p, Integer bestScore) {
    return new PassageSummary(p.getId(), p.getTitle(), p.getLevel(), p.getWordCount(), p.readingMinutes(),
        p.getQuestions().size(), bestScore, p.getPublishedAt());
  }

  public PassageView toView(Passage p) {
    return new PassageView(p.getId(), p.getTitle(), p.getContent(), p.getLevel(), p.getWordCount(), p.readingMinutes());
  }

  /** Đề để làm bài: không lộ đáp án, chỉ kèm những gợi ý học viên ĐÃ mở. */
  public AttemptResponse toAttempt(ReadingAttempt attempt) {
    List<QuestionView> questions = attempt.getAnswers().stream().map(this::toQuestionView).toList();
    return new AttemptResponse(attempt.getId(), toView(attempt.getPassage()), questions, attempt.getStartedAt());
  }

  public HintResponse toHint(ReadingAnswer answer, ReadingHint hint) {
    int remaining = answer.getQuestion().getHints().size() - answer.getHintsUsed();
    return new HintResponse(answer.getQuestion().getId(), toHintView(hint), remaining);
  }

  public AttemptResultResponse toResult(ReadingAttempt attempt) {
    List<QuestionResult> questions = attempt.getAnswers().stream().map(this::toQuestionResult).toList();
    return new AttemptResultResponse(attempt.getId(), toView(attempt.getPassage()), attempt.getScore(),
        attempt.getTotal(), attempt.correctWithoutHints(), attempt.getStartedAt(), attempt.getSubmittedAt(), questions);
  }

  private QuestionView toQuestionView(ReadingAnswer answer) {
    ReadingQuestion q = unproxy(answer.getQuestion());
    List<HintView> revealed = q.getHints().stream().limit(answer.getHintsUsed()).map(this::toHintView).toList();
    int remaining = q.getHints().size() - answer.getHintsUsed();

    List<OptionView> options = null;
    String instruction = null;
    switch (q) {
      case McqQuestion m -> options = m.getOptions().stream()
          .map(o -> new OptionView(o.getId(), o.label(), o.getContent()))
          .toList();
      case BlankQuestion b -> instruction = b.instruction();
      default -> { } // TFNG: FE tự hiện 3 nút True / False / Not Given
    }
    return new QuestionView(q.getId(), q.getPosition(), q.type(), q.getPrompt(), instruction, options, revealed, remaining);
  }

  private QuestionResult toQuestionResult(ReadingAnswer answer) {
    ReadingQuestion q = unproxy(answer.getQuestion());
    List<OptionResult> options = null;
    Long selectedOptionId = null;
    TfngAnswer yourTfng = null;
    TfngAnswer correctTfng = null;
    String yourText = null;
    List<String> accepted = null;
    String instruction = null;
    switch (q) {
      case McqQuestion m -> {
        options = toOptionResults(m);
        // getId() trên proxy LAZY không làm Hibernate truy vấn thêm.
        selectedOptionId = answer.getSelectedOption() == null ? null : answer.getSelectedOption().getId();
      }
      case TfngQuestion t -> {
        yourTfng = answer.getTfngAnswer();
        correctTfng = t.getAnswer();
      }
      case BlankQuestion b -> {
        yourText = answer.getTextAnswer();
        accepted = List.copyOf(b.getAcceptedAnswers()); // copy: xem ghi chú ở toAdminQuestion
        instruction = b.instruction();
      }
      default -> throw new IllegalStateException("Dạng câu hỏi chưa được hỗ trợ: " + q.type());
    }
    return new QuestionResult(q.getId(), q.getPosition(), q.type(), q.getPrompt(), instruction,
        options, selectedOptionId, yourTfng, correctTfng, yourText, accepted, answer.getRejectedReason(),
        answer.isCorrect(), answer.getHintsUsed(), q.getHints().stream().map(this::toHintView).toList(),
        q.getEvidence(), q.getExplanation());
  }

  // ─────────────── admin ───────────────

  public AdminPassageSummary toAdminSummary(Passage p) {
    return new AdminPassageSummary(p.getId(), p.getTitle(), p.getLevel(), p.getStatus(), p.getWordCount(),
        p.getQuestions().size(), p.getUpdatedAt(), p.getPublishedAt());
  }

  /** @param locked service tính: bài đã có lượt làm hay chưa */
  public AdminPassageResponse toAdmin(Passage p, boolean locked) {
    List<AdminQuestionResponse> questions = p.getQuestions().stream().map(this::toAdminQuestion).toList();
    return new AdminPassageResponse(p.getId(), p.getTitle(), p.getContent(), p.getLevel(), p.getStatus(),
        p.getWordCount(), locked, p.getCreatedAt(), p.getUpdatedAt(), p.getPublishedAt(), questions);
  }

  private AdminQuestionResponse toAdminQuestion(ReadingQuestion question) {
    ReadingQuestion q = unproxy(question);
    List<HintView> hints = q.getHints().stream().map(this::toHintView).toList();
    return switch (q) {
      case McqQuestion m -> new AdminQuestionResponse(q.getId(), q.getPosition(), q.type(), q.getPrompt(),
          q.getEvidence(), q.getExplanation(), hints, toOptionResults(m), null, null, null, null);
      case TfngQuestion t -> new AdminQuestionResponse(q.getId(), q.getPosition(), q.type(), q.getPrompt(),
          q.getEvidence(), q.getExplanation(), hints, null, t.getAnswer(), null, null, null);
      case BlankQuestion b -> new AdminQuestionResponse(q.getId(), q.getPosition(), q.type(), q.getPrompt(),
          q.getEvidence(), q.getExplanation(), hints, null, null, b.getAnswerKind(), b.getMaxWords(),
          // List.copyOf thay vì đưa thẳng b.getAcceptedAnswers(): đó là collection LAZY của Hibernate.
          // Jackson chỉ đọc nó khi ghi JSON, lúc transaction đã đóng → LazyInitializationException.
          // Copy ngay trong mapper (còn trong transaction) để DTO chỉ chứa dữ liệu thường.
          List.copyOf(b.getAcceptedAnswers()));
      default -> throw new IllegalStateException("Dạng câu hỏi chưa được hỗ trợ: " + q.type());
    };
  }

  // ─────────────── dùng chung ───────────────

  private List<OptionResult> toOptionResults(McqQuestion m) {
    return m.getOptions().stream()
        .map(o -> new OptionResult(o.getId(), o.label(), o.getContent(), o.isCorrect()))
        .toList();
  }

  private HintView toHintView(ReadingHint hint) {
    return new HintView(hint.getQuestionPhrase(), hint.getPassagePhrase());
  }

  /**
   * answer.getQuestion() là quan hệ LAZY → có thể là PROXY (lớp con sinh lúc chạy của ReadingQuestion).
   * Proxy không phải McqQuestion/TfngQuestion/BlankQuestion → switch theo kiểu sẽ rơi vào default.
   * Hibernate.unproxy trả về object thật để pattern matching chạy đúng.
   */
  private static ReadingQuestion unproxy(ReadingQuestion question) {
    return (ReadingQuestion) Hibernate.unproxy(question);
  }
}
