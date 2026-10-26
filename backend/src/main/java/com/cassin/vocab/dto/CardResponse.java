package com.cassin.vocab.dto;

import java.util.List;

import com.cassin.vocab.Card;
import com.cassin.vocab.PartOfSpeech;

public record CardResponse(
  Long id, 
  String word, 
  PartOfSpeech partOfSpeech, 
  String phonetic,
  String definition, 
  List<String> examples
) {
  public static CardResponse from(Card card) {
    return new CardResponse(
      card.getId(), 
      card.getWord(), 
      card.getPartOfSpeech(), 
      card.getPhonetic(),
      card.getDefinition(),
      // List.copyOf: tạo bản sao. Trả ra list mà Hibernate đang quản lý là không an toàn
      List.copyOf(card.getExamples())
    );
  }
}
