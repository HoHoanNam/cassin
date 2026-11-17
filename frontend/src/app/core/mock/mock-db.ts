import { Injectable } from '@angular/core';

import { CefrLevel } from '../models/grammar.models';
import { DeckType, PartOfSpeech } from '../models/deck.models';
import { PracticeMode } from '../models/practice.models';
import { ReportReason, ReportStatus } from '../models/report.models';
import { Role } from '../models/user.models';
import { addDays, todayIso } from '../util/dates';
import { candidates } from './mock-scan-text';
import { SEED_IDIOM_DECK, SEED_SYSTEM_DECK, SEED_TOPICS, SEED_USER_DECKS, SeedCard } from './mock-seed';

/*
 * "Database" giả trong trình duyệt cho chế độ mock.
 * Mỗi interface ở đây giống MỘT bảng trong PostgreSQL (V1..V7), không phải DTO trả ra API.
 * Mock API đọc/ghi các bảng này rồi tự dựng DTO, giống cách backend làm.
 */

export interface DbUser {
  id: number;
  email: string;
  password: string; // chỉ có trong mock; backend thật lưu chuỗi băm BCrypt
  displayName: string;
  role: Role;
  bio: string | null;
}

export interface DbDeck {
  id: number;
  deckName: string;
  description: string | null;
  deckType: DeckType;
  ownerId: number | null;
  createdAt: string;
  updatedAt: string;
}

/** [V10] Bảng words. ownerId = null: từ hệ thống (dùng chung, admin quản lý). */
export interface DbWord {
  id: number;
  ownerId: number | null;
  term: string;
  phonetic: string | null;
  createdAt: string;
}

/** [V10] Bảng senses (+ sense_examples gộp vào mảng examples). Mỗi nghĩa là một thẻ ôn. */
export interface DbSense {
  id: number;
  wordId: number;
  position: number;
  partOfSpeech: PartOfSpeech;
  definition: string;
  /** Giữ nguyên dấu [ ] đánh dấu từ đích */
  examples: string[];
}

/** [V10] Bảng deck_words: từ nào nằm trong bộ nào (nhiều-nhiều). */
export interface DbDeckWord {
  deckId: number;
  wordId: number;
}

export interface DbProgress {
  userId: number;
  senseId: number;
  box: number;
  dueDate: string;
  lastReviewedOn: string | null;
  reviewCount: number;
  lapseCount: number;
}

export interface DbOption {
  id: number;
  content: string;
  correct: boolean;
}

export interface DbTopic {
  id: number;
  title: string;
  level: CefrLevel;
  explanation: string;
}

export interface DbQuestion {
  id: number;
  topicId: number;
  content: string;
  explanation: string;
  options: DbOption[];
}

export interface DbAttempt {
  id: number;
  userId: number;
  topicId: number;
  questionIds: number[];
  selections: Record<number, number | null>;
  score: number | null;
  total: number;
  startedAt: string;
  submittedAt: string | null;
}

/** [THÊM] [V12] Bảng content_reports: học viên báo lỗi một nghĩa của từ hệ thống. */
export interface DbReport {
  id: number;
  reporterId: number;
  senseId: number;
  reason: ReportReason;
  message: string | null;
  status: ReportStatus;
  adminNote: string | null;
  resolvedById: number | null;
  createdAt: string;
  resolvedAt: string | null;
}

/** [THÊM] [V10] Bảng review_events: MỖI lần trả lời (lật thẻ, điền từ, xếp câu) một dòng. Nguồn của Sổ lỗi sai. */
export interface DbReviewEvent {
  id: number;
  userId: number;
  senseId: number;
  mode: PracticeMode;
  correct: boolean;
  durationMs: number;
  reviewedAt: string;
}

interface DbState {
  version: number;
  seq: number;
  users: DbUser[];
  decks: DbDeck[];
  words: DbWord[];
  senses: DbSense[];
  deckWords: DbDeckWord[];
  progresses: DbProgress[];
  topics: DbTopic[];
  questions: DbQuestion[];
  attempts: DbAttempt[];
  reports: DbReport[];
  reviewEvents: DbReviewEvent[];
  /** Những ngày user có ôn thẻ (để tính chuỗi ngày học liên tiếp). */
  studyDays: Record<number, string[]>;
}

const STORAGE_KEY = 'cassin.mockdb';
/**
 * Tăng số này mỗi khi đổi cấu trúc dữ liệu mẫu: trình duyệt đang giữ bản cũ sẽ tự dựng lại từ đầu.
 * 2: thêm tài khoản admin + field bio cho user.
 * 3: [V10] thẻ → từ nhiều nghĩa (words, senses, deck_words), tiến độ theo nghĩa.
 * 4: [THÊM] bộ hệ thống "Thành ngữ & cụm động từ" (V11).
 * 5: [THÊM] báo lỗi nội dung (content_reports) + nhật ký trả lời (review_events) cho Luyện tập / Sổ lỗi sai.
 */
const VERSION = 5;

/**
 * Lưu vào localStorage sau mỗi lần ghi: tải lại trang vẫn còn bộ từ / từ / tiến độ vừa tạo.
 * Muốn làm lại từ đầu: gọi reset() (nút "Đặt lại dữ liệu mẫu" trong menu tài khoản).
 */
@Injectable({ providedIn: 'root' })
export class MockDb {
  private state: DbState = this.load() ?? buildSeed();

  get users() {
    return this.state.users;
  }
  get decks() {
    return this.state.decks;
  }
  get words() {
    return this.state.words;
  }
  get senses() {
    return this.state.senses;
  }
  get deckWords() {
    return this.state.deckWords;
  }
  get progresses() {
    return this.state.progresses;
  }
  get topics() {
    return this.state.topics;
  }
  get questions() {
    return this.state.questions;
  }
  get attempts() {
    return this.state.attempts;
  }
  get reports() {
    return this.state.reports;
  }
  get reviewEvents() {
    return this.state.reviewEvents;
  }

  /** Cấp id mới, giống BIGSERIAL (dùng chung một dãy số cho mọi bảng cho đơn giản). */
  nextId(): number {
    this.state.seq += 1;
    return this.state.seq;
  }

  /** Xóa các dòng thỏa điều kiện khỏi một bảng (giống DELETE ... WHERE). */
  removeWhere<
    K extends 'decks' | 'words' | 'senses' | 'deckWords' | 'progresses' | 'attempts' | 'topics' | 'questions' | 'reports' | 'reviewEvents',
  >(
    table: K,
    predicate: (row: DbState[K][number]) => boolean,
  ): void {
    const rows = this.state[table] as DbState[K][number][];
    Object.assign(this.state, { [table]: rows.filter((row) => !predicate(row)) });
  }

  /** [THÊM] Xóa nghĩa kéo theo mọi dòng trỏ tới nó (ON DELETE CASCADE): tiến độ, báo lỗi, nhật ký trả lời. */
  removeSenseRows(senseIds: Set<number>): void {
    this.removeWhere('progresses', (p) => senseIds.has(p.senseId));
    this.removeWhere('reports', (r) => senseIds.has(r.senseId));
    this.removeWhere('reviewEvents', (e) => senseIds.has(e.senseId));
  }

  studyDays(userId: number): string[] {
    return this.state.studyDays[userId] ?? [];
  }

  markStudied(userId: number, day: string): void {
    const days = this.studyDays(userId);
    if (!days.includes(day)) {
      this.state.studyDays[userId] = [...days, day];
    }
  }

  save(): void {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(this.state));
    } catch {
      // Không lưu được: dữ liệu vẫn dùng được tới khi tải lại trang.
    }
  }

  reset(): void {
    this.state = buildSeed();
    this.save();
  }

  private load(): DbState | null {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      const parsed = raw ? (JSON.parse(raw) as DbState) : null;
      return parsed?.version === VERSION ? parsed : null;
    } catch {
      return null;
    }
  }
}

/** Dựng dữ liệu mẫu. Ngày hạn ôn tính tương đối so với hôm nay để trang Hôm nay luôn có việc. */
function buildSeed(): DbState {
  const today = todayIso();
  const now = new Date().toISOString();
  const state: DbState = {
    version: VERSION,
    seq: 1000,
    users: [],
    decks: [],
    words: [],
    senses: [],
    deckWords: [],
    progresses: [],
    topics: [],
    questions: [],
    attempts: [],
    reports: [],
    reviewEvents: [],
    studyDays: {},
  };
  const id = () => ++state.seq;

  const nam: DbUser = { id: 1, email: 'nam@gmail.com', password: '12345678', displayName: 'Nam', role: 'USER', bio: null };
  // Tài khoản quản trị: giống tài khoản AdminBootstrap tạo ở backend khi chạy profile dev.
  const admin: DbUser = {
    id: 2,
    email: 'admin@cassin.local',
    password: 'admin-dev-123',
    displayName: 'Quản trị viên',
    role: 'ADMIN',
    bio: null,
  };
  state.users.push(nam, admin);

  const addDeck = (deckName: string, description: string | null, deckType: DeckType, ownerId: number | null, updatedDaysAgo: number) => {
    const deck: DbDeck = {
      id: id(),
      deckName,
      description,
      deckType,
      ownerId,
      createdAt: now,
      updatedAt: new Date(Date.now() - updatedDaysAgo * 86_400_000).toISOString(),
    };
    state.decks.push(deck);
    return deck;
  };
  /**
   * Thêm các dòng mẫu vào một bộ. Các dòng cùng cách viết (vd "light" danh từ + "light" tính từ)
   * gộp thành MỘT từ nhiều nghĩa, giống cách V10 gộp thẻ cũ. Trả về các nghĩa theo thứ tự.
   */
  const addWords = (deckId: number, ownerId: number | null, rows: SeedCard[]): DbSense[] => {
    const byTerm = new Map<string, DbWord>();
    const added: DbSense[] = [];
    rows.forEach(([term, partOfSpeech, phonetic, definition, examples], index) => {
      let word = byTerm.get(term.toLowerCase());
      if (!word) {
        word = {
          id: id(),
          ownerId,
          term,
          phonetic,
          createdAt: new Date(Date.now() - (rows.length - index) * 60_000).toISOString(),
        };
        byTerm.set(term.toLowerCase(), word);
        state.words.push(word);
        state.deckWords.push({ deckId, wordId: word.id });
      }
      const position = state.senses.filter((s) => s.wordId === word.id).length;
      // [THÊM] Đánh dấu sẵn [từ đích] để bài Điền từ có câu luyện (xem markTarget)
      const marked = examples.map((example) => markTarget(term, example));
      const sense: DbSense = { id: id(), wordId: word.id, position, partOfSpeech, definition, examples: marked };
      state.senses.push(sense);
      added.push(sense);
    });
    return added;
  };

  // Tiến độ mẫu: [hộp, hạn ôn (số ngày so với hôm nay, âm = quá hạn)]
  const plan: [number, number][] = [
    [1, 0], [2, -1], [3, 0], [1, -2], [4, 0], [2, 1], [5, 9], [3, 2],
    [2, 0], [4, 5], [1, 0], [3, -1], [5, 12], [2, 0], [4, 3], [1, 1],
  ];
  const addProgress = (senses: DbSense[], count: number) =>
    senses.slice(0, count).forEach((sense, i) => {
      const [box, dueIn] = plan[i % plan.length];
      state.progresses.push({
        userId: nam.id,
        senseId: sense.id,
        box,
        dueDate: addDays(today, dueIn),
        lastReviewedOn: dueIn > 0 ? today : addDays(today, -2),
        reviewCount: box + 1,
        lapseCount: box === 1 ? 1 : 0,
      });
    });

  const system = addDeck(SEED_SYSTEM_DECK.deckName, SEED_SYSTEM_DECK.description, 'SYSTEM_DECK', null, 0);
  addProgress(addWords(system.id, null, SEED_SYSTEM_DECK.cards), 16);
  // [THÊM] Bộ thành ngữ: chưa học nghĩa nào (để thử luồng "thẻ mới" với cụm nhiều chữ)
  const idioms = addDeck(SEED_IDIOM_DECK.deckName, SEED_IDIOM_DECK.description, 'SYSTEM_DECK', null, 0);
  addWords(idioms.id, null, SEED_IDIOM_DECK.cards);

  SEED_USER_DECKS.forEach((seed, index) => {
    const deck = addDeck(seed.deckName, seed.description, 'USER_DECK', nam.id, index === 0 ? 1 : 10);
    addProgress(addWords(deck.id, nam.id, seed.cards), index === 0 ? 10 : 0);
  });

  for (const seed of SEED_TOPICS) {
    const topic: DbTopic = { id: id(), title: seed.title, level: seed.level, explanation: seed.explanation };
    state.topics.push(topic);
    for (const [content, explanation, options, correctIndex] of seed.questions) {
      state.questions.push({
        id: id(),
        topicId: topic.id,
        content,
        explanation,
        options: options.map((text, i) => ({ id: id(), content: text, correct: i === correctIndex })),
      });
    }
  }

  // Lịch sử làm bài mẫu: [vị trí chủ đề, số câu làm đúng, cách đây mấy ngày]
  const history: [number, number, number][] = [[1, 7, 1], [0, 6, 7], [2, 6, 10]];
  for (const [topicIndex, correctWanted, daysAgo] of history) {
    const topic = state.topics[topicIndex];
    const questions = state.questions.filter((q) => q.topicId === topic.id);
    const selections: Record<number, number | null> = {};
    questions.forEach((q, i) => {
      const correct = q.options.find((o) => o.correct)!;
      const wrong = q.options.find((o) => !o.correct)!;
      selections[q.id] = i < correctWanted ? correct.id : wrong.id;
    });
    const at = new Date(Date.now() - daysAgo * 86_400_000);
    state.attempts.push({
      id: id(),
      userId: nam.id,
      topicId: topic.id,
      questionIds: questions.map((q) => q.id),
      selections,
      score: Math.min(correctWanted, questions.length),
      total: questions.length,
      startedAt: new Date(at.getTime() - 6 * 60_000).toISOString(),
      submittedAt: at.toISOString(),
    });
  }

  state.studyDays[nam.id] = [1, 2, 3, 4, 5].map((d) => addDays(today, -d));
  // [THÊM] Báo lỗi mẫu của Nam trên từ hệ thống: một cái đang chờ, một cái admin đã xử lý.
  const systemSenses = state.senses.filter((s) => state.words.find((w) => w.id === s.wordId)?.ownerId === null);
  const daysAgo = (days: number, hours = 0) => new Date(Date.now() - days * 86_400_000 - hours * 3_600_000).toISOString();
  if (systemSenses.length >= 2) {
    state.reports.push(
      {
        id: id(), reporterId: nam.id, senseId: systemSenses[1].id, reason: 'WRONG_EXAMPLE',
        message: 'Câu ví dụ thứ hai dùng từ chưa tự nhiên lắm.', status: 'OPEN', adminNote: null,
        resolvedById: null, createdAt: daysAgo(1), resolvedAt: null,
      },
      {
        id: id(), reporterId: nam.id, senseId: systemSenses[0].id, reason: 'TYPO',
        message: 'Nghĩa bị gõ thiếu dấu.', status: 'RESOLVED', adminNote: 'Đã sửa lại nghĩa, cảm ơn bạn!',
        resolvedById: admin.id, createdAt: daysAgo(4), resolvedAt: daysAgo(3),
      },
    );
  }

  // [THÊM] Nhật ký trả lời mẫu (5 ngày gần đây) để Sổ lỗi sai và thống kê luyện tập có dữ liệu.
  const learned = state.progresses.map((p) => p.senseId);
  const modes: PracticeMode[] = ['FLASHCARD', 'FILL_BLANK', 'SENTENCE_BUILD'];
  learned.slice(0, 12).forEach((senseId, i) => {
    const wrongTimes = i < 3 ? 3 - i : i % 4 === 0 ? 1 : 0; // vài nghĩa hay sai, còn lại toàn đúng
    for (let k = 0; k < 3; k++) {
      state.reviewEvents.push({
        id: id(), userId: nam.id, senseId, mode: modes[(i + k) % modes.length], correct: k >= wrongTimes,
        durationMs: 4000 + ((i * 7 + k * 3) % 9) * 1000, reviewedAt: daysAgo((i + k) % 5, i % 6),
      });
    }
  });

  return state;
}

/**
 * [THÊM] Câu ví dụ mẫu viết chưa có dấu [ ], nên bài "Điền từ" không có câu nào để luyện.
 * Tự đánh dấu chữ đầu tiên trong câu có dạng gốc là `term` (dùng bộ đưa về dạng gốc của tính năng quét):
 *   ("improve", "Her English has improved a lot.") → "Her English has [improved] a lot."
 * Câu đã có dấu, từ nhiều chữ (thành ngữ), hoặc không tìm thấy thì giữ nguyên.
 * Lưu ý: chỉ là dữ liệu mẫu của MOCK. Dữ liệu mẫu ở backend (V9) cũng chưa đánh dấu.
 */
function markTarget(term: string, example: string): string {
  const wanted = term.toLowerCase();
  if (example.includes('[') || wanted.includes(' ')) {
    return example;
  }
  for (const match of example.matchAll(/[A-Za-z]+/g)) {
    if (candidates(match[0].toLowerCase()).includes(wanted)) {
      const start = match.index ?? 0;
      return `${example.slice(0, start)}[${match[0]}]${example.slice(start + match[0].length)}`;
    }
  }
  return example;
}
