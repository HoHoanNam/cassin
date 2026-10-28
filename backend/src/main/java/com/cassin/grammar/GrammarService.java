package com.cassin.grammar;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cassin.common.BusinessException;
import com.cassin.grammar.dto.TopicDetailResponse;
import com.cassin.grammar.dto.TopicSummary;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GrammarService {
  private final GrammarTopicRepository topicRepository;
  private final QuestionRepository questionRepository;

  /**
   * Trả về danh sách topic kèm số câu hỏi
   */
  @Transactional(readOnly = true)
  public List<TopicSummary> listTopics(CefrLevel level) {
    return topicRepository.findSummaries(level);
  }

  /**
   * Trả về chi tiết một topic kèm số câu hỏi
   */
  @Transactional(readOnly = true)
  public TopicDetailResponse getTopic(Long topicId) {
    GrammarTopic topic = findTopic(topicId);
    return TopicDetailResponse.from(topic, questionRepository.countByTopicId(topicId));
  }

  /**
   * Tìm topic theo id, không có thì throw ngoại lệ.
   * public: QuizService (package quiz, khác package) cũng cần dùng.
   */
  public GrammarTopic findTopic(Long topicId) {
    return topicRepository.findById(topicId)
      .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "TOPIC_NOT_FOUND", "Không tìm thấy chủ đề"));
  }
}

