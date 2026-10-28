package com.cassin.grammar;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cassin.grammar.dto.TopicDetailResponse;
import com.cassin.grammar.dto.TopicSummary;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/grammar/topics")
@RequiredArgsConstructor
public class GrammarController {
  private final GrammarService grammarService;

  // required = false: không gửi ?level thì level = null → lấy tất cả.
  // Gửi ?level=B9 (không có trong enum) → Spring tự trả 400.
  @GetMapping
  public List<TopicSummary> list(@RequestParam(required = false) CefrLevel level) {
    return grammarService.listTopics(level);
  }

  @GetMapping("/{topicId}")
  public TopicDetailResponse get(@PathVariable Long topicId) {
    return grammarService.getTopic(topicId);
  }
}
