package com.cassin.badge;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BadgeDefinitionRepository extends JpaRepository<BadgeDefinition, String> {
  List<BadgeDefinition> findAllByOrderBySortOrderAsc();
}
