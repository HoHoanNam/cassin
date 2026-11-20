package com.cassin.passage;

import static com.cassin.passage.PassageFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cassin.grammar.CefrLevel;

/** Luật của aggregate root Passage: vòng đời DRAFT → PUBLISHED → ARCHIVED và tính nhất quán với câu hỏi. */
class PassageTest {

  @Test
  @DisplayName("Tạo bài nháp: cắt khoảng trắng, đếm từ, chưa có thời điểm publish")
  void draft_countsWords() {
    Passage passage = Passage.draft("  Solar  ", "  One two, three — four.  ", CefrLevel.A2, null, NOW);

    assertThat(passage.getTitle()).isEqualTo("Solar");
    assertThat(passage.getStatus()).isEqualTo(PassageStatus.DRAFT);
    assertThat(passage.getWordCount()).isEqualTo(4); // "—" không tính là từ
    assertThat(passage.getPublishedAt()).isNull();
    assertThat(passage.readingMinutes()).isEqualTo(1);
  }

  @Test
  @DisplayName("Publish: cần ít nhất 1 câu hỏi; chỉ publish được bài nháp")
  void publish_rules() {
    Passage empty = PassageFixtures.draft();
    assertThatThrownBy(() -> empty.publish(NOW))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ít nhất 1 câu hỏi");

    Passage passage = PassageFixtures.draft();
    PassageFixtures.tfng(passage, 1L);
    passage.publish(NOW);
    assertThat(passage.getStatus()).isEqualTo(PassageStatus.PUBLISHED);
    assertThat(passage.getPublishedAt()).isEqualTo(NOW);

    assertThatThrownBy(() -> passage.publish(NOW))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("bài nháp");
  }

  @Test
  @DisplayName("Archive: chỉ từ PUBLISHED; bài đã lưu trữ thì không sửa được nữa")
  void archive_thenReadOnly() {
    Passage draft = PassageFixtures.draft();
    assertThatThrownBy(() -> draft.archive(NOW)).isInstanceOf(IllegalStateException.class);

    Passage passage = PassageFixtures.publishedWithAllTypes();
    passage.archive(NOW);

    assertThat(passage.getStatus()).isEqualTo(PassageStatus.ARCHIVED);
    assertThatThrownBy(() -> passage.update("x", PassageFixtures.TEXT, CefrLevel.B1, NOW))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("lưu trữ");
    assertThatThrownBy(() -> passage.clearQuestions(NOW)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("Sửa nội dung làm mất câu bằng chứng của câu hỏi đang có → bị chặn")
  void update_mustKeepEvidence() {
    Passage passage = PassageFixtures.draft();
    PassageFixtures.tfng(passage, 1L);

    assertThatThrownBy(() -> passage.update("Solar", "A totally different text.", CefrLevel.B1, NOW))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Câu 1: câu bằng chứng");
  }

  @Test
  @DisplayName("Thêm câu hỏi sai thứ tự hoặc của bài khác → bị chặn")
  void addQuestion_guards() {
    Passage passage = PassageFixtures.draft();
    Passage other = PassageFixtures.draft();
    TfngQuestion ofOther = TfngQuestion.create(other, 0,
        PassageFixtures.content("Most homes used solar panels.", PassageFixtures.EVIDENCE_2), TfngAnswer.FALSE, NOW);
    TfngQuestion wrongPosition = TfngQuestion.create(passage, 5,
        PassageFixtures.content("Most homes used solar panels.", PassageFixtures.EVIDENCE_2), TfngAnswer.FALSE, NOW);

    assertThatThrownBy(() -> passage.addQuestion(ofOther, NOW)).hasMessageContaining("không thuộc bài đọc này");
    assertThatThrownBy(() -> passage.addQuestion(wrongPosition, NOW)).hasMessageContaining("đúng thứ tự");
  }
}
