package app.logis.core.pushover;

import app.logis.core.pushover.PushoverClient.PushoverException;
import app.logis.core.pushover.PushoverClient.PushoverException.Kind;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The HTTP client against a local stand-in that answers as the Pushover documentation describes (design D7 of
 * notifications-pushover). Token and key are invented.
 */
class HttpPushoverClientTest {

    private static final String TOKEN = "apptoken0000000000000000000000";
    private static final String KEY = "userkey00000000000000000000000";

    private record Call(String method, String path, Map<String, String> form) {
    }

    private HttpServer server;
    private HttpPushoverClient client;
    private final List<Call> calls = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private volatile String body = "{\"status\":1,\"request\":\"r1\"}";

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            var raw = "GET".equals(exchange.getRequestMethod()) ? exchange.getRequestURI().getRawQuery()
                    : new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            var form = new HashMap<String, String>();
            for (var pair : (raw == null ? "" : raw).split("&")) {
                var at = pair.indexOf('=');
                if (at > 0) {
                    form.put(URLDecoder.decode(pair.substring(0, at), StandardCharsets.UTF_8),
                            URLDecoder.decode(pair.substring(at + 1), StandardCharsets.UTF_8));
                }
            }
            calls.add(new Call(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), form));
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        client = new HttpPushoverClient("http://127.0.0.1:" + server.getAddress().getPort() + "/",
                new ObjectMapper());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void answer(int status, String body) {
        this.status = status;
        this.body = body;
    }

    private Kind kindOf(Runnable call) {
        try {
            call.run();
            return null;
        } catch (PushoverException ex) {
            return ex.kind();
        }
    }

    @Test
    void sendsExactlyTokenKeyTitleTextAndLink() {
        client.send(TOKEN, KEY, "Neue Buchung zu prüfen", "Haus Waldeck, 12.07.2027 – 19.07.2027",
                "https://logis.example.org/ferienvermietung/buchungen/7");

        assertThat(calls).singleElement().satisfies(call -> {
            assertThat(call.method()).isEqualTo("POST");
            assertThat(call.path()).isEqualTo("/1/messages.json");
            assertThat(call.form()).containsOnly(Map.entry("token", TOKEN), Map.entry("user", KEY),
                    Map.entry("title", "Neue Buchung zu prüfen"),
                    Map.entry("message", "Haus Waldeck, 12.07.2027 – 19.07.2027"),
                    Map.entry("url", "https://logis.example.org/ferienvermietung/buchungen/7"),
                    Map.entry("url_title", "In Logis öffnen"));
        });
    }

    @Test
    void withoutALinkNoUrlIsSentAndLongTextsAreShortenedWithAnEllipsis() {
        client.send(TOKEN, KEY, "T".repeat(300), "Whg. ü ".repeat(200), null);

        var form = calls.get(0).form();
        assertThat(form).doesNotContainKeys("url", "url_title");
        assertThat(form.get("title")).hasSize(250).endsWith("…");
        assertThat(form.get("message").length()).isLessThanOrEqualTo(1024);
        assertThat(form.get("message")).endsWith("…").startsWith("Whg. ü Whg. ü");
        assertThat(HttpPushoverClient.shorten("kurz", 250)).isEqualTo("kurz");
    }

    @Test
    void namesWhatPushoverRejectedAndTellsRejectionsFromOutages() {
        answer(400, "{\"token\":\"invalid\",\"errors\":[\"application token is invalid\"],\"status\":0}");
        assertThat(kindOf(() -> client.send(TOKEN, KEY, "T", "t", null))).isEqualTo(Kind.INVALID_TOKEN);
        answer(400, "{\"user\":\"invalid\",\"errors\":[\"user identifier is invalid\"],\"status\":0}");
        assertThat(kindOf(() -> client.send(TOKEN, KEY, "T", "t", null))).isEqualTo(Kind.INVALID_USER);
        answer(400, "{\"message\":\"cannot be blank\",\"errors\":[\"message cannot be blank\"],\"status\":0}");
        assertThat(kindOf(() -> client.send(TOKEN, KEY, "T", "t", null))).isEqualTo(Kind.REJECTED);
        answer(429, "{\"errors\":[\"application has exceeded its monthly quota\"],\"status\":0}");
        assertThat(kindOf(() -> client.send(TOKEN, KEY, "T", "t", null))).isEqualTo(Kind.LIMIT_REACHED);
        answer(500, "oops");
        assertThat(kindOf(() -> client.send(TOKEN, KEY, "T", "t", null))).isEqualTo(Kind.UNAVAILABLE);
        answer(200, "not json");
        assertThat(kindOf(() -> client.send(TOKEN, KEY, "T", "t", null))).isEqualTo(Kind.REJECTED);
        // No automatic retry: one call each.
        assertThat(calls).hasSize(6);

        server.stop(0);
        assertThatThrownBy(() -> client.send(TOKEN, KEY, "T", "t", null)).isInstanceOf(PushoverException.class)
                .satisfies(ex -> assertThat(((PushoverException) ex).kind()).isEqualTo(Kind.UNAVAILABLE));
    }

    @Test
    void checksTheTokenOnItsOwnThroughTheLimitsOfTheApplication() {
        answer(200, "{\"limit\":10000,\"remaining\":7496,\"reset\":1393653600,\"status\":1}");
        client.checkToken(TOKEN);
        assertThat(calls).singleElement().satisfies(call -> {
            assertThat(call.method()).isEqualTo("GET");
            assertThat(call.path()).isEqualTo("/1/apps/limits.json");
            assertThat(call.form()).containsOnly(Map.entry("token", TOKEN));
        });

        answer(400, "{\"token\":\"invalid\",\"errors\":[\"application token is invalid\"],\"status\":0}");
        assertThat(kindOf(() -> client.checkToken("wrong"))).isEqualTo(Kind.INVALID_TOKEN);
        // Known, but over its monthly limit.
        answer(429, "{\"status\":0}");
        client.checkToken(TOKEN);
        answer(503, "");
        assertThat(kindOf(() -> client.checkToken(TOKEN))).isEqualTo(Kind.UNAVAILABLE);
    }

    @Test
    void checksAUserKeyWithTheToken() {
        answer(200, "{\"status\":1,\"devices\":[\"phone\"],\"licenses\":[\"Android\"]}");
        client.checkUser(TOKEN, KEY);
        assertThat(calls.get(0).path()).isEqualTo("/1/users/validate.json");
        assertThat(calls.get(0).form()).containsOnly(Map.entry("token", TOKEN), Map.entry("user", KEY));

        answer(400, "{\"user\":\"invalid\",\"errors\":[\"user key is invalid\"],\"status\":0}");
        assertThat(kindOf(() -> client.checkUser(TOKEN, "wrong"))).isEqualTo(Kind.INVALID_USER);
        // A valid account without an active device cannot be used either.
        answer(400, "{\"errors\":[\"user has no active devices\"],\"status\":0}");
        assertThat(kindOf(() -> client.checkUser(TOKEN, KEY))).isEqualTo(Kind.INVALID_USER);
        answer(400, "{\"token\":\"invalid\",\"errors\":[\"application token is invalid\"],\"status\":0}");
        assertThat(kindOf(() -> client.checkUser(TOKEN, KEY))).isEqualTo(Kind.INVALID_TOKEN);
        answer(500, "");
        assertThat(kindOf(() -> client.checkUser(TOKEN, KEY))).isEqualTo(Kind.UNAVAILABLE);
    }
}
