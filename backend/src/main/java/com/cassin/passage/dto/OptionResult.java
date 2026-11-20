package com.cassin.passage.dto;

/** Lựa chọn sau khi nộp bài và trong trang admin: có correct. */
public record OptionResult(Long id, char label, String content, boolean correct) {
}
