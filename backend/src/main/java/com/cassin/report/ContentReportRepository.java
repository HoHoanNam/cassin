package com.cassin.report;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContentReportRepository extends JpaRepository<ContentReport, Long> {
  // Chặn báo trùng trước khi chạm unique index
  boolean existsByReporterIdAndSenseIdAndStatus(Long reporterId, Long senseId, ReportStatus status);

  // EntityGraph: lấy kèm sense + word trong CÙNG câu SELECT → mapper đọc term không sinh thêm query
  @EntityGraph(attributePaths = { "sense", "sense.word" })
  Page<ContentReport> findByReporterId(Long reporterId, Pageable pageable);

  @Query(value = """
       select r from ContentReport r
         join fetch r.reporter
         join fetch r.sense s
         join fetch s.word
         left join fetch r.resolvedBy
       where(:status is null or r.status = :status)
      """, countQuery = """
        select count(r) from ContentReport r
        where (:status is null or r.status = :status)
      """)
  Page<ContentReport> search(@Param("status") ReportStatus status, Pageable pageable);

  long countByStatus(ReportStatus status);
}
