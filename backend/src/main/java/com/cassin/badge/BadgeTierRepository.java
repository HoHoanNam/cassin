package com.cassin.badge;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BadgeTierRepository extends JpaRepository<BadgeTier, BadgeTierId> {
  /** Toàn bộ 35 cấp, theo mã rồi theo cấp. Đủ nhỏ để đọc hết một lần mỗi request. */
  @Query("select t from BadgeTier t order by t.id.badgeCode, t.id.level")
  List<BadgeTier> findAllOrdered();

  @Query("select t from BadgeTier t where t.id.badgeCode = :code order by t.id.level")
  List<BadgeTier> findByCode(@Param("code") String code);
}
