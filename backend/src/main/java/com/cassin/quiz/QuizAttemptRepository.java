package com.cassin.quiz;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {
  /**
   * Lịch sử của tôi, mới nhất trước. Sắp xếp nằm sẵn trong tên method (OrderByStartedAtDesc)
   * nên không cần Sort viết bằng chuỗi. Khớp index idx_quiz_attempts_user_started (V7).
   *
   * @EntityGraph(attributePaths = "topic"): tải luôn chủ đề trong cùng câu SQL (join),
   * vì mỗi dòng lịch sử đều hiện tên chủ đề → tránh N+1.
   */
  // Tìm theo userId và sắp xếp theo thời gian bắt đầu làm bài mới nhất
  @EntityGraph(attributePaths = "topic")
  Page<QuizAttempt> findByUserIdOrderByStartedAtDesc(Long userId, Pageable pageable);
}

