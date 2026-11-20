package com.cassin.passage;

import static com.cassin.passage.PassageFixtures.EVIDENCE_1;
import static com.cassin.passage.PassageFixtures.NOW;
import static com.cassin.passage.PassageFixtures.content;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tạo và chấm 3 dạng câu hỏi, cùng luật gợi ý dùng chung ở lớp cha. */
class ReadingQuestionTest {

  private final Passage passage = PassageFixtures.draft();

  @Nested
  @DisplayName("Phần chung (lớp cha ReadingQuestion)")
  class Common {

    @Test
    @DisplayName("Câu bằng chứng không có trong bài → bị chặn khi thêm vào bài")
    void evidenceMustBeInPassage() {
      TfngQuestion q = TfngQuestion.create(passage, 0, content("Coal is cheap.", "Coal is cheap."), TfngAnswer.TRUE,
          NOW);

      assertThatThrownBy(() -> passage.addQuestion(q, NOW)).hasMessageContaining("trích nguyên văn");
    }

    @Test
    @DisplayName("Gợi ý: cụm câu hỏi phải nằm trong đề, cụm trong bài phải có trong bài, hai cụm phải khác nhau")
    void hintRules() {
      assertThatThrownBy(() -> TfngQuestion.create(passage, 0,
          content("Solar is cheap.", EVIDENCE_1, new HintInput("replacement", "alternative")), TfngAnswer.TRUE, NOW))
          .hasMessageContaining("không tìm thấy \"replacement\" trong câu hỏi");

      assertThatThrownBy(() -> TfngQuestion.create(passage, 0,
          content("Solar is a cheap option.", EVIDENCE_1, new HintInput("option", "Option")), TfngAnswer.TRUE, NOW))
          .hasMessageContaining("hai cách diễn đạt khác nhau");

      TfngQuestion missingInPassage = TfngQuestion.create(passage, 0,
          content("Solar is a cheap option.", EVIDENCE_1, new HintInput("option", "choice")), TfngAnswer.TRUE, NOW);
      assertThatThrownBy(() -> passage.addQuestion(missingInPassage, NOW))
          .hasMessageContaining("không tìm thấy \"choice\" trong bài đọc");
    }

    @Test
    @DisplayName("hintAt trả gợi ý theo thứ tự, hết thì rỗng")
    void hintAt() {
      BlankQuestion q = PassageFixtures.blank(passage, 1L);

      assertThat(q.hintAt(0)).get().extracting(ReadingHint::getPassagePhrase).isEqualTo("alternative");
      assertThat(q.hintAt(1)).isEmpty();
    }
  }

  @Nested
  @DisplayName("MCQ")
  class Mcq {

    @Test
    @DisplayName("Cần đúng 4 lựa chọn, đúng 1 lựa chọn đúng, không trùng")
    void createRules() {
      List<ChoiceInput> twoCorrect = List.of(new ChoiceInput("a", true), new ChoiceInput("b", true),
          new ChoiceInput("c", false), new ChoiceInput("d", false));
      List<ChoiceInput> duplicate = List.of(new ChoiceInput("a", true), new ChoiceInput("A ", false),
          new ChoiceInput("c", false), new ChoiceInput("d", false));

      assertThatThrownBy(() -> McqQuestion.create(passage, 0, content("Q?", EVIDENCE_1), twoCorrect.subList(0, 3), NOW))
          .hasMessageContaining("đúng 4 lựa chọn");
      assertThatThrownBy(() -> McqQuestion.create(passage, 0, content("Q?", EVIDENCE_1), twoCorrect, NOW))
          .hasMessageContaining("đúng 1 lựa chọn đúng");
      assertThatThrownBy(() -> McqQuestion.create(passage, 0, content("Q?", EVIDENCE_1), duplicate, NOW))
          .hasMessageContaining("trùng");
    }

    @Test
    @DisplayName("Chấm: đúng / sai / bỏ trống; id lựa chọn của câu khác → lỗi dữ liệu")
    void grade() {
      McqQuestion q = PassageFixtures.mcq(passage, 3L); // đúng: id 31

      assertThat(q.grade(new AnswerInput(31L, null, null)).correct()).isTrue();
      assertThat(q.grade(new AnswerInput(30L, null, null)).correct()).isFalse();
      assertThat(q.grade(new AnswerInput(30L, null, null)).selectedOption().getContent()).isEqualTo("expensive");
      assertThat(q.grade(AnswerInput.empty())).isEqualTo(GradeResult.blank());
      assertThatThrownBy(() -> q.grade(new AnswerInput(99L, null, null)))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("True / False / Not Given")
  class Tfng {

    @Test
    @DisplayName("Bắt buộc có đáp án; chấm bằng cách so enum")
    void createAndGrade() {
      assertThatThrownBy(() -> TfngQuestion.create(passage, 0, content("Q", EVIDENCE_1), null, NOW))
          .hasMessageContaining("True / False / Not Given");

      TfngQuestion q = PassageFixtures.tfng(passage, 2L); // đáp án FALSE
      assertThat(q.grade(new AnswerInput(null, TfngAnswer.FALSE, null)).correct()).isTrue();
      assertThat(q.grade(new AnswerInput(null, TfngAnswer.NOT_GIVEN, null)).correct()).isFalse();
      assertThat(q.grade(AnswerInput.empty())).isEqualTo(GradeResult.blank());
    }
  }

  @Nested
  @DisplayName("Điền từ")
  class Blank {

    @Test
    @DisplayName("Đề phải có đúng một ____; đáp án chuẩn phải đúng giới hạn số từ")
    void createRules() {
      assertThatThrownBy(() -> BlankQuestion.create(passage, 0, content("No blank here.", EVIDENCE_1),
          AnswerKind.WORDS, 1, List.of("coal"), NOW)).hasMessageContaining("đúng một chỗ trống");
      assertThatThrownBy(() -> BlankQuestion.create(passage, 0, content("____ and ____.", EVIDENCE_1),
          AnswerKind.WORDS, 1, List.of("coal"), NOW)).hasMessageContaining("đúng một chỗ trống");
      assertThatThrownBy(() -> BlankQuestion.create(passage, 0, content("A ____.", EVIDENCE_1),
          AnswerKind.WORDS, 1, List.of("black coal"), NOW)).hasMessageContaining("ONE WORD ONLY");
      assertThatThrownBy(() -> BlankQuestion.create(passage, 0, content("A ____.", EVIDENCE_1),
          AnswerKind.WORDS, 4, List.of("coal"), NOW)).hasMessageContaining("từ 1 đến 3");
    }

    @Test
    @DisplayName("NUMBER: bỏ qua maxWords admin gửi lên")
    void numberIgnoresMaxWords() {
      BlankQuestion q = BlankQuestion.create(passage, 0, content("About ____ of homes.", EVIDENCE_1),
          AnswerKind.NUMBER, 2, List.of("15%"), NOW);

      assertThat(q.getMaxWords()).isNull();
      assertThat(q.instruction()).isEqualTo("A NUMBER");
    }

    @Test
    @DisplayName("Chấm: chuẩn hóa trước khi so; sai định dạng → kèm lý do")
    void grade() {
      BlankQuestion q = PassageFixtures.blank(passage, 1L); // "coal", ONE WORD ONLY

      assertThat(q.grade(new AnswerInput(null, null, "  Coal. ")).correct()).isTrue();
      assertThat(q.grade(new AnswerInput(null, null, "oil")).correct()).isFalse();
      GradeResult tooLong = q.grade(new AnswerInput(null, null, "black coal"));
      assertThat(tooLong.correct()).isFalse();
      assertThat(tooLong.rejectedReason()).isEqualTo(RejectedReason.OVER_WORD_LIMIT);
      assertThat(q.grade(new AnswerInput(null, null, "   "))).isEqualTo(GradeResult.blank());
    }
  }
}
