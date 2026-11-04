package com.cassin.practice.dto;

import com.cassin.practice.PracticeMode;

/**
 * Thống kê một chế độ luyện tập. Vừa là kết quả của JPQL (select new), vừa nằm trong response.
 * Long, không phải long: count / sum trong JPQL trả về Long (giống MistakeItem).
 */
public record ModeStat(PracticeMode mode, Long total, Long correct) {
}