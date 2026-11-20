package com.cassin.passage;

/**
 * Loại đáp án của câu điền từ:
 *   WORDS               : chỉ chữ, tối đa maxWords từ          "NO MORE THAN TWO WORDS"
 *   NUMBER              : đúng một số (không có maxWords)       "A NUMBER"
 *   WORDS_AND_OR_NUMBER : tối đa maxWords từ và/hoặc một số     "NO MORE THAN TWO WORDS AND/OR A NUMBER"
 */
public enum AnswerKind {
  WORDS, NUMBER, WORDS_AND_OR_NUMBER
}
