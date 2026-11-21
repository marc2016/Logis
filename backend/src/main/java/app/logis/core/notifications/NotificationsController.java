package app.logis.core.notifications;

import app.logis.core.auth.LogisPrincipal;
import app.logis.core.users.UserRepository;
import app.logis.core.web.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** „Benachrichtigungen“ under „Mein Konto“: the signed-in user's switches (spec notifications). */
@RestController
@RequestMapping("/api/notifications")
class NotificationsController {

    static final String INVALID = "Die Einstellung ist ungültig.";
    static final String MANDATORY = "Diese Benachrichtigung kann nicht abgeschaltet werden.";

    private final NotificationTypes registry;
    private final NotificationSettings settings;
    private final UserRepository users;

    NotificationsController(NotificationTypes registry, NotificationSettings settings, UserRepository users) {
        this.registry = registry;
        this.settings = settings;
        this.users = users;
    }

    /** @param hint what is missing if the channel is not usable for the user */
    record ChannelView(String id, String label, boolean usable, String hint) {
    }

    record SwitchView(boolean enabled, boolean mandatory) {
    }

    record TypeView(String key, String label, String group, Map<String, SwitchView> channels) {
    }

    record View(List<ChannelView> channels, List<TypeView> types) {
    }

    record SwitchRequest(Boolean enabled) {
    }

    @GetMapping
    View get(@AuthenticationPrincipal LogisPrincipal me) {
        var user = users.findById(me.userId()).orElseThrow(() -> ApiException.unauthorized("Nicht angemeldet"));
        var channels = registry.channels().stream().map(channel -> {
            var hint = channel.unusable(user);
            return new ChannelView(channel.id(), channel.label(), hint.isEmpty(), hint.orElse(null));
        }).toList();
        var stored = settings.stored(me.userId());
        var types = registry.types().stream().map(type -> {
            var switches = new LinkedHashMap<String, SwitchView>();
            for (var channel : registry.channels()) {
                var mandatory = type.mandatoryChannels().contains(channel.id());
                var enabled = mandatory || stored.getOrDefault(type.key(), Map.of())
                        .getOrDefault(channel.id(), false);
                switches.put(channel.id(), new SwitchView(enabled, mandatory));
            }
            return new TypeView(type.key(), type.label(), type.group(), switches);
        }).toList();
        return new View(channels, types);
    }

    @PutMapping("/{type}/{channel}")
    ResponseEntity<Void> set(@PathVariable String type, @PathVariable String channel,
            @RequestBody(required = false) SwitchRequest body, @AuthenticationPrincipal LogisPrincipal me) {
        var registered = registry.type(type).orElseThrow(() -> ApiException.badRequest(INVALID));
        if (registry.channel(channel).isEmpty() || body == null || body.enabled() == null) {
            throw ApiException.badRequest(INVALID);
        }
        if (registered.mandatoryChannels().contains(channel) && !body.enabled()) {
            throw ApiException.badRequest(MANDATORY);
        }
        settings.set(me.userId(), type, channel, body.enabled());
        return ResponseEntity.noContent().build();
    }
}
