package com.cassin.report.dto;

import java.time.Instant;

import com.cassin.report.ReportReason;
import com.cassin.report.ReportStatus;

public record ReportResponse(
    Long id,
    Long senseId,
    String term,
    String definition,
    ReportReason reason,
    String message,
    ReportStatus status,
    String adminNote,
    Instant createdAt,
    Instant resolvedAt) {
}
