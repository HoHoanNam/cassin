package com.cassin.report;

import java.time.Clock;
import java.time.Instant;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cassin.common.BusinessException;
import com.cassin.common.dto.PageResponse;
import com.cassin.report.dto.AdminReportResponse;
import com.cassin.report.dto.ReviewReportRequest;
import com.cassin.user.User;
import com.cassin.user.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Phần của ADMIN: xem danh sách báo lỗi (lọc theo trạng thái) và xử lý từng báo cáo.
 * Chỉ ADMIN gọi được vì controller nằm dưới /api/admin/** (SecurityConfig: hasRole("ADMIN")).
 *
 * Luật (đánh số như trong tài liệu thiết kế):
 *   6. admin chỉ chuyển sang RESOLVED hoặc REJECTED → 400 INVALID_STATUS
 *   7. từ chối (REJECTED) phải có adminNote         → 400 NOTE_REQUIRED
 *   +  báo cáo không còn OPEN thì không xử lý lại    → 409 REPORT_CLOSED
 *
 * Luật 7 và "đã đóng" nằm trong ContentReport (reject / close). Service chỉ gọi rồi đổi exception
 * Java sang lỗi HTTP, cùng cách ReviewService đổi lỗi của CardProgress.
 */
@Service
@RequiredArgsConstructor
public class AdminReportService {
  private static final int MAX_PAGE_SIZE = 50;

  private final ContentReportRepository reportRepository;
  private final UserRepository userRepository;
  private final ReportMapper mapper;
  private final Clock clock;

  /**
   * GET /api/admin/reports?status=&page=&size=
   * status = null → mọi trạng thái. Mới nhất trước.
   *
   * search() đã join fetch reporter, sense, word, resolvedBy → mapper đọc các quan hệ đó KHÔNG sinh thêm query.
   * Sort được Spring Data nối vào cuối câu JPQL thành "order by r.createdAt desc".
   */
  @Transactional(readOnly = true)
  public PageResponse<AdminReportResponse> list(ReportStatus status, int page, int size) {
    Pageable pageable = PageRequest.of(
        Math.max(page, 0),
        Math.clamp(size, 1, MAX_PAGE_SIZE),
        Sort.by(Sort.Direction.DESC, "createdAt"));
    return PageResponse.from(reportRepository.search(status, pageable).map(mapper::toAdminResponse));
  }

  /**
   * PATCH /api/admin/reports/{reportId}
   *
   * Không gọi save(): report là entity được Hibernate theo dõi (managed) trong transaction này.
   * Đổi field xong, cuối transaction Hibernate tự so sánh và chạy UPDATE (dirty checking).
   */
  @Transactional
  public AdminReportResponse review(Long reportId, ReviewReportRequest request, Long adminId) {
    ContentReport report = reportRepository.findById(reportId)
        .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "Không tìm thấy báo cáo"));

    // getReferenceById: không SELECT, chỉ tạo "đại diện" mang id để ghi cột resolved_by.
    // Mapper đọc admin.getDisplayName() ở cuối → lúc đó Hibernate mới SELECT user (vẫn trong transaction nên được).
    User admin = userRepository.getReferenceById(adminId);
    Instant now = Instant.now(clock);

    try {
      // switch trên enum: thiếu case thì rơi vào default. @NotNull ở DTO đã chặn null, OPEN chặn ở đây (luật 6).
      switch (request.status()) {
        case RESOLVED -> report.resolve(admin, request.adminNote(), now);
        case REJECTED -> report.reject(admin, request.adminNote(), now);
        default -> throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_STATUS",
            "Chỉ chuyển được sang RESOLVED hoặc REJECTED");
      }
    } catch (IllegalStateException ex) {
      // close(): báo cáo không còn OPEN (đã có admin khác xử lý trước)
      throw new BusinessException(HttpStatus.CONFLICT, "REPORT_CLOSED", ex.getMessage());
    } catch (IllegalArgumentException ex) {
      // reject(): thiếu lý do từ chối (luật 7)
      throw new BusinessException(HttpStatus.BAD_REQUEST, "NOTE_REQUIRED", ex.getMessage());
    }
    // ⚠ Hai admin bấm xử lý CÙNG LÚC: cả hai đọc thấy OPEN, cùng qua close(), người lưu sau đè người trước
    //   (lost update). Sửa triệt để: thêm cột version + @Version vào ContentReport (câu 10 trong tài liệu).
    return mapper.toAdminResponse(report);
  }

  /** GET /api/admin/reports/open-count: số báo cáo đang chờ, cho huy hiệu trên menu admin. */
  @Transactional(readOnly = true)
  public long countOpen() {
    return reportRepository.countByStatus(ReportStatus.OPEN);
  }
}
