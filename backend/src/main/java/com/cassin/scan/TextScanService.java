package com.cassin.scan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cassin.scan.dto.ScanItem;
import com.cassin.scan.dto.ScanItem.SensePreview;
import com.cassin.scan.dto.ScanResult;
import com.cassin.scan.dto.Token;
import com.cassin.vocab.Word;
import com.cassin.vocab.WordRepository;

import lombok.RequiredArgsConstructor;

/**
 * Quét đoạn văn → từ chưa có trong sổ từ. Chi tiết 6 bước: tài liệu "Cassin — Quét đoạn văn tìm từ mới".
 *
 *   ① Tokenizer.tokenize                 ④ gộp theo khóa, bỏ tên riêng
 *   ② dò cụm nhiều chữ (SQL 1)           ⑤ phân loại: đã biết / LIBRARY / NEW (SQL 3, 4 có sẵn)
 *   ③ từ đơn: candidates → SQL 2         ⑥ câu ví dụ, sắp xếp, cắt MAX_ITEMS
 *
 * Chỉ ĐỌC dữ liệu (readOnly). Cả lần quét chạy tối đa 4 câu SQL, không phụ thuộc đoạn văn dài bao nhiêu.
 */
@Service
@RequiredArgsConstructor
public class TextScanService {
  static final int MAX_ITEMS = 200;
  static final int MAX_EXAMPLE_LENGTH = 500;

  private final WordRepository wordRepository;

  /**
   * Một lần xuất hiện của một từ / cụm trong đoạn văn.
   *
   * @param key    khóa gộp nhóm: term viết thường của từ khớp trong DB, hoặc Lemmatizer.guess(...)
   * @param first  index token đầu (trong Tokenizer.Result.tokens)
   * @param last   index token cuối (= first với từ đơn)
   * @param word   từ khớp trong DB, null nếu không khớp (NEW)
   */
  record Occurrence(String key, int first, int last, Word word) {
  }

  @Transactional(readOnly = true)
  public ScanResult scan(String text, Long userId) {
    // ① Tách câu, tách từ
    Tokenizer.Result tokenized = Tokenizer.tokenize(text);
    List<Token> tokens = tokenized.tokens();
    boolean[] used = new boolean[tokens.size()]; // token đã thuộc một cụm thì bước ③ bỏ qua
    List<Occurrence> occurrences = new ArrayList<>();

    // ② Cụm nhiều chữ TRƯỚC (thành ngữ, cụm động từ), để "break the ice" không bị tách thành break / ice
    occurrences.addAll(findPhrases(tokens, used, userId));

    // ③ Từ đơn: gom ứng viên của mọi token → MỘT câu SQL
    occurrences.addAll(findSingleWords(tokens, used, userId));

    // ④ Gộp theo khóa, giữ thứ tự xuất hiện; bỏ tên riêng
    occurrences.sort(Comparator.comparingInt(Occurrence::first));
    Map<String, List<Occurrence>> groups = new LinkedHashMap<>();
    for (Occurrence o : occurrences) {
      groups.computeIfAbsent(o.key(), k -> new ArrayList<>()).add(o);
    }
    groups.values().removeIf(group -> isProperNoun(group, tokens));

    // ⑤ Từ hệ thống nào ĐÃ nằm trong sổ từ (trong bộ của tôi, hoặc đã học ít nhất một nghĩa)
    Set<Long> knownSystemIds = knownSystemWordIds(groups.values(), userId);

    // ⑥ Kết quả
    int wordCount = 0;
    int knownCount = 0;
    List<ScanItem> items = new ArrayList<>();
    for (Map.Entry<String, List<Occurrence>> entry : groups.entrySet()) {
      List<Occurrence> group = entry.getValue();
      wordCount += group.size();
      Word word = group.get(0).word();
      boolean known = word != null && (!word.isSystem() || knownSystemIds.contains(word.getId()));
      if (known) {
        knownCount++;
        continue;
      }
      items.add(toItem(entry.getKey(), group, word, tokenized));
    }
    // List.sort ỔN ĐỊNH: count bằng nhau thì giữ thứ tự xuất hiện trong đoạn văn
    items.sort(Comparator.comparingInt(ScanItem::count).reversed());
    boolean truncated = items.size() > MAX_ITEMS;
    return new ScanResult(wordCount, groups.size(), knownCount, truncated,
        truncated ? List.copyOf(items.subList(0, MAX_ITEMS)) : items);
  }

  // ===================== ② cụm nhiều chữ =====================

  private List<Occurrence> findPhrases(List<Token> tokens, boolean[] used, Long userId) {
    // Cụm dài thử trước: "run out of" phải thắng "run out" nếu cả hai cùng có
    List<Word> phrases = preferOwn(wordRepository.findMultiWordTerms(userId)).stream()
        .sorted(Comparator.comparingInt((Word w) -> w.getTerm().split(" ").length).reversed())
        .toList();
    List<Occurrence> found = new ArrayList<>();
    if (phrases.isEmpty()) {
      return found;
    }
    for (int i = 0; i < tokens.size(); i++) {
      for (Word phrase : phrases) {
        String[] parts = phrase.getTerm().toLowerCase(Locale.ROOT).split(" ");
        if (matchesAt(tokens, used, i, parts)) {
          int last = i + parts.length - 1;
          for (int k = i; k <= last; k++) {
            used[k] = true;
          }
          found.add(new Occurrence(phrase.getTerm().toLowerCase(Locale.ROOT), i, last, phrase));
          break;
        }
      }
    }
    return found;
  }

  /** Các chữ của cụm khớp các token từ vị trí i, cùng một câu, token chưa thuộc cụm khác. "gave" khớp "give". */
  private static boolean matchesAt(List<Token> tokens, boolean[] used, int i, String[] parts) {
    if (i + parts.length > tokens.size()) {
      return false;
    }
    int sentence = tokens.get(i).sentenceIndex();
    for (int k = 0; k < parts.length; k++) {
      Token t = tokens.get(i + k);
      if (used[i + k] || t.sentenceIndex() != sentence || !Lemmatizer.candidates(t.lower()).contains(parts[k])) {
        return false;
      }
    }
    return true;
  }

  // ===================== ③ từ đơn =====================

  private List<Occurrence> findSingleWords(List<Token> tokens, boolean[] used, Long userId) {
    // Vòng 1: gom mọi ứng viên
    Set<String> allCandidates = new LinkedHashSet<>();
    for (int i = 0; i < tokens.size(); i++) {
      if (!used[i] && !StopWords.contains(tokens.get(i).lower())) {
        allCandidates.addAll(Lemmatizer.candidates(tokens.get(i).lower()));
      }
    }
    if (allCandidates.isEmpty()) {
      return List.of(); // đoạn chỉ có từ chức năng / số: không gọi SQL ("in ()" rỗng)
    }
    Map<String, Word> byTerm = new LinkedHashMap<>();
    for (Word w : preferOwn(wordRepository.findOwnOrSystemByTermsLower(userId, allCandidates))) {
      byTerm.putIfAbsent(w.getTerm().toLowerCase(Locale.ROOT), w);
    }

    // Vòng 2: mỗi token lấy ứng viên ĐẦU TIÊN có trong DB; không có thì đoán dạng gốc
    List<Occurrence> found = new ArrayList<>();
    for (int i = 0; i < tokens.size(); i++) {
      String lower = tokens.get(i).lower();
      if (used[i] || StopWords.contains(lower)) {
        continue;
      }
      String hit = Lemmatizer.candidates(lower).stream().filter(byTerm::containsKey).findFirst().orElse(null);
      found.add(hit != null
          ? new Occurrence(hit, i, i, byTerm.get(hit))
          : new Occurrence(Lemmatizer.guess(lower), i, i, null));
    }
    return found;
  }

  /**
   * Cùng một term có cả từ CỦA TÔI lẫn từ hệ thống → giữ từ của tôi (đã biết).
   * Sắp từ của tôi lên trước, rồi bỏ các bản trùng term (viết thường) phía sau.
   */
  private static List<Word> preferOwn(List<Word> words) {
    Set<String> seen = new HashSet<>();
    return words.stream()
        .sorted(Comparator.comparing(Word::isSystem)) // false (của tôi) trước true (hệ thống)
        .filter(w -> seen.add(w.getTerm().toLowerCase(Locale.ROOT)))
        .toList();
  }

  // ===================== ④ ⑤ ⑥ =====================

  /** Nhóm là TÊN RIÊNG: không khớp DB, lần nào cũng viết hoa, ít nhất một lần viết hoa giữa câu. */
  static boolean isProperNoun(List<Occurrence> group, List<Token> tokens) {
    return group.get(0).word() == null
        && group.stream().allMatch(o -> tokens.get(o.first()).upper())
        && group.stream().anyMatch(o -> tokens.get(o.first()).capitalizedMidSentence());
  }

  /** Hai câu SQL có sẵn, mỗi câu gọi MỘT lần cho cả đoạn văn (không gọi trong vòng lặp: N+1). */
  private Set<Long> knownSystemWordIds(Iterable<List<Occurrence>> groups, Long userId) {
    Set<Long> systemIds = new HashSet<>();
    for (List<Occurrence> group : groups) {
      Word w = group.get(0).word();
      if (w != null && w.isSystem()) {
        systemIds.add(w.getId());
      }
    }
    if (systemIds.isEmpty()) {
      return Set.of();
    }
    Set<Long> known = new HashSet<>(wordRepository.findInOwnDecks(userId, systemIds));
    known.addAll(wordRepository.findStudiedWords(userId, systemIds));
    return known;
  }

  private static ScanItem toItem(String key, List<Occurrence> group, Word word, Tokenizer.Result tokenized) {
    Occurrence first = group.get(0);
    List<String> forms = group.stream()
        .map(o -> surface(tokenized, o).toLowerCase(Locale.ROOT))
        .distinct()
        .toList();
    String example = buildExample(tokenized, first.first(), first.last());
    if (word == null) {
      return new ScanItem(key, forms, group.size(), example, ScanSource.NEW, null, List.of());
    }
    // word.getSenses() là LAZY: đọc trong @Transactional; default_batch_fetch_size gom thành WHERE word_id IN (...)
    List<SensePreview> senses = word.getSenses().stream()
        .map(s -> new SensePreview(s.getPartOfSpeech(), s.getDefinition()))
        .toList();
    return new ScanItem(word.getTerm(), forms, group.size(), example, ScanSource.LIBRARY, word.getId(), senses);
  }

  /** Đoạn văn gốc của một lần xuất hiện: "running", "Breaking the ice". */
  private static String surface(Tokenizer.Result tokenized, Occurrence o) {
    Token first = tokenized.tokens().get(o.first());
    String sentence = tokenized.sentences().get(first.sentenceIndex());
    return sentence.substring(first.start(), tokenized.tokens().get(o.last()).end());
  }

  /**
   * Câu ví dụ: đổi [ ] có sẵn trong câu thành ( ), rồi bao token first..last bằng [ ].
   *   "He was running late."  + token "running" → "He was [running] late."
   * Đổi [ ] → ( ) giữ nguyên độ dài câu nên vị trí token vẫn đúng.
   * Kết quả dài hơn MAX_EXAMPLE_LENGTH → null (sense_examples.example là VARCHAR(500)).
   */
  static String buildExample(Tokenizer.Result tokenized, int first, int last) {
    Token a = tokenized.tokens().get(first);
    int end = tokenized.tokens().get(last).end();
    String sentence = tokenized.sentences().get(a.sentenceIndex()).replace('[', '(').replace(']', ')');
    String marked = sentence.substring(0, a.start()) + "[" + sentence.substring(a.start(), end) + "]"
        + sentence.substring(end);
    return marked.length() > MAX_EXAMPLE_LENGTH ? null : marked;
  }
}
