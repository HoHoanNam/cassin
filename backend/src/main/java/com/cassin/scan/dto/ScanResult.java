package com.cassin.scan.dto;

import java.util.List;

/**
 * Kết quả quét.
 *
 * @param wordCount    số từ có nghĩa trong đoạn (đã bỏ từ chức năng, tên riêng; một cụm tính là 1)
 * @param uniqueCount  số từ gốc khác nhau trong số đó
 * @param knownCount   số từ gốc ĐÃ có trong sổ từ (uniqueCount - knownCount = số từ chưa học)
 * @param truncated    true nếu có hơn MAX_ITEMS từ chưa học và chỉ trả MAX_ITEMS từ đầu
 * @param items        từ chưa học, count giảm dần, bằng nhau thì từ xuất hiện trước đứng trước
 */
public record ScanResult(
    int wordCount,
    int uniqueCount,
    int knownCount,
    boolean truncated,
    List<ScanItem> items) {
}
