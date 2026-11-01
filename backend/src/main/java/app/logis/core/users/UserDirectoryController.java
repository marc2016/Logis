package app.logis.core.users;

import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * User search for inviting. The only place where non-admins see other users -
 * hence only ID, username and display name of active users.
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
