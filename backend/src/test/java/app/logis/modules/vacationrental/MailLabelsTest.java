package app.logis.modules.vacationrental;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The backend's labels for the admin export match the inbox labels in the frontend (design D3 of
 * mail-export-pseudonymization).
 */
class MailLabelsTest {

    private static final Path FRONTEND_API = Path.of("../frontend/src/modules/vacationrental/api.ts");

    private static Map<String, String> frontendMap(String source, String name) {
        var block = Pattern.compile("export const " + name + ": Record<string, string> = \\{(.*?)\\n}",
                Pattern.DOTALL).matcher(source);
        assertThat(block.find()).as(name).isTrue();
        var entries = new HashMap<String, String>();
        var entry = Pattern.compile("(\\w+): '([^']*)'").matcher(block.group(1));
        while (entry.find()) {
            entries.put(entry.group(1), entry.group(2));
        }
        return entries;
    }

    @Test
    void labelsMatchTheFrontend() throws Exception {
        var source = Files.readString(FRONTEND_API);
        assertThat(BookingMailHandler.RESULT_LABELS).isEqualTo(frontendMap(source, "mailResultLabel"));
        assertThat(BookingMailHandler.KIND_LABELS).isEqualTo(frontendMap(source, "mailKindLabel"));
    }
}
