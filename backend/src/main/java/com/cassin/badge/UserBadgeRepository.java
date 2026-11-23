package com.cassin.badge;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UserBadgeRepository extends JpaRepository<UserBadge, UserBadgeId> {
  List<UserBadge> findByIdUserId(Long userId);

  @Query("select ub from UserBadge ub where ub.id.userId = :userId and ub.seenAt is null")
  List<UserBadge> findUnseen(@Param("userId") Long userId);

  /**
   * Cấp một cấp huy hiệu. "on conflict do nothing" (riêng PostgreSQL): đã có thì bỏ qua, không lỗi trùng khóa.
   * Trả 1 nếu VỪA cấp, 0 nếu đã có từ trước → service biết cấp nào là mới.
   * Cùng cách với StudyDayRepository.markStudied.
   */
  @Modifying
  @Transactional
  @Query(value = """
      insert into user_badges (user_id, badge_code, level, earned_at)
      values (:userId, :code, :level, :earnedAt)
      on conflict do nothing
      """, nativeQuery = true)
  int award(@Param("userId") Long userId, @Param("code") String code, @Param("level") short level,
      @Param("earnedAt") Instant earnedAt);

  /** Đã xem cấp N thì mọi cấp <= N của huy hiệu đó cũng tính là đã xem. Trả số dòng đổi. */
  @Modifying
  @Transactional
  @Query("""
      update UserBadge ub set ub.seenAt = :now
      where ub.id.userId = :userId and ub.id.badgeCode = :code and ub.id.level <= :level and ub.seenAt is null
      """)
  int markSeen(@Param("userId") Long userId, @Param("code") String code, @Param("level") short level,
      @Param("now") Instant now);
}
