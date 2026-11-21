package app.logis.core.pushover;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

/**
 * Talks to the Pushover API as its documentation asks: form posts over HTTPS, never more than two requests at a
 * time, and no automatic retry (a rejected request would be rejected again; a temporary failure is left to the
 * next event).
 */
@Component
class HttpPushoverClient implements PushoverClient {

    static final int MAX_TITLE = 250;
    static final int MAX_MESSAGE = 1024;
    static final int MAX_URL = 512;
    static final String URL_TITLE = "In Logis öffnen";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final String baseUrl;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT)
            .proxy(ProxySelector.getDefault()).build();
    /** Pushover asks for at most two concurrent connections. */
    private final Semaphore slots = new Semaphore(2, true);

    HttpPushoverClient(@Value("${logis.pushover.base-url:https://api.pushover.net}") String baseUrl,
            ObjectMapper json) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.json = json;
    }

    @Override
    public void checkToken(String token) {
        // The limits of an application are readable with its token alone: the documented call without a user key.
        var request = HttpRequest.newBuilder(URI.create(baseUrl + "/1/apps/limits.json?token=" + encode(token)))
                .timeout(REQUEST_TIMEOUT).GET().build();
        var response = call(request);
        // Over the monthly limit, but known.
        if (response.statusCode() != 429) {
            requireOk(response);
        }
    }

    @Override
    public void checkUser(String token, String userKey) {
        var response = call(post("/1/users/validate.json", Map.of("token", token, "user", userKey)));
        if (response.statusCode() / 100 == 4 && response.statusCode() != 429 && !names(response, "token")) {
            // Whatever is wrong with the key or its devices: it cannot be used.
            throw new PushoverException(PushoverException.Kind.INVALID_USER, null);
        }
        requireOk(response);
    }

    @Override
    public void send(String token, String userKey, String title, String text, String link) {
        var form = new LinkedHashMap<String, String>();
        form.put("token", token);
        form.put("user", userKey);
        form.put("title", shorten(title, MAX_TITLE));
        form.put("message", shorten(text, MAX_MESSAGE));
        if (link != null && link.length() <= MAX_URL) {
            form.put("url", link);
            form.put("url_title", URL_TITLE);
        }
        requireOk(call(post("/1/messages.json", form)));
    }

    /** Cuts at a character boundary and marks the cut with „…“. */
    static String shorten(String value, int max) {
        if (value.codePointCount(0, value.length()) <= max) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, max - 1)).stripTrailing() + "…";
    }

    private HttpRequest post(String path, Map<String, String> form) {
        var body = form.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
        return HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
    }

    private HttpResponse<String> call(HttpRequest request) {
        try {
            slots.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PushoverException(PushoverException.Kind.UNAVAILABLE, e);
        }
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new PushoverException(PushoverException.Kind.UNAVAILABLE, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PushoverException(PushoverException.Kind.UNAVAILABLE, e);
        } finally {
            slots.release();
        }
    }

    /** 200 with status 1 is the only success; a 4xx says which input was invalid. */
    private void requireOk(HttpResponse<String> response) {
        var status = response.statusCode();
        if (status == 200 && body(response).path("status").asInt(0) == 1) {
            return;
        }
        if (status == 429) {
            throw new PushoverException(PushoverException.Kind.LIMIT_REACHED, null);
        }
        if (status / 100 == 4 || status == 200) {
            throw new PushoverException(names(response, "token") ? PushoverException.Kind.INVALID_TOKEN
                    : names(response, "user") ? PushoverException.Kind.INVALID_USER
                    : PushoverException.Kind.REJECTED, null);
        }
        throw new PushoverException(PushoverException.Kind.UNAVAILABLE, null);
    }

    /** Whether the response names the parameter as invalid, e.g. {@code "user":"invalid"}. */
    private boolean names(HttpResponse<String> response, String parameter) {
        return body(response).has(parameter);
    }

    private JsonNode body(HttpResponse<String> response) {
        try {
            var node = json.readTree(response.body());
            return node == null ? json.createObjectNode() : node;
        } catch (IOException | RuntimeException e) {
            return json.createObjectNode();
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
