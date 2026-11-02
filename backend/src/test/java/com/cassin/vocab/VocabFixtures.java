package com.cassin.vocab;

import java.util.Arrays;
import java.util.List;

import org.springframework.test.util.ReflectionTestUtils;

import com.cassin.user.User;

/**
 * [V10] Dữ liệu mẫu dùng chung cho test phần từ vựng (giống GrammarFixtures bên grammar).
 *
 * Entity tạo bằng factory method CHƯA có id (id do DB cấp khi INSERT), nhưng nhiều logic cần id:
 * Word.replaceSenses so id nghĩa, ReviewCardResponse lọc "nghĩa khác" theo id...
 * → gán id giả bằng ReflectionTestUtils (chỉ dùng trong TEST).
 *
 * Quy ước id: từ id = 7 → các nghĩa id 70, 71, 72... (id từ * 10 + vị trí), dễ đọc khi test hỏng.
 * public: test của package practice cũng dùng.
 */
public final class VocabFixtures {

  private VocabFixtures() {
  }

  public static User user(long id, String email) {
    User user = new User(email, "hash", "Tên");
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }

  /** Một nghĩa mới (chưa có id) để đưa vào word(...). */
  public static SenseInput sense(PartOfSpeech pos, String definition, String... examples) {
    return new SenseInput(null, pos, definition, Arrays.asList(examples));
  }

  /**
   * Một từ đã có id, kèm các nghĩa đã có id (id từ * 10 + vị trí).
   * owner == null → từ hệ thống.
   */
  public static Word word(long id, String term, User owner, SenseInput... senses) {
    Word word = owner == null ? Word.system(term, null) : Word.personal(term, null, owner);
    word.replaceSenses(List.of(senses));
    ReflectionTestUtils.setField(word, "id", id);
    for (Sense sense : word.getSenses()) {
      ReflectionTestUtils.setField(sense, "id", id * 10 + sense.getPosition());
    }
    return word;
  }

  public static Deck withId(Deck deck, long id) {
    ReflectionTestUtils.setField(deck, "id", id);
    return deck;
  }
}
