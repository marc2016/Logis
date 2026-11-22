package app.logis.core.pushover;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Pushover for tests: knows one application token and the user keys added to it, records what is sent and can be
 * made unreachable. All values are invented.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FakePushover {

    public static final String TOKEN = "apptoken0000000000000000000000";
    public static final String KEY = "userkey00000000000000000000000";

    public record Message(String token, String userKey, String title, String text, String link) {
    }

    public static class Client implements PushoverClient {

        public final Set<String> userKeys = ConcurrentHashMap.newKeySet();
        public final List<Message> messages = new CopyOnWriteArrayList<>();
        public volatile boolean unreachable;
        public volatile PushoverException.Kind failSendWith;

        public void reset() {
            userKeys.clear();
            userKeys.add(KEY);
            messages.clear();
            unreachable = false;
            failSendWith = null;
        }

        private void reach() {
            if (unreachable) {
                throw new PushoverException(PushoverException.Kind.UNAVAILABLE, null);
            }
        }

        @Override
        public void checkToken(String token) {
            reach();
            if (!TOKEN.equals(token)) {
                throw new PushoverException(PushoverException.Kind.INVALID_TOKEN, null);
            }
        }

        @Override
        public void checkUser(String token, String userKey) {
            checkToken(token);
            if (!userKeys.contains(userKey)) {
                throw new PushoverException(PushoverException.Kind.INVALID_USER, null);
            }
        }

        @Override
        public void send(String token, String userKey, String title, String text, String link) {
            checkUser(token, userKey);
            if (failSendWith != null) {
                throw new PushoverException(failSendWith, null);
            }
            messages.add(new Message(token, userKey, title, text, link));
        }

        public List<Message> sentTo(String userKey) {
            return messages.stream().filter(m -> m.userKey().equals(userKey)).toList();
        }

        /** Waits until {@code count} messages went to the key; for 0 waits a moment and expects none. */
        public List<Message> await(String userKey, int count) throws InterruptedException {
            var deadline = System.currentTimeMillis() + (count == 0 ? 700 : 5000);
            while (System.currentTimeMillis() < deadline && (count == 0 || sentTo(userKey).size() < count)) {
                Thread.sleep(25);
            }
            var result = sentTo(userKey);
            if (result.size() != count) {
                throw new AssertionError("Expected " + count + " messages for " + userKey + " but got " + result);
            }
            return result;
        }
    }

    @Bean
    @Primary
    Client fakePushoverClient() {
        var client = new Client();
        client.reset();
        return client;
    }
}
