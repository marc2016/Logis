package app.logis.core.auth;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceFamilyTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:132.0) Gecko/20100101 Firefox/132.0 | Firefox | Windows
            Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 | Chrome | Windows
            Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0 | Edge | Windows
            Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Safari/605.1.15 | Safari | macOS
            Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Mobile/15E148 Safari/604.1 | Safari | iOS
            Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/131.0.6778.73 Mobile/15E148 Safari/604.1 | Chrome | iOS
            Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/26.0 Chrome/122.0.0.0 Mobile Safari/537.36 | Samsung Internet | Android
            Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36 | Chrome | Android
            Mozilla/5.0 (X11; Linux x86_64; rv:132.0) Gecko/20100101 Firefox/132.0 | Firefox | Linux
            Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 OPR/115.0.0.0 | Opera | Windows
            curl/8.5.0 | Other | Other
            """)
    void recognisesFamilies(String userAgent, String browser, String os) {
        assertThat(DeviceFamily.of(userAgent)).isEqualTo(new DeviceFamily(browser, os));
    }

    @ParameterizedTest
    @CsvSource({"Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0",
            "Mozilla/5.0 (X11; Linux x86_64; rv:133.0) Gecko/20100101 Firefox/133.0"})
    void ignoresVersions(String userAgent) {
        assertThat(DeviceFamily.of(userAgent)).isEqualTo(new DeviceFamily("Firefox", "Linux"));
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {"NULL", "''"})
    void missingUserAgent(String userAgent) {
        assertThat(DeviceFamily.of(userAgent)).isEqualTo(new DeviceFamily("Other", "Other"));
    }
}
