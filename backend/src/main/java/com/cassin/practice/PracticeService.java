package com.cassin.practice;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cassin.common.BusinessException;
import com.cassin.practice.dto.FillBlankQuestion;
import com.cassin.practice.dto.PracticeAnswer;
import com.cassin.practice.dto.PracticeResult;
import com.cassin.practice.dto.SentenceBuildQuestion;
import com.cassin.user.UserRepository;
import com.cassin.vocab.CardProgressRepository;
import com.cassin.vocab.ExampleMarkup;
import com.cassin.vocab.Sense;
import com.cassin.vocab.WordService;

import lombok.RequiredArgsConstructor;

/**
 * Hai chế độ luyện dựa trên CÂU VÍ DỤ của từng nghĩa:
 *
 *   Fill in blank   : "Her English has ____ a lot."  → gõ "improved"
 *                     Cần câu có đánh dấu [từ đích] (xem ExampleMarkup).
 *   Sentence builder: ["a", "lot.", "Her", "has", "English", "improved"] → xếp lại thành câu
 *                     Cần câu dài 5–15 từ (ngắn quá thì dễ, dài quá thì nản).
 *
 * Nguyên tắc:
 *   - Câu hỏi KHÔNG chứa đáp án. Chấm Ở SERVER: đọc lại câu gốc từ DB, client không tự báo "tôi đúng" được.
 *   - Mỗi lần trả lời ghi 1 dòng review_events.
 *   - Sai → nghĩa đó đến hạn ôn ngày mai (CardProgress.dueSoon). Đúng → KHÔNG đổi hộp Leitner.
 */
@Service
@RequiredArgsConstructor
public class PracticeService {
  static final int MIN_TOKENS = 5;
  static final int MAX_TOKENS = 15;
  static final int MAX_QUESTIONS = 30;

  private final WordService wordService;
  private final CardProgressRepository progressRepository;
  private final ReviewEventRepository eventRepository;
  private final UserRepository userRepository;
  private final Clock clock;

  /** GET /api/practice/fill-blank?deckId=&count= */
  @Transactional(readOnly = true)
  public List<FillBlankQuestion> fillBlank(Long deckId, int count, Long userId) {
    List<FillBlankQuestion> candidates = new ArrayList<>();
    for (Sense sense : wordService.practiceSenses(deckId, userId)) {
      List<String> examples = sense.getExamples();
      for (int i = 0; i < examples.size(); i++) {
        Optional<ExampleMarkup> markup = ExampleMarkup.parse(examples.get(i));
        if (markup.isPresent()) {
          ExampleMarkup m = markup.get();
          candidates.add(new FillBlankQuestion(sense.getId(), i, m.blanked(), sense.getPartOfSpeech(),
              sense.getDefinition(), m.target().substring(0, 1).toLowerCase(Locale.ROOT), m.target().length()));
        }
      }
    }
    return pickOnePerSense(candidates, count, q -> q.senseId());
  }

  /** GET /api/practice/sentence-build?deckId=&count= */
  @Transactional(readOnly = true)
  public List<SentenceBuildQuestion> sentenceBuild(Long deckId, int count, Long userId) {
    Random random = ThreadLocalRandom.current();
    List<SentenceBuildQuestion> candidates = new ArrayList<>();
    for (Sense sense : wordService.practiceSenses(deckId, userId)) {
      List<String> examples = sense.getExamples();
      for (int i = 0; i < examples.size(); i++) {
        List<String> tokens = tokens(ExampleMarkup.plainOf(examples.get(i)));
        if (tokens.size() >= MIN_TOKENS && tokens.size() <= MAX_TOKENS) {
          candidates.add(new SentenceBuildQuestion(sense.getId(), i, sense.getWord().getTerm(),
              sense.getDefinition(), shuffled(tokens, random)));
        }
      }
    }
    return pickOnePerSense(candidates, count, q -> q.senseId());
  }

  /** POST /api/practice/answers */
  @Transactional
  public PracticeResult check(PracticeAnswer a, Long userId) {
    Sense sense = wordService.findReadableSense(a.senseId(), userId);
    List<String> examples = sense.getExamples();
    if (a.exampleIndex() >= examples.size()) {
      throw notPracticable();
    }
    String raw = examples.get(a.exampleIndex());

    PracticeResult result = switch (a.mode()) {
      case FILL_BLANK -> {
        ExampleMarkup markup = ExampleMarkup.parse(raw).orElseThrow(PracticeService::notPracticable);
        boolean correct = normalize(a.answer()).equals(normalize(markup.target()));
        // Gõ đúng TỪ GỐC nhưng câu cần dạng đã chia ("improve" cho chỗ cần "improved").
        String hint = !correct && normalize(a.answer()).equals(normalize(sense.getWord().getTerm()))
            ? "Đúng từ rồi, nhưng cần chia đúng dạng trong câu"
            : null;
        yield new PracticeResult(correct, markup.target(), hint);
      }
      case SENTENCE_BUILD -> {
        String plain = ExampleMarkup.plainOf(raw);
        yield new PracticeResult(normalize(a.answer()).equals(normalize(plain)), plain, null);
      }
      case FLASHCARD -> throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_MODE",
          "Thẻ lật chấm ở POST /api/senses/{id}/review");
    };

    eventRepository.save(ReviewEvent.of(userRepository.getReferenceById(userId), sense, a.mode(),
        result.correct(), a.durationMs()));
    if (!result.correct()) {
      // Chỉ áp dụng cho nghĩa user ĐÃ học (có tiến độ). Nghĩa chưa học thì để hàng đợi "thẻ mới" lo.
      progressRepository.findByUserIdAndSenseId(userId, sense.getId())
          .ifPresent(p -> p.dueSoon(LocalDate.now(clock)));
    }
    return result;
  }

  // ===================== helpers (static, package-private để test thẳng) =====================

  /** Tách câu thành mảnh theo khoảng trắng; dấu câu dính theo chữ ("lot."). */
  static List<String> tokens(String sentence) {
    String trimmed = sentence == null ? "" : sentence.strip();
    return trimmed.isEmpty() ? List.of() : List.of(trimmed.split("\\s+"));
  }

  /**
   * Xáo trộn, và đảm bảo KHÁC thứ tự gốc (nếu có thể): xáo ra đúng câu gốc thì câu hỏi vô nghĩa.
   * Nhận Random làm tham số (giống Clock) để test truyền new Random(seed) cố định.
   */
  static List<String> shuffled(List<String> tokens, Random random) {
    List<String> copy = new ArrayList<>(tokens);
    if (new HashSet<>(tokens).size() < 2) {
      return copy; // mọi mảnh giống nhau: không có cách xáo nào khác
    }
    do {
      Collections.shuffle(copy, random);
    } while (copy.equals(tokens));
    return copy;
  }

  /**
   * So sánh "lỏng": bỏ khoảng trắng thừa, không phân biệt hoa thường, bỏ dấu câu ở cuối,
   * đổi dấu nháy cong ’ thành '. "Can't" và "can’t" là một; "a lot." và "A lot" là một.
   */
  static String normalize(String value) {
    if (value == null) {
      return "";
    }
    return value.replace('’', '\'')
        .strip()
        .replaceAll("\\s+", " ")
        .replaceAll("[.,!?;:]+$", "")
        .toLowerCase(Locale.ROOT);
  }

  /** Xáo danh sách, mỗi nghĩa lấy tối đa 1 câu (tránh 3 câu liền nhau cùng một từ), lấy count câu. */
  private static <T> List<T> pickOnePerSense(List<T> candidates, int count, Function<T, Long> senseOf) {
    List<T> pool = new ArrayList<>(candidates);
    Collections.shuffle(pool, ThreadLocalRandom.current());
    int limit = Math.clamp(count, 1, MAX_QUESTIONS);
    Set<Long> usedSenses = new HashSet<>();
    List<T> picked = new ArrayList<>();
    for (T question : pool) {
      if (picked.size() == limit) {
        break;
      }
      if (usedSenses.add(senseOf.apply(question))) {
        picked.add(question);
      }
    }
    return picked;
  }

  private static BusinessException notPracticable() {
    return new BusinessException(HttpStatus.BAD_REQUEST, "NOT_PRACTICABLE", "Câu ví dụ này không dùng để luyện được");
  }
}
