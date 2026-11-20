package com.cassin.passage;

import java.time.Instant;
import java.util.List;

import org.springframework.test.util.ReflectionTestUtils;

import com.cassin.grammar.CefrLevel;
import com.cassin.user.User;

/**
 * Dữ liệu mẫu dùng chung cho test module bài đọc.
 *
 * Entity tạo bằng factory CHƯA có id (DB cấp lúc INSERT), mà chấm MCQ lại tìm lựa chọn theo id
 * → gán id giả bằng ReflectionTestUtils, theo quy ước dễ đọc:
 *   câu id = 1 → lựa chọn id 10, 11, 12, 13 (id câu * 10 + vị trí)
 */
public final class PassageFixtures {
  public static final Instant NOW = Instant.parse("2026-11-20T03:00:00Z");

  /** Hai đoạn; câu đầu có "alternative" để làm gợi ý "replacement ≈ alternative". */
  public static final String TEXT = """
      Solar power is now a cheap alternative to coal in many countries.

      In 2020, about 15% of homes in the town used solar panels on their roofs.""";

  public static final String EVIDENCE_1 = "Solar power is now a cheap alternative to coal in many countries.";
  public static final String EVIDENCE_2 = "In 2020, about 15% of homes in the town used solar panels on their roofs.";

  private PassageFixtures() {
  }

  public static Passage draft() {
    Passage passage = Passage.draft("Solar power", TEXT, CefrLevel.B1, null, NOW);
    ReflectionTestUtils.setField(passage, "id", 1L);
    return passage;
  }

  public static QuestionContent content(String prompt, String evidence, HintInput... hints) {
    return new QuestionContent(prompt, evidence, "Giải thích", List.of(hints));
  }

  /** BLANK câu 0: "replacement for ____" → "coal", ONE WORD ONLY, 1 gợi ý. */
  public static BlankQuestion blank(Passage passage, long id) {
    BlankQuestion q = BlankQuestion.create(passage, passage.getQuestions().size(),
        content("Solar power is a cheap replacement for ____.", EVIDENCE_1, new HintInput("replacement", "alternative")),
        AnswerKind.WORDS, 1, List.of("coal"), NOW);
    ReflectionTestUtils.setField(q, "id", id);
    passage.addQuestion(q, NOW);
    return q;
  }

  /** TFNG: "Most homes used solar panels in 2020." → FALSE (chỉ 15%). */
  public static TfngQuestion tfng(Passage passage, long id) {
    TfngQuestion q = TfngQuestion.create(passage, passage.getQuestions().size(),
        content("Most homes used solar panels in 2020.", EVIDENCE_2), TfngAnswer.FALSE, NOW);
    ReflectionTestUtils.setField(q, "id", id);
    passage.addQuestion(q, NOW);
    return q;
  }

  /** MCQ: đáp án đúng là "cheap" (vị trí 1 → option id = id*10 + 1). */
  public static McqQuestion mcq(Passage passage, long id) {
    McqQuestion q = McqQuestion.create(passage, passage.getQuestions().size(),
        content("How is solar power described?", EVIDENCE_1),
        List.of(new ChoiceInput("expensive", false), new ChoiceInput("cheap", true),
            new ChoiceInput("dangerous", false), new ChoiceInput("old", false)), NOW);
    ReflectionTestUtils.setField(q, "id", id);
    for (ReadingOption option : q.getOptions()) {
      ReflectionTestUtils.setField(option, "id", id * 10 + option.getPosition());
    }
    passage.addQuestion(q, NOW);
    return q;
  }

  /** Bài đã publish có đủ 3 dạng: câu 1 BLANK, câu 2 TFNG, câu 3 MCQ. */
  public static Passage publishedWithAllTypes() {
    Passage passage = draft();
    blank(passage, 1L);
    tfng(passage, 2L);
    mcq(passage, 3L);
    passage.publish(NOW);
    return passage;
  }

  public static User user(long id) {
    User user = new User("hv" + id + "@cassin.vn", "hash", "Học viên " + id);
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }
}
