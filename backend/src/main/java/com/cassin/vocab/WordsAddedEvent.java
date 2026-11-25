package com.cassin.vocab;

import com.cassin.badge.event.BadgeTrigger;

/**
 * Phát khi người học tự thêm từ MỚI vào sổ từ của mình: tạo một từ (WordService.createWord) hoặc nhập CSV
 * (WordImportService.importWords, chỉ khi nhập thật và có ít nhất một từ mới).
 *
 * Từ của hệ thống do admin thêm (AdminDeckService) KHÔNG phát: đó không phải từ người học tự sưu tầm.
 * count chỉ để log / mở rộng về sau; rule Nhà sưu tầm vẫn đếm lại từ bảng words.
 */
public record WordsAddedEvent(Long userId, int count) implements BadgeTrigger {
}
