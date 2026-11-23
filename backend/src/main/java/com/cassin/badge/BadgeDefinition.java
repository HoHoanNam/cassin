package com.cassin.badge;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "badge_definitions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BadgeDefinition {
  @Id
  @Column(length = 40)
  private String code;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private BadgeKind kind;

  @Column(nullable = false, length = 80)
  private String name;

  @Column(nullable = false, length = 200)
  private String description;

  @Column(nullable = false, length = 30)
  private String unit;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private BadgeCategory category;

  @Column(nullable = false)
  private boolean secret;

  @Column(name = "sort_order", nullable = false, unique = true)
  private int sortOrder;
}
