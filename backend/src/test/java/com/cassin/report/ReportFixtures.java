package com.cassin.report;

import java.time.Instant;

import org.springframework.test.util.ReflectionTestUtils;

import com.cassin.user.User;
import com.cassin.vocab.PartOfSpeech;
import com.cassin.vocab.Sense;
import com.cassin.vocab.VocabFixtures;
import com.cassin.vocab.Word;

/**
 * Dữ liệu mẫu dùng chung cho test module report (giống VocabFixtures bên vocab).
 *
 *   học viên  id 1  "Học viên An"
 *   admin     id 99 "Quản trị"
 *   từ HỆ THỐNG "break the ice"  → nghĩa id 60
 *   từ CỦA HỌC VIÊN "my word"    → nghĩa id 70
 */
final class ReportFixtures {
  static final Long USER_ID = 1L;
  static final Long ADMIN_ID = 99L;
  static final Instant NOW = Instant.parse("2026-11-10T03:00:00Z");   // 10/11/2026 10:00 giờ VN

  private ReportFixtures() {
  }

  static User user(long id, String displayName) {
    User user = new User("u" + id + "@cassin.vn", "hash", displayName);
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }

  static User learner() {
    return user(USER_ID, "Học viên An");
  }

  static User admin() {
    return user(ADMIN_ID, "Quản trị");
  }

  /** Nghĩa id 60 của từ HỆ THỐNG "break the ice". */
  static Sense systemSense() {
    Word word = VocabFixtures.word(6L, "break the ice", null,
        VocabFixtures.sense(PartOfSpeech.IDIOM, "phá vỡ sự ngại ngùng ban đầu", "He told a joke to [break the ice]."));
    return word.getSenses().get(0);
  }

  /** Nghĩa id 70 của từ CÁ NHÂN "my word" (owner = owner). */
  static Sense personalSense(User owner) {
    Word word = VocabFixtures.word(7L, "my word", owner,
        VocabFixtures.sense(PartOfSpeech.NOUN, "nghĩa của tôi"));
    return word.getSenses().get(0);
  }

  /** Báo cáo OPEN đã có id (như vừa đọc từ DB). */
  static ContentReport openReport(long id, User reporter, Sense sense) {
    ContentReport report = ContentReport.open(reporter, sense, ReportReason.TYPO, "Sai chính tả", NOW);
    ReflectionTestUtils.setField(report, "id", id);
    return report;
  }
}
