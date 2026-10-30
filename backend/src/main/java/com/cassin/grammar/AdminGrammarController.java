package com.cassin.grammar;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.cassin.grammar.dto.AdminQuestionResponse;
import com.cassin.grammar.dto.QuestionRequest;
import com.cassin.grammar.dto.TopicDetailResponse;
import com.cassin.grammar.dto.TopicRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminGrammarController {
  private final AdminGrammarService adminGrammarService;

  /**
   * TOPIC
   */
  @PostMapping("/topics")
  @ResponseStatus(HttpStatus.CREATED)
  public TopicDetailResponse createTopic(@Valid @RequestBody TopicRequest request) {
    return adminGrammarService.createTopic(request);
  }

  @PutMapping("/topics/{topicId}")
  public TopicDetailResponse updateTopic(
      @PathVariable Long topicId,
      @Valid @RequestBody TopicRequest request) {
    return adminGrammarService.updateTopic(topicId, request);
  }

  @DeleteMapping("/topics/{topicId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteTopic(@PathVariable Long topicId) {
    adminGrammarService.deleteTopic(topicId);
  }

  /**
   * QUESTION
   */
  @GetMapping("/topics/{topicId}/questions")
  public List<AdminQuestionResponse> listQuestions(@PathVariable Long topicId) {
    return adminGrammarService.listQuestions(topicId);
  }

  @PostMapping("/topics/{topicId}/questions")
  @ResponseStatus(HttpStatus.CREATED)
  public AdminQuestionResponse addQuestion(
      @PathVariable Long topicId,
      @Valid @RequestBody QuestionRequest request) {
    return adminGrammarService.addQuestion(topicId, request);
  }

  @PutMapping("/questions/{questionId}")
  public AdminQuestionResponse updateQuestion(
      @PathVariable Long questionId,
      @Valid @RequestBody QuestionRequest request) {
    return adminGrammarService.updateQuestion(questionId, request);
  }

  @DeleteMapping("/questions/{questionId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteQuestion(@PathVariable Long questionId) {
    adminGrammarService.deleteQuestion(questionId);
  }
}
