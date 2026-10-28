package com.cassin.grammar;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cassin.grammar.dto.TopicSummary;

public interface GrammarTopicRepository extends JpaRepository<GrammarTopic, Long> {
  /**
   * Danh sách chủ đề + số câu hỏi, MỘT câu SQL (giống DeckRepository.findVisibleSummaries).
   *
   * "(:level is null or t.level = :level)": một câu truy vấn dùng cho cả 2 trường hợp
   *   - level = null  → vế đầu đúng với mọi dòng → lấy TẤT CẢ chủ đề
   *   - level = B1    → vế đầu sai, xét vế sau → chỉ lấy chủ đề B1
   * Hibernate đoán được kiểu của :level (CefrLevel) từ vế "t.level = :level",
   * nên truyền null vẫn bind đúng kiểu.
   *
   * order by t.level: enum lưu dạng chữ, "A1" < "A2" < "B1"... đúng thứ tự dễ → khó.
   */
  @Query("""
    select new com.cassin.grammar.dto.TopicSummary(t.id, t.title, t.level, count(q))
    from GrammarTopic t left join t.questions q
    where (:level is null or t.level = :level)
    group by t.id, t.title, t.level
    order by t.level, t.title
  """)
  List<TopicSummary> findSummaries(@Param("level") CefrLevel level);
}
