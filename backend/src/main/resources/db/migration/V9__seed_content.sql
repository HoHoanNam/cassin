-- ============================================================================
-- V9: Dữ liệu mẫu dùng chung cho MỌI người dùng (chạy cả trên server thật).
--   - 1 bộ từ hệ thống "1000 từ thông dụng" (24 thẻ, kèm câu ví dụ)
--   - 6 chủ đề ngữ pháp, 30 câu hỏi, mỗi câu 4 đáp án
--
-- KHÔNG có ở đây:
--   - Tài khoản (admin do AdminBootstrap tạo từ biến môi trường; mật khẩu không được nằm trong git)
--   - Bộ từ cá nhân: bộ cá nhân luôn phải có chủ (CHECK trong V4), migration không biết chủ là ai
--
-- Nội dung sinh ra từ frontend/src/app/core/mock/mock-seed.ts, nên chế độ mock và backend thật
-- hiển thị CÙNG một bộ dữ liệu. Sửa nội dung về sau: viết migration mới (V10...) hoặc dùng API admin,
-- KHÔNG sửa file này (Flyway lưu checksum, sửa file đã chạy thì app không khởi động).
-- ============================================================================

-- Hai hàm phụ trong schema pg_temp: chỉ tồn tại trong phiên chạy migration này,
-- xong phiên là tự biến mất, không để lại gì trong database.

-- Thêm một thẻ + các câu ví dụ.
-- WITH ORDINALITY: đánh số 1, 2, 3... cho từng phần tử của mảng → position = số đó - 1 (bắt đầu từ 0,
-- khớp @OrderColumn của Card.examples).
CREATE FUNCTION pg_temp.add_card(p_deck BIGINT, p_word TEXT, p_pos TEXT, p_phonetic TEXT,
                                 p_definition TEXT, p_examples TEXT[]) RETURNS void AS $$
DECLARE
  cid BIGINT;
BEGIN
  INSERT INTO cards (deck_id, word, part_of_speech, phonetic, definition)
  VALUES (p_deck, p_word, p_pos, p_phonetic, p_definition)
  RETURNING id INTO cid;

  INSERT INTO card_examples (card_id, position, example)
  SELECT cid, ord - 1, ex
  FROM unnest(p_examples) WITH ORDINALITY AS u(ex, ord);
END
$$ LANGUAGE plpgsql;

-- Thêm một câu hỏi + đúng 4 đáp án; p_correct là VỊ TRÍ (0..3) của đáp án đúng.
-- Luật "đúng 4 đáp án, đúng 1 đáp án đúng, không trùng" chỉ được Question.java kiểm tra,
-- nên hàm này tự kiểm tra lại: dữ liệu mẫu sai thì migration DỪNG, không lặng lẽ ghi vào DB.
CREATE FUNCTION pg_temp.add_question(p_topic BIGINT, p_content TEXT, p_explanation TEXT,
                                     p_options TEXT[], p_correct INT) RETURNS void AS $$
DECLARE
  qid BIGINT;
BEGIN
  IF array_length(p_options, 1) <> 4 OR p_correct NOT BETWEEN 0 AND 3 THEN
    RAISE EXCEPTION 'Câu hỏi "%" phải có đúng 4 đáp án và 1 đáp án đúng', p_content;
  END IF;
  IF (SELECT count(DISTINCT lower(o)) FROM unnest(p_options) AS o) <> 4 THEN
    RAISE EXCEPTION 'Câu hỏi "%" có đáp án trùng nhau', p_content;
  END IF;

  INSERT INTO questions (topic_id, content, explanation)
  VALUES (p_topic, p_content, p_explanation)
  RETURNING id INTO qid;

  INSERT INTO question_options (question_id, content, is_correct, position)
  SELECT qid, opt, (ord - 1) = p_correct, ord - 1
  FROM unnest(p_options) WITH ORDINALITY AS u(opt, ord);
END
$$ LANGUAGE plpgsql;

DO $$
DECLARE
  v_deck  BIGINT;
  v_topic BIGINT;
BEGIN
  -- Bài giải thích của chủ đề bọc trong dấu $txt$ ... $txt$ (chuỗi "dollar-quoted"):
  -- giữ nguyên xuống dòng, dấu nháy đơn / kép, không phải thoát từng ký tự.

  -- ===================== Bộ từ hệ thống =====================
  -- deck_type = SYSTEM_DECK thì owner_id phải NULL (CHECK chk_decks_type_owner trong V4).
  INSERT INTO decks (deck_name, description, deck_type, owner_id)
  VALUES ('1000 từ thông dụng', 'Những từ xuất hiện nhiều nhất trong giao tiếp và bài đọc hằng ngày.', 'SYSTEM_DECK', NULL)
  RETURNING id INTO v_deck;

  -- [từ, loại từ, phiên âm, nghĩa, câu ví dụ]
  PERFORM pg_temp.add_card(v_deck, 'improve', 'VERB', '/ɪmˈpruːv/', 'cải thiện, tiến bộ',
                           ARRAY['Her English has improved a lot.']);
  PERFORM pg_temp.add_card(v_deck, 'achieve', 'VERB', '/əˈtʃiːv/', 'đạt được',
                           ARRAY['She achieved her goal of running a marathon.']);
  PERFORM pg_temp.add_card(v_deck, 'opportunity', 'NOUN', '/ˌɒpəˈtjuːnəti/', 'cơ hội',
                           ARRAY['This job is a great opportunity for you.']);
  PERFORM pg_temp.add_card(v_deck, 'environment', 'NOUN', '/ɪnˈvaɪrənmənt/', 'môi trường',
                           ARRAY['We must protect the environment.']);
  PERFORM pg_temp.add_card(v_deck, 'necessary', 'ADJECTIVE', '/ˈnesəsəri/', 'cần thiết',
                           ARRAY['Is it necessary to book in advance?']);
  PERFORM pg_temp.add_card(v_deck, 'decide', 'VERB', '/dɪˈsaɪd/', 'quyết định',
                           ARRAY['They decided to stay at home.']);
  PERFORM pg_temp.add_card(v_deck, 'experience', 'NOUN', '/ɪkˈspɪəriəns/', 'kinh nghiệm',
                           ARRAY['Do you have any teaching experience?']);
  PERFORM pg_temp.add_card(v_deck, 'experience', 'VERB', '/ɪkˈspɪəriəns/', 'trải qua',
                           ARRAY['The city experienced heavy rain last week.']);
  PERFORM pg_temp.add_card(v_deck, 'light', 'NOUN', '/laɪt/', 'ánh sáng',
                           ARRAY['Turn on the light, please.']);
  PERFORM pg_temp.add_card(v_deck, 'light', 'ADJECTIVE', '/laɪt/', 'nhẹ',
                           ARRAY['This bag is light enough to carry.']);
  PERFORM pg_temp.add_card(v_deck, 'although', 'CONJUNCTION', '/ɔːlˈðəʊ/', 'mặc dù',
                           ARRAY['Although it was raining, we went out.']);
  PERFORM pg_temp.add_card(v_deck, 'probably', 'ADVERB', '/ˈprɒbəbli/', 'có lẽ, chắc là',
                           ARRAY['He will probably be late.']);
  PERFORM pg_temp.add_card(v_deck, 'borrow', 'VERB', '/ˈbɒrəʊ/', 'mượn',
                           ARRAY['Can I borrow your pen?']);
  PERFORM pg_temp.add_card(v_deck, 'knowledge', 'NOUN', '/ˈnɒlɪdʒ/', 'kiến thức',
                           ARRAY['Reading improves your knowledge.']);
  PERFORM pg_temp.add_card(v_deck, 'careful', 'ADJECTIVE', '/ˈkeəfl/', 'cẩn thận',
                           ARRAY['Be careful when you cross the road.']);
  PERFORM pg_temp.add_card(v_deck, 'between', 'PREPOSITION', '/bɪˈtwiːn/', 'giữa (hai vật)',
                           ARRAY['The bank is between the café and the school.']);
  PERFORM pg_temp.add_card(v_deck, 'remember', 'VERB', '/rɪˈmembə(r)/', 'nhớ',
                           ARRAY['I can’t remember his name.']);
  PERFORM pg_temp.add_card(v_deck, 'healthy', 'ADJECTIVE', '/ˈhelθi/', 'khỏe mạnh, tốt cho sức khỏe',
                           ARRAY['Fruit is part of a healthy diet.']);
  PERFORM pg_temp.add_card(v_deck, 'suddenly', 'ADVERB', '/ˈsʌdənli/', 'đột nhiên',
                           ARRAY['Suddenly, the lights went out.']);
  PERFORM pg_temp.add_card(v_deck, 'support', 'VERB', '/səˈpɔːt/', 'ủng hộ, hỗ trợ',
                           ARRAY['My parents always support me.']);
  PERFORM pg_temp.add_card(v_deck, 'journey', 'NOUN', '/ˈdʒɜːni/', 'chuyến đi, hành trình',
                           ARRAY['The journey takes three hours.']);
  PERFORM pg_temp.add_card(v_deck, 'enough', 'ADVERB', '/ɪˈnʌf/', 'đủ',
                           ARRAY['He isn’t old enough to drive.']);
  PERFORM pg_temp.add_card(v_deck, 'explain', 'VERB', '/ɪkˈspleɪn/', 'giải thích',
                           ARRAY['Can you explain this word to me?']);
  PERFORM pg_temp.add_card(v_deck, 'familiar', 'ADJECTIVE', '/fəˈmɪliə(r)/', 'quen thuộc',
                           ARRAY['Her face looks familiar.']);

  -- ===================== Chủ đề: Thì hiện tại hoàn thành (A2) =====================
  INSERT INTO grammar_topics (title, level, explanation)
  VALUES ('Thì hiện tại hoàn thành', 'A2', $txt$Dùng thì hiện tại hoàn thành khi một việc xảy ra trong quá khứ nhưng vẫn còn liên quan tới hiện tại: kết quả còn đó, hoặc việc vẫn đang tiếp diễn.

Cấu trúc: S + have/has + V3 (quá khứ phân từ).
• I/You/We/They + have + V3
• He/She/It + has + V3

Dấu hiệu thường gặp: since (kể từ một mốc), for (trong một khoảng), already, yet, ever, never, just, so far.

Ví dụ: She has lived in Hanoi since 2020. (Cô ấy sống ở Hà Nội từ 2020 và bây giờ vẫn sống ở đó.)

Phân biệt với quá khứ đơn: có mốc thời gian đã kết thúc (yesterday, last year, in 2019) thì dùng quá khứ đơn, không dùng hiện tại hoàn thành.$txt$)
  RETURNING id INTO v_topic;

  PERFORM pg_temp.add_question(v_topic, 'She ___ in Hanoi since 2020.',
      'Có "since" + mốc thời gian, việc vẫn còn tiếp diễn → hiện tại hoàn thành: has + V3.',
      ARRAY['has lived', 'lived', 'lives', 'is living'], 0);
  PERFORM pg_temp.add_question(v_topic, 'I ___ this film twice.',
      'Nói về số lần trải nghiệm tính tới hiện tại → hiện tại hoàn thành.',
      ARRAY['saw', 'have seen', 'see', 'am seeing'], 1);
  PERFORM pg_temp.add_question(v_topic, 'They ___ their homework yet.',
      '"yet" trong câu phủ định → hiện tại hoàn thành: haven’t + V3.',
      ARRAY['didn’t finish', 'don’t finish', 'haven’t finished', 'aren’t finishing'], 2);
  PERFORM pg_temp.add_question(v_topic, '___ you ever ___ to Da Lat?',
      '"ever" hỏi về trải nghiệm → Have you ever + V3?',
      ARRAY['Did / go', 'Have / been', 'Do / go', 'Are / going'], 1);
  PERFORM pg_temp.add_question(v_topic, 'He ___ his keys, so he can’t open the door.',
      'Kết quả còn ở hiện tại (không mở được cửa) → hiện tại hoàn thành.',
      ARRAY['has lost', 'lost', 'loses', 'was losing'], 0);
  PERFORM pg_temp.add_question(v_topic, 'We ___ each other for ten years.',
      '"for" + khoảng thời gian, quan hệ vẫn còn → hiện tại hoàn thành.',
      ARRAY['know', 'knew', 'are knowing', 'have known'], 3);
  PERFORM pg_temp.add_question(v_topic, 'I ___ to the cinema last night.',
      '"last night" là mốc đã kết thúc → quá khứ đơn, không dùng hiện tại hoàn thành.',
      ARRAY['have gone', 'went', 'have been going', 'go'], 1);
  PERFORM pg_temp.add_question(v_topic, 'She has ___ finished her report.',
      '"just" (vừa mới) đứng giữa has và V3.',
      ARRAY['yet', 'ever', 'just', 'since'], 2);

  -- ===================== Chủ đề: Câu bị động (B1) =====================
  INSERT INTO grammar_topics (title, level, explanation)
  VALUES ('Câu bị động', 'B1', $txt$Dùng câu bị động khi muốn nhấn mạnh việc được làm hoặc đối tượng chịu tác động, hơn là người làm.

Cấu trúc chung: S + be (chia theo thì) + V3 (+ by + người làm).
• Hiện tại đơn: is/are + V3 → English is spoken here.
• Quá khứ đơn: was/were + V3 → This bridge was built in 1998.
• Hiện tại hoàn thành: has/have been + V3 → The letter has been sent.
• Với động từ khuyết thiếu: can/must/will + be + V3 → It must be done today.

Chỉ thêm "by + người làm" khi thông tin đó quan trọng.$txt$)
  RETURNING id INTO v_topic;

  PERFORM pg_temp.add_question(v_topic, 'This bridge ___ in 1998.',
      'Cây cầu không tự xây, có mốc quá khứ → bị động quá khứ đơn: was + V3.',
      ARRAY['was built', 'built', 'has built', 'is building'], 0);
  PERFORM pg_temp.add_question(v_topic, 'English ___ in many countries.',
      'Sự thật chung, ngôn ngữ "được nói" → is spoken.',
      ARRAY['speaks', 'is spoken', 'spoke', 'is speaking'], 1);
  PERFORM pg_temp.add_question(v_topic, 'The letter ___ yet.',
      '"yet" + bị động → hasn’t been + V3.',
      ARRAY['didn’t send', 'isn’t sent', 'hasn’t been sent', 'wasn’t sending'], 2);
  PERFORM pg_temp.add_question(v_topic, 'This work must ___ before Friday.',
      'Sau động từ khuyết thiếu: be + V3.',
      ARRAY['finish', 'be finished', 'finished', 'be finishing'], 1);
  PERFORM pg_temp.add_question(v_topic, 'The thief ___ by the police last night.',
      'Mốc "last night", tên trộm bị bắt → was caught.',
      ARRAY['caught', 'is caught', 'was caught', 'has caught'], 2);
  PERFORM pg_temp.add_question(v_topic, 'Rice ___ in the Mekong Delta.',
      'Sự thật chung, lúa "được trồng" → is grown.',
      ARRAY['grows', 'is grown', 'grew', 'is growing'], 1);
  PERFORM pg_temp.add_question(v_topic, 'A new hospital ___ next year.',
      'Tương lai + bị động → will be + V3.',
      ARRAY['will build', 'will be built', 'is building', 'builds'], 1);
  PERFORM pg_temp.add_question(v_topic, 'The windows ___ every week.',
      'Thói quen lặp lại, cửa sổ "được lau" → are cleaned.',
      ARRAY['clean', 'cleaned', 'are cleaned', 'are cleaning'], 2);

  -- ===================== Chủ đề: Câu điều kiện loại 1 và 2 (B1) =====================
  INSERT INTO grammar_topics (title, level, explanation)
  VALUES ('Câu điều kiện loại 1 và 2', 'B1', $txt$Loại 1: điều kiện CÓ THỂ xảy ra ở hiện tại hoặc tương lai.
If + hiện tại đơn, will + V.
Ví dụ: If it rains, we will stay at home.

Loại 2: điều kiện KHÔNG CÓ THẬT hoặc khó xảy ra ở hiện tại.
If + quá khứ đơn, would + V.
Ví dụ: If I had more time, I would learn French.

Lưu ý: ở loại 2, động từ "be" thường dùng "were" cho mọi chủ ngữ: If I were you, I would rest.$txt$)
  RETURNING id INTO v_topic;

  PERFORM pg_temp.add_question(v_topic, 'If it ___ tomorrow, we will cancel the trip.',
      'Loại 1: mệnh đề if dùng hiện tại đơn.',
      ARRAY['rains', 'will rain', 'rained', 'would rain'], 0);
  PERFORM pg_temp.add_question(v_topic, 'If I ___ rich, I would travel the world.',
      'Loại 2: không có thật ở hiện tại → quá khứ đơn, "be" dùng were.',
      ARRAY['am', 'will be', 'were', 'have been'], 2);
  PERFORM pg_temp.add_question(v_topic, 'If you heat ice, it ___.',
      'Sự thật luôn đúng: điều kiện + kết quả đều ở hiện tại đơn.',
      ARRAY['melts', 'would melt', 'melted', 'has melted'], 0);
  PERFORM pg_temp.add_question(v_topic, 'She ___ the bus if she leaves now.',
      'Loại 1: mệnh đề chính dùng will + V.',
      ARRAY['would catch', 'caught', 'will catch', 'catches'], 2);
  PERFORM pg_temp.add_question(v_topic, 'If I were you, I ___ that job.',
      'Lời khuyên với "If I were you" → would + V.',
      ARRAY['will take', 'would take', 'take', 'took'], 1);
  PERFORM pg_temp.add_question(v_topic, 'What would you do if you ___ a snake?',
      'Loại 2 (tình huống giả định) → quá khứ đơn.',
      ARRAY['see', 'will see', 'saw', 'have seen'], 2);

  -- ===================== Chủ đề: Quá khứ đơn và quá khứ tiếp diễn (A2) =====================
  INSERT INTO grammar_topics (title, level, explanation)
  VALUES ('Quá khứ đơn và quá khứ tiếp diễn', 'A2', $txt$Quá khứ tiếp diễn (was/were + V-ing) diễn tả một việc ĐANG xảy ra tại một thời điểm trong quá khứ.
Quá khứ đơn diễn tả một việc đã xảy ra và kết thúc.

Khi một việc đang diễn ra thì một việc khác xen vào:
• Việc đang diễn ra → quá khứ tiếp diễn
• Việc xen vào → quá khứ đơn
Ví dụ: I was cooking when the phone rang.$txt$)
  RETURNING id INTO v_topic;

  PERFORM pg_temp.add_question(v_topic, 'I ___ dinner when the phone rang.',
      'Việc đang diễn ra bị việc khác xen vào → was cooking.',
      ARRAY['cooked', 'was cooking', 'cook', 'have cooked'], 1);
  PERFORM pg_temp.add_question(v_topic, 'At 8 p.m. yesterday, they ___ TV.',
      'Thời điểm cụ thể trong quá khứ → quá khứ tiếp diễn.',
      ARRAY['watched', 'were watching', 'watch', 'have watched'], 1);
  PERFORM pg_temp.add_question(v_topic, 'She ___ her leg while she was skiing.',
      'Việc xen vào (gãy chân) → quá khứ đơn.',
      ARRAY['broke', 'was breaking', 'breaks', 'has broken'], 0);
  PERFORM pg_temp.add_question(v_topic, 'While I ___ to work, I saw an accident.',
      '"While" + việc đang diễn ra → was walking.',
      ARRAY['walked', 'walk', 'was walking', 'have walked'], 2);

  -- ===================== Chủ đề: Mệnh đề quan hệ (B2) =====================
  INSERT INTO grammar_topics (title, level, explanation)
  VALUES ('Mệnh đề quan hệ', 'B2', $txt$Mệnh đề quan hệ bổ nghĩa cho danh từ đứng trước nó.
• who: thay cho người
• which: thay cho vật
• that: thay cho cả người và vật (không dùng sau dấu phẩy)
• whose: chỉ sở hữu
• where: chỉ nơi chốn

Ví dụ: The woman who lives next door is a doctor.$txt$)
  RETURNING id INTO v_topic;

  PERFORM pg_temp.add_question(v_topic, 'The man ___ called you is my uncle.',
      'Thay cho người, làm chủ ngữ → who.',
      ARRAY['which', 'who', 'whose', 'where'], 1);
  PERFORM pg_temp.add_question(v_topic, 'This is the house ___ I was born.',
      'Chỉ nơi chốn → where.',
      ARRAY['which', 'who', 'where', 'whose'], 2);
  PERFORM pg_temp.add_question(v_topic, 'I have a friend ___ father is a pilot.',
      'Sở hữu (bố CỦA bạn ấy) → whose.',
      ARRAY['who', 'whose', 'which', 'that'], 1);
  PERFORM pg_temp.add_question(v_topic, 'My phone, ___ I bought last year, is broken.',
      'Sau dấu phẩy, thay cho vật → which (không dùng that).',
      ARRAY['that', 'who', 'which', 'where'], 2);

  -- ===================== Chủ đề: Câu tường thuật (B2) =====================
  INSERT INTO grammar_topics (title, level, explanation)
  VALUES ('Câu tường thuật', 'B2', $txt$Câu tường thuật thuật lại lời người khác nói. Khi động từ tường thuật ở quá khứ (said, told), thì của câu thường lùi một bậc: hiện tại đơn → quá khứ đơn, will → would...

Bộ câu hỏi cho chủ đề này đang được biên soạn.$txt$)
  RETURNING id INTO v_topic;

END
$$;
