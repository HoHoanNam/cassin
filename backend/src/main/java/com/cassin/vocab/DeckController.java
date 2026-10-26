package com.cassin.vocab;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.cassin.auth.AuthUser;
import com.cassin.vocab.dto.DeckRequest;
import com.cassin.vocab.dto.DeckResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
// [SỬA] Thêm dấu "/" đầu cho thống nhất với các controller khác ("/api/auth", "/api").
// Spring vẫn tự thêm nếu thiếu, nhưng viết đủ thì đọc code không phải đoán.
@RequestMapping("/api/decks")
@RequiredArgsConstructor
public class DeckController {
  private final DeckService deckService;

  // [CÂU HỎI] Giải thích thêm về @AuthenticationPrincipal - Tại sao cần dùng
  // annotation này ở đây? Có phải dùng để xác thực Role của người dùng?
  //
  // [TRẢ LỜI] KHÔNG. Nó không xác thực, không kiểm tra role. Nó chỉ LẤY RA "người đang gọi API".
  //
  // Lúc request tới, JwtAuthenticationFilter đã đọc token và cất vào SecurityContext:
  //     new UsernamePasswordAuthenticationToken(authUser, null, authorities)
  //                                             ^ principal = AuthUser(id, email, role)
  // @AuthenticationPrincipal bảo Spring: "lấy principal đó, truyền vào tham số này".
  // Nó tương đương viết tay:
  //     AuthUser user = (AuthUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
  //
  // Ba việc khác nhau, nằm ở ba chỗ khác nhau:
  //   - Xác thực (bạn là ai?)        → JwtAuthenticationFilter kiểm tra chữ ký token
  //   - Phân quyền theo role         → SecurityConfig: hasRole("ADMIN"), anyRequest().authenticated()
  //   - Quyền theo dữ liệu (bộ này có phải của bạn?) → DeckService.checkReadable / checkEditable
  //
  // Tại sao cần ở đây: service cần user.id() để biết lọc "bộ của ai".
  // Lấy id từ token (đã ký, không giả được) chứ KHÔNG nhận userId từ URL hay body:
  // nếu API là GET /api/decks?userId=5 thì ai cũng sửa số 5 thành số khác để xem bộ người khác.
  //
  // user không bao giờ null ở đây: không có token hợp lệ thì SecurityConfig đã trả 401 trước khi tới controller.
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
  public DeckResponse update(@PathVariable Long deckId, @Valid @RequestBody DeckRequest request, @AuthenticationPrincipal AuthUser user) {
    return deckService.updateDeck(deckId, request, user.id());
  }

  // [SỬA] Thiếu dấu "}": "/{deckId" → "/{deckId}". Lỗi này làm app KHÔNG KHỞI ĐỘNG ĐƯỢC
  // (Spring báo lỗi cú pháp đường dẫn khi đăng ký các API lúc khởi động).
  @DeleteMapping("/{deckId}")
  @ResponseStatus(HttpStatus.NO_CONTENT) // 204: xóa thành công, không có nội dung trả về
  public void delete(@PathVariable Long deckId, @AuthenticationPrincipal AuthUser user) {
    deckService.deleteDeck(deckId, user.id());
  }
}
