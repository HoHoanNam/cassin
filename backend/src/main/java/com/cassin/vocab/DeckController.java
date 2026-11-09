package com.cassin.vocab;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.cassin.auth.AuthUser;
import com.cassin.common.dto.PageResponse;
import com.cassin.vocab.dto.DeckRequest;
import com.cassin.vocab.dto.DeckResponse;
import com.cassin.vocab.dto.ImportResult;
import com.cassin.vocab.dto.WordSummary;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/decks")
@RequiredArgsConstructor
public class DeckController {
  private final DeckService deckService;
  private final WordImportService wordImportService;   // [THÊM]

  @GetMapping
  public List<DeckResponse> list(@AuthenticationPrincipal AuthUser user) {
    return deckService.listDecks(user.id());
  }

  @GetMapping("/{deckId}")
  public DeckResponse get(@PathVariable Long deckId, @AuthenticationPrincipal AuthUser user) {
    return deckService.getDeck(deckId, user.id());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public DeckResponse create(@Valid @RequestBody DeckRequest request, @AuthenticationPrincipal AuthUser user) {
    return deckService.createDeck(request, user.id());
  }

  @PutMapping("/{deckId}")
  public DeckResponse update(@PathVariable Long deckId, @Valid @RequestBody DeckRequest request,
      @AuthenticationPrincipal AuthUser user) {
    return deckService.updateDeck(deckId, request, user.id());
  }

  @DeleteMapping("/{deckId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long deckId, @AuthenticationPrincipal AuthUser user) {
    deckService.deleteDeck(deckId, user.id());
  }

  // ===================== [V10] Từ trong bộ (thay /decks/{id}/cards) =====================

  @GetMapping("/{deckId}/words")
  public PageResponse<WordSummary> words(
      @PathVariable Long deckId,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @AuthenticationPrincipal AuthUser user) {
    return deckService.listWords(deckId, q, page, size, user.id());
  }

  /** Đặt một từ có sẵn (của mình hoặc từ hệ thống) vào bộ. Tạo từ MỚI thì dùng POST /api/words kèm deckIds. */
  @PostMapping("/{deckId}/words/{wordId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void addWord(@PathVariable Long deckId, @PathVariable Long wordId, @AuthenticationPrincipal AuthUser user) {
    deckService.addWord(deckId, wordId, user.id());
  }

  /** Bỏ từ khỏi bộ, từ vẫn còn trong sổ từ. */
  @DeleteMapping("/{deckId}/words/{wordId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void removeWord(@PathVariable Long deckId, @PathVariable Long wordId, @AuthenticationPrincipal AuthUser user) {
    deckService.removeWord(deckId, wordId, user.id());
  }

  // ===================== [THÊM] Nhập từ bằng CSV =====================

  /**
   * POST /api/decks/{deckId}/import?dryRun=true   (multipart/form-data, part "file")
   *
   *   dryRun = true  (mặc định): chỉ kiểm tra và đếm, KHÔNG lưu → FE hiện "Sẽ nhập 42 từ" để xác nhận
   *   dryRun = false            : lưu thật (tất cả hoặc không gì cả)
   *
   * Mặc định true cho an toàn: quên truyền tham số thì chỉ xem trước, không lỡ lưu 500 từ.
   * Lỗi TỪNG DÒNG vẫn trả 200: errors[] là báo cáo kiểm tra, request không hỏng.
   * Lỗi CẢ FILE (rỗng, thiếu cột, > 500 dòng) → WordImportService ném 400.
   * File > 1MB, thiếu part "file", sai Content-Type → bị chặn trước khi vào đây (GlobalExceptionHandler).
   *
   * consumes = multipart: gửi JSON tới endpoint này → 415. Tên "file" phải khớp formData.append('file', ...) ở FE.
   */
  @PostMapping(path = "/{deckId}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ImportResult importWords(
      @PathVariable Long deckId,
      @RequestParam("file") MultipartFile file,
      @RequestParam(defaultValue = "true") boolean dryRun,
      @AuthenticationPrincipal AuthUser user) {
    return wordImportService.importWords(deckId, file, dryRun, user.id());
  }
}
