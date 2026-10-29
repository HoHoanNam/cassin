package com.cassin.vocab;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cassin.vocab.dto.BoxCount;

public interface CardProgressRepository extends JpaRepository<CardProgress, Long> {
  // Tra tiến độ của 1 user với 1 thẻ. Dùng index unique (user_id, card_id) có sẵn trong V5.
  Optional<CardProgress> findByUserIdAndCardId(Long userId, Long cardId);
  
  /**
   * Thẻ đến hạn trong một bộ, quá hạn lâu nhất lên trước.
   * "join fetch p.card": lấy luôn thẻ trong cùng câu SQL. Không có nó, mỗi lần gọi
   * p.getCard().getWord() là thêm 1 câu SQL (N+1).
   * Pageable ở đây chỉ dùng để giới hạn số dòng (LIMIT), không cần biết tổng số trang
   * → trả về List thay vì Page, đỡ một câu count(*).
   */
  @Query("""
    select p from CardProgress p join fetch p.card c
    where p.user.id = :userId and c.deck.id = :deckId and p.dueDate <= :today
    order by p.dueDate, p.box, c.id
  """)
  List<CardProgress> findDueInDeck(
    @Param("userId") Long userId, 
    @Param("deckId") Long deckId, 
    @Param("today") LocalDate today, 
    Pageable limit
  );

  // [SỬA] Bỏ countByUserIdAndDueDateLessThanEqual: nó không lọc bộ đọc được nên lệch số với
  // findAllDue (xem ReviewService.getStats). Thay bằng countAllDue ở cuối file.

  @Query("""
    select new com.cassin.vocab.dto.BoxCount(p.box, count(p))
    from CardProgress p where p.user.id = :userId
    group by p.box order by p.box
  """)
  List<BoxCount> countByBox(@Param("userId") Long userId);

  // Đếm số thẻ đến hạn trong một bộ
  // [GHI CHÚ] Thứ tự tham số ở đây là (deckId, userId, ...), còn findDueInDeck là (userId, deckId, ...).
  // Cả hai đều là Long nên truyền NGƯỢC vẫn biên dịch được, chỉ ra số sai. Mình giữ nguyên tên/thứ tự
  // bạn đặt; DeckServiceTest đã kiểm tra đúng thứ tự gọi. Nếu muốn an toàn hơn: thống nhất một thứ tự.
  @Query("""
      select count(p) from CardProgress p
      where p.user.id = :userId and p.card.deck.id = :deckId and p.dueDate <= :today
  """)
  long countDueInDeck(@Param("deckId") Long deckId, @Param("userId") Long userId, @Param("today") LocalDate today);

  // Đếm số thẻ mới trong một bộ 
  @Query("""
      select count(c) from Card c
      where c.deck.id = :deckId
        and not exists (select 1 from CardProgress p where p.card = c and p.user.id = :userId)
  """)
  long countNewInDeck(@Param("deckId") Long deckId, @Param("userId") Long userId);

  /**
   * Thẻ đến hạn của user ở MỌI bộ đọc được, quá hạn lâu nhất lên trước.
   * Vẫn phải lọc bộ (hệ thống hoặc của mình): nếu sau này có tính năng "chuyển bộ sang riêng tư",
   * progress cũ của người khác không được lọt vào hàng đợi.
   */
  @Query("""
      select p from CardProgress p join fetch p.card c join c.deck d
      where p.user.id = :userId and p.dueDate <= :today
        and (d.deckType = com.cassin.vocab.DeckType.SYSTEM_DECK or d.owner.id = :userId)
      order by p.dueDate, p.box, c.id
  """)
  List<CardProgress> findAllDue(@Param("userId") Long userId, @Param("today") LocalDate today, Pageable limit);

  /**
   * [THÊM] Đếm thẻ đến hạn ở mọi bộ đọc được: WHERE giống hệt findAllDue.
   * Số "đến hạn hôm nay" trên trang Hôm nay = đúng số thẻ hàng đợi /api/review-queue trả về
   * (trừ khi vượt giới hạn 100 thẻ/lượt).
   */
  @Query("""
      select count(p) from CardProgress p join p.card c join c.deck d
      where p.user.id = :userId and p.dueDate <= :today
        and (d.deckType = com.cassin.vocab.DeckType.SYSTEM_DECK or d.owner.id = :userId)
  """)
  long countAllDue(@Param("userId") Long userId, @Param("today") LocalDate today);
}


