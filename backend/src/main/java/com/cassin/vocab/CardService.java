package com.cassin.vocab;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cassin.common.BusinessException;
import com.cassin.common.dto.PageResponse;
import com.cassin.vocab.dto.CardRequest;
import com.cassin.vocab.dto.CardResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CardService {
  private static final int MAX_PAGE_SIZE = 100;

  private final CardRepository cardRepository;
  private final DeckService deckService;   // dùng lại luật quyền, không viết lại

  @Transactional(readOnly = true)
  public PageResponse<CardResponse> listCards(Long deckId, int page, int size, Long userId) {
    deckService.findReadable(deckId, userId);
    // Chặn page âm và size quá lớn: ?size=1000000 sẽ kéo cả bảng lên RAM.
    // [CÂU HỎI] Giải thích thêm về phần phân trang Pageable, PageRequest, PageResponse dùng để làm gì?
    //
    // [TRẢ LỜI] Một bộ có thể có 1000 thẻ. Trả hết một lần thì chậm, tốn RAM, và màn hình
    // cũng chỉ hiện ~20 thẻ. Phân trang = mỗi lần chỉ lấy "một trang" thẻ.
    //
    //   Pageable     : interface của Spring Data mô tả "muốn trang nào": số trang, cỡ trang, sắp xếp.
    //   PageRequest  : class cài đặt Pageable. PageRequest.of(page, size, sort) tạo ra một yêu cầu cụ thể.
    //   Page<Card>   : Spring Data trả về: danh sách thẻ của trang đó + tổng số thẻ, tổng số trang.
    //   PageResponse : record của mình, chỉ giữ 5 field cần cho frontend. Không trả thẳng Page ra JSON
    //                  vì JSON của nó dài, lộ chi tiết nội bộ và có thể đổi giữa các phiên bản Spring.
    //
    // Math.max(page, 0): ?page=-1 → 0 (PageRequest.of ném lỗi với số âm → thành lỗi 500).
    // Math.clamp(size, 1, MAX_PAGE_SIZE): ép size vào khoảng [1, 100]. ?size=1000000 → 100.
    Pageable pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
        // Sắp theo createdAt tăng dần (thẻ thêm trước hiện trước); trùng createdAt thì theo id.
        // Thêm id để thứ tự LUÔN cố định: nếu 2 thẻ cùng createdAt mà không có id,
        // DB được phép trả thứ tự khác nhau giữa các lần → một thẻ có thể hiện ở cả trang 1 lẫn trang 2.
        Sort.by("createdAt").ascending().and(Sort.by("id"))); // Giải thích thêm workflow và kết quả trả về ở đoạn này?
    //
    // [TRẢ LỜI] Workflow, ví dụ GET /api/decks/5/cards?page=1&size=20 (trang thứ HAI, đếm từ 0):
    //
    //   1. findByDeckId(5, pageable): Spring Data chạy 2 câu SQL:
    //        select ... from cards where deck_id = 5 order by created_at, id  offset 20 rows fetch first 20 rows only
    //        select count(*) from cards where deck_id = 5        ← để biết tổng số thẻ / số trang
    //      (offset = page * size = 20: bỏ qua 20 thẻ của trang 0)
    //   2. .map(CardResponse::from): đổi từng Card trong trang thành CardResponse, vẫn giữ thông tin trang.
    //      Câu ví dụ (LAZY) được tải ở bước này, gom 1 câu SQL nhờ default_batch_fetch_size trong application.yml.
    //   3. PageResponse.from(...): rút gọn thành JSON trả về, ví dụ bộ có 45 thẻ:
    //        {
    //          "content": [ { "id": 31, "word": "improve", ... }, ... 20 thẻ ... ],
    //          "page": 1, "size": 20, "totalElements": 45, "totalPages": 3
    //        }
    //      Frontend dựa vào totalPages để vẽ nút "Trang sau" (trang 2 sẽ chỉ có 5 thẻ).
    return PageResponse.from(cardRepository.findByDeckId(deckId, pageable).map(CardResponse::from));
  }

  @Transactional
  public CardResponse createCard(Long deckId, CardRequest r, Long userId) {
    Deck deck = deckService.findEditable(deckId, userId);
    Card card = cardRepository.save(
        Card.create(r.word(), r.partOfSpeech(), r.phonetic(), r.definition(), r.examples(), deck));
    return CardResponse.from(card);
  }

  @Transactional
  public CardResponse updateCard(Long cardId, CardRequest r, Long userId) {
    Card card = findEditableCard(cardId, userId);
    card.update(r.word(), r.partOfSpeech(), r.phonetic(), r.definition(), r.examples());
    return CardResponse.from(card);
  }

  @Transactional
  public void deleteCard(Long cardId, Long userId) {
    cardRepository.delete(findEditableCard(cardId, userId));
  }

  private Card findEditableCard(Long cardId, Long userId) {
    Card card = cardRepository.findById(cardId).orElseThrow(
        () -> new BusinessException(HttpStatus.NOT_FOUND, "CARD_NOT_FOUND", "Không tìm thấy thẻ"));
    // Quyền với thẻ = quyền với bộ chứa nó.
    deckService.checkEditable(card.getDeck(), userId);
    return card;
  }
}
