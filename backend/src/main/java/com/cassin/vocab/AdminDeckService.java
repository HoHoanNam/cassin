package com.cassin.vocab;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cassin.common.BusinessException;
import com.cassin.vocab.dto.CardRequest;
import com.cassin.vocab.dto.CardResponse;
import com.cassin.vocab.dto.DeckRequest;
import com.cassin.vocab.dto.DeckResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminDeckService {
  private final DeckRepository deckRepository;
  private final CardRepository cardRepository;
  private final CardProgressRepository progressRepository;

  /* DECK */
  @Transactional
  public DeckResponse createDeck(DeckRequest r) {
    Deck deck = deckRepository.save(Deck.system(r.deckName(), r.description()));
    // 0L (hoặc 0l) có hậu tố L là một Long Literal,
    // biểu thị số 0 có kiểu dữ liệu là long (chiếm 64-bit trong bộ nhớ).
    return DeckResponse.from(deck, 0L, 0L, 0L, null);
  }

  @Transactional
  public DeckResponse updateDeck(Long deckId, DeckRequest r) {
    Deck deck = findDeckSystem(deckId);
    deck.update(r.deckName(), r.description());
    // [SỬA] Trước đây trả cardCount = 0L → bộ đang có 50 thẻ, sửa tên xong màn admin hiện "0 thẻ".
    // Chỉ due/new mới là 0 (admin không "học" bộ), còn số thẻ phải đếm thật.
    // [SỬA nhỏ] Bỏ deckRepository.save(deck): deck lấy từ findById nên Hibernate đang theo dõi,
    // dirty checking tự UPDATE cuối transaction (giống DeckService.updateDeck).
    return DeckResponse.from(deck, cardRepository.countByDeckId(deckId), 0L, 0L, null);
  }

  @Transactional
  public void deleteDeck(Long deckId) {
    // [SỬA] Đổi thứ tự: TÌM bộ trước, KIỂM TRA người học sau.
    // Trước đây kiểm tra existsByCardDeckId trước → gọi DELETE với id một bộ CÁ NHÂN đã có người học
    // thì nhận 409 "DECK_IN_USE" thay vì 404 → admin biết được "id này là bộ có thật của ai đó",
    // phá luật "không để lộ" mà findDeckSystem đang giữ. Id không tồn tại cũng ra 404 đúng hơn.
    Deck deck = findDeckSystem(deckId);
    // Kiểm tra deck đã có người học chưa
    if (progressRepository.existsByCardDeckId(deckId)) {
      throw new BusinessException(HttpStatus.CONFLICT, "DECK_IN_USE",
          "Bộ từ đã có người học, không thể xóa");
    }
    deckRepository.delete(deck);
  }

  /* CARD */
  @Transactional
  public CardResponse addCard(Long deckId, CardRequest r) {
    Deck deck = findDeckSystem(deckId);
    Card card = Card.create(r.word(), r.partOfSpeech(), r.phonetic(), r.definition(), r.examples(), deck);
    return CardResponse.from(cardRepository.save(card));
  }

  @Transactional
  public CardResponse updateCard(Long cardId, CardRequest r) {
    Card card = findCardSystem(cardId);
    card.update(r.word(), r.partOfSpeech(), r.phonetic(), r.definition(), r.examples());
    // [SỬA nhỏ] Bỏ save(card): cùng lý do với updateDeck.
    return CardResponse.from(card);
  }

  // [GHI CHÚ] Xóa MỘT thẻ thì DB xóa luôn tiến độ của mọi người với thẻ đó (ON DELETE CASCADE, V5).
  // Mình KHÔNG chặn như deleteDeck: xóa một thẻ sai/trùng là thao tác sửa nội dung bình thường,
  // người học chỉ mất tiến độ của đúng thẻ đó. Còn xóa cả bộ là mất toàn bộ quá trình học.
  @Transactional
  public void deleteCard(Long cardId) {
    cardRepository.delete(findCardSystem(cardId));
  }

  // HELPERS: ADMIN chỉ quản lý bộ hệ thống. Bộ cá nhân của user → 404, giống luật
  // "không để lộ" ở DeckService.
  // [SỬA] public → private: chỉ dùng trong class này. Để public thì service khác có thể gọi
  // findDeckSystem mà không qua /api/admin/** → bỏ qua lớp kiểm tra quyền ADMIN của SecurityConfig.
  private Deck findDeckSystem(Long deckId) {
    return deckRepository.findById(deckId)
        .filter(d -> d.getDeckType() == DeckType.SYSTEM_DECK)
        .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "DECK_NOT_FOUND", "Không tìm thấy bộ từ"));
  }

  private Card findCardSystem(Long cardId) {
    return cardRepository.findById(cardId)
        .filter(c -> c.getDeck().getDeckType() == DeckType.SYSTEM_DECK)
        .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "CARD_NOT_FOUND", "Không tìm thấy thẻ"));
  }
}
