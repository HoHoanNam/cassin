package com.cassin.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** [THÊM] User-Agent thật của các trình duyệt phổ biến → nhãn hiển thị. Thứ tự luật là điểm dễ sai nhất. */
class DeviceLabelTest {

  @ParameterizedTest(name = "{1} · {2}")
  @CsvSource(delimiter = '|', textBlock = """
      Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 | Chrome | Windows
      Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0 | Edge | Windows
      Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 OPR/115.0.0.0 | Opera | Windows
      Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:133.0) Gecko/20100101 Firefox/133.0 | Firefox | Windows
      Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Safari/605.1.15 | Safari | macOS
      Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 | Chrome | macOS
      Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Mobile/15E148 Safari/604.1 | Safari | iOS
      Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/131.0.6778.73 Mobile/15E148 Safari/604.1 | Chrome | iOS
      Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1 | Safari | iPadOS
      Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36 | Chrome | Android
      Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/26.0 Chrome/122.0.0.0 Mobile Safari/537.36 | Samsung Internet | Android
      Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 | Chrome | Linux
      PostmanRuntime/7.43.0 | Postman | Không rõ
      """)
  void parse_commonBrowsers(String userAgent, String browser, String os) {
    DeviceLabel label = DeviceLabel.parse(userAgent);

    assertThat(label.browser()).isEqualTo(browser);
    assertThat(label.os()).isEqualTo(os);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = { "   ", "some-bot/1.0" })
  void parse_missingOrUnknown_isUnknown(String userAgent) {
    DeviceLabel label = DeviceLabel.parse(userAgent);

    assertThat(label.browser()).isEqualTo(DeviceLabel.UNKNOWN);
    assertThat(label.os()).isEqualTo(DeviceLabel.UNKNOWN);
  }
}
