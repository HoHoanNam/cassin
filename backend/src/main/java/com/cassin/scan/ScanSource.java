package com.cassin.scan;

/** Từ chưa học tìm thấy khi quét đoạn văn đến từ đâu (từ ĐÃ BIẾT không được trả về, chỉ được đếm). */
public enum ScanSource {
  /** Có trong từ điển HỆ THỐNG nhưng chưa học → thêm 1 chạm: POST /api/decks/{deckId}/words/{wordId} */
  LIBRARY,
  /** Không có ở đâu → người học tự nhập nghĩa: POST /api/words */
  NEW
}
