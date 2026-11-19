package app.logis.core.auth;

/**
 * Browser and operating system family of a {@code User-Agent} (design.md of remember-me, D4). Only families are
 * kept, never versions, so browser updates do not change the result.
 */
record DeviceFamily(String browser, String os) {

    static final String OTHER = "Other";

    static DeviceFamily of(String userAgent) {
        var ua = userAgent == null ? "" : userAgent;
        return new DeviceFamily(browser(ua), os(ua));
    }

    private static String browser(String ua) {
        if (ua.contains("SamsungBrowser/")) {
            return "Samsung Internet";
        }
        if (ua.contains("Edg/") || ua.contains("EdgA/") || ua.contains("EdgiOS/")) {
            return "Edge";
        }
        if (ua.contains("OPR/") || ua.contains("OPX/") || ua.contains("Opera")) {
            return "Opera";
        }
        if (ua.contains("Firefox/") || ua.contains("FxiOS/")) {
            return "Firefox";
        }
        if (ua.contains("Chrome/") || ua.contains("CriOS/") || ua.contains("Chromium/")) {
            return "Chrome";
        }
        if (ua.contains("Safari/") && ua.contains("Version/")) {
            return "Safari";
        }
        return OTHER;
    }

    private static String os(String ua) {
        // iOS and Android first: their user agents also contain "Mac OS X" and "Linux".
        if (ua.contains("iPhone") || ua.contains("iPad") || ua.contains("iPod")) {
            return "iOS";
        }
        if (ua.contains("Android")) {
            return "Android";
        }
        if (ua.contains("Windows")) {
            return "Windows";
        }
        if (ua.contains("Macintosh") || ua.contains("Mac OS X")) {
            return "macOS";
        }
        if (ua.contains("CrOS")) {
            return "ChromeOS";
        }
        if (ua.contains("Linux")) {
            return "Linux";
        }
        return OTHER;
    }
}
