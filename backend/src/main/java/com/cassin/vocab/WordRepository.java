package com.cassin.vocab;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WordRepository extends JpaRepository<Word, Long> {

  /**
   * "Sổ từ của tôi" = 3 nguồn gộp lại:
   *   (1) từ do tôi tạo                                       w.owner.id = :userId
   *   (2) từ hệ thống nằm trong một bộ CÁ NHÂN của tôi        exists ... d.owner.id = :userId
   *   (3) từ hệ thống tôi ĐÃ BẮT ĐẦU HỌC (có tiến độ ôn)      exists CardProgress ...
   *
   * :q = "" thì khớp mọi từ (like '%%'). Dùng chuỗi rỗng thay cho null: tham số null trong
   * "(:q is null or ...)" dễ gặp lỗi "could not determine data type of parameter" ở PostgreSQL.
   *
   * Không "join fetch w.senses" vì có phân trang (Hibernate sẽ phải tải hết rồi cắt trang trong RAM).
   * Nghĩa của 20 từ trên trang được tải bằng 1 câu nhờ default_batch_fetch_size: 50 (application.yml).
   * Spring Data tự sinh câu đếm tổng (select count(w) ...) từ câu này để làm Page.
   */
  @Query("""
      select w from Word w
      where lower(w.term) like lower(concat('%', :q, '%'))
        and (w.owner.id = :userId
             or (w.owner is null and exists (
                   select 1 from Deck d join d.words dw where dw = w and d.owner.id = :userId))
             or (w.owner is null and exists (
                   select 1 from CardProgress p where p.sense.word = w and p.user.id = :userId)))
      order by w.createdAt desc, w.id desc
      """)
  Page<Word> findNotebook(@Param("userId") Long userId, @Param("q") String q, Pageable pageable);

  /** Từ trong một bộ. Quyền xem bộ được kiểm tra ở service trước khi gọi. */
  @Query("""
      select w from Deck d join d.words w
      where d.id = :deckId and lower(w.term) like lower(concat('%', :q, '%'))
      order by w.term, w.id
      """)
  Page<Word> findInDeck(@Param("deckId") Long deckId, @Param("q") String q, Pageable pageable);

  /** Chống trùng từ của một học viên: "Light" và "light" là một (khớp uq_words_owner_term trong V10). */
  boolean existsByOwnerIdAndTermIgnoreCase(Long ownerId, String term);

  /** Chống trùng từ hệ thống (khớp uq_words_system_term trong V10). */
  boolean existsByOwnerIsNullAndTermIgnoreCase(String term);

  /** Số từ hệ thống (trang thống kê admin). */
  long countByOwnerIsNull();
}
