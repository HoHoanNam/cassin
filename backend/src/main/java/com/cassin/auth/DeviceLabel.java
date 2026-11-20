package com.cassin.auth;

import java.util.List;
import java.util.Map;

/**
 * [THÊM] Đổi header User-Agent thành nhãn dễ đọc cho màn hình "Thiết bị đang đăng nhập": "Chrome · Windows".
 *
 * Chỉ cần nhận ra các trình duyệt / hệ điều hành phổ biến, không cần thư viện phân tích UA.
 * THỨ TỰ quan trọng vì UA "mượn" tên nhau:
 *   Edge   : "... Chrome/120 Safari/537 Edg/120"     → phải xét Edg/ TRƯỚC Chrome/
 *   Opera  : "... Chrome/120 Safari/537 OPR/106"     → phải xét OPR/ TRƯỚC Chrome/
 *   Chrome : "... Chrome/120 Safari/537"             → phải xét Chrome/ TRƯỚC Safari/
 *   iPad   : "... (iPad; CPU OS 17 like Mac OS X)"   → phải xét iPad TRƯỚC Mac OS X
 *   Android: "... (Linux; Android 14; ...)"          → phải xét Android TRƯỚC Linux
 */
public record DeviceLabel(String browser, String os) {

  static final String UNKNOWN = "Không rõ";

  // [dấu hiệu trong UA, tên hiển thị]: xét từ trên xuống, khớp cái đầu tiên
  private static final List<Map.Entry<String, String>> BROWSERS = List.of(
      Map.entry("Edg", "Edge"), // Edg/ (desktop), EdgA/ (Android), EdgiOS/ (iPhone)
      Map.entry("OPR/", "Opera"),
      Map.entry("SamsungBrowser/", "Samsung Internet"),
      Map.entry("CriOS/", "Chrome"), // Chrome trên iPhone
      Map.entry("FxiOS/", "Firefox"), // Firefox trên iPhone
      Map.entry("Firefox/", "Firefox"),
      Map.entry("Chrome/", "Chrome"),
      Map.entry("Safari/", "Safari"),
      Map.entry("PostmanRuntime/", "Postman"),
      Map.entry("curl/", "curl"));

  private static final List<Map.Entry<String, String>> SYSTEMS = List.of(
      Map.entry("Windows", "Windows"),
      Map.entry("Android", "Android"),
      Map.entry("iPhone", "iOS"),
      Map.entry("iPad", "iPadOS"),
      Map.entry("Mac OS X", "macOS"),
      Map.entry("CrOS", "ChromeOS"),
      Map.entry("Linux", "Linux"));

  public static DeviceLabel parse(String userAgent) {
    if (userAgent == null || userAgent.isBlank()) {
      return new DeviceLabel(UNKNOWN, UNKNOWN);
    }
    return new DeviceLabel(firstMatch(BROWSERS, userAgent), firstMatch(SYSTEMS, userAgent));
  }

  private static String firstMatch(List<Map.Entry<String, String>> rules, String userAgent) {
    return rules.stream()
        .filter(rule -> userAgent.contains(rule.getKey()))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse(UNKNOWN);
  }
}
