package app.logis.core.sharing;

import app.logis.core.auth.LogisPrincipal;
import app.logis.core.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generic sharing API for all registered {@link ShareableType}s.
 */
@RestController
@RequestMapping("/api/shares/{type}/{objectId}")
class SharesController {

    private final SharingService sharing;
    private final Set<String> types;

    SharesController(SharingService sharing, List<ShareableType> shareableTypes) {
        this.sharing = sharing;
        this.types = shareableTypes.stream().map(ShareableType::type).collect(Collectors.toSet());
    }

    /** {@code myRole} is the effective role; {@code canManage} says whether the user may change the shares. */
    record SharesResponse(ShareRole myRole, boolean canManage, List<SharingService.ShareView> shares) {
    }

    record InviteRequest(@NotNull(message = "Bitte einen Benutzer wählen.") Long userId,
            @NotNull(message = "Bitte eine Rolle wählen.") ShareRole role) {
    }

    record RoleRequest(@NotNull(message = "Bitte eine Rolle wählen.") ShareRole role) {
    }

    @GetMapping
    SharesResponse list(@PathVariable String type, @PathVariable Long objectId,
            @AuthenticationPrincipal LogisPrincipal me) {
        requireType(type);
        var shares = sharing.list(type, objectId, me.userId());
        return new SharesResponse(sharing.roleOf(type, objectId, me.userId()).orElseThrow(),
                sharing.canManage(type, objectId, me.userId()), shares);
    }

    @PostMapping
    SharesResponse invite(@PathVariable String type, @PathVariable Long objectId,
            @Valid @RequestBody InviteRequest body, @AuthenticationPrincipal LogisPrincipal me) {
        requireType(type);
        sharing.invite(type, objectId, me.userId(), body.userId(), body.role());
        return list(type, objectId, me);
    }

    @PatchMapping("/{userId}")
    SharesResponse changeRole(@PathVariable String type, @PathVariable Long objectId, @PathVariable Long userId,
            @Valid @RequestBody RoleRequest body, @AuthenticationPrincipal LogisPrincipal me) {
        requireType(type);
        sharing.changeRole(type, objectId, me.userId(), userId, body.role());
        return list(type, objectId, me);
    }

    @DeleteMapping("/{userId}")
    ResponseEntity<Void> revoke(@PathVariable String type, @PathVariable Long objectId, @PathVariable Long userId,
            @AuthenticationPrincipal LogisPrincipal me) {
        requireType(type);
        sharing.revoke(type, objectId, me.userId(), userId);
        return ResponseEntity.noContent().build();
    }

    private void requireType(String type) {
        if (!types.contains(type)) {
            throw ApiException.notFound(SharingService.NOT_FOUND);
        }
    }
}
