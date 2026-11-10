import { PartOfSpeech } from './deck.models';

/**
 * Quét đoạn văn tìm từ chưa học. Khớp các record trong com.cassin.scan (backend).
 * Tài liệu: "Cassin — Quét đoạn văn tìm từ mới", phần Hợp đồng API.
 */

/** Khớp ScanSource. LIBRARY: từ hệ thống chưa học (thêm 1 chạm). NEW: không có ở đâu (tự nhập nghĩa). */
export type ScanSource = 'LIBRARY' | 'NEW';

/** Khớp ScanRequest. Tối đa SCAN_MAX_LENGTH ký tự. */
export interface ScanRequest {
  text: string;
}

export const SCAN_MAX_LENGTH = 5000;

/** Khớp ScanItem.SensePreview: một nghĩa của từ hệ thống, chỉ để xem trước. */
export interface SensePreview {
  partOfSpeech: PartOfSpeech;
  definition: string;
}

/** Khớp ScanItem: một từ (hoặc cụm) chưa học. */
export interface ScanItem {
  /** LIBRARY: term của từ hệ thống. NEW: dạng gốc đoán được (cho người học sửa). */
  term: string;
  /** Các dạng đã gặp, viết thường: ["running", "ran"] */
  forms: string[];
  count: number;
  /** Câu đầu tiên chứa từ, đã đánh dấu [ ]. null nếu câu quá dài. */
  example: string | null;
  source: ScanSource;
  /** Chỉ có với LIBRARY */
  wordId: number | null;
  senses: SensePreview[];
}

/** Khớp ScanResult. */
export interface ScanResult {
  wordCount: number;
  uniqueCount: number;
  knownCount: number;
  truncated: boolean;
  items: ScanItem[];
}
