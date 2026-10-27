package com.cassin.vocab;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CardRepository extends JpaRepository<Card, Long> {
  // Spring tự sinh SQL từ tên method: ... where deck_id = ? (kèm limit/offset theo Pageable)
  Page<Card> findByDeckId(Long deckId, Pageable pageable);

  // Đếm số thẻ trong bộ
  long countByDeckId(Long deckId);

  /**
   * Thẻ MỚI của user trong bộ = thẻ chưa có dòng tiến độ nào của user đó.
   * NOT EXISTS: với mỗi thẻ c, DB kiểm tra "có dòng progress nào của user này cho c không?",
   * dừng ngay khi tìm thấy 1 dòng → nhanh, nhờ index unique (user_id, card_id).
   */
  @Query("""
    select c from Card c
    where c.deck.id = :deckId
      and not exists (
        select 1 from CardProgress p where p.card = c and p.user.id = :userId)
    order by c.createdAt, c.id
  """)
  List<Card> findNewCards(@Param("deckId") Long deckId, @Param("userId") Long userId, Pageable limit);
}
