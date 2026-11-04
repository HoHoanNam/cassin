import { Observable } from 'rxjs';

import { AdminQuestion, AdminStats, QuestionRequest, TopicRequest } from '../models/admin.models';
import { Deck, DeckRequest, Word, WordRequest } from '../models/deck.models';
import { TopicDetail } from '../models/grammar.models';

/**
 * API quản trị: chỉ tài khoản ADMIN gọi được (backend chặn /api/admin/** bằng hasRole ADMIN → 403).
 *
 * Chỉ có phần GHI + những gì học viên không xem được (đáp án đúng, thống kê).
 * Phần ĐỌC bộ từ / từ / chủ đề dùng lại DeckApi, WordApi và GrammarApi: bộ hệ thống ai cũng xem được.
 */
export abstract class AdminApi {
  /** GET /api/admin/stats */
  abstract stats(): Observable<AdminStats>;

  // ---------- Bộ từ hệ thống ----------
  /** POST /api/admin/decks → 201 */
  abstract createDeck(request: DeckRequest): Observable<Deck>;
  /** PUT /api/admin/decks/{deckId} */
  abstract updateDeck(deckId: number, request: DeckRequest): Observable<Deck>;
  /** DELETE /api/admin/decks/{deckId} → 204 | 409 DECK_IN_USE */
  abstract deleteDeck(deckId: number): Observable<void>;
  /** POST /api/admin/decks/{deckId}/words → 201 | 409 WORD_EXISTS. Tạo từ hệ thống MỚI và đặt vào bộ. */
  abstract createWord(deckId: number, request: WordRequest): Observable<Word>;
  /** POST /api/admin/decks/{deckId}/words/{wordId} → 204. Đặt một từ hệ thống CÓ SẴN vào bộ. */
  abstract addWord(deckId: number, wordId: number): Observable<void>;
  /** DELETE /api/admin/decks/{deckId}/words/{wordId} → 204. Bỏ khỏi bộ, từ vẫn còn. */
  abstract removeWord(deckId: number, wordId: number): Observable<void>;
  /** PUT /api/admin/words/{wordId}: sửa từ hệ thống + toàn bộ nghĩa (giữ id nghĩa → giữ tiến độ học viên) */
  abstract updateWord(wordId: number, request: WordRequest): Observable<Word>;
  /** DELETE /api/admin/words/{wordId} → 204. Xóa hẳn, kèm tiến độ của học viên với từ này. */
  abstract deleteWord(wordId: number): Observable<void>;

  // ---------- Ngữ pháp ----------
  /** POST /api/admin/topics → 201 */
  abstract createTopic(request: TopicRequest): Observable<TopicDetail>;
  /** PUT /api/admin/topics/{topicId} */
  abstract updateTopic(topicId: number, request: TopicRequest): Observable<TopicDetail>;
  /** DELETE /api/admin/topics/{topicId} → 204 | 409 TOPIC_IN_USE */
  abstract deleteTopic(topicId: number): Observable<void>;
  /** GET /api/admin/topics/{topicId}/questions (CÓ đáp án đúng) */
  abstract listQuestions(topicId: number): Observable<AdminQuestion[]>;
  /** POST /api/admin/topics/{topicId}/questions → 201 | 400 INVALID_QUESTION */
  abstract createQuestion(topicId: number, request: QuestionRequest): Observable<AdminQuestion>;
  /** PUT /api/admin/questions/{questionId} → giữ nguyên id 4 đáp án */
  abstract updateQuestion(questionId: number, request: QuestionRequest): Observable<AdminQuestion>;
  /** DELETE /api/admin/questions/{questionId} → 204 | 409 QUESTION_IN_USE */
  abstract deleteQuestion(questionId: number): Observable<void>;
}
