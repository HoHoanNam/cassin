package com.cassin.vocab;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface StudyDayRepository extends JpaRepository<StudyDay, StudyDayId> {
  /**
   * "on conflict do nothing" (riêng PostgreSQL): hôm nay đã có dòng thì bỏ qua,
   * không lỗi trùng khóa.
   * Ôn 50 thẻ trong ngày = 1 dòng INSERT + 49 lần "bỏ qua", không cần SELECT kiểm
   * tra trước.
   * 
   * @Modifying: báo Spring đây là câu GHI (insert/update/delete), không phải câu
   *             đọc.
   *
   *             [THÊM] @Transactional: method tự khai báo trong repository mặc
   *             định KHÔNG có transaction ghi
   *             (Spring Data chỉ cấu hình sẵn cho các method có sẵn như save,
   *             delete). Hiện tại
   *             ReviewService.review đã mở transaction nên vẫn chạy. Nhưng ai gọi
   *             markStudied từ chỗ không có
   *             transaction (một job, một test tích hợp...) sẽ gặp
   *             TransactionRequiredException.
   *             Gắn ở đây: có transaction bên ngoài thì tham gia chung, không có
   *             thì tự mở.
   */
  @Modifying
  @Transactional
  @Query(value = """
          insert into study_days (user_id, study_date) values (:userId, :day)
          on conflict do nothing
      """, nativeQuery = true)
  void markStudied(@Param("userId") Long userId, @Param("day") LocalDate day);

  // JPQL (không phải native) nên Hibernate trả đúng kiểu LocalDate.
  @Query("select s.id.studyDate from StudyDay s where s.id.userId = :userId order by s.id.studyDate desc")
  List<LocalDate> recentDays(@Param("userId") Long userId, Pageable limit);

  long countByIdStudyDate(LocalDate studyDate); // id.studyDate (khóa ghép)

  /**
   * [THÊM] Chuỗi ngày học liên tiếp tính tới hôm nay, ĐÚNG con số trang Ôn tập đang hiện (ReviewStatsResponse).
   * Dùng lại ReviewService.streak (cùng package nên gọi được) thay vì viết lại luật đếm ở nơi khác:
   * huy hiệu Lửa bền và trang Ôn tập không bao giờ lệch nhau.
   *
   * default method: Spring Data chỉ sinh code cho method TRỪU TƯỢNG; method có thân thì proxy gọi thẳng thân này.
   */
  default int currentStreak(Long userId, LocalDate today) {
    return ReviewService.streak(recentDays(userId, PageRequest.of(0, ReviewService.MAX_STREAK_DAYS)), today);
  }
}
