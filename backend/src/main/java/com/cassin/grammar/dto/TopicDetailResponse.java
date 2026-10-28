package com.cassin.grammar.dto;

import com.cassin.grammar.CefrLevel;
import com.cassin.grammar.GrammarTopic;

/**
 * Chi tiết một chủ đề + số câu hỏi, TÙY CHỈNH DÀNH RIÊNG cho Grammar.
 *
 * Không dùng lại TopicSummary (thiếu explanation) và không dùng lại DeckDetail
 * (để tránh lặp code dto-level-3). Đây là version thứ 3: gọn, chỉ đủ cho ngữ pháp.
 */
public record TopicDetailResponse(
  Long id,
  String title,
  CefrLevel level,
  String explanation,
  long questionCount) 
{
  public static TopicDetailResponse from(GrammarTopic topic, long questionCount) {
    return new TopicDetailResponse(
      topic.getId(), 
      topic.getTitle(), 
      topic.getLevel(), 
      topic.getExplanation(), 
      questionCount
    );
  }
}
