package com.cassin.grammar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.cassin.common.BusinessException;
import com.cassin.grammar.dto.TopicDetailResponse;

@ExtendWith(MockitoExtension.class)
class GrammarServiceTest {
  @Mock GrammarTopicRepository topicRepository;
  @Mock QuestionRepository questionRepository;

  private GrammarService grammarService;

  @BeforeEach
  void setUp() {
    grammarService = new GrammarService(topicRepository, questionRepository);
  }

  @Test
  @DisplayName("Chi tiết chủ đề: kèm bài giải thích và số câu hỏi")
  void getTopic_returnsDetailWithQuestionCount() {
    GrammarTopic topic = GrammarFixtures.topic(1L, "Present Perfect");
    when(topicRepository.findById(1L)).thenReturn(Optional.of(topic));
    when(questionRepository.countByTopicId(1L)).thenReturn(12L);

    TopicDetailResponse detail = grammarService.getTopic(1L);

    assertThat(detail.title()).isEqualTo("Present Perfect");
    assertThat(detail.explanation()).isEqualTo("Giải thích Present Perfect");
    assertThat(detail.questionCount()).isEqualTo(12);
  }

  @Test
  @DisplayName("Chủ đề không tồn tại → 404 TOPIC_NOT_FOUND")
  void findTopic_notFound() {
    when(topicRepository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> grammarService.findTopic(1L))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
          assertThat(ex.getCode()).isEqualTo("TOPIC_NOT_FOUND");
        });
  }
}
