package com.cassin.scan;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.DisplayName;

class StopWordsTest {

  @ParameterizedTest(name = "\"{0}\" là từ chức năng")
  @ValueSource(strings = { "the", "is", "of", "they", "don't", "it's", "would" })
  @DisplayName("Từ chức năng bị bỏ qua")
  void functionWords(String word) {
    assertThat(StopWords.contains(word)).isTrue();
  }

  @ParameterizedTest(name = "\"{0}\" là từ có nghĩa")
  @ValueSource(strings = { "station", "run", "break", "ice", "hard" })
  @DisplayName("Từ có nghĩa không bị bỏ")
  void contentWords(String word) {
    assertThat(StopWords.contains(word)).isFalse();
  }
}
