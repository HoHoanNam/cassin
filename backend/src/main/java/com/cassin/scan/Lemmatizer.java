package com.cassin.scan;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Đưa một từ về DẠNG GỐC bằng quy tắc (cách A, không dùng thư viện NLP).
 * Chỉ xử lý BIẾN TỐ (inflection): số nhiều -s/-es, quá khứ -ed, tiếp diễn -ing, cộng danh sách bất quy tắc.
 * KHÔNG bỏ -er / -ly / -ness...: "teacher" không phải "teach", "family" không phải "fami".
 *
 * Hai method, hai mục đích:
 *
 *   candidates("running") = [running, runn, run, rune]   → đưa HẾT vào câu SQL, từ nào có trong DB thì khớp.
 *                                                           Đoán thừa không sao: dạng sai không tồn tại trong DB.
 *   guess("running")      = "run"                        → khi KHÔNG khớp gì trong DB, đây là term gợi ý để
 *                                                           người học tự nhập nghĩa (FE cho sửa trước khi thêm).
 */
public final class Lemmatizer {
  private Lemmatizer() {
  }

  /** Bất quy tắc: động từ (quá khứ, phân từ) và danh từ số nhiều thường gặp. */
  private static final Map<String, String> IRREGULAR = Map.ofEntries(
      Map.entry("went", "go"), Map.entry("gone", "go"), Map.entry("goes", "go"),
      Map.entry("ran", "run"), Map.entry("came", "come"), Map.entry("became", "become"),
      Map.entry("began", "begin"), Map.entry("begun", "begin"), Map.entry("broke", "break"), Map.entry("broken", "break"),
      Map.entry("brought", "bring"), Map.entry("built", "build"), Map.entry("bought", "buy"), Map.entry("caught", "catch"),
      Map.entry("chose", "choose"), Map.entry("chosen", "choose"), Map.entry("drew", "draw"), Map.entry("drawn", "draw"),
      Map.entry("drank", "drink"), Map.entry("drunk", "drink"), Map.entry("drove", "drive"), Map.entry("driven", "drive"),
      Map.entry("ate", "eat"), Map.entry("eaten", "eat"), Map.entry("fell", "fall"), Map.entry("fallen", "fall"),
      Map.entry("felt", "feel"), Map.entry("fought", "fight"), Map.entry("found", "find"), Map.entry("flew", "fly"),
      Map.entry("flown", "fly"), Map.entry("forgot", "forget"), Map.entry("forgotten", "forget"), Map.entry("gave", "give"),
      Map.entry("given", "give"), Map.entry("got", "get"), Map.entry("gotten", "get"), Map.entry("grew", "grow"),
      Map.entry("grown", "grow"), Map.entry("heard", "hear"), Map.entry("held", "hold"), Map.entry("hid", "hide"),
      Map.entry("hidden", "hide"), Map.entry("kept", "keep"), Map.entry("knew", "know"), Map.entry("known", "know"),
      Map.entry("laid", "lay"), Map.entry("led", "lead"), Map.entry("left", "leave"), Map.entry("lent", "lend"),
      Map.entry("lost", "lose"), Map.entry("made", "make"), Map.entry("meant", "mean"), Map.entry("met", "meet"),
      Map.entry("paid", "pay"), Map.entry("rode", "ride"), Map.entry("ridden", "ride"), Map.entry("rang", "ring"),
      Map.entry("rose", "rise"), Map.entry("risen", "rise"), Map.entry("said", "say"), Map.entry("saw", "see"),
      Map.entry("seen", "see"), Map.entry("sold", "sell"), Map.entry("sent", "send"), Map.entry("shook", "shake"),
      Map.entry("shot", "shoot"), Map.entry("showed", "show"), Map.entry("shown", "show"), Map.entry("sang", "sing"),
      Map.entry("sung", "sing"), Map.entry("sat", "sit"), Map.entry("slept", "sleep"), Map.entry("spoke", "speak"),
      Map.entry("spoken", "speak"), Map.entry("spent", "spend"), Map.entry("stood", "stand"), Map.entry("stole", "steal"),
      Map.entry("stolen", "steal"), Map.entry("swam", "swim"), Map.entry("took", "take"), Map.entry("taken", "take"),
      Map.entry("taught", "teach"), Map.entry("tore", "tear"), Map.entry("torn", "tear"), Map.entry("told", "tell"),
      Map.entry("thought", "think"), Map.entry("threw", "throw"), Map.entry("thrown", "throw"),
      Map.entry("understood", "understand"), Map.entry("woke", "wake"), Map.entry("woken", "wake"), Map.entry("wore", "wear"),
      Map.entry("worn", "wear"), Map.entry("won", "win"), Map.entry("wrote", "write"), Map.entry("written", "write"),
      Map.entry("fed", "feed"), Map.entry("fled", "flee"), Map.entry("bit", "bite"), Map.entry("bitten", "bite"),
      Map.entry("blew", "blow"), Map.entry("blown", "blow"), Map.entry("dealt", "deal"), Map.entry("dug", "dig"),
      Map.entry("froze", "freeze"), Map.entry("frozen", "freeze"), Map.entry("hung", "hang"), Map.entry("lit", "light"),
      Map.entry("slid", "slide"), Map.entry("stuck", "stick"), Map.entry("struck", "strike"), Map.entry("swore", "swear"),
      Map.entry("sworn", "swear"), Map.entry("wept", "weep"), Map.entry("lay", "lie"), Map.entry("lain", "lie"),
      Map.entry("children", "child"), Map.entry("men", "man"), Map.entry("women", "woman"), Map.entry("people", "person"),
      Map.entry("feet", "foot"), Map.entry("teeth", "tooth"), Map.entry("mice", "mouse"), Map.entry("geese", "goose"),
      Map.entry("lives", "life"), Map.entry("wives", "wife"), Map.entry("knives", "knife"), Map.entry("leaves", "leaf"),
      Map.entry("halves", "half"), Map.entry("shelves", "shelf"), Map.entry("wolves", "wolf"), Map.entry("thieves", "thief"),
      Map.entry("better", "good"), Map.entry("best", "good"), Map.entry("worse", "bad"), Map.entry("worst", "bad"));

  /** Trông như có đuôi -s / -ing nhưng ĐÃ là dạng gốc: không cắt. */
  private static final Set<String> KEEP = Set.of(
      "news", "bus", "gas", "yes", "this", "his", "is", "was", "has", "always", "perhaps", "series", "species",
      "physics", "mathematics", "economics", "politics", "means", "lens", "plus", "status", "virus", "campus",
      "bonus", "focus", "chaos", "crisis", "basis", "analysis", "thesis", "morning", "evening", "nothing",
      "something", "anything", "everything", "thing", "king", "ring", "sing", "bring", "spring", "string",
      "wing", "swing", "during", "ceiling", "building", "meaning", "feeling", "wedding", "pudding", "need",
      "speed", "seed", "feed", "bed", "red", "shed", "hundred", "sacred", "naked", "wicked");

  /** Các dạng có thể là gốc, theo thứ tự ưu tiên. Phần tử đầu luôn là chính word. */
  public static List<String> candidates(String word) {
    Set<String> out = new LinkedHashSet<>();
    out.add(word);
    String irregular = IRREGULAR.get(word);
    if (irregular != null) {
      out.add(irregular);
    }
    if (!KEEP.contains(word)) {
      int n = word.length();
      if (word.endsWith("ies") && n > 4) out.add(word.substring(0, n - 3) + "y");      // studies → study
      if (word.endsWith("es") && n > 3) out.add(word.substring(0, n - 2));             // boxes → box
      if (word.endsWith("s") && !word.endsWith("ss") && n > 3) out.add(word.substring(0, n - 1));   // books → book
      if (word.endsWith("ied") && n > 4) out.add(word.substring(0, n - 3) + "y");      // studied → study
      if (word.endsWith("ed") && n > 3) addStemVariants(out, word.substring(0, n - 2)); // walked, liked, stopped
      if (word.endsWith("ing") && n > 4) {
        addStemVariants(out, word.substring(0, n - 3));                                // walking, making, running
        if (word.endsWith("ying")) out.add(word.substring(0, n - 4) + "ie");           // lying → lie
      }
    }
    out.removeIf(s -> s.length() < 2);
    return new ArrayList<>(out);
  }

  /** Dạng gốc đoán được, dùng khi không có dạng nào trong DB. */
  public static String guess(String word) {
    String irregular = IRREGULAR.get(word);
    if (irregular != null) {
      return irregular;
    }
    if (KEEP.contains(word)) {
      return word;
    }
    int n = word.length();
    if ((word.endsWith("ies") || word.endsWith("ied")) && n > 4) {
      return word.substring(0, n - 3) + "y";
    }
    if (word.endsWith("ying") && n == 5) {
      return word.charAt(0) + "ie";                                                     // lying → lie, dying → die
    }
    if (word.endsWith("ing") && n > 4) {
      return restoreStem(word.substring(0, n - 3));
    }
    if (word.endsWith("ed") && n > 3) {
      return restoreStem(word.substring(0, n - 2));
    }
    if (word.endsWith("es") && n > 4 && endsWithAny(word.substring(0, n - 2), "s", "x", "z", "ch", "sh", "o")) {
      return word.substring(0, n - 2);                                                  // watches → watch
    }
    if (word.endsWith("s") && n > 3 && !endsWithAny(word, "ss", "us", "is")) {
      return word.substring(0, n - 1);                                                  // makes → make, books → book
    }
    return word;
  }

  // ---------- helpers ----------

  /** stem sau khi cắt -ed / -ing: thêm cả 3 khả năng, DB quyết định cái nào đúng. */
  private static void addStemVariants(Set<String> out, String stem) {
    out.add(stem);                                   // walk
    out.add(stem + "e");                             // make
    if (isDoubled(stem) || stem.endsWith("ll")) {
      out.add(stem.substring(0, stem.length() - 1));                         // run, travel (travelled)
    }
  }

  /** Chọn MỘT gốc cho -ed / -ing: bỏ phụ âm đôi, hoặc thêm e, hoặc giữ nguyên. */
  private static String restoreStem(String stem) {
    if (isDoubled(stem)) {
      return stem.substring(0, stem.length() - 1);   // stopp → stop, runn → run
    }
    if (stem.endsWith("e")) {
      return stem + "e";                             // agre(ed) → agree, fre(ed) → free
    }
    if (needsE(stem)) {
      return stem + "e";                             // mak → make, us → use, improv → improve
    }
    return stem;                                     // walk, open, visit, play
  }

  /** "stopp", "runn": hai phụ âm cuối giống nhau (trừ l, s, z: "fill", "pass", "buzz" là gốc thật). */
  private static boolean isDoubled(String s) {
    int n = s.length();
    return n >= 3 && s.charAt(n - 1) == s.charAt(n - 2) && !isVowel(s.charAt(n - 1)) && "lsz".indexOf(s.charAt(n - 1)) < 0;
  }

  /**
   * Gốc mất chữ e cuối khi thêm -ing / -ed?
   *   - kết thúc bằng v, z, c, dg, rg: tiếng Anh gần như không có từ gốc kết thúc như vậy (improv, realiz, produc, judg, charg)
   *   - hoặc một âm tiết dạng phụ âm–nguyên âm–phụ âm: mak, lik, writ, us (nhưng không áp cho w, x, y: snow, fix, play)
   */
  private static boolean needsE(String s) {
    if (endsWithAny(s, "v", "z", "c", "dg", "rg")) {
      return true;
    }
    int n = s.length();
    if (n < 2) {
      return false;
    }
    char last = s.charAt(n - 1), prev = s.charAt(n - 2);
    boolean cvc = !isVowel(last) && "wxy".indexOf(last) < 0 && isVowel(prev) && (n == 2 || !isVowel(s.charAt(n - 3)));
    return cvc && vowelGroups(s) == 1;
  }

  private static int vowelGroups(String s) {
    int groups = 0;
    boolean inVowel = false;
    for (char c : s.toCharArray()) {
      boolean v = isVowel(c);
      if (v && !inVowel) groups++;
      inVowel = v;
    }
    return groups;
  }

  private static boolean isVowel(char c) {
    return "aeiouy".indexOf(c) >= 0;
  }

  private static boolean endsWithAny(String s, String... suffixes) {
    for (String suffix : suffixes) {
      if (s.endsWith(suffix)) return true;
    }
    return false;
  }
}
