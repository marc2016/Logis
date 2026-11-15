package app.logis.core.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DevTotpBypassGuardTest {

    private static MockEnvironment profiles(String... active) {
        var env = new MockEnvironment();
        env.setActiveProfiles(active);
        return env;
    }

    @Test
    void bypassOutsideDevStopsTheStart() {
        assertThatThrownBy(() -> DevTotpBypassGuard.check(true, profiles()))
                .isInstanceOf(IllegalStateException.class).hasMessage(DevTotpBypassGuard.ONLY_IN_DEV);
        assertThatThrownBy(() -> DevTotpBypassGuard.check(true, profiles("test")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void bypassInDevAndNoBypassAreAllowed() {
        assertThatCode(() -> DevTotpBypassGuard.check(true, profiles("dev"))).doesNotThrowAnyException();
        assertThatCode(() -> DevTotpBypassGuard.check(false, profiles())).doesNotThrowAnyException();
    }
}
