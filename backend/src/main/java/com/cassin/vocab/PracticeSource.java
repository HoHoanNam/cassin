package com.cassin.vocab;

import java.util.List;

/**
 * [THÊM] Nguồn câu hỏi của một lượt Luyện tập (gộp được nhiều bộ).
 *
 *   all = true              → mọi bộ hệ thống + sổ từ của user ("Tất cả"); deckIds / notebook bị bỏ qua
 *   deckIds = [3, 7]        → chỉ các bộ đó (mỗi bộ phải đọc được: bộ hệ thống hoặc bộ của mình)
 *   notebook = true         → thêm sổ từ của user vào nguồn (kết hợp được với deckIds)
 *   không chọn gì           → sổ từ (giữ hành vi cũ khi FE không gửi deckId)
 *
 * Đặt ở package vocab (không phải practice) vì WordService nhận nó: practice phụ thuộc vocab, không ngược lại.
 */
public record PracticeSource(boolean all, boolean notebook, List<Long> deckIds) {

  public PracticeSource {
    deckIds = deckIds == null ? List.of() : deckIds.stream().distinct().toList();
  }

  public static PracticeSource everything() {
    return new PracticeSource(true, false, List.of());
  }

  public static PracticeSource notebookOnly() {
    return new PracticeSource(false, true, List.of());
  }

  public static PracticeSource decks(List<Long> deckIds) {
    return new PracticeSource(false, false, deckIds);
  }

  /** Có lấy sổ từ không: chọn rõ, hoặc không chọn bộ nào (mặc định). */
  public boolean includesNotebook() {
    return all || notebook || deckIds.isEmpty();
  }
}
