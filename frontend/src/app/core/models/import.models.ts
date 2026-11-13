/**
 * Nhập từ bằng file CSV. Khớp com.cassin.vocab.dto.ImportResult (backend).
 * Tài liệu: backend/docs/word-import.md
 */

/** Một lỗi trong file. field = null: lỗi của cả từ (vd 2 nghĩa trùng nhau). */
export interface ImportError {
  /** Số dòng trong file (dòng tiêu đề là 1) */
  line: number;
  field: string | null;
  message: string;
}

/** Báo cáo trả về cả khi xem trước (dryRun) lẫn khi nhập thật. */
export interface ImportResult {
  /** true = đã lưu. false = chỉ xem trước, hoặc file có lỗi */
  imported: boolean;
  /** Số từ mới (các dòng cùng term gộp thành 1 từ) */
  words: number;
  /** Số nghĩa tương ứng */
  senses: number;
  /** Từ đã có trong sổ từ → bỏ qua, không tính là lỗi */
  skipped: string[];
  /** Rỗng = hợp lệ. Có lỗi thì không lưu gì */
  errors: ImportError[];
}

/** spring.servlet.multipart.max-file-size */
export const IMPORT_MAX_BYTES = 1024 * 1024;
/** WordImportService.MAX_ROWS */
export const IMPORT_MAX_ROWS = 500;

/** Nội dung file mẫu cho nút "Tải file mẫu" (BOM để Excel mở đúng tiếng Việt). */
export const IMPORT_TEMPLATE =
  '﻿term,phonetic,part_of_speech,definition,example_1,example_2\n' +
  'light,/laɪt/,NOUN,ánh sáng,"Turn on the [light], please.",\n' +
  'light,,ADJECTIVE,nhẹ,This bag is very [light].,\n' +
  'improve,/ɪmˈpruːv/,VERB,cải thiện,She wants to [improve] her English.,\n' +
  'give up,,PHRASAL_VERB,từ bỏ,Don\'t [give up] on your dreams.,\n';
