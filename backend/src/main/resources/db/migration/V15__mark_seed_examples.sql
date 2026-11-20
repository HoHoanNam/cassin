-- [THÊM] Đánh dấu [từ đích] cho 24 câu ví dụ seed ở V9 để dùng được cho chế độ Điền từ.
--
-- Điền từ chỉ lấy câu có đánh dấu (ExampleMarkup): "Her English has [improved] a lot." → hỏi "improved".
-- V9 seed câu không đánh dấu nên trên dữ liệu thật chế độ này gần như trống. (Thành ngữ ở V11 đã có sẵn.)
-- Ôn thẻ và Xếp câu hiển thị câu đã bỏ [ ], nên đánh dấu thêm không đổi gì ở hai chế độ đó.
--
-- An toàn: chỉ sửa câu của từ HỆ THỐNG (owner_id NULL) và đúng NGUYÊN VĂN câu gốc.
-- Admin đã sửa câu nào thì câu đó không còn khớp → giữ nguyên. Chạy lại cũng vô hại (câu đã đánh dấu không khớp nữa).
UPDATE sense_examples e
SET example = v.marked
FROM (VALUES
  ('Her English has improved a lot.',              'Her English has [improved] a lot.'),
  ('She achieved her goal of running a marathon.', 'She [achieved] her goal of running a marathon.'),
  ('This job is a great opportunity for you.',     'This job is a great [opportunity] for you.'),
  ('We must protect the environment.',             'We must protect the [environment].'),
  ('Is it necessary to book in advance?',          'Is it [necessary] to book in advance?'),
  ('They decided to stay at home.',                'They [decided] to stay at home.'),
  ('Do you have any teaching experience?',         'Do you have any teaching [experience]?'),
  ('The city experienced heavy rain last week.',   'The city [experienced] heavy rain last week.'),
  ('Turn on the light, please.',                   'Turn on the [light], please.'),
  ('This bag is light enough to carry.',           'This bag is [light] enough to carry.'),
  ('Although it was raining, we went out.',        '[Although] it was raining, we went out.'),
  ('He will probably be late.',                    'He will [probably] be late.'),
  ('Can I borrow your pen?',                       'Can I [borrow] your pen?'),
  ('Reading improves your knowledge.',             'Reading improves your [knowledge].'),
  ('Be careful when you cross the road.',          'Be [careful] when you cross the road.'),
  ('The bank is between the café and the school.', 'The bank is [between] the café and the school.'),
  ('I can’t remember his name.',                   'I can’t [remember] his name.'),
  ('Fruit is part of a healthy diet.',             'Fruit is part of a [healthy] diet.'),
  ('Suddenly, the lights went out.',               '[Suddenly], the lights went out.'),
  ('My parents always support me.',                'My parents always [support] me.'),
  ('The journey takes three hours.',               'The [journey] takes three hours.'),
  ('He isn’t old enough to drive.',                'He isn’t old [enough] to drive.'),
  ('Can you explain this word to me?',             'Can you [explain] this word to me?'),
  ('Her face looks familiar.',                     'Her face looks [familiar].')
) AS v(plain, marked),
senses s,
words w
WHERE e.example = v.plain
  AND s.id = e.sense_id
  AND w.id = s.word_id
  AND w.owner_id IS NULL;
