package com.cassin.vocab;

import static com.cassin.vocab.VocabFixtures.sense;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cassin.user.User;

/** [V10] Luật của Word: nhiều nghĩa, giữ id khi sửa, chặn trùng, quyền xem / sửa. */
class WordTest {
  private final User an = VocabFixtures.user(1L, "an@gmail.com");
  private final User binh = VocabFixtures.user(2L, "binh@gmail.com");

  @Test
  @DisplayName("Tạo từ: cắt khoảng trắng, phiên âm rỗng → null, nghĩa theo đúng thứ tự")
  void create_withSenses() {
    Word word = Word.personal("  light ", "  ", an);
    word.replaceSenses(List.of(
        sense(PartOfSpeech.NOUN, "ánh sáng", "Turn on the [light]."),
        sense(PartOfSpeech.ADJECTIVE, "nhẹ")));

    assertThat(word.getTerm()).isEqualTo("light");
    assertThat(word.getPhonetic()).isNull();
    assertThat(word.getSenses())
        .extracting(s -> s.getPosition(), s -> s.getPartOfSpeech(), s -> s.getDefinition())
        .containsExactly(
            tuple(0, PartOfSpeech.NOUN, "ánh sáng"),
            tuple(1, PartOfSpeech.ADJECTIVE, "nhẹ"));
  }

  @Test
  @DisplayName("Sửa: nghĩa giữ id → sửa TẠI CHỖ (giữ tiến độ), đổi thứ tự, nghĩa bị bỏ thì xóa")
  void replaceSenses_keepsIdsAndRemovesMissing() {
    Word word = VocabFixtures.word(7L, "light", an,
        sense(PartOfSpeech.NOUN, "ánh sáng"),
        sense(PartOfSpeech.ADJECTIVE, "nhẹ"),
        sense(PartOfSpeech.VERB, "thắp"));                    // nghĩa id 70, 71, 72
    Sense adjective = word.getSenses().get(1);

    word.replaceSenses(List.of(
        new SenseInput(71L, PartOfSpeech.ADJECTIVE, "nhẹ (cân nặng)", List.of()),
        new SenseInput(70L, PartOfSpeech.NOUN, "ánh sáng", List.of()),
        new SenseInput(null, PartOfSpeech.ADJECTIVE, "sáng sủa", List.of())));

    assertThat(word.getSenses()).hasSize(3);
    assertThat(word.getSenses().get(0)).isSameAs(adjective);           // cùng object → cùng id
    assertThat(adjective.getDefinition()).isEqualTo("nhẹ (cân nặng)");
    assertThat(adjective.getPosition()).isZero();
    assertThat(word.getSenses()).extracting(s -> s.getId()).containsExactly(71L, 70L, null);  // 72 bị xóa, nghĩa mới chưa có id
  }

  @Test
  @DisplayName("Hai nghĩa trùng (không phân biệt hoa thường): lỗi, và từ GIỮ NGUYÊN trạng thái cũ")
  void replaceSenses_duplicate_isRejectedWithoutChanges() {
    Word word = VocabFixtures.word(7L, "light", an, sense(PartOfSpeech.NOUN, "ánh sáng"));

    assertThatThrownBy(() -> word.replaceSenses(List.of(
        sense(PartOfSpeech.NOUN, "Nhẹ"),
        sense(PartOfSpeech.ADJECTIVE, " nhẹ "))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("trùng");
    assertThat(word.getSenses()).extracting(s -> s.getDefinition()).containsExactly("ánh sáng");
  }

  @Test
  @DisplayName("Nghĩa thứ 2 sai (câu ví dụ hỏng): nghĩa thứ 1 KHÔNG bị sửa dở dang")
  void replaceSenses_errorInLaterSense_leavesEarlierUntouched() {
    Word word = VocabFixtures.word(7L, "light", an, sense(PartOfSpeech.NOUN, "ánh sáng"));

    assertThatThrownBy(() -> word.replaceSenses(List.of(
        new SenseInput(70L, PartOfSpeech.NOUN, "ĐÃ SỬA", List.of()),
        sense(PartOfSpeech.ADJECTIVE, "nhẹ", "câu [hỏng"))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(word.getSenses().get(0).getDefinition()).isEqualTo("ánh sáng");
  }

  @Test
  @DisplayName("Id nghĩa của từ KHÁC, danh sách rỗng, quá 10 nghĩa: đều bị từ chối")
  void replaceSenses_invalidInputs() {
    Word word = VocabFixtures.word(7L, "light", an, sense(PartOfSpeech.NOUN, "ánh sáng"));

    assertThatThrownBy(() -> word.replaceSenses(List.of(new SenseInput(999L, PartOfSpeech.NOUN, "x", List.of()))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> word.replaceSenses(List.of())).isInstanceOf(IllegalArgumentException.class);

    List<SenseInput> eleven = java.util.stream.IntStream.range(0, 11)
        .mapToObj(i -> sense(PartOfSpeech.NOUN, "nghĩa " + i)).toList();
    assertThatThrownBy(() -> word.replaceSenses(eleven)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Câu ví dụ: bỏ câu trống, kiểm tra đánh dấu, tối đa 5 câu")
  void examples_areCleanedAndValidated() {
    Word word = Word.personal("light", null, an);
    word.replaceSenses(List.of(sense(PartOfSpeech.NOUN, "ánh sáng", " Turn on the [light]. ", "  ")));
    assertThat(word.getSenses().get(0).getExamples()).containsExactly("Turn on the [light].");
    assertThat(word.getSenses().get(0).plainExamples()).containsExactly("Turn on the light.");

    assertThatThrownBy(() -> word.replaceSenses(List.of(sense(PartOfSpeech.NOUN, "ánh sáng", "Turn on the [light."))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> word.replaceSenses(List.of(sense(PartOfSpeech.NOUN, "ánh sáng", "1", "2", "3", "4", "5", "6"))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Quyền: từ của mình đọc + sửa được; từ hệ thống ai cũng đọc; từ người khác thì không")
  void ownership() {
    Word mine = VocabFixtures.word(1L, "light", an, sense(PartOfSpeech.NOUN, "ánh sáng"));
    Word system = VocabFixtures.word(2L, "improve", null, sense(PartOfSpeech.VERB, "cải thiện"));

    assertThat(mine.isOwnedBy(1L)).isTrue();
    assertThat(mine.isReadableBy(2L)).isFalse();
    assertThat(system.isSystem()).isTrue();
    assertThat(system.isReadableBy(2L)).isTrue();
    assertThat(system.isOwnedBy(2L)).isFalse();
  }

  @Test
  @DisplayName("Deck.canContain: bộ hệ thống chỉ nhận từ hệ thống; bộ cá nhân nhận từ của chủ bộ + từ hệ thống")
  void deckCanContain() {
    Deck systemDeck = Deck.system("1000 từ", null);
    Deck anDeck = Deck.personal("IELTS", null, an);
    Word anWord = VocabFixtures.word(1L, "light", an, sense(PartOfSpeech.NOUN, "ánh sáng"));
    Word binhWord = VocabFixtures.word(2L, "tackle", binh, sense(PartOfSpeech.VERB, "giải quyết"));
    Word systemWord = VocabFixtures.word(3L, "improve", null, sense(PartOfSpeech.VERB, "cải thiện"));

    assertThat(systemDeck.canContain(systemWord)).isTrue();
    assertThat(systemDeck.canContain(anWord)).isFalse();
    assertThat(anDeck.canContain(anWord)).isTrue();
    assertThat(anDeck.canContain(systemWord)).isTrue();
    assertThat(anDeck.canContain(binhWord)).isFalse();
  }
}
