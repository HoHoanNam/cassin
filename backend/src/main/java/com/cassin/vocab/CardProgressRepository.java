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

  // Spring tự sinh: select count(*) ... where user_id = ? and due_date <= ?
  long countByUserIdAndDueDateLessThanEqual(Long userId, LocalDate today);

  @Query("""
    select new com.cassin.vocab.dto.BoxCount(p.box, count(p))
    from CardProgress p where p.user.id = :userId
    group by p.box order by p.box
  """)
  List<BoxCount> countByBox(@Param("userId") Long userId);
}
