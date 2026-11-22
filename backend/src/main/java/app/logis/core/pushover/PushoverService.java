package app.logis.core.pushover;

import app.logis.core.pushover.PushoverClient.PushoverException;
import app.logis.core.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Setting up Pushover: the admin's application token and each user's own key, both checked with Pushover. */
@Service
class PushoverService {

    static final String INVALID_TOKEN = "Das Pushover-Token ist ungültig";
    static final String UNREACHABLE = "Pushover ist nicht erreichbar";
    static final String INVALID_KEY = "Der Pushover-Schlüssel ist ungültig";
    static final String KEY_NO_LONGER_VALID = "Der Pushover-Schlüssel ist nicht mehr gültig";
    static final String NO_KEY = "Kein Pushover-Schlüssel hinterlegt";
    static final String NOT_SET_UP = "Pushover ist noch nicht eingerichtet";
    static final String TEST_FAILED = "Die Testnachricht konnte nicht gesendet werden";
    static final String TEST_TITLE = "Testnachricht von Logis";
    static final String TEST_TEXT = "Diese Testnachricht bestätigt, dass Logis dir über Pushover Benachrichtigungen "
            + "senden kann.";
    private static final int MAX_SECRET = 64;
    private static final Logger log = LoggerFactory.getLogger(PushoverService.class);

    private final PushoverStore store;
    private final PushoverClient client;

    PushoverService(PushoverStore store, PushoverClient client) {
        this.store = store;
        this.client = client;
    }

    /** Stores the application token once Pushover accepted it; otherwise the previous one stays in effect. */
    void saveToken(String input) {
        var token = required(input, "Bitte das Pushover-Token angeben.");
        try {
            client.checkToken(token);
        } catch (PushoverException ex) {
            throw ApiException.badRequest(rejected(ex) ? INVALID_TOKEN : UNREACHABLE);
        }
        store.saveToken(token);
    }

    void deleteToken() {
        store.deleteToken();
    }

    /** Stores the user's key once Pushover accepted it. */
    void saveUserKey(Long userId, String input) {
        var token = store.token().orElseThrow(() -> ApiException.badRequest(NOT_SET_UP));
        var key = required(input, "Bitte den Pushover-Schlüssel angeben.");
        try {
            client.checkUser(token, key);
        } catch (PushoverException ex) {
            if (ex.kind() == PushoverException.Kind.INVALID_USER) {
                throw ApiException.badRequest(INVALID_KEY);
            }
            log.warn("Pushover key of user {} could not be checked: {}", userId, ex.kind());
            throw ApiException.badRequest(UNREACHABLE);
        }
        store.saveUserKey(userId, key);
    }

    void deleteUserKey(Long userId) {
        store.deleteUserKey(userId);
    }

    /** Sends the test notification to the user's own key at once. */
    void sendTest(Long userId) {
        var token = store.token().orElseThrow(() -> ApiException.badRequest(NOT_SET_UP));
        var key = store.userKey(userId).filter(k -> !k.invalid())
                .orElseThrow(() -> ApiException.badRequest(NO_KEY));
        try {
            client.send(token, key.key(), TEST_TITLE, TEST_TEXT, null);
        } catch (PushoverException ex) {
            if (ex.kind() == PushoverException.Kind.INVALID_USER) {
                store.markInvalid(userId);
            }
            log.warn("Pushover test notification for user {} failed: {}", userId, ex.kind());
            throw ApiException.badRequest(TEST_FAILED);
        }
    }

    /** Pushover answered and said no, as opposed to not answering. */
    private static boolean rejected(PushoverException ex) {
        return ex.kind() == PushoverException.Kind.INVALID_TOKEN || ex.kind() == PushoverException.Kind.REJECTED
                || ex.kind() == PushoverException.Kind.INVALID_USER;
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest(message);
        }
        var stripped = value.strip();
        if (stripped.length() > MAX_SECRET || !stripped.matches("[A-Za-z0-9]+")) {
            throw ApiException.badRequest("Die Eingabe ist ungültig.");
        }
        return stripped;
    }
}
