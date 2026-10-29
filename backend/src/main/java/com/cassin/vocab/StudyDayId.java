package com.cassin.vocab;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Khóa chính gồm 2 cột. Dùng record: tự có equals/hashCode theo cả 2 field,
 * điều JPA bắt buộc với khóa chính ghép (Hibernate 6.2+ hỗ trợ record làm @Embeddable).
 */
@Embeddable 
public record StudyDayId(
    @Column (name = "user_id") Long userId,
    @Column(name = "study_date") LocalDate studyDate
) {}

