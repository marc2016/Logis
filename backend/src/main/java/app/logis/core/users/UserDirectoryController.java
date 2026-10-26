package app.logis.core.users;

import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Benutzersuche zum Einladen. Die einzige Stelle, an der Nicht-Admins andere Benutzer sehen -
 * daher nur ID, Benutzername und Anzeigename aktiver Benutzer.
 */
@RestController
class UserDirectoryController {

    private final UserRepository users;

    UserDirectoryController(UserRepository users) {
        this.users = users;
    }

    record DirectoryEntry(Long id, String username, String displayName) {
    }

    @GetMapping("/api/users/search")
    List<DirectoryEntry> search(@RequestParam(defaultValue = "") String q) {
        var term = q.trim();
        if (term.length() < 2) {
            return List.of();
        }
        var pattern = "%" + term.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return users.searchActive(pattern, PageRequest.of(0, 10)).stream()
                .map(u -> new DirectoryEntry(u.getId(), u.getUsername(), u.getDisplayName()))
                .toList();
    }
}
