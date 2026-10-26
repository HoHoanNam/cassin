package com.cassin.vocab;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardRepository extends JpaRepository<Card, Long> {
  // Spring tự sinh SQL từ tên method: ... where deck_id = ? (kèm limit/offset theo Pageable)
  Page<Card> findByDeckId(Long deckId, Pageable pageable);

  // Đếm số thẻ trong bộ
  long countByDeckId(Long deckId);
}
