package app.logis.core.documents;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/** Allowed file types, recognised by their signature (magic bytes), not by name or declared type. */
final class FileType {

    private static final Set<String> HEIF_BRANDS = Set.of("heic", "heix", "heim", "heis", "hevc", "hevx", "mif1",
            "msf1");

    private FileType() {
    }

    static Optional<String> detect(byte[] content) {
        if (startsWith(content, 0, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
            return Optional.of("application/pdf");
        }
        if (startsWith(content, 0, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) {
            return Optional.of("image/jpeg");
        }
        if (startsWith(content, 0, new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'})) {
            return Optional.of("image/png");
        }
        if (startsWith(content, 0, ascii("RIFF")) && startsWith(content, 8, ascii("WEBP"))) {
            return Optional.of("image/webp");
        }
        if (startsWith(content, 4, ascii("ftyp")) && content.length >= 12
                && HEIF_BRANDS.contains(new String(content, 8, 4, StandardCharsets.US_ASCII))) {
            return Optional.of("image/heic");
        }
        return Optional.empty();
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    private static boolean startsWith(byte[] content, int offset, byte[] prefix) {
        return content.length >= offset + prefix.length
                && Arrays.equals(content, offset, offset + prefix.length, prefix, 0, prefix.length);
    }
}
