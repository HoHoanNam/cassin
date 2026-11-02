package com.cassin.vocab;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cassin.vocab.dto.BoxCount;

/**
 * [V10] Tiến độ ôn theo NGHĨA (p.sense), không còn theo thẻ.
 * Tên class / bảng vẫn là CardProgress / card_progresses: đổi tên bảng chỉ để đẹp thì không đáng
 * (thêm một migration, sửa mọi câu truy vấn), trong khi "thẻ ôn" vẫn là cách gọi quen thuộc trên giao diện.
 */
public interface CardProgressRepository extends JpaRepository<CardProgress, Long> {

  // Dùng index unique (user_id, card_id → nay là sense_id) có sẵn từ V5.
  Optional<CardProgress> findByUserIdAndSenseId(Long userId, Long senseId);

  /**
   * Nghĩa đến hạn trong MỘT bộ, quá hạn lâu nhất lên trước.
   * "join fetch p.sense s join fetch s.word w": tải luôn nghĩa và từ trong cùng câu SQL (tránh N+1).
   */
  @Query("""
      select p from CardProgress p join fetch p.sense s join fetch s.word w
      where p.user.id = :userId and p.dueDate <= :today
        and exists (select 1 from Deck d join d.words dw where d.id = :deckId and dw = w)
      order by p.dueDate, p.box, s.id
      """)
  List<CardProgress> findDueInDeck(
      @Param("userId") Long userId,
      @Param("deckId") Long deckId,
      @Param("today") LocalDate today,
      Pageable limit);

  /**
   * Nghĩa đến hạn ở MỌI nơi ("ôn tất cả"). Chỉ lấy nghĩa của từ user còn xem được:
   * từ của mình hoặc từ hệ thống. (Trước V10 lọc theo bộ; giờ quyền nằm ở từ.)
   */
  @Query("""
      select p from CardProgress p join fetch p.sense s join fetch s.word w
      where p.user.id = :userId and p.dueDate <= :today
        and (w.owner is null or w.owner.id = :userId)
      order by p.dueDate, p.box, s.id
      """)
  List<CardProgress> findAllDue(@Param("userId") Long userId, @Param("today") LocalDate today, Pageable limit);

  /** Đếm cùng điều kiện với findAllDue: số "đến hạn hôm nay" khớp đúng hàng đợi. */
  @Query("""
      select count(p) from CardProgress p join p.sense s join s.word w
      where p.user.id = :userId and p.dueDate <= :today
        and (w.owner is null or w.owner.id = :userId)
      """)
  long countAllDue(@Param("userId") Long userId, @Param("today") LocalDate today);

  /** Số nghĩa đến hạn trong một bộ (trang chi tiết bộ). */
  @Query("""
      select count(p) from CardProgress p
      where p.user.id = :userId and p.dueDate <= :today
        and exists (select 1 from Deck d join d.words dw where d.id = :deckId and dw = p.sense.word)
      """)
  long countDueInDeck(@Param("deckId") Long deckId, @Param("userId") Long userId, @Param("today") LocalDate today);

  @Query("""
      select new com.cassin.vocab.dto.BoxCount(p.box, count(p))
      from CardProgress p where p.user.id = :userId
      group by p.box order by p.box
      """)
  List<BoxCount> countByBox(@Param("userId") Long userId);

  /** Bộ này đã có AI học chưa (chặn admin xóa bộ hệ thống). */
  @Query("""
      select case when count(p) > 0 then true else false end
      from CardProgress p
      where exists (select 1 from Deck d join d.words dw where d.id = :deckId and dw = p.sense.word)
      """)
  boolean existsInDeck(@Param("deckId") Long deckId);
}
