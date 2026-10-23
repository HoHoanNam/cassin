import { PartOfSpeech } from '../../shared/part-of-speech';
import { DeckType } from '../home/home.models';

/** Một thẻ = một nghĩa của một từ (giống entity Card ở backend). */
export interface CardItem {
  id: number;
  word: string;
  partOfSpeech: PartOfSpeech;
  phonetic: string | null;
  definition: string;
  examples: string[];
  /** Hộp Leitner của người đang xem. null = chưa ôn lần nào. */
  box: number | null;
}

export interface DeckDetail {
  id: number;
  name: string;
  description: string | null;
  type: DeckType;
  dueCount: number;
  cards: CardItem[];
}

/** Các thẻ cùng một từ, gom lại để hiển thị như một mục từ điển. */
export interface WordEntry {
  word: string;
  phonetic: string | null;
  senses: CardItem[];
}
