package com.cassin.passage;

/** Một cặp diễn đạt lại: cụm trong câu hỏi ≈ cụm trong bài. Record domain, không dính annotation web. */
public record HintInput(String questionPhrase, String passagePhrase) {
}
