package com.cassin.passage;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cassin.common.BusinessException;
import com.cassin.common.dto.PageResponse;
import com.cassin.grammar.CefrLevel;
import com.cassin.passage.dto.AnswerRequest;
import com.cassin.passage.dto.AttemptResponse;
import com.cassin.passage.dto.AttemptResultResponse;
import com.cassin.passage.dto.HintResponse;
import com.cassin.passage.dto.PassageSummary;
import com.cassin.passage.dto.SubmitRequest;
import com.cassin.user.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Phần của HỌC VIÊN: xem danh sách bài, làm bài, xin gợi ý, nộp, xem kết quả.
 *
 * Luật:
 *   1. chỉ thấy bài PUBLISHED; bài nháp / lưu trữ coi như không tồn tại → 404 PASSAGE_NOT_FOUND
 *   2. lượt làm của người khác coi như không tồn tại                      → 404 ATTEMPT_NOT_FOUND
 *   3. "Làm bài" khi đang có lượt dở → trả lại lượt đó (200), không tạo lượt mới
 *   4. đã nộp thì không xin gợi ý, không nộp lại                          → 409 ATTEMPT_SUBMITTED
 *   5. hết gợi ý                                                          → 409 NO_MORE_HINTS
 *   6. câu trả lời sai dữ liệu (câu lạ, gửi trùng, lựa chọn của câu khác)  → 400 INVALID_ANSWER
 */
@Service
@RequiredArgsConstructor
public class PassageService {
  private static final int MAX_PAGE_SIZE = 50;

  private final PassageRepository passageRepository;
  private final ReadingAttemptRepository attemptRepository;
  private final UserRepository userRepository;
  private final PassageMapper mapper;
  private final Clock clock;
  private final ApplicationEventPublisher publisher; // [THÊM] huy hiệu

  @Transactional(readOnly = true)
  public PageResponse<PassageSummary> list(CefrLevel level, int page, int size, Long userId) {
    Pageable pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
        Sort.by(Sort.Direction.DESC, "publishedAt", "id"));
    Page<Passage> passages = level == null
        ? passageRepository.findByStatus(PassageStatus.PUBLISHED, pageable)
        : passageRepository.findByStatusAndLevel(PassageStatus.PUBLISHED, level, pageable);

    List<Long> ids = passages.map(Passage::getId).getContent();
    Map<Long, Integer> bestScores = ids.isEmpty() ? Map.of()
        : attemptRepository.findBestScores(userId, ids).stream()
            .collect(Collectors.toMap(PassageBestScore::passageId, PassageBestScore::bestScore));
    return PageResponse.from(passages.map(p -> mapper.toSummary(p, bestScores.get(p.getId()))));
  }

  @Transactional
  public StartedAttempt start(Long passageId, Long userId) {
    Passage passage = passageRepository.findById(passageId)
        .filter(Passage::isPublished) // luật 1
        .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "PASSAGE_NOT_FOUND", "Không tìm thấy bài đọc"));

    Optional<ReadingAttempt> open =
        attemptRepository.findFirstByUserIdAndPassageIdAndSubmittedAtIsNullOrderByStartedAtDesc(userId, passageId);
    if (open.isPresent()) { // luật 3
      return new StartedAttempt(mapper.toAttempt(open.get()), false);
    }
    ReadingAttempt attempt = ReadingAttempt.start(userRepository.getReferenceById(userId), passage, Instant.now(clock));
    attemptRepository.save(attempt); // IDENTITY → INSERT ngay, cascade lưu luôn các câu trả lời trống
    return new StartedAttempt(mapper.toAttempt(attempt), true);
  }

  @Transactional(readOnly = true)
  public AttemptResponse getAttempt(Long attemptId, Long userId) {
    ReadingAttempt attempt = findOwnedAttempt(attemptId, userId);
    requireOpen(attempt);
    return mapper.toAttempt(attempt);
  }

  @Transactional
  public HintResponse revealHint(Long attemptId, Long questionId, Long userId) {
    ReadingAttempt attempt = findOwnedAttempt(attemptId, userId);
    requireOpen(attempt);
    ReadingHint hint = guard(() -> attempt.revealNextHint(questionId))
        .orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT, "NO_MORE_HINTS", "Câu này đã hết gợi ý")); // luật 5
    return mapper.toHint(attempt.answerOf(questionId), hint);
  }

  @Transactional
  public AttemptResultResponse submit(Long attemptId, SubmitRequest request, Long userId) {
    ReadingAttempt attempt = findOwnedAttempt(attemptId, userId);
    requireOpen(attempt);

    Map<Long, AnswerInput> inputs = new HashMap<>();
    for (AnswerRequest answer : request.answers()) {
      if (inputs.put(answer.questionId(), answer.toInput()) != null) { // luật 6
        throw invalidAnswer("Câu hỏi " + answer.questionId() + " được gửi hai lần");
      }
    }
    guard(() -> {
      attempt.submit(inputs, Instant.now(clock));
      return attempt;
    });
    // [THÊM] Huy hiệu (Mọt sách, Tự lực). Listener chạy sau khi lượt nộp này commit.
    publisher.publishEvent(new ReadingSubmittedEvent(userId, attempt.getPassage().getId(), attempt.getScore(),
        attempt.getTotal(), attempt.correctWithoutHints()));
    return mapper.toResult(attempt);
  }

  @Transactional(readOnly = true)
  public AttemptResultResponse result(Long attemptId, Long userId) {
    ReadingAttempt attempt = findOwnedAttempt(attemptId, userId);
    if (!attempt.isSubmitted()) {
      throw new BusinessException(HttpStatus.CONFLICT, "ATTEMPT_NOT_SUBMITTED", "Bạn chưa nộp bài");
    }
    return mapper.toResult(attempt);
  }

  // ─────────────── helpers ───────────────

  private ReadingAttempt findOwnedAttempt(Long attemptId, Long userId) {
    // Không tồn tại và CỦA NGƯỜI KHÁC trả cùng một lỗi → không ai dò được id nào có thật (luật 2).
    return attemptRepository.findById(attemptId)
        .filter(a -> a.isOwnedBy(userId))
        .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "ATTEMPT_NOT_FOUND", "Không tìm thấy lượt làm bài"));
  }

  private static void requireOpen(ReadingAttempt attempt) {
    if (attempt.isSubmitted()) { // luật 4
      throw new BusinessException(HttpStatus.CONFLICT, "ATTEMPT_SUBMITTED", "Lượt làm bài đã nộp, hãy xem kết quả");
    }
  }

  private static BusinessException invalidAnswer(String message) {
    return new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_ANSWER", message);
  }

  private static <T> T guard(Supplier<T> action) {
    try {
      return action.get();
    } catch (IllegalArgumentException ex) {
      throw invalidAnswer(ex.getMessage());
    }
  }
}
