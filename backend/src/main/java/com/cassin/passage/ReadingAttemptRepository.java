package com.cassin.passage;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReadingAttemptRepository extends JpaRepository<ReadingAttempt, Long> {

  /** Bài đã có người làm → khóa: không sửa câu hỏi, không xóa (chỉ lưu trữ). */
  boolean existsByPassageId(Long passageId);

  /** Lượt đang làm dở (chưa nộp) mới nhất → bấm "Làm bài" lần nữa thì làm tiếp lượt này. */
  Optional<ReadingAttempt> findFirstByUserIdAndPassageIdAndSubmittedAtIsNullOrderByStartedAtDesc(
      Long userId, Long passageId);

  /**
   * Điểm cao nhất của học viên cho cả TRANG bài đọc trong MỘT câu SQL
   * (thay vì hỏi từng bài → N+1). Bài chưa nộp lần nào không có dòng nào.
   */
  @Query("""
      select new com.cassin.passage.PassageBestScore(a.passage.id, max(a.score))
      from ReadingAttempt a
      where a.user.id = :userId and a.passage.id in :passageIds and a.submittedAt is not null
      group by a.passage.id
      """)
  List<PassageBestScore> findBestScores(@Param("userId") Long userId,
      @Param("passageIds") Collection<Long> passageIds);
}
