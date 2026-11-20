package com.cassin.passage;

/** Điểm cao nhất của một học viên ở một bài (projection: JPQL tạo thẳng record này, không tải entity). */
public record PassageBestScore(Long passageId, Integer bestScore) {
}
