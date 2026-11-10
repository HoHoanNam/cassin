package com.cassin.scan;

import java.util.Set;

/**
 * Từ chức năng (mạo từ, đại từ, giới từ, trợ động từ...): xuất hiện ở mọi câu, ai cũng biết,
 * đưa vào danh sách "từ mới" chỉ làm nhiễu. Viết thường; dạng rút gọn dùng dấu ' thẳng.
 */
public final class StopWords {
  private StopWords() {
  }

  private static final Set<String> WORDS = Set.of(
      // mạo từ, từ hạn định
      "a", "an", "the", "this", "that", "these", "those", "some", "any", "each", "every", "no", "all", "both",
      "either", "neither", "much", "many", "more", "most", "few", "less", "other", "another", "such", "own",
      // đại từ
      "i", "me", "my", "mine", "myself", "you", "your", "yours", "yourself", "yourselves", "he", "him", "his",
      "himself", "she", "her", "hers", "herself", "it", "its", "itself", "we", "us", "our", "ours", "ourselves",
      "they", "them", "their", "theirs", "themselves", "who", "whom", "whose", "which", "what", "one",
      // trợ động từ, be / have / do
      "am", "is", "are", "was", "were", "be", "been", "being", "have", "has", "had", "having", "do", "does",
      "did", "doing", "done", "will", "would", "shall", "should", "can", "could", "may", "might", "must",
      // giới từ
      "of", "in", "on", "at", "by", "for", "with", "about", "against", "between", "into", "through", "during",
      "before", "after", "above", "below", "to", "from", "up", "down", "out", "off", "over", "under", "again",
      "around", "near", "onto", "upon", "within", "without", "across", "along", "behind", "beyond", "since",
      "until", "till", "toward", "towards", "via",
      // liên từ, trạng từ chức năng
      "and", "but", "or", "nor", "so", "yet", "if", "then", "than", "because", "as", "while", "when", "where",
      "why", "how", "although", "though", "whether", "not", "only", "very", "too", "also", "just", "here",
      "there", "now", "ever", "never", "still", "already", "even", "else", "once",
      // dạng rút gọn
      "i'm", "i've", "i'll", "i'd", "you're", "you've", "you'll", "you'd", "he's", "he'll", "he'd", "she's",
      "she'll", "she'd", "it's", "it'll", "we're", "we've", "we'll", "we'd", "they're", "they've", "they'll",
      "they'd", "that's", "there's", "here's", "what's", "who's", "let's", "isn't", "aren't", "wasn't",
      "weren't", "don't", "doesn't", "didn't", "haven't", "hasn't", "hadn't", "won't", "wouldn't", "can't",
      "cannot", "couldn't", "shouldn't", "mustn't",
      // khác
      "yes", "ok", "okay", "oh", "mr", "mrs", "ms", "etc");

  public static boolean contains(String lowerWord) {
    return WORDS.contains(lowerWord);
  }
}
