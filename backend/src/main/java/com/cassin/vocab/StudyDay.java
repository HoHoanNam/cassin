package com.cassin.vocab;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity 
@Table (name = "study_days")
@Getter 
@NoArgsConstructor (access = AccessLevel.PROTECTED)
public class StudyDay {
  @EmbeddedId 
  private StudyDayId id;
}
