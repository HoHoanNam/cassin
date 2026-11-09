package com.cassin.report;

import java.time.Clock;
import java.time.Instant;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cassin.common.BusinessException;
import com.cassin.common.dto.PageResponse;
import com.cassin.report.dto.CreateReportRequest;
import com.cassin.report.dto.ReportResponse;
import com.cassin.user.UserRepository;
import com.cassin.vocab.Sense;
import com.cassin.vocab.WordService;

import lombok.RequiredArgsConstructor;

/**
 * Phần của HỌC VIÊN: gửi, xem, rút báo lỗi. Phần admin nằm ở AdminReportService (chưa làm).
 *
 * Luật (đánh số như trong tài liệu thiết kế):
 *   1. nghĩa phải tồn tại và đọc được           → 404 SENSE_NOT_FOUND (WordService ném)
 *   2. chỉ báo từ HỆ THỐNG                       → 400 REPORT_OWN_WORD
 *   3. không 2 báo cáo đang mở cho cùng nghĩa    → 409 REPORT_EXISTS
 *   4. chỉ rút báo cáo của chính mình            → 404 REPORT_NOT_FOUND
 *   5. chỉ rút báo cáo đang OPEN                 → 409 REPORT_CLOSED
 */
@Service
@RequiredArgsConstructor
public class ReportService {
  private static final int MAX_PAGE_SIZE = 50;

  // [SỬA] Thêm final. @RequiredArgsConstructor chỉ đưa field FINAL vào constructor.
  // Thiếu final → Spring không inject → reportRepository = null → MỌI API của module ném NullPointerException (500).
  private final ContentReportRepository reportRepository;
  private final WordService wordService;
  private final UserRepository userRepository;
  private final ReportMapper mapper;
  private final Clock clock;

  @Transactional
  public ReportResponse create(CreateReportRequest request, Long userId) {
    Sense sense = wordService.findReadableSense(request.senseId(), userId); // luật 1

    // [SỬA] "== false" → "!", mã lỗi theo thiết kế, thêm câu thông báo.
    // BusinessException(..., null) → client nhận "message": null, FE không có gì để hiển thị.
    if (!sense.getWord().isSystem()) { // luật 2
      throw new BusinessException(HttpStatus.BAD_REQUEST, "REPORT_OWN_WORD",
          "Đây là từ của bạn, bạn có thể tự sửa trực tiếp");
    }

    // [SỬA] "ALREADY_REPORTERD" (sai chính tả) → "REPORT_EXISTS".
    // Cùng một tình huống (đã báo rồi) phải trả CÙNG một mã, dù bị chặn ở đây hay ở unique index bên dưới.
    // Hai mã khác nhau → FE phải xử lý hai trường hợp cho một lỗi.
    if (reportRepository.existsByReporterIdAndSenseIdAndStatus(userId, sense.getId(), ReportStatus.OPEN)) { // luật 3
      throw reportExists();
    }

    ContentReport report = ContentReport.open(
        userRepository.getReferenceById(userId), sense, request.reason(), request.message(), Instant.now(clock));
    try {
      // saveAndFlush: ép INSERT chạy NGAY để lỗi unique index nổ trong try này,
      // không đợi tới lúc commit (khi đó không còn bắt được để đổi thành REPORT_EXISTS).
      reportRepository.saveAndFlush(report);
    } catch (DataIntegrityViolationException ex) {
      // Hai request cùng lúc: cả hai qua existsBy (chưa ai lưu), request sau bị uq_reports_open_per_user_sense chặn.
      throw reportExists();
    }
    return mapper.toResponse(report);
  }

  @Transactional(readOnly = true)
  public PageResponse<ReportResponse> listMine(int page, int size, Long userId) {
    // [SỬA] Math.clamp(1, size, MAX) → Math.clamp(size, 1, MAX).
    // Thứ tự tham số là clamp(GIÁ TRỊ, min, max). Bản cũ coi 1 là giá trị, size là min:
    // size = 60 → min 60 > max 50 → IllegalArgumentException → 500.
    // [SỬA] page âm → PageRequest.of ném IllegalArgumentException (500) → Math.max(page, 0).
    // [SỬA] Thêm sắp xếp mới nhất trước: không có ORDER BY thì DB trả thứ tự tùy ý, trang 2 có thể lặp dòng trang 1.
    Pageable pageable = PageRequest.of(
        Math.max(page, 0),
        Math.clamp(size, 1, MAX_PAGE_SIZE),
        Sort.by(Sort.Direction.DESC, "createdAt"));
    return PageResponse.from(reportRepository.findByReporterId(userId, pageable).map(mapper::toResponse));
  }

  @Transactional
  public void withdraw(Long reportId, Long userId) {
    // [SỬA] Không có báo cáo và báo cáo CỦA NGƯỜI KHÁC trả CÙNG MỘT lỗi 404 REPORT_NOT_FOUND (luật 4).
    // Bản cũ trả "USER_NOT_FOUND" cho trường hợp thứ hai → kẻ xấu thử từng id sẽ phân biệt được
    // "id này có tồn tại nhưng không phải của tôi" (IDOR). Gộp vào một filter thì hai trường hợp giống hệt nhau.
    ContentReport report = reportRepository.findById(reportId)
        .filter(r -> r.isReportedBy(userId))
        .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "Không tìm thấy báo cáo"));

    // [THÊM] Luật 5: báo cáo đã được admin xử lý là lịch sử, không cho xóa.
    if (!report.isOpen()) {
      throw new BusinessException(HttpStatus.CONFLICT, "REPORT_CLOSED", "Báo cáo này đã được xử lý, không thể rút");
    }
    reportRepository.delete(report);
  }

  // [THÊM] Dùng chung cho cả hai chỗ chặn báo trùng.
  private static BusinessException reportExists() {
    return new BusinessException(HttpStatus.CONFLICT, "REPORT_EXISTS", "Bạn đã báo lỗi nghĩa này rồi");
  }
}
