package com.cassin.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cassin.scan.dto.ScanItem;
import com.cassin.scan.dto.ScanResult;
import com.cassin.user.User;
import com.cassin.vocab.PartOfSpeech;
import com.cassin.vocab.VocabFixtures;
import com.cassin.vocab.Word;
import com.cassin.vocab.WordRepository;

/**
 * TextScanService. WordRepository là MOCK nhưng trả lời GIỐNG DB THẬT: lọc từ một danh sách Word trong bộ nhớ
 * theo đúng điều kiện của câu SQL (xem stubRepository). Nhờ vậy test kiểm tra được cả thuật toán lẫn
 * việc service gọi repository bao nhiêu lần.
 *
 * Dữ liệu:
 *   từ CỦA TÔI      : late (1), station (8)
 *   từ HỆ THỐNG     : work (2, đang học), run (3), break the ice (4), give up (5), station (6)
 */
@ExtendWith(MockitoExtension.class)
class TextScanServiceTest {
  private static final Long USER_ID = 1L;

  @Mock WordRepository wordRepository;
  @InjectMocks TextScanService scanService;

  private final User me = VocabFixtures.user(USER_ID, "me@cassin.vn");
  /** "Bảng words" giả: chỉ gồm từ của tôi + từ hệ thống (đúng như điều kiện owner trong 2 câu SQL). */
  private final List<Word> words = new ArrayList<>();
  private final Set<Long> inMyDecks = new HashSet<>();
  private final Set<Long> studied = new HashSet<>();

  private Word system(long id, String term) {
    Word w = VocabFixtures.word(id, term, null, VocabFixtures.sense(PartOfSpeech.NOUN, "nghĩa của " + term));
    words.add(w);
    return w;
  }

  private Word mine(long id, String term) {
    Word w = VocabFixtures.word(id, term, me, VocabFixtures.sense(PartOfSpeech.NOUN, "nghĩa của tôi"));
    words.add(w);
    return w;
  }

  @BeforeEach
  void stubRepository() {
    mine(1, "late");
    system(2, "work");
    studied.add(2L);
    system(3, "run");
    system(4, "break the ice");
    system(5, "give up");
    system(6, "station");
    mine(8, "station");

    // lenient: không phải test nào cũng gọi đủ 4 method (vd đoạn văn không có từ hệ thống nào)
    lenient().when(wordRepository.findMultiWordTerms(USER_ID))
        .thenAnswer(inv -> words.stream().filter(w -> w.getTerm().contains(" ")).toList());
    lenient().when(wordRepository.findOwnOrSystemByTermsLower(eq(USER_ID), anyCollection()))
        .thenAnswer(inv -> {
          Collection<String> terms = inv.getArgument(1);
          return words.stream().filter(w -> terms.contains(w.getTerm().toLowerCase(Locale.ROOT))).toList();
        });
    lenient().when(wordRepository.findInOwnDecks(eq(USER_ID), anyCollection()))
        .thenAnswer(inv -> inv.<Collection<Long>>getArgument(1).stream().filter(inMyDecks::contains).toList());
    lenient().when(wordRepository.findStudiedWords(eq(USER_ID), anyCollection()))
        .thenAnswer(inv -> inv.<Collection<Long>>getArgument(1).stream().filter(studied::contains).toList());
  }

  private static ScanItem item(ScanResult result, String term) {
    return result.items().stream().filter(i -> i.term().equals(term)).findFirst()
        .orElseThrow(() -> new AssertionError("Không có mục \"" + term + "\" trong " + result.items()));
  }

  private static List<String> terms(ScanResult result) {
    return result.items().stream().map(i -> i.term()).toList();
  }

  // ===================== Từ đơn =====================

  @Test
  @DisplayName("running + ran → MỘT mục 'run' (LIBRARY), đếm 2, câu ví dụ đánh dấu lần xuất hiện ĐẦU")
  void inflectedForms_groupIntoBaseWord() {
    ScanResult r = scanService.scan("He was running late, so he ran to the station.", USER_ID);

    ScanItem run = item(r, "run");
    assertThat(run.source()).isEqualTo(ScanSource.LIBRARY);
    assertThat(run.wordId()).isEqualTo(3L);
    assertThat(run.count()).isEqualTo(2);
    assertThat(run.forms()).containsExactly("running", "ran");
    assertThat(run.example()).isEqualTo("He was [running] late, so he ran to the station.");
    assertThat(run.senses()).extracting(s -> s.definition()).containsExactly("nghĩa của run");
  }

  @Test
  @DisplayName("Đã biết: từ của tôi (late), từ hệ thống đang học (work), trùng term → ưu tiên từ của tôi (station)")
  void knownWords_areCountedNotListed() {
    ScanResult r = scanService.scan("He was late for work at the station.", USER_ID);

    assertThat(terms(r)).doesNotContain("late", "work", "station");
    assertThat(r.knownCount()).isEqualTo(3);
    assertThat(r.uniqueCount()).isEqualTo(3);
  }

  @Test
  @DisplayName("Từ hệ thống nằm trong bộ của tôi cũng là đã biết")
  void systemWordInMyDeck_isKnown() {
    inMyDecks.add(3L);

    ScanResult r = scanService.scan("I run every morning.", USER_ID);

    assertThat(terms(r)).doesNotContain("run");
  }

  @Test
  @DisplayName("Không khớp DB: NEW, term = dạng gốc đoán được, không có wordId / nghĩa")
  void unknownWord_isNew() {
    ScanResult r = scanService.scan("Visiting museums is fun.", USER_ID);

    ScanItem visit = item(r, "visit");
    assertThat(visit.source()).isEqualTo(ScanSource.NEW);
    assertThat(visit.wordId()).isNull();
    assertThat(visit.senses()).isEmpty();
    assertThat(visit.forms()).containsExactly("visiting");
    assertThat(item(r, "museum").forms()).containsExactly("museums");
  }

  // ===================== Cụm nhiều chữ =====================

  @Test
  @DisplayName("Cụm: 'Breaking the ice' → break the ice, 'gave up' → give up; câu ví dụ bao CẢ CỤM")
  void phrases_matchInflectedForms() {
    ScanResult r = scanService.scan("Breaking the ice at work is hard! She gave up.", USER_ID);

    ScanItem ice = item(r, "break the ice");
    assertThat(ice.source()).isEqualTo(ScanSource.LIBRARY);
    assertThat(ice.forms()).containsExactly("breaking the ice");
    assertThat(ice.example()).isEqualTo("[Breaking the ice] at work is hard!");
    assertThat(item(r, "give up").example()).isEqualTo("She [gave up].");
  }

  @Test
  @DisplayName("Token thuộc cụm không bị tính lại thành từ đơn: không có mục 'ice', 'break'")
  void phraseTokens_areNotCountedTwice() {
    ScanResult r = scanService.scan("Breaking the ice is hard.", USER_ID);

    assertThat(terms(r)).doesNotContain("ice", "break");
    assertThat(r.wordCount()).isEqualTo(2);   // break the ice + hard
  }

  @Test
  @DisplayName("Cụm không vắt qua hai câu: 'gave. Up' không khớp give up")
  void phrase_doesNotCrossSentences() {
    ScanResult r = scanService.scan("She gave. Up the hill we went.", USER_ID);

    assertThat(terms(r)).doesNotContain("give up");
  }

  // ===================== Tên riêng, câu ví dụ =====================

  @Test
  @DisplayName("Tên riêng bị bỏ (London; Anna kể cả khi có lần đứng đầu câu), không tính vào wordCount")
  void properNouns_areDropped() {
    ScanResult r = scanService.scan("Anna loves cities. Visiting London with Anna was fun.", USER_ID);

    assertThat(terms(r)).doesNotContain("london", "anna", "London", "Anna");
    assertThat(terms(r)).contains("love", "city", "visit", "fun");
    assertThat(r.wordCount()).isEqualTo(4);
  }

  @Test
  @DisplayName("Từ đầu câu viết hoa nhưng lần khác viết thường → KHÔNG phải tên riêng")
  void capitalizedAtSentenceStart_isNotProperNoun() {
    ScanResult r = scanService.scan("Coffee is hot. I like coffee.", USER_ID);

    assertThat(item(r, "coffee").count()).isEqualTo(2);
  }

  @Test
  @DisplayName("[ ] có sẵn trong đoạn văn đổi thành ( ), để câu ví dụ chỉ có MỘT chỗ đánh dấu")
  void existingBrackets_areReplaced() {
    ScanResult r = scanService.scan("Anna [said] hello.", USER_ID);

    assertThat(item(r, "say").example()).isEqualTo("Anna ([said]) hello.");
  }

  @Test
  @DisplayName("Câu ví dụ dài quá 500 ký tự → example = null")
  void longSentence_hasNoExample() {
    String longSentence = "Wonderful " + "very ".repeat(120) + "day.";   // "very" là từ chức năng

    ScanResult r = scanService.scan(longSentence, USER_ID);

    assertThat(item(r, "wonderful").example()).isNull();
  }

  // ===================== Số lượng, sắp xếp, số câu SQL =====================

  @Test
  @DisplayName("Chỉ có từ chức năng: không có mục nào, KHÔNG gọi câu SQL tìm từ đơn")
  void onlyStopWords_skipsQuery() {
    ScanResult r = scanService.scan("It is what it is.", USER_ID);

    assertThat(r.items()).isEmpty();
    assertThat(r.wordCount()).isZero();
    verify(wordRepository, never()).findOwnOrSystemByTermsLower(anyLong(), anyCollection());
  }

  @Test
  @DisplayName("Cả đoạn văn chỉ MỘT câu SQL tìm từ đơn, dù có nhiều từ")
  void singleWords_useOneQuery() {
    scanService.scan("Apples, bananas and cherries. Running, jumping and swimming.", USER_ID);

    verify(wordRepository, times(1)).findOwnOrSystemByTermsLower(eq(USER_ID), anyCollection());
  }

  @Test
  @DisplayName("Sắp xếp: count giảm dần, bằng nhau giữ thứ tự xuất hiện")
  void items_sortedByCountThenAppearance() {
    ScanResult r = scanService.scan("Apple banana apple. Cherry banana apple.", USER_ID);

    assertThat(r.items()).extracting(i -> i.term(), i -> i.count())
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("apple", 3),
            org.assertj.core.groups.Tuple.tuple("banana", 2),
            org.assertj.core.groups.Tuple.tuple("cherry", 1));
  }

  @Test
  @DisplayName("Hơn 200 từ chưa học: trả đúng 200 mục, truncated = true; uniqueCount vẫn đếm đủ")
  void moreThanMaxItems_isTruncated() {
    // 205 "từ" khác nhau, chỉ có phụ âm nên không bị cắt đuôi -s / -ed / -ing
    StringBuilder text = new StringBuilder();
    String letters = "bcdfghjklmnpqrtvwxz";
    int n = 0;
    for (char x : letters.toCharArray()) {
      for (char y : letters.toCharArray()) {
        if (n++ < 205) {
          text.append('q').append(x).append(y).append(' ');
        }
      }
    }

    ScanResult r = scanService.scan(text.toString(), USER_ID);

    assertThat(r.items()).hasSize(TextScanService.MAX_ITEMS);
    assertThat(r.truncated()).isTrue();
    assertThat(r.uniqueCount()).isEqualTo(205);
  }
}
