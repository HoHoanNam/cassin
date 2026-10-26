package com.cassin.vocab;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cassin.common.BusinessException;
import com.cassin.user.User;
import com.cassin.user.UserRepository;
import com.cassin.vocab.dto.DeckRequest;
import com.cassin.vocab.dto.DeckResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeckService {
  private final DeckRepository deckRepository;
  private final CardRepository cardRepository;
  private final UserRepository userRepository;

  @Transactional(readOnly = true)
  // Liệt kê danh sách deck công khai + deck cá nhân
  public List<DeckResponse> listDecks(Long userId) {
    return deckRepository.findVisibleSummaries(userId)
        .stream()
        .map(s -> DeckResponse.from(s, userId))
        .toList();
  }

  @Transactional(readOnly = true)
  public DeckResponse getDeck(Long deckId, Long userId) {
    Deck deck = findReadable(deckId, userId);
    return DeckResponse.from(deck, cardRepository.countByDeckId(deckId), userId);
  }

  @Transactional
  public DeckResponse createDeck(DeckRequest request, Long userId) {
    // getReferenceById: KHÔNG chạy SELECT. Hibernate chỉ tạo object "đại diện" mang sẵn id,
    // đủ để ghi owner_id vào bảng decks. Dùng findById ở đây là tốn thêm 1 câu SQL.
    User owner = userRepository.getReferenceById(userId);
    Deck deck = deckRepository.save(Deck.personal(request.deckName(), request.description(), owner));
    return DeckResponse.from(deck, 0, userId);
  }

  @Transactional
  public DeckResponse updateDeck(Long deckId, DeckRequest request, Long userId) {
    Deck deck = findEditable(deckId, userId);
    deck.update(request.deckName(), request.description());
    // Không cần save(): "dirty checking", cuối transaction Hibernate tự chạy UPDATE.
    return DeckResponse.from(deck, cardRepository.countByDeckId(deckId), userId);
  }

  @Transactional
  public void deleteDeck(Long deckId, Long userId) {
    // Thẻ trong bộ do DB tự xóa (ON DELETE CASCADE).
    deckRepository.delete(findEditable(deckId, userId));
  }

  // ===== Luật quyền: dùng chung cho cả DeckService và CardService =====
  // Không ghi "public": chỉ các class trong package vocab gọi được.
  
  // Tìm bộ có quyền đọc được, nếu không tìm thấy hoặc không có quyền thì ném lỗi
  Deck findReadable(Long deckId, Long userId) {
    Deck deck = deckRepository.findById(deckId).orElseThrow(DeckService::deckNotFound);
    checkReadable(deck, userId);
    return deck;
  }

  // Tìm bộ có quyền sửa được, nếu không tìm thấy hoặc không có quyền thì ném lỗi
  Deck findEditable(Long deckId, Long userId) {
    Deck deck = findReadable(deckId, userId);
    checkEditable(deck, userId);
    return deck;
  }

  // Chỉ đọc được bộ của hệ thống và chính user sở hữu
  void checkReadable(Deck deck, Long userId) {
    boolean readable = deck.getDeckType() == DeckType.SYSTEM_DECK || deck.isOwnedBy(userId);
    if (!readable) {
      throw deckNotFound(); // giả vờ "không tồn tại", không để lộ bộ của người khác
    }
  }

  // Chỉ sửa được bộ của chính user sở hữu
  void checkEditable(Deck deck, Long userId) {
    checkReadable(deck, userId);           // bộ của người khác → 404
    if (!deck.isOwnedBy(userId)) {         // tới được đây mà không phải chủ → là bộ hệ thống → 403
      throw new BusinessException(HttpStatus.FORBIDDEN, "DECK_READ_ONLY", "Bộ từ hệ thống chỉ để ôn, không sửa được");
    }
  }

  // [LỖI]: Không tìm thấy bộ từ
  private static BusinessException deckNotFound() {
    return new BusinessException(HttpStatus.NOT_FOUND, "DECK_NOT_FOUND", "Không tìm thấy bộ từ");
  }
}

