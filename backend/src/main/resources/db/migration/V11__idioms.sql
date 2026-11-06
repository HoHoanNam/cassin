-- ============================================================================
-- V11: Thành ngữ (IDIOM) và cụm động từ (PHRASAL_VERB) là hai LOẠI TỪ mới.
--
--   Không có bảng mới: thành ngữ cũng là một Word, mỗi nghĩa là một Sense.
--   words  "break the ice"
--     └─ senses  part_of_speech = 'IDIOM', "phá vỡ sự ngại ngùng ban đầu"
--          └─ sense_examples  "He told a joke to [break the ice]."
--   → flashcard, Leitner, luyện tập, sổ lỗi, thư viện, nhập CSV dùng lại nguyên vẹn.
--
-- File này làm 2 việc:
--   1. Thêm CHECK cho senses.part_of_speech (trước đây cột nhận MỌI chuỗi ≤ 20 ký tự).
--   2. Thêm bộ hệ thống "Thành ngữ & cụm động từ" (18 cụm, nội dung giống mock-seed.ts ở frontend).
--
-- ⚠ Enum PartOfSpeech trong Java PHẢI có IDIOM, PHRASAL_VERB trước khi chạy app với file này:
--   dữ liệu mẫu bên dưới dùng 2 giá trị đó, Hibernate đọc giá trị lạ → lỗi khi mở bộ.
-- ⚠ Từ nay thêm một loại từ mới = sửa enum + viết migration đổi CHECK (giống V4 đổi CHECK của decks).
-- KHÔNG sửa file này sau khi đã chạy (Flyway lưu checksum).
-- ============================================================================

-- 1. CHECK: DB chỉ nhận đúng các giá trị của enum PartOfSpeech.
--    Lớp bảo vệ cuối: dù ai đó INSERT bằng tay sai chính tả ('IDOM'), DB vẫn từ chối.
--    Dữ liệu cũ đều do Java ghi (đúng enum) nên ADD CONSTRAINT không bị lỗi.
ALTER TABLE senses ADD CONSTRAINT chk_senses_part_of_speech CHECK (part_of_speech IN (
    'NOUN', 'VERB', 'ADJECTIVE', 'ADVERB', 'PREPOSITION', 'PRONOUN',
    'CONJUNCTION', 'INTERJECTION', 'ARTICLE', 'IDIOM', 'PHRASAL_VERB', 'OTHER'
));

-- 2. Hàm phụ (pg_temp: chỉ sống trong phiên chạy migration, giống V9).
--    Thêm MỘT nghĩa cho một từ hệ thống và đặt từ đó vào bộ p_deck.
--    Chạy an toàn cả khi admin đã tạo sẵn từ hệ thống trùng tên (vd "give up"):
--      - từ đã có         → dùng lại, không tạo từ thứ hai (uq_words_system_term cũng sẽ chặn)
--      - nghĩa đã có      → bỏ qua (ON CONFLICT ... DO NOTHING), không thêm ví dụ
--      - từ đã trong bộ   → bỏ qua
CREATE FUNCTION pg_temp.add_sense(p_deck BIGINT, p_term TEXT, p_pos TEXT, p_phonetic TEXT,
                                  p_definition TEXT, p_examples TEXT[]) RETURNS void AS $$
DECLARE
  wid BIGINT;
  sid BIGINT;
BEGIN
  SELECT id INTO wid FROM words WHERE owner_id IS NULL AND lower(term) = lower(p_term);
  IF wid IS NULL THEN
    INSERT INTO words (owner_id, term, phonetic) VALUES (NULL, p_term, p_phonetic)
    RETURNING id INTO wid;
  END IF;

  -- Nghĩa mới đứng CUỐI danh sách nghĩa của từ: position = vị trí lớn nhất + 1 (từ mới → 0).
  -- ON CONFLICT (word_id, lower(definition)): khớp unique index uq_senses_word_definition của V10.
  -- Không chèn dòng nào → RETURNING không trả gì → sid giữ NULL.
  INSERT INTO senses (word_id, part_of_speech, definition, position)
  SELECT wid, p_pos, p_definition, coalesce(max(position) + 1, 0) FROM senses WHERE word_id = wid
  ON CONFLICT (word_id, lower(definition)) DO NOTHING
  RETURNING id INTO sid;

  IF sid IS NOT NULL THEN
    INSERT INTO sense_examples (sense_id, position, example)
    SELECT sid, ord - 1, ex FROM unnest(p_examples) WITH ORDINALITY AS u(ex, ord);
  END IF;

  INSERT INTO deck_words (deck_id, word_id) VALUES (p_deck, wid) ON CONFLICT DO NOTHING;
END
$$ LANGUAGE plpgsql;

DO $$
DECLARE
  v_deck BIGINT;
BEGIN
  INSERT INTO decks (deck_name, description, deck_type, owner_id)
  VALUES ('Thành ngữ & cụm động từ',
          'Thành ngữ và cụm động từ hay gặp trong giao tiếp. Câu ví dụ đánh dấu cả cụm để luyện điền từ.',
          'SYSTEM_DECK', NULL)
  RETURNING id INTO v_deck;

  -- [cụm, loại, phiên âm, nghĩa, câu ví dụ]
  -- Đánh dấu ĐÚNG DẠNG trong câu: [gave up] chứ không phải [give up] → điền từ chấm đúng chia động từ.

  -- ---------- Thành ngữ ----------
  PERFORM pg_temp.add_sense(v_deck, 'break the ice', 'IDIOM', NULL, 'phá vỡ sự ngại ngùng ban đầu',
      ARRAY['He told a funny story to [break the ice].']);
  PERFORM pg_temp.add_sense(v_deck, 'a piece of cake', 'IDIOM', NULL, 'dễ như ăn bánh, rất dễ',
      ARRAY['The final test was [a piece of cake].']);
  PERFORM pg_temp.add_sense(v_deck, 'hit the books', 'IDIOM', NULL, 'học bài chăm chỉ',
      ARRAY['I have to [hit the books] tonight.']);
  PERFORM pg_temp.add_sense(v_deck, 'under the weather', 'IDIOM', NULL, 'thấy trong người không khỏe',
      ARRAY['I’m feeling a bit [under the weather] today.']);
  PERFORM pg_temp.add_sense(v_deck, 'cost an arm and a leg', 'IDIOM', NULL, 'đắt cắt cổ',
      ARRAY['This new phone [cost an arm and a leg].']);
  PERFORM pg_temp.add_sense(v_deck, 'once in a blue moon', 'IDIOM', NULL, 'hiếm khi, năm thì mười họa',
      ARRAY['We only eat out [once in a blue moon].']);
  PERFORM pg_temp.add_sense(v_deck, 'call it a day', 'IDIOM', NULL, 'dừng việc, nghỉ (hôm nay) thôi',
      ARRAY['It’s getting late, so let’s [call it a day].']);
  PERFORM pg_temp.add_sense(v_deck, 'let the cat out of the bag', 'IDIOM', NULL, 'lỡ để lộ bí mật',
      ARRAY['She [let the cat out of the bag] about the surprise party.']);
  PERFORM pg_temp.add_sense(v_deck, 'hit the road', 'IDIOM', NULL, 'lên đường',
      ARRAY['We should [hit the road] before it gets dark.']);
  PERFORM pg_temp.add_sense(v_deck, 'the last straw', 'IDIOM', NULL, 'giọt nước tràn ly',
      ARRAY['Being late again was [the last straw] for his boss.']);
  PERFORM pg_temp.add_sense(v_deck, 'on the same page', 'IDIOM', NULL, 'cùng quan điểm, hiểu ý nhau',
      ARRAY['Let’s make sure we are all [on the same page].']);
  PERFORM pg_temp.add_sense(v_deck, 'better late than never', 'IDIOM', NULL, 'muộn còn hơn không',
      ARRAY['You finally replied to my email. [Better late than never]!']);

  -- ---------- Cụm động từ ----------
  PERFORM pg_temp.add_sense(v_deck, 'give up', 'PHRASAL_VERB', '/ɡɪv ʌp/', 'từ bỏ',
      ARRAY['He [gave up] smoking last year.', 'Don’t [give up] on your dreams.']);
  PERFORM pg_temp.add_sense(v_deck, 'look after', 'PHRASAL_VERB', '/lʊk ˈɑːftə(r)/', 'chăm sóc, trông nom',
      ARRAY['Can you [look after] my dog this weekend?']);
  PERFORM pg_temp.add_sense(v_deck, 'find out', 'PHRASAL_VERB', '/faɪnd aʊt/', 'tìm ra, phát hiện ra',
      ARRAY['I [found out] the truth yesterday.']);
  PERFORM pg_temp.add_sense(v_deck, 'run out of', 'PHRASAL_VERB', '/rʌn aʊt əv/', 'hết, cạn (cái gì)',
      ARRAY['We have [run out of] milk again.']);
  PERFORM pg_temp.add_sense(v_deck, 'put off', 'PHRASAL_VERB', '/pʊt ɒf/', 'trì hoãn',
      ARRAY['Stop [putting off] your homework until tomorrow.']);
  PERFORM pg_temp.add_sense(v_deck, 'get along with', 'PHRASAL_VERB', '/ɡet əˈlɒŋ wɪð/', 'hòa hợp với (ai)',
      ARRAY['I [get along with] all my new classmates.']);
END
$$;
