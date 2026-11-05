/** Khớp com.cassin.vocab.DeckType */
export type DeckType = 'SYSTEM_DECK' | 'USER_DECK';

/** Khớp com.cassin.vocab.PartOfSpeech */
export type PartOfSpeech =
  | 'NOUN'
  | 'VERB'
  | 'ADJECTIVE'
  | 'ADVERB'
  | 'PREPOSITION'
  | 'PRONOUN'
  | 'CONJUNCTION'
  | 'INTERJECTION'
  | 'ARTICLE'
  | 'OTHER';

/**
 * Khớp DeckResponse.
 * [V10] Bộ chứa TỪ (wordCount). dueCount / newCount đếm theo NGHĨA, vì mỗi nghĩa là một thẻ ôn.
 */
export interface Deck {
  id: number;
  deckName: string;
  description: string | null;
  deckType: DeckType;
  wordCount: number;
  dueCount: number;
  newCount: number;
  editable: boolean;
  updatedAt: string;
}

/** Khớp DeckRequest (dùng cho cả tạo và sửa) */
export interface DeckRequest {
  deckName: string;
  description: string | null;
}

// ===================== [V10] Từ và nghĩa =====================

/** Khớp WordSummary.SenseBrief: một nghĩa trong danh sách từ (không có câu ví dụ). */
export interface SenseBrief {
  id: number;
  partOfSpeech: PartOfSpeech;
  definition: string;
}

/** Khớp WordSummary: một dòng trong danh sách từ (sổ từ, từ trong bộ). */
export interface WordSummary {
  id: number;
  term: string;
  phonetic: string | null;
  /** true = từ của chính mình (hiện nút Sửa). Từ hệ thống luôn false. */
  editable: boolean;
  system: boolean;
  senses: SenseBrief[];
}

/**
 * Khớp SenseResponse. examples giữ NGUYÊN dấu [ ] đánh dấu từ cần điền,
 * để form sửa hiện đúng chỗ đã đánh dấu. Muốn hiển thị thì dùng plainExample().
 */
export interface Sense {
  id: number;
  position: number;
  partOfSpeech: PartOfSpeech;
  definition: string;
  examples: string[];
}

/** Khớp WordResponse.DeckRef: bộ đang chứa từ (đủ để hiện nhãn + link). */
export interface DeckRef {
  id: number;
  deckName: string;
}

/** Khớp WordResponse: chi tiết một từ. */
export interface Word {
  id: number;
  term: string;
  phonetic: string | null;
  editable: boolean;
  system: boolean;
  senses: Sense[];
  /** Các bộ mình XEM được đang chứa từ này */
  decks: DeckRef[];
}

/** Khớp SenseRequest. id = null: nghĩa mới; có id: sửa nghĩa cũ (giữ tiến độ ôn của nghĩa đó). */
export interface SenseRequest {
  id: number | null;
  partOfSpeech: PartOfSpeech;
  definition: string;
  examples: string[];
}

/**
 * Khớp WordRequest. Gửi ĐỦ mọi nghĩa: nghĩa cũ không có trong danh sách sẽ bị xóa.
 * deckIds: các bộ CÁ NHÂN muốn đặt từ vào. null = không đổi (khi sửa). Bản admin bỏ qua field này.
 */
export interface WordRequest {
  term: string;
  phonetic: string | null;
  senses: SenseRequest[];
  deckIds: number[] | null;
}

/** Giới hạn khớp validation ở backend (Word.MAX_SENSES, Sense.MAX_EXAMPLES, @Size...). */
export const WORD_LIMITS = {
  term: 100,
  phonetic: 100,
  definition: 255,
  example: 500,
  maxExamples: 5,
  maxSenses: 10,
} as const;

export const DECK_LIMITS = {
  deckName: 100,
  description: 500,
} as const;

/**
 * Khớp LibraryWord: một từ HỆ THỐNG trong Thư viện từ, kèm trạng thái của người đang xem.
 * inMyDecks: đã nằm trong ít nhất một bộ của tôi. studying: đã học ít nhất một nghĩa.
 */
export interface LibraryWord {
  id: number;
  term: string;
  phonetic: string | null;
  senses: SenseBrief[];
  inMyDecks: boolean;
  studying: boolean;
}
