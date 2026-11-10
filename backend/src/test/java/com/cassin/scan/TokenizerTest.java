package com.cassin.scan;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cassin.scan.dto.Token;

/** Tokenizer: tách câu, tách từ, vị trí trong câu, đánh dấu viết hoa. */
class TokenizerTest {

  @Test
  @DisplayName("Tách câu sau . ! ?, gom khoảng trắng thừa trong câu")
  void splitsSentences() {
    Tokenizer.Result r = Tokenizer.tokenize("He ran.   She   smiled! Did you?");

    assertThat(r.sentences()).containsExactly("He ran.", "She smiled!", "Did you?");
  }

  @Test
  @DisplayName("Xuống dòng ĐƠN không tách câu (văn bản copy từ PDF), dòng TRỐNG thì tách")
  void singleNewlineDoesNotSplit() {
    Tokenizer.Result r = Tokenizer.tokenize("Breaking the ice at\nwork is hard\n\nNew paragraph");

    assertThat(r.sentences()).containsExactly("Breaking the ice at work is hard", "New paragraph");
  }

  @Test
  @DisplayName("Vị trí start / end của từ khớp substring trong câu")
  void tokenPositionsMatchSentence() {
    Tokenizer.Result r = Tokenizer.tokenize("He was running late.");
    Token running = r.tokens().get(2);

    assertThat(running.lower()).isEqualTo("running");
    assertThat(r.sentences().get(0).substring(running.start(), running.end())).isEqualTo("running");
  }

  @Test
  @DisplayName("Viết thường, bỏ 's sở hữu, giữ dạng rút gọn (it's, don’t → don't)")
  void normalizesWords() {
    Tokenizer.Result r = Tokenizer.tokenize("Anna's teacher said don’t. It's late.");

    assertThat(r.tokens()).extracting(t -> t.lower())
        .containsExactly("anna", "teacher", "said", "don't", "it's", "late");
  }

  @Test
  @DisplayName("upper / sentenceStart: phân biệt viết hoa đầu câu với viết hoa giữa câu (tên riêng)")
  void marksCapitalization() {
    Tokenizer.Result r = Tokenizer.tokenize("Visiting London was fun.");

    Token visiting = r.tokens().get(0), london = r.tokens().get(1), was = r.tokens().get(2);
    assertThat(visiting.upper()).isTrue();
    assertThat(visiting.capitalizedMidSentence()).isFalse(); // đầu câu: viết hoa không nói lên gì
    assertThat(london.capitalizedMidSentence()).isTrue();
    assertThat(was.upper()).isFalse();
  }

  @Test
  @DisplayName("Số và ký hiệu không phải từ; gạch nối tách 2 từ")
  void ignoresNumbersAndSplitsHyphen() {
    Tokenizer.Result r = Tokenizer.tokenize("In 2026, well-known apps cost $5.");

    assertThat(r.tokens()).extracting(t -> t.lower()).containsExactly("in", "well", "known", "apps", "cost");
  }
}
