package com.cassin.vocab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import com.cassin.common.BusinessException;
import com.cassin.user.User;
import com.cassin.user.UserRepository;
import com.cassin.vocab.dto.ImportResult;
import com.cassin.vocab.dto.ImportResult.ImportError;
import com.cassin.vocab.dto.ImportRow;

/**
 * Nhập từ bằng CSV. File được tạo ngay trong test bằng MockMultipartFile (file "giả" trong bộ nhớ,
 * giống hệt thứ Spring đưa vào controller), nên phần ĐỌC FILE chạy thật với Commons CSV.
 */
@ExtendWith(MockitoExtension.class)
class WordImportServiceTest {
  private static final Long USER_ID = 1L;
  private static final Long DECK_ID = 5L;
  private static final String HEADER = "term,phonetic,part_of_speech,definition,example_1,example_2\n";

  @Mock DeckService deckService;
  @Mock WordRepository wordRepository;
  @Mock UserRepository userRepository;
  @InjectMocks WordImportService importService;

  private final User me = VocabFixtures.user(USER_ID, "an@gmail.com");
  private final Deck myDeck = VocabFixtures.withId(Deck.personal("IELTS", null, me), DECK_ID);

  private static MockMultipartFile csv(String content) {
    return new MockMultipartFile("file", "vocab.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
  }

  // ===================== ① Đọc file =====================

  @Test
  @DisplayName("Số dòng = dòng người dùng thấy trong file (tiêu đề là dòng 1) — chốt hành vi của getRecordNumber()")
  void read_reportsFileLineNumbers() {
    List<ImportRow> rows = importService.read(csv(HEADER
        + "light,/laɪt/,NOUN,ánh sáng,,\n"
        + "improve,,VERB,cải thiện,,\n"));

    // Test này hỏng nghĩa là getRecordNumber() đếm khác đi: sửa "+ 1" trong WordImportService.read()
    assertThat(rows).extracting(ImportRow::line, ImportRow::term).containsExactly(tuple(2L, "light"), tuple(3L, "improve"));
  }

  @Test
  @DisplayName("Ô có dấu phẩy trong ngoặc kép giữ nguyên; ô trống bị bỏ; khoảng trắng bị cắt")
  void read_handlesQuotesAndBlanks() {
    List<ImportRow> rows = importService.read(csv(HEADER
        + "  light , ,NOUN,ánh sáng,\"Turn on the [light], please.\",\n"));

    ImportRow row = rows.get(0);
    assertThat(row.term()).isEqualTo("light");
    assertThat(row.phonetic()).isEmpty();
    assertThat(row.examples()).containsExactly("Turn on the [light], please.");
  }

  @Test
  @DisplayName("File lưu từ Excel có BOM ở đầu: vẫn nhận ra cột term")
  void read_skipsBom() {
    List<ImportRow> rows = importService.read(csv("﻿" + HEADER + "light,,NOUN,ánh sáng,,\n"));

    assertThat(rows).extracting(ImportRow::term).containsExactly("light");
  }

  @Test
  @DisplayName("Thứ tự cột tùy ý, cột không bắt buộc có thể bỏ")
  void read_columnsByName() {
    List<ImportRow> rows = importService.read(csv("definition,term,part_of_speech\nánh sáng,light,NOUN\n"));

    assertThat(rows.get(0).term()).isEqualTo("light");
    assertThat(rows.get(0).definition()).isEqualTo("ánh sáng");
    assertThat(rows.get(0).examples()).isEmpty();
  }

  @Test
  @DisplayName("Thiếu cột bắt buộc: 400 INVALID_FILE, nêu tên cột thiếu")
  void read_missingColumn_isInvalid() {
    assertCode(() -> importService.read(csv("term,part_of_speech\nlight,NOUN\n")), "INVALID_FILE", "definition");
  }

  @Test
  @DisplayName("File rỗng hoặc chỉ có tiêu đề: 400 EMPTY_FILE")
  void read_empty_isInvalid() {
    assertCode(() -> importService.read(csv("")), "EMPTY_FILE", null);
    assertCode(() -> importService.read(csv(HEADER)), "EMPTY_FILE", null);
  }

  @Test
  @DisplayName("Quá 500 dòng: 400 TOO_MANY_ROWS")
  void read_tooManyRows_isInvalid() {
    StringBuilder content = new StringBuilder(HEADER);
    for (int i = 0; i <= WordImportService.MAX_ROWS; i++) {
      content.append("word").append(i).append(",,NOUN,nghĩa ").append(i).append(",,\n");
    }
    assertCode(() -> importService.read(csv(content.toString())), "TOO_MANY_ROWS", null);
  }

  // ===================== ②–⑥ Nhập =====================

  @Test
  @DisplayName("Có lỗi: báo MỌI lỗi kèm số dòng + tên cột, xếp theo dòng, KHÔNG lưu gì")
  void import_withErrors_reportsAllAndSavesNothing() {
    when(deckService.findEditable(DECK_ID, USER_ID)).thenReturn(myDeck);
    when(userRepository.getReferenceById(USER_ID)).thenReturn(me);

    ImportResult result = importService.importWords(DECK_ID, csv(HEADER
        + "light,,NOUN,ánh sáng,,\n"            // dòng 2: hợp lệ
        + "improve,,VERBB,,,\n"                 // dòng 3: sai loại từ + thiếu nghĩa
        + "tackle,,VERB,giải quyết,[tackle,\n"  // dòng 4: đánh dấu sai
    ), false, USER_ID);

    assertThat(result.imported()).isFalse();
    assertThat(result.errors()).extracting(ImportError::line, ImportError::field)
        .containsExactly(tuple(3L, "part_of_speech"), tuple(3L, "definition"), tuple(4L, "example_1"));
    verify(wordRepository, never()).saveAll(any());
  }

  @Test
  @DisplayName("Hai dòng cùng term trùng nghĩa: lỗi của CẢ TỪ, gắn vào dòng đầu của từ")
  void import_duplicateSense_isWordError() {
    when(deckService.findEditable(DECK_ID, USER_ID)).thenReturn(myDeck);
    when(userRepository.getReferenceById(USER_ID)).thenReturn(me);

    ImportResult result = importService.importWords(DECK_ID, csv(HEADER
        + "light,,NOUN,ánh sáng,,\n"
        + "Light,,NOUN,Ánh sáng,,\n"), false, USER_ID);

    assertThat(result.errors()).singleElement().satisfies(error -> {
      assertThat(error.line()).isEqualTo(2L);
      assertThat(error.field()).isNull();
    });
  }

  @Test
  @DisplayName("Xem trước (dryRun): đếm đúng từ / nghĩa / bỏ qua, nhưng không lưu")
  void import_dryRun_countsWithoutSaving() {
    when(deckService.findEditable(DECK_ID, USER_ID)).thenReturn(myDeck);
    when(userRepository.getReferenceById(USER_ID)).thenReturn(me);
    when(wordRepository.findOwnTermsLower(eq(USER_ID), anyCollection())).thenReturn(List.of("improve"));

    ImportResult result = importService.importWords(DECK_ID, csv(HEADER
        + "light,,NOUN,ánh sáng,,\n"
        + "light,,ADJECTIVE,nhẹ,,\n"            // gộp với dòng 2 → 1 từ, 2 nghĩa
        + "Improve,,VERB,cải thiện,,\n"), true, USER_ID);   // đã có trong sổ từ → bỏ qua

    assertThat(result.imported()).isFalse();
    assertThat(result.words()).isEqualTo(1);
    assertThat(result.senses()).isEqualTo(2);
    assertThat(result.skipped()).containsExactly("Improve");
    assertThat(result.errors()).isEmpty();
    verify(wordRepository, never()).saveAll(any());
    verify(deckService, never()).linkWord(any(), any());
  }

  @Test
  @DisplayName("Nhập thật: lưu từ mới và đặt từng từ vào bộ")
  void import_saveAndLink() {
    when(deckService.findEditable(DECK_ID, USER_ID)).thenReturn(myDeck);
    when(userRepository.getReferenceById(USER_ID)).thenReturn(me);
    when(wordRepository.findOwnTermsLower(eq(USER_ID), anyCollection())).thenReturn(List.of());

    ImportResult result = importService.importWords(DECK_ID, csv(HEADER
        + "light,,NOUN,ánh sáng,,\n"
        + "improve,,VERB,cải thiện,,\n"), false, USER_ID);

    assertThat(result.imported()).isTrue();
    assertThat(result.words()).isEqualTo(2);
    verify(wordRepository).saveAll(any());
    verify(deckService, times(2)).linkWord(eq(myDeck), any(Word.class));
  }

  @Test
  @DisplayName("Bộ hệ thống: lỗi 403 từ DeckService, không đọc file")
  void import_systemDeck_isForbidden() {
    when(deckService.findEditable(DECK_ID, USER_ID))
        .thenThrow(new BusinessException(HttpStatus.FORBIDDEN, "DECK_READ_ONLY", "Bộ từ hệ thống chỉ để ôn"));

    assertCode(() -> importService.importWords(DECK_ID, csv(HEADER), false, USER_ID), "DECK_READ_ONLY", null);
  }

  // ===================== helper =====================

  private static void assertCode(Runnable action, String code, String messagePart) {
    assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class, ex -> {
      assertThat(ex.getCode()).isEqualTo(code);
      if (messagePart != null) {
        assertThat(ex.getMessage()).contains(messagePart);
      }
    });
  }
}
