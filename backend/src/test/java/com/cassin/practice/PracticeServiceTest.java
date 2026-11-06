package com.cassin.practice;

import static com.cassin.vocab.VocabFixtures.sense;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.cassin.common.BusinessException;
import com.cassin.common.TimeConfig;
import com.cassin.practice.dto.FillBlankQuestion;
import com.cassin.practice.dto.PracticeAnswer;
import com.cassin.practice.dto.PracticeResult;
import com.cassin.practice.dto.SentenceBuildQuestion;
import com.cassin.user.User;
import com.cassin.user.UserRepository;
import com.cassin.vocab.CardProgress;
import com.cassin.vocab.CardProgressRepository;
import com.cassin.vocab.PartOfSpeech;
import com.cassin.vocab.Sense;
import com.cassin.vocab.VocabFixtures;
import com.cassin.vocab.Word;
import com.cassin.vocab.WordService;

/**
 * [V10] Fill in blank + Sentence builder: câu hỏi không lộ đáp án, chấm ở server,
 * mỗi lần trả lời ghi review_events, sai → nghĩa đến hạn ôn ngày mai.
 */
@ExtendWith(MockitoExtension.class)
class PracticeServiceTest {
  private static final Long USER_ID = 1L;
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);

  @Mock WordService wordService;
  @Mock CardProgressRepository progressRepository;
  @Mock ReviewEventRepository eventRepository;
  @Mock UserRepository userRepository;

  private PracticeService practiceService;

  private final User me = VocabFixtures.user(USER_ID, "an@gmail.com");

  // Nghĩa 30: 1 câu có đánh dấu [improved] (8 mảnh) + 1 câu không đánh dấu, quá ngắn (3 mảnh).
  private final Word improve = VocabFixtures.word(3L, "improve", null,
      sense(PartOfSpeech.VERB, "cải thiện",
          "Her English has [improved] a lot this year.",
          "Things will improve."));
  private final Sense improveVerb = improve.getSenses().get(0);

  @BeforeEach
  void setUp() {
    Clock fixedClock = Clock.fixed(TODAY.atStartOfDay(TimeConfig.APP_ZONE).toInstant(), TimeConfig.APP_ZONE);
    practiceService = new PracticeService(wordService, progressRepository, eventRepository, userRepository, fixedClock);
  }

  // ===================== Tạo câu hỏi =====================

  @Test
  @DisplayName("Fill blank: chỉ dùng câu có [từ], câu đã che, KHÔNG lộ đáp án")
  void fillBlank_hidesAnswer() {
    when(wordService.practiceSenses(null, USER_ID)).thenReturn(List.of(improveVerb));

    List<FillBlankQuestion> questions = practiceService.fillBlank(null, 10, USER_ID);

    assertThat(questions).singleElement().satisfies(q -> {
      assertThat(q.senseId()).isEqualTo(30L);
      assertThat(q.exampleIndex()).isZero();
      assertThat(q.sentence()).isEqualTo("Her English has ____ a lot this year.").doesNotContain("improved");
      assertThat(q.firstLetter()).isEqualTo("i");
      assertThat(q.answerLength()).isEqualTo("improved".length());
    });
  }

  @Test
  @DisplayName("Sentence build: bỏ câu ngắn hơn 5 mảnh, mảnh đã xáo nhưng đủ chữ")
  void sentenceBuild_filtersAndShuffles() {
    when(wordService.practiceSenses(5L, USER_ID)).thenReturn(List.of(improveVerb));

    List<SentenceBuildQuestion> questions = practiceService.sentenceBuild(5L, 10, USER_ID);

    List<String> original = List.of("Her", "English", "has", "improved", "a", "lot", "this", "year.");
    assertThat(questions).singleElement().satisfies(q -> {
      assertThat(q.exampleIndex()).isZero();   // câu 2 "Things will improve." chỉ 3 mảnh → bị bỏ
      assertThat(q.term()).isEqualTo("improve");
      assertThat(q.tokens()).containsExactlyInAnyOrderElementsOf(original).isNotEqualTo(original);
    });
  }

  @Test
  @DisplayName("Mỗi nghĩa tối đa 1 câu hỏi, số câu không vượt count")
  void questions_onePerSense_andCountLimit() {
    Word light = VocabFixtures.word(4L, "light", null,
        sense(PartOfSpeech.NOUN, "ánh sáng", "Turn on the [light].", "The [light] is too bright."),
        sense(PartOfSpeech.ADJECTIVE, "nhẹ", "This bag is [light]."));
    when(wordService.practiceSenses(null, USER_ID)).thenReturn(List.of(improveVerb, light.getSenses().get(0), light.getSenses().get(1)));

    List<FillBlankQuestion> all = practiceService.fillBlank(null, 10, USER_ID);
    List<FillBlankQuestion> two = practiceService.fillBlank(null, 2, USER_ID);

    assertThat(all).extracting(q -> q.senseId()).containsExactlyInAnyOrder(30L, 40L, 41L);
    assertThat(two).hasSize(2).extracting(q -> q.senseId()).doesNotHaveDuplicates();
  }

  // ===================== Chấm =====================

  @Test
  @DisplayName("Fill blank đúng (khác hoa thường): ghi event correct, KHÔNG đụng tiến độ")
  void check_fillBlank_correct() {
    when(wordService.findReadableSense(30L, USER_ID)).thenReturn(improveVerb);
    when(userRepository.getReferenceById(USER_ID)).thenReturn(me);

    PracticeResult result = practiceService.check(answer(PracticeMode.FILL_BLANK, " Improved "), USER_ID);

    assertThat(result.correct()).isTrue();
    assertThat(result.expected()).isEqualTo("improved");
    ArgumentCaptor<ReviewEvent> event = ArgumentCaptor.forClass(ReviewEvent.class);
    verify(eventRepository).save(event.capture());
    assertThat(event.getValue().isCorrect()).isTrue();
    assertThat(event.getValue().getMode()).isEqualTo(PracticeMode.FILL_BLANK);
    verifyNoInteractions(progressRepository);
  }

  @Test
  @DisplayName("Fill blank gõ từ gốc 'improve': sai + gợi ý chia dạng; nghĩa đang học → hạn ôn về ngày mai")
  void check_fillBlank_baseForm_hintAndDueSoon() {
    // Ôn đúng 2 lần: hộp 3, lần ôn cuối 2 ngày trước → hạn = TODAY + 2 (sau ngày mai).
    CardProgress progress = CardProgress.start(me, improveVerb, TODAY.minusDays(4));
    progress.review(true, TODAY.minusDays(4));   // hộp 2, hạn TODAY - 2
    progress.review(true, TODAY.minusDays(2));   // hộp 3, hạn TODAY + 2
    assertThat(progress.getDueDate()).isEqualTo(TODAY.plusDays(2));
    when(wordService.findReadableSense(30L, USER_ID)).thenReturn(improveVerb);
    when(userRepository.getReferenceById(USER_ID)).thenReturn(me);
    when(progressRepository.findByUserIdAndSenseId(USER_ID, 30L)).thenReturn(Optional.of(progress));
    int boxBefore = progress.getBox();

    PracticeResult result = practiceService.check(answer(PracticeMode.FILL_BLANK, "improve"), USER_ID);

    assertThat(result.correct()).isFalse();
    assertThat(result.hint()).isNotNull();
    assertThat(progress.getDueDate()).isEqualTo(TODAY.plusDays(1));
    assertThat(progress.getBox()).isEqualTo(boxBefore);   // luyện sai KHÔNG đổi hộp
  }

  @Test
  @DisplayName("Sai với nghĩa CHƯA học: chỉ ghi event, không tạo tiến độ")
  void check_wrong_unstartedSense_onlyLogs() {
    when(wordService.findReadableSense(30L, USER_ID)).thenReturn(improveVerb);
    when(userRepository.getReferenceById(USER_ID)).thenReturn(me);
    when(progressRepository.findByUserIdAndSenseId(USER_ID, 30L)).thenReturn(Optional.empty());

    PracticeResult result = practiceService.check(answer(PracticeMode.FILL_BLANK, "better"), USER_ID);

    assertThat(result.correct()).isFalse();
    assertThat(result.hint()).isNull();
    verify(eventRepository).save(any(ReviewEvent.class));
    verify(progressRepository, never()).save(any());
  }

  @Test
  @DisplayName("Sentence build: bỏ qua khoảng trắng thừa, hoa thường, dấu chấm cuối")
  void check_sentenceBuild_looseCompare() {
    when(wordService.findReadableSense(30L, USER_ID)).thenReturn(improveVerb);
    when(userRepository.getReferenceById(USER_ID)).thenReturn(me);

    PracticeResult result = practiceService.check(
        answer(PracticeMode.SENTENCE_BUILD, "her english  has improved a lot this year"), USER_ID);

    assertThat(result.correct()).isTrue();
    assertThat(result.expected()).isEqualTo("Her English has improved a lot this year.");
  }

  @Test
  @DisplayName("Fill blank với câu KHÔNG có [từ]: 400 NOT_PRACTICABLE, không ghi gì")
  void check_fillBlank_unmarkedExample_isBadRequest() {
    when(wordService.findReadableSense(30L, USER_ID)).thenReturn(improveVerb);

    PracticeAnswer unmarked = new PracticeAnswer(30L, 1, PracticeMode.FILL_BLANK, "improve", 1000);
    assertStatus(() -> practiceService.check(unmarked, USER_ID), HttpStatus.BAD_REQUEST, "NOT_PRACTICABLE");
    verifyNoInteractions(eventRepository, progressRepository);
  }

  @Test
  @DisplayName("exampleIndex vượt số câu: 400 NOT_PRACTICABLE")
  void check_indexOutOfRange_isBadRequest() {
    when(wordService.findReadableSense(30L, USER_ID)).thenReturn(improveVerb);

    PracticeAnswer outOfRange = new PracticeAnswer(30L, 9, PracticeMode.SENTENCE_BUILD, "x", 1000);
    assertStatus(() -> practiceService.check(outOfRange, USER_ID), HttpStatus.BAD_REQUEST, "NOT_PRACTICABLE");
  }

  @Test
  @DisplayName("Mode FLASHCARD: 400 INVALID_MODE (thẻ lật chấm ở /api/senses/{id}/review)")
  void check_flashcard_isBadRequest() {
    when(wordService.findReadableSense(30L, USER_ID)).thenReturn(improveVerb);

    assertStatus(() -> practiceService.check(answer(PracticeMode.FLASHCARD, "x"), USER_ID), HttpStatus.BAD_REQUEST, "INVALID_MODE");
    verifyNoInteractions(eventRepository);
    verify(progressRepository, never()).findByUserIdAndSenseId(anyLong(), anyLong());
  }

  // ===================== helpers tĩnh =====================

  @Test
  @DisplayName("tokens: tách theo khoảng trắng, dấu câu dính theo chữ, chuỗi rỗng → []")
  void tokens_split() {
    assertThat(PracticeService.tokens("  a  lot. ")).containsExactly("a", "lot.");
    assertThat(PracticeService.tokens("")).isEmpty();
    assertThat(PracticeService.tokens(null)).isEmpty();
  }

  @Test
  @DisplayName("shuffled: luôn khác thứ tự gốc; mọi mảnh giống nhau thì giữ nguyên")
  void shuffled_neverOriginal() {
    List<String> original = List.of("a", "b");
    Random seeded = new Random(42);
    for (int i = 0; i < 20; i++) {
      assertThat(PracticeService.shuffled(original, seeded)).containsExactly("b", "a");
    }
    assertThat(PracticeService.shuffled(List.of("x", "x"), seeded)).containsExactly("x", "x");
  }

  @Test
  @DisplayName("normalize: nháy cong = nháy thẳng, bỏ dấu câu cuối, gộp khoảng trắng")
  void normalize_rules() {
    assertThat(PracticeService.normalize("Can’t  stop!")).isEqualTo(PracticeService.normalize("can't stop"));
    assertThat(PracticeService.normalize(null)).isEmpty();
  }

  // ===================== [THÊM] Thành ngữ & cụm động từ =====================
  // PracticeService KHÔNG cần sửa: các test này chốt rằng luồng cũ đã chạy đúng với cụm nhiều chữ.

  // [THÊM] Từ 6 → nghĩa 60 (quy ước id của VocabFixtures).
  private final Word breakTheIce = VocabFixtures.word(6L, "break the ice", null,
      sense(PartOfSpeech.IDIOM, "phá vỡ sự ngại ngùng ban đầu",
          "He told a funny story to [break the ice]."));
  // [THÊM] Từ 7 → nghĩa 70. Câu đánh dấu dạng QUÁ KHỨ "gave up".
  private final Word giveUp = VocabFixtures.word(7L, "give up", null,
      sense(PartOfSpeech.PHRASAL_VERB, "từ bỏ", "He [gave up] smoking last year."));

  @Test
  @DisplayName("[THÊM] Fill blank thành ngữ: che cả cụm, gợi ý chữ cái đầu 'b', độ dài tính cả khoảng trắng")
  void fillBlank_idiom_hidesWholePhrase() {
    when(wordService.practiceSenses(null, USER_ID)).thenReturn(List.of(breakTheIce.getSenses().get(0)));

    List<FillBlankQuestion> questions = practiceService.fillBlank(null, 10, USER_ID);

    assertThat(questions).singleElement().satisfies(q -> {
      assertThat(q.senseId()).isEqualTo(60L);
      assertThat(q.sentence()).isEqualTo("He told a funny story to ____.").doesNotContain("ice");
      assertThat(q.partOfSpeech()).isEqualTo(PartOfSpeech.IDIOM);
      assertThat(q.firstLetter()).isEqualTo("b");
      assertThat(q.answerLength()).isEqualTo("break the ice".length());   // 13
    });
  }

  @Test
  @DisplayName("[THÊM] Chấm thành ngữ: hoa thường và khoảng trắng thừa GIỮA các chữ không tính là sai")
  void check_idiom_looseCompare() {
    when(wordService.findReadableSense(60L, USER_ID)).thenReturn(breakTheIce.getSenses().get(0));
    when(userRepository.getReferenceById(USER_ID)).thenReturn(me);

    PracticeResult result = practiceService.check(
        new PracticeAnswer(60L, 0, PracticeMode.FILL_BLANK, "  Break   the ICE ", 3_000), USER_ID);

    assertThat(result.correct()).isTrue();
    assertThat(result.expected()).isEqualTo("break the ice");
  }

  @Test
  @DisplayName("[THÊM] Cụm động từ gõ dạng gốc 'give up' khi câu cần 'gave up': sai + gợi ý chia dạng")
  void check_phrasalVerb_baseForm_hint() {
    when(wordService.findReadableSense(70L, USER_ID)).thenReturn(giveUp.getSenses().get(0));
    when(userRepository.getReferenceById(USER_ID)).thenReturn(me);

    PracticeResult result = practiceService.check(
        new PracticeAnswer(70L, 0, PracticeMode.FILL_BLANK, "Give up", 3_000), USER_ID);

    assertThat(result.correct()).isFalse();
    assertThat(result.expected()).isEqualTo("gave up");
    assertThat(result.hint()).isNotNull();
  }

  @Test
  @DisplayName("[THÊM] Sentence build với thành ngữ: term hiển thị cả cụm, mảnh xáo đủ chữ")
  void sentenceBuild_idiom() {
    when(wordService.practiceSenses(null, USER_ID)).thenReturn(List.of(breakTheIce.getSenses().get(0)));

    List<SentenceBuildQuestion> questions = practiceService.sentenceBuild(null, 10, USER_ID);

    assertThat(questions).singleElement().satisfies(q -> {
      assertThat(q.term()).isEqualTo("break the ice");
      assertThat(q.tokens()).containsExactlyInAnyOrder("He", "told", "a", "funny", "story", "to", "break", "the", "ice.");
    });
  }

  // ===================== helper =====================

  private static PracticeAnswer answer(PracticeMode mode, String text) {
    return new PracticeAnswer(30L, 0, mode, text, 4_000);
  }

  private static void assertStatus(Runnable action, HttpStatus status, String code) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getStatus()).isEqualTo(status);
          assertThat(ex.getCode()).isEqualTo(code);
        });
  }
}
