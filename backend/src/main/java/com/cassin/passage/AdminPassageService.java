package com.cassin.passage;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cassin.common.BusinessException;
import com.cassin.common.dto.PageResponse;
import com.cassin.passage.dto.AdminPassageResponse;
import com.cassin.passage.dto.AdminPassageSummary;
import com.cassin.passage.dto.PassageRequest;
import com.cassin.passage.dto.QuestionRequest;
import com.cassin.user.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Phần của ADMIN: soạn bài đọc và câu hỏi, publish, lưu trữ, xóa.
 *
 * Luật:
 *   1. bài không tồn tại                              → 404 PASSAGE_NOT_FOUND
 *   2. dữ liệu sai luật của entity (thiếu "____"...)  → 400 INVALID_PASSAGE (kèm "Câu 3: ...")
 *   3. bài ĐÃ CÓ người làm: không sửa, không xóa      → 409 PASSAGE_LOCKED (chỉ lưu trữ được)
 *   4. sai trạng thái (publish bài đã publish...)     → 409 PASSAGE_STATUS
 */
@Service
@RequiredArgsConstructor
public class AdminPassageService {
  private static final int MAX_PAGE_SIZE = 50;

  private final PassageRepository passageRepository;
  private final ReadingAttemptRepository attemptRepository;
  private final UserRepository userRepository;
  private final PassageMapper mapper;
  private final Clock clock;

  @Transactional(readOnly = true)
  public PageResponse<AdminPassageSummary> list(PassageStatus status, int page, int size) {
    Pageable pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
        Sort.by(Sort.Direction.DESC, "updatedAt", "id"));
    Page<Passage> result = status == null
        ? passageRepository.findAll(pageable)
        : passageRepository.findByStatus(status, pageable);
    return PageResponse.from(result.map(mapper::toAdminSummary));
  }

  @Transactional(readOnly = true)
  public AdminPassageResponse get(Long passageId) {
    Passage passage = findPassage(passageId);
    return mapper.toAdmin(passage, attemptRepository.existsByPassageId(passageId));
  }

  @Transactional
  public AdminPassageResponse create(PassageRequest request, Long adminId) {
    Instant now = Instant.now(clock);
    Passage passage = guard(() -> {
      Passage p = Passage.draft(request.title(), request.content(), request.level(),
          userRepository.getReferenceById(adminId), now);
      addQuestions(p, request.questions(), now);
      return p;
    });
    // save NGOÀI guard: guard chỉ bọc chỗ entity kiểm tra dữ liệu (cùng lý do như AdminGrammarService).
    passageRepository.saveAndFlush(passage); // flush để câu hỏi, lựa chọn có id trước khi map sang response
    return mapper.toAdmin(passage, false);
  }

  /** Thay TOÀN BỘ nội dung và câu hỏi. Chỉ khi chưa ai làm bài. */
  @Transactional
  public AdminPassageResponse update(Long passageId, PassageRequest request) {
    Passage passage = findPassage(passageId);
    requireNotLocked(passageId);
    Instant now = Instant.now(clock);

    guard(() -> {
      passage.clearQuestions(now);
      return passage;
    });
    // Ép DELETE câu cũ chạy NGAY. Không flush thì lúc commit Hibernate INSERT câu mới trước,
    // đụng UNIQUE (passage_id, position) của câu cũ chưa xóa (xem Passage.clearQuestions).
    passageRepository.flush();

    guard(() -> {
      passage.update(request.title(), request.content(), request.level(), now);
      addQuestions(passage, request.questions(), now);
      if (passage.isPublished()) {
        passage.requireQuestions(); // bài đang hiện cho học viên không được thành bài rỗng
      }
      return passage;
    });
    passageRepository.flush(); // INSERT câu mới để có id trong response
    return mapper.toAdmin(passage, false);
  }

  @Transactional
  public AdminPassageResponse publish(Long passageId) {
    Passage passage = findPassage(passageId);
    guard(() -> {
      passage.publish(Instant.now(clock));
      return passage;
    });
    return mapper.toAdmin(passage, attemptRepository.existsByPassageId(passageId));
  }

  /** Ẩn bài khỏi danh sách học viên, giữ nguyên lịch sử làm bài. */
  @Transactional
  public AdminPassageResponse archive(Long passageId) {
    Passage passage = findPassage(passageId);
    guard(() -> {
      passage.archive(Instant.now(clock));
      return passage;
    });
    return mapper.toAdmin(passage, attemptRepository.existsByPassageId(passageId));
  }

  @Transactional
  public void delete(Long passageId) {
    Passage passage = findPassage(passageId);
    requireNotLocked(passageId);
    passageRepository.delete(passage);
  }

  // ─────────────── helpers ───────────────

  private void addQuestions(Passage passage, List<QuestionRequest> requests, Instant now) {
    for (QuestionRequest request : requests) {
      passage.addQuestion(mapper.toNewQuestion(passage, passage.getQuestions().size(), request, now), now);
    }
  }

  private void requireNotLocked(Long passageId) {
    if (attemptRepository.existsByPassageId(passageId)) {
      throw new BusinessException(HttpStatus.CONFLICT, "PASSAGE_LOCKED",
          "Bài đọc đã có học viên làm, không sửa hay xóa được nữa. Hãy lưu trữ bài và tạo bài mới.");
    }
  }

  private Passage findPassage(Long passageId) {
    return passageRepository.findById(passageId)
        .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "PASSAGE_NOT_FOUND", "Không tìm thấy bài đọc"));
  }

  /**
   * Entity báo lỗi bằng exception chuẩn của Java; đổi sang lỗi nghiệp vụ có mã HTTP:
   *   IllegalArgumentException (dữ liệu sai)  → 400 INVALID_PASSAGE
   *   IllegalStateException    (sai trạng thái) → 409 PASSAGE_STATUS
   */
  private static <T> T guard(Supplier<T> action) {
    try {
      return action.get();
    } catch (IllegalArgumentException ex) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_PASSAGE", ex.getMessage());
    } catch (IllegalStateException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, "PASSAGE_STATUS", ex.getMessage());
    }
  }
}
