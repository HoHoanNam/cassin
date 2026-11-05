# Nhập từ bằng file CSV — luồng xử lý & thuật ngữ

Tài liệu đi kèm `vocab/WordImportService.java`. Đọc phần **1** để nắm luồng, tra phần **2** khi gặp thuật ngữ lạ.

## 1. Luồng của một file, từ trình duyệt đến database

```
① Trình duyệt          ② HTTP                    ③ Tomcat + Spring          ④ Controller
   chọn vocab.csv  ──►  multipart/form-data  ──►  tách phần "file",       ──► MultipartFile file
                        (nội dung là BYTE)        ghi ra file tạm,
                                                  kiểm tra ≤ 1MB
                                                                                 │
⑧ Lưu + đặt vào bộ ◄─ ⑦ Gom theo term ◄─ ⑥ Kiểm tra từng dòng ◄─ ⑤ Đọc: byte → chữ → dòng → ImportRow
   (một transaction)     → Word nhiều nghĩa   (gom lỗi, không ném)     InputStream → Reader(UTF-8) → CSVParser
```

| Bước | Method | Lỗi kiểu gì | Xử lý |
|---|---|---|---|
| ⑤ đọc | `read()` | lỗi của **cả file**: rỗng, sai cú pháp, thiếu cột, > 500 dòng | ném 400 ngay |
| ⑥ kiểm tra | `checkRow()` | lỗi của **một dòng**: thiếu nghĩa, sai loại từ, `[ ]` sai | **gom** vào danh sách |
| ⑦ gom nhóm | `group()` + `toWord()` | lỗi của **cả từ**: 2 nghĩa trùng, > 10 nghĩa | gom, gắn vào dòng đầu của từ |
| ⑧ lưu | `importWords()` | có lỗi → không lưu; `dryRun` → không lưu; còn lại → lưu hết | "tất cả hoặc không gì cả" |

Định dạng file:

```csv
term,phonetic,part_of_speech,definition,example_1,example_2
light,/laɪt/,NOUN,ánh sáng,"Turn on the [light], please.",
light,,ADJECTIVE,nhẹ,This bag is very [light].,
```

- Dòng đầu là **tiêu đề** (bắt buộc). Thứ tự cột tùy ý; `phonetic`, `example_1`…`example_5` có thể bỏ.
- Một dòng = **một nghĩa**. Các dòng cùng `term` (không phân biệt hoa thường) gộp thành một từ.
- Ô có dấu phẩy thì bao trong ngoặc kép `"..."`.

## 2. Thuật ngữ

### HTTP & Spring

| Thuật ngữ | Nghĩa |
|---|---|
| **multipart/form-data** | Định dạng request để gửi FILE. Request chia thành nhiều *phần* (part), mỗi phần là một ô form. JSON không chở được file nên cần định dạng này. |
| **boundary** | Chuỗi ngẫu nhiên ngăn cách các phần trong request, như tờ giấy kẻ ngăn giữa các tài liệu trong một phong bì. |
| **part `name="file"`** | Tên ô form chứa file. Phía Java lấy ra bằng `@RequestParam("file")`. |
| **`MultipartFile`** | "Cái vỏ" Spring bọc file người dùng tải lên. Spring đã lưu nội dung ra file tạm và tự xóa khi hết request. |
| `getInputStream()` | Mở luồng đọc DẦN nội dung file (nên dùng). |
| `getBytes()` | Đọc HẾT file vào RAM một lần (tốn bộ nhớ với file lớn). |
| `getOriginalFilename()`, `getContentType()` | Do **client tự khai** → không tin được. Muốn biết có phải CSV không: đọc nội dung. |
| `max-file-size` (application.yml) | Giới hạn kích thước. Quá giới hạn → Spring từ chối TRƯỚC khi vào controller (`MaxUploadSizeExceededException`). |
| `consumes = MULTIPART_FORM_DATA_VALUE` | Endpoint chỉ nhận multipart; gửi JSON tới → 415. |

### Đọc file trong Java

| Thuật ngữ | Nghĩa |
|---|---|
| **`InputStream`** | Luồng **BYTE** (số 0–255). Máy hiểu, người không đọc được. |
| **`Reader`** | Luồng **KÝ TỰ** (chữ). |
| **`InputStreamReader(in, UTF_8)`** | Cầu nối byte → ký tự theo một **bảng mã**. |
| **Bảng mã (charset), UTF-8** | Quy tắc đổi byte thành chữ. "á" trong UTF-8 là 2 byte `C3 A1`; đọc nhầm bảng mã → `Ã¡`. **Luôn ghi rõ `StandardCharsets.UTF_8`**, không dùng mặc định của máy. |
| **`BufferedReader`** | Bọc Reader, đọc theo khối cho nhanh, có `mark()` / `reset()`. |
| **`mark(n)` / `reset()`** | Đánh dấu vị trí hiện tại / quay lại chỗ đã đánh dấu (đọc thử rồi "nhả" lại). |
| **BOM (`﻿`)** | 3 byte vô hình Excel thêm vào đầu file khi lưu "CSV UTF-8". Không bỏ thì tên cột đầu thành `"﻿term"` → báo thiếu cột `term`. `skipBom()` bỏ nó đi. |
| **Luồng (stream) = đọc dần** | Mỗi lần chỉ giữ một đoạn nhỏ trong RAM, thay vì cả file. |
| **try-with-resources** | `try (Reader r = ...; CSVParser p = ...) { }` → Java **tự đóng** r, p khi ra khỏi khối, kể cả khi có lỗi (đóng ngược thứ tự mở). Quên đóng file → server dần báo "too many open files". Mọi thứ trong ngoặc phải là `AutoCloseable`. |
| **`IOException`** | Lỗi khi đọc / ghi (file hỏng, mất kết nối). Java **bắt buộc** xử lý (checked exception). |

### Apache Commons CSV

| Thuật ngữ | Nghĩa |
|---|---|
| **Vì sao dùng thư viện** | `line.split(",")` hỏng ngay với `"Turn on the [light], please."` (dấu phẩy trong ngoặc kép), `""` lồng nhau, xuống dòng trong một ô, BOM... |
| **`CSVFormat`** | "Luật đọc" file. Tạo một lần (`static final`), dùng lại mãi. |
| `CSVFormat.DEFAULT` | Luật chuẩn: phân cách `,`, bao ô bằng `"`, `""` = một dấu `"`. |
| `setHeader()` | Không truyền tên → lấy **dòng đầu** làm tên cột ⇒ `record.get("term")` thay vì `record.get(0)`. |
| `setSkipHeaderRecord(true)` | Không trả dòng tiêu đề như một dòng dữ liệu. |
| `setIgnoreEmptyLines(true)` | Bỏ dòng trống. |
| `setTrim(true)` | Cắt khoảng trắng hai đầu mỗi ô. |
| `.get()` | Tạo `CSVFormat` từ builder (bản cũ dùng `.build()`). |
| **`CSVParser`** | "Máy đọc" theo luật `CSVFormat`. `for (CSVRecord r : parser)` đọc DẦN từng dòng. |
| `parser.getHeaderNames()` | Danh sách tên cột ở dòng tiêu đề. |
| **`CSVRecord`** | MỘT dòng dữ liệu đã tách ô. |
| `record.get("term")` | Ô theo tên cột. |
| `record.isMapped("phonetic")` | File **có cột** này không. |
| `record.isSet("example_2")` | Dòng **này** có giá trị ở cột đó không (dòng ngắn hơn tiêu đề → false). |
| `record.getRecordNumber()` | Số thứ tự **dòng dữ liệu**, bắt đầu từ 1 (tiêu đề không tính) → `+ 1` ra số dòng người dùng thấy. Được chốt bởi test `read_reportsFileLineNumbers` (commons-csv 1.14.1). |

### Thiết kế

| Thuật ngữ | Nghĩa |
|---|---|
| **Gom lỗi thay vì ném lỗi** | `checkRow()` trả `List<ImportError>` thay vì `throw` → báo được MỌI lỗi một lần, người dùng sửa file một lượt. |
| **Lỗi cả file / dòng / từ** | Mỗi loại xử lý ở một bước (xem bảng phần 1). |
| **Tất cả hoặc không gì cả** | Có lỗi → không lưu từ nào. Cộng với `@Transactional` trên cả method: lỗi lúc lưu → rollback toàn bộ. |
| **dryRun (xem trước)** | Chạy đủ mọi bước kiểm tra nhưng KHÔNG ghi database → FE hiện "Sẽ nhập 42 từ", người dùng bấm xác nhận mới gửi lại với `dryRun=false`. |
| **`LinkedHashMap`** | Map giữ đúng **thứ tự thêm vào** → từ được nhập theo thứ tự trong file (HashMap thường xáo trộn). |
| **`Collectors.groupingBy`** | Gom phần tử của stream theo một khóa: `term` viết thường → danh sách dòng. |
| **Một câu SQL cho cả file** | `findOwnTermsLower(userId, terms)` hỏi "từ nào đã có" bằng `in (...)` một lần, không hỏi 500 lần (tránh N+1). |
| **Dùng lại luật có sẵn** | `ExampleMarkup.validate`, `Word.replaceSenses`, `DeckService.findEditable` / `linkWord`: nhập tay hay nhập file đều qua **cùng một bộ luật**. |
