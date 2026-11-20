package com.cassin.passage.dto;

import com.cassin.grammar.CefrLevel;

public record PassageView(Long id, String title, String content, CefrLevel level, int wordCount, int readingMinutes) {
}
