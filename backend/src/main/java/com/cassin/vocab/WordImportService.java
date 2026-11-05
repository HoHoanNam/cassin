package com.cassin.vocab;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.cassin.common.BusinessException;
import com.cassin.user.User;
import com.cassin.user.UserRepository;
import com.cassin.vocab.dto.ImportResult;
import com.cassin.vocab.dto.ImportResult.ImportError;
import com.cassin.vocab.dto.ImportRow;

import lombok.RequiredArgsConstructor;

/**
 * Nhập từ vào bộ CỦA MÌNH từ file CSV. Một dòng = một NGHĨA; các dòng cùng term gộp thành 1 từ.
 * Giải thích thuật ngữ (multipart, InputStream, Reader, BOM, CSVFormat...): backend/docs/word-import.md
 *
 * Định dạng file (dòng đầu là tiêu đề, thứ tự cột tùy ý, cột không bắt buộc có thể bỏ):
 *
 *   term,phonetic,part_of_speech,definition,example_1,example_2
 *   light,/laɪt/,NOUN,ánh sáng,"Turn on the [light], please.",
 *   light,,ADJECTIVE,nhẹ,This bag is very [light].,
 *
 * Luồng xử lý (mỗi bước một method bên dưới):
 *
 *   ① read()      MultipartFile → List<ImportRow>        lỗi CẢ FILE (sai định dạng, quá dài) → ném 400
 *   ② checkRow()  kiểm tra từng dòng                      lỗi TỪNG DÒNG → GOM vào danh sách, không ném
 *   ③ group()     gom dòng cùng term → 1 từ nhiều nghĩa
 *   ④ toWord()    dựng entity Word, để Word tự kiểm tra   lỗi CẢ TỪ (2 nghĩa trùng...) → gom
 *   ⑤ bỏ qua từ đã có trong sổ từ của mình (skipped)
 *   ⑥ có lỗi → báo cáo, KHÔNG lưu | dryRun → xem trước, KHÔNG lưu | còn lại → lưu hết + đặt vào bộ
 *
 * "Tất cả hoặc không gì cả": có bất kỳ lỗi nào thì không lưu từ nào. Người dùng sửa file rồi tải lại,
 * không phải đoán dòng nào đã vào, dòng nào chưa.
 */
@Service
@RequiredArgsConstructor
public class WordImportService {
  static final int MAX_ROWS = 500;
  static final int MAX_EXAMPLE_COLUMNS = Sense.MAX_EXAMPLES; // example_1 … example_5
  static final List<String> REQUIRED_COLUMNS = List.of("term", "part_of_speech", "definition");

  // Giới hạn giống @Size trong WordRequest / SenseRequest (nhập tay hay nhập file đều cùng luật)
  private static final int MAX_TERM = 100;
  private static final int MAX_PHONETIC = 100;
  private static final int MAX_DEFINITION = 255;
  private static final int MAX_EXAMPLE = 500;

  /**
   * "Luật đọc" file CSV. Tạo MỘT lần (static final), dùng lại cho mọi lần đọc.
   *   DEFAULT               : phân cách bằng dấu phẩy, ô có dấu phẩy thì bao bằng "...", "" = một dấu "
   *   setHeader()           : không truyền tên cột → lấy DÒNG ĐẦU của file làm tên cột (record.get("term"))
   *   setSkipHeaderRecord   : không trả dòng tiêu đề như một dòng dữ liệu
   *   setIgnoreEmptyLines   : bỏ qua dòng trống (Excel hay thêm ở cuối file)
   *   setTrim               : cắt khoảng trắng hai đầu mỗi ô: "  light " → "light"
   *   get()                 : tạo CSVFormat từ các luật trên (bản cũ của thư viện dùng build())
   */
  private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
      .setHeader()
      .setSkipHeaderRecord(true)
      .setIgnoreEmptyLines(true)
      .setTrim(true)
      .get();

  private final DeckService deckService;
  private final WordRepository wordRepository;
  private final UserRepository userRepository;

  /**
   * POST /api/decks/{deckId}/import?dryRun=
   * Một transaction cho CẢ FILE: lỗi giữa chừng khi lưu thì rollback toàn bộ, không có chuyện lưu dở.
   */
  @Transactional
  public ImportResult importWords(Long deckId, MultipartFile file, boolean dryRun, Long userId) {
    // Bộ hệ thống → 403 DECK_READ_ONLY, bộ người khác → 404 (luật chung của DeckService)
    Deck deck = deckService.findEditable(deckId, userId);

    List<ImportRow> rows = read(file); // ①
    List<ImportError> errors = new ArrayList<>();
    List<ImportRow> validRows = new ArrayList<>();
    for (ImportRow row : rows) {
      List<ImportError> rowErrors = checkRow(row); // ②
      if (rowErrors.isEmpty()) {
        validRows.add(row);
      } else {
        errors.addAll(rowErrors);
      }
    }

    User owner = userRepository.getReferenceById(userId);
    List<Word> words = new ArrayList<>();
    for (List<ImportRow> sameTerm : group(validRows).values()) { // ③
      try {
        words.add(toWord(sameTerm, owner)); // ④
      } catch (IllegalArgumentException ex) {
        // Word.replaceSenses báo lỗi của CẢ TỪ → gắn vào dòng đầu tiên của từ đó
        errors.add(new ImportError(sameTerm.get(0).line(), null, ex.getMessage()));
      }
    }

    // ⑥a Có lỗi → không lưu gì, báo TẤT CẢ lỗi một lần (xếp theo số dòng cho dễ sửa file)
    if (!errors.isEmpty()) {
      errors.sort((a, b) -> Long.compare(a.line(), b.line()));
      return ImportResult.failed(errors);
    }

    // ⑤ Từ đã có trong sổ từ của mình → bỏ qua. MỘT câu SQL cho cả file (không hỏi từng từ).
    Set<String> existing = words.isEmpty() ? Set.of()
        : Set.copyOf(wordRepository.findOwnTermsLower(
            userId, words.stream().map(w -> w.getTerm().toLowerCase(Locale.ROOT)).toList()));
    List<String> skipped = words.stream().map(w -> w.getTerm())
        .filter(term -> existing.contains(term.toLowerCase(Locale.ROOT))).toList();
    List<Word> newWords = words.stream()
        .filter(w -> !existing.contains(w.getTerm().toLowerCase(Locale.ROOT))).toList();
    int senseCount = newWords.stream().mapToInt(w -> w.getSenses().size()).sum();

    // ⑥b Xem trước: trả đúng con số SẼ được nhập, nhưng không ghi gì vào database
    if (dryRun) {
      return new ImportResult(false, newWords.size(), senseCount, skipped, List.of());
    }

    // ⑥c Nhập thật: lưu từ (kèm nghĩa, nhờ cascade) rồi đặt vào bộ
    wordRepository.saveAll(newWords);
    for (Word word : newWords) {
      deckService.linkWord(deck, word);
    }
    return new ImportResult(true, newWords.size(), senseCount, skipped, List.of());
  }

  // ===================== ① Đọc file =====================

  /**
   * MultipartFile → danh sách dòng thô. Chỉ báo lỗi của CẢ FILE (ném 400 ngay):
   * file rỗng, sai cú pháp CSV, thiếu cột bắt buộc, quá MAX_ROWS dòng.
   * Package-private (không có public) để test gọi được trực tiếp.
   */
  List<ImportRow> read(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw invalidFile("EMPTY_FILE", "Vui lòng chọn một file CSV có dữ liệu");
    }

    // try-with-resources: reader và parser TỰ ĐÓNG khi ra khỏi khối (kể cả khi có lỗi),
    // theo thứ tự ngược lại: parser trước, reader sau.
    //   getInputStream()        : luồng BYTE, đọc dần, không nạp cả file vào RAM
    //   InputStreamReader(UTF_8): giải mã byte → KÝ TỰ. Luôn ghi rõ UTF-8, mặc định của Windows làm vỡ chữ Việt
    //   BufferedReader          : đọc theo khối, nhanh hơn; cần cho mark/reset ở skipBom()
    try (
        Reader reader = skipBom(
            new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)));
        CSVParser parser = FORMAT.parse(reader)) {

      List<String> missing = REQUIRED_COLUMNS.stream()
          .filter(column -> !parser.getHeaderNames().contains(column))
          .toList();
      if (!missing.isEmpty()) {
        throw invalidFile("INVALID_FILE", "File thiếu cột: " + String.join(", ", missing)
            + ". Dòng đầu tiên phải là tiêu đề, vd: term,phonetic,part_of_speech,definition,example_1");
      }

      List<ImportRow> rows = new ArrayList<>();
      for (CSVRecord record : parser) { // mỗi vòng = MỘT dòng dữ liệu, parser đọc dần từng dòng
        if (rows.size() == MAX_ROWS) {
          // Dừng NGAY ở dòng thứ 501: không cần đọc nốt phần còn lại của một file đằng nào cũng bị từ chối
          throw invalidFile("TOO_MANY_ROWS", "Mỗi file tối đa " + MAX_ROWS + " dòng. Hãy chia thành nhiều file.");
        }
        List<String> examples = new ArrayList<>();
        for (int i = 1; i <= MAX_EXAMPLE_COLUMNS; i++) {
          String example = cell(record, "example_" + i);
          if (!example.isEmpty()) {
            examples.add(example);
          }
        }
        rows.add(new ImportRow(
            // getRecordNumber() đếm DÒNG DỮ LIỆU từ 1 (dòng tiêu đề không tính) → +1 ra số dòng trong file.
            // Điều này được KIỂM CHỨNG bởi WordImportServiceTest.read_reportsFileLineNumbers:
            // nâng phiên bản commons-csv mà test đó hỏng thì sửa lại dòng này.
            record.getRecordNumber() + 1,
            cell(record, "term"),
            cell(record, "phonetic"),
            cell(record, "part_of_speech"),
            cell(record, "definition"),
            examples));
      }
      if (rows.isEmpty()) {
        throw invalidFile("EMPTY_FILE", "File chỉ có dòng tiêu đề, chưa có từ nào");
      }
      return rows;

    } catch (IOException | IllegalArgumentException | IllegalStateException | java.io.UncheckedIOException ex) {
      // Commons CSV ném IllegalArgument/IllegalState/UncheckedIO khi file sai cú pháp
      // (ngoặc kép không đóng, tiêu đề trùng tên...). Đó là lỗi của FILE người dùng → 400, không phải 500.
      throw invalidFile("INVALID_FILE", "Không đọc được file CSV: " + ex.getMessage());
    }
  }

  /**
   * Bỏ ký tự BOM (﻿) ở đầu file nếu có. Excel thêm 3 byte vô hình này khi lưu "CSV UTF-8";
   * không bỏ thì tên cột đầu tiên thành "﻿term" và bị báo là thiếu cột "term".
   * mark(1): đánh dấu vị trí hiện tại; đọc thử 1 ký tự; không phải BOM thì reset() quay lại chỗ đánh dấu.
   */
  private static Reader skipBom(BufferedReader reader) throws IOException {
    reader.mark(1);
    if (reader.read() != '﻿') {
      reader.reset();
    }
    return reader;
  }

  /** Lấy một ô theo tên cột. Cột không có trong file (isMapped) hoặc dòng ngắn hơn tiêu đề (isSet) → "". */
  private static String cell(CSVRecord record, String column) {
    return record.isMapped(column) && record.isSet(column) ? record.get(column) : "";
  }

  // ===================== ② Kiểm tra từng dòng =====================

  /** Mọi lỗi của MỘT dòng. Trả danh sách (không ném) để báo được nhiều lỗi cùng lúc. */
  List<ImportError> checkRow(ImportRow row) {
    List<ImportError> errors = new ArrayList<>();
    long line = row.line();
    if (row.term().isEmpty()) {
      errors.add(new ImportError(line, "term", "Thiếu từ"));
    } else if (row.term().length() > MAX_TERM) {
      errors.add(new ImportError(line, "term", "Từ tối đa " + MAX_TERM + " ký tự"));
    }
    if (row.phonetic().length() > MAX_PHONETIC) {
      errors.add(new ImportError(line, "phonetic", "Phiên âm tối đa " + MAX_PHONETIC + " ký tự"));
    }
    if (parsePartOfSpeech(row.partOfSpeech()) == null) {
      errors.add(new ImportError(line, "part_of_speech",
          "Loại từ không hợp lệ: \"" + row.partOfSpeech() + "\". Dùng một trong: " + PART_OF_SPEECH_NAMES));
    }
    if (row.definition().isEmpty()) {
      errors.add(new ImportError(line, "definition", "Thiếu nghĩa"));
    } else if (row.definition().length() > MAX_DEFINITION) {
      errors.add(new ImportError(line, "definition", "Nghĩa tối đa " + MAX_DEFINITION + " ký tự"));
    }
    for (int i = 0; i < row.examples().size(); i++) {
      String example = row.examples().get(i);
      String column = "example_" + (i + 1);
      if (example.length() > MAX_EXAMPLE) {
        errors.add(new ImportError(line, column, "Câu ví dụ tối đa " + MAX_EXAMPLE + " ký tự"));
      } else {
        try {
          ExampleMarkup.validate(example); // dùng lại đúng luật đánh dấu [từ] của form nhập tay
        } catch (IllegalArgumentException ex) {
          errors.add(new ImportError(line, column, ex.getMessage()));
        }
      }
    }
    return errors;
  }

  private static final String PART_OF_SPEECH_NAMES = java.util.Arrays.stream(PartOfSpeech.values())
      .map(p -> p.name()).collect(Collectors.joining(", "));

  /** "noun", " Noun " → NOUN. Không khớp giá trị nào → null (không ném, để checkRow gom lỗi). */
  static PartOfSpeech parsePartOfSpeech(String raw) {
    try {
      return PartOfSpeech.valueOf(raw.strip().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  // ===================== ③ ④ Gom nhóm + dựng Word =====================

  /**
   * Gom các dòng cùng term (không phân biệt hoa thường) → mỗi nhóm thành 1 từ nhiều nghĩa.
   * LinkedHashMap: giữ THỨ TỰ xuất hiện trong file (HashMap thường sẽ xáo trộn thứ tự).
   */
  static Map<String, List<ImportRow>> group(List<ImportRow> rows) {
    return rows.stream().collect(Collectors.groupingBy(
        row -> row.term().toLowerCase(Locale.ROOT),
        LinkedHashMap::new,
        Collectors.toList()));
  }

  /**
   * Dựng entity Word (CHƯA lưu) từ một nhóm dòng. Cách viết (hoa thường) và phiên âm lấy từ dòng ĐẦU.
   * Word.replaceSenses tự kiểm tra: tối đa 10 nghĩa, 2 nghĩa trùng nhau... → IllegalArgumentException.
   */
  private static Word toWord(List<ImportRow> sameTerm, User owner) {
    ImportRow first = sameTerm.get(0);
    Word word = Word.personal(first.term(), first.phonetic().isEmpty() ? null : first.phonetic(), owner);
    word.replaceSenses(sameTerm.stream()
        .map(row -> new SenseInput(null, parsePartOfSpeech(row.partOfSpeech()), row.definition(), row.examples()))
        .toList());
    return word;
  }

  private static BusinessException invalidFile(String code, String message) {
    return new BusinessException(HttpStatus.BAD_REQUEST, code, message);
  }
}
