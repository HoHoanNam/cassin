package com.cassin.passage;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.cassin.grammar.CefrLevel;

public interface PassageRepository extends JpaRepository<Passage, Long> {

  /** Danh sách cho học viên (status = PUBLISHED) và bộ lọc theo trạng thái cho admin. */
  Page<Passage> findByStatus(PassageStatus status, Pageable pageable);

  /** Học viên lọc thêm theo trình độ. Dùng index idx_passages_status_level_published. */
  Page<Passage> findByStatusAndLevel(PassageStatus status, CefrLevel level, Pageable pageable);
}
