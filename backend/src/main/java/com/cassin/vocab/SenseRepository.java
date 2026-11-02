package com.cassin.vocab;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Truy vấn theo NGHĨA (đơn vị được ôn / luyện).
 * Thêm / sửa / xóa nghĩa KHÔNG làm ở đây: đi qua Word.replaceSenses + WordRepository.
 *
 * Tách 2 phiên bản "trong bộ" / "trong sổ từ" thay vì một câu có ":deckId is null or ...":
 * mỗi câu đơn giản hơn, PostgreSQL lập kế hoạch truy vấn tốt hơn, và tránh lỗi tham số null.
 */
public interface SenseRepository extends JpaRepository<Sense, Long> {

  /** Nghĩa MỚI (user chưa có tiến độ) của các từ trong một bộ. Từ thêm vào bộ trước học trước. */
  @Query("""
      select s from Sense s join fetch s.word w
      where exists (select 1 from Deck d join d.words dw where d.id = :deckId and dw = w)
        and not exists (select 1 from CardProgress p where p.sense = s and p.user.id = :userId)
      order by w.createdAt, w.id, s.position
      """)
  List<Sense> findNewInDeck(@Param("userId") Long userId, @Param("deckId") Long deckId, Pageable limit);

  /**
   * Nghĩa MỚI trong sổ từ: cùng 3 nguồn với WordRepository.findNotebook.
   * Nguồn (3) giúp học tiếp nghĩa còn lại: đã học "experience (n)" thì "experience (v)" sẽ được gợi ý.
   */
  @Query("""
      select s from Sense s join fetch s.word w
      where not exists (select 1 from CardProgress p where p.sense = s and p.user.id = :userId)
        and (w.owner.id = :userId
             or (w.owner is null and exists (
                   select 1 from Deck d join d.words dw where dw = w and d.owner.id = :userId))
             or (w.owner is null and exists (
                   select 1 from CardProgress p2 where p2.sense.word = w and p2.user.id = :userId)))
      order by w.createdAt, w.id, s.position
      """)
  List<Sense> findNewInNotebook(@Param("userId") Long userId, Pageable limit);

  /** Số nghĩa mới trong một bộ (trang chi tiết bộ). */
  @Query("""
      select count(s) from Sense s
      where exists (select 1 from Deck d join d.words dw where d.id = :deckId and dw = s.word)
        and not exists (select 1 from CardProgress p where p.sense = s and p.user.id = :userId)
      """)
  long countNewInDeck(@Param("deckId") Long deckId, @Param("userId") Long userId);

  /**
   * Nghĩa CÓ câu ví dụ trong một bộ (nguồn câu hỏi luyện tập).
   * size(s.examples): hàm của HQL, đếm số phần tử collection (Hibernate dịch thành subquery count).
   */
  @Query("""
      select s from Sense s join fetch s.word w
      where size(s.examples) > 0
        and exists (select 1 from Deck d join d.words dw where d.id = :deckId and dw = w)
      """)
  List<Sense> findPracticeInDeck(@Param("deckId") Long deckId);

  /** Nghĩa CÓ câu ví dụ trong sổ từ của user (cùng 3 nguồn). */
  @Query("""
      select s from Sense s join fetch s.word w
      where size(s.examples) > 0
        and (w.owner.id = :userId
             or (w.owner is null and exists (
                   select 1 from Deck d join d.words dw where dw = w and d.owner.id = :userId))
             or (w.owner is null and exists (
                   select 1 from CardProgress p where p.sense.word = w and p.user.id = :userId)))
      """)
  List<Sense> findPracticeInNotebook(@Param("userId") Long userId);
}
