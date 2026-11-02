package com.cassin.admin;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cassin.admin.dto.AdminStatsResponse;
import com.cassin.grammar.GrammarTopicRepository;
import com.cassin.grammar.QuestionRepository;
import com.cassin.quiz.QuizAttemptRepository;
import com.cassin.user.UserRepository;
import com.cassin.vocab.DeckRepository;
import com.cassin.vocab.DeckType;
import com.cassin.vocab.StudyDayRepository;
import com.cassin.vocab.WordRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminStatsService {
  private final UserRepository userRepository;
  private final DeckRepository deckRepository;
  // [V10] CardRepository → WordRepository (bảng cards đã thành senses, thẻ giờ là từ + nhiều nghĩa).
  private final WordRepository wordRepository;
  private final GrammarTopicRepository topicRepository;
  private final QuestionRepository questionRepository;
  private final QuizAttemptRepository attemptRepository;
  private final StudyDayRepository studyDayRepository;
  private final Clock clock;

  @Transactional(readOnly = true)
  public AdminStatsResponse getStats() {
    return new AdminStatsResponse(
        userRepository.count(),
        deckRepository.countByDeckType(DeckType.SYSTEM_DECK),
        // [V10] Giữ tên field systemCards để không đổi hợp đồng với frontend;
        // giá trị giờ là SỐ TỪ hệ thống (một từ nhiều nghĩa chỉ tính 1).
        wordRepository.countByOwnerIsNull(),
        topicRepository.count(),
        questionRepository.count(),
        attemptRepository.count(),
        studyDayRepository.countByIdStudyDate(LocalDate.now(clock))); // số người có học hôm nay
  }
}
