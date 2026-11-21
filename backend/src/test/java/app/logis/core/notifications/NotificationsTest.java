package app.logis.core.notifications;

import app.logis.IntegrationTest;
import app.logis.core.users.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Sending in the background (spec notifications: forms, link, recipients, „Sending never disturbs the event“). */
@Import(TestChannels.class)
@TestPropertySource(properties = "logis.auth.public-url=https://logis.example.org/")
@ExtendWith(OutputCaptureExtension.class)
class NotificationsTest extends IntegrationTest {

    private static final Notification NOTE = new Notification("Titel", "kurzer Text", "langer geheimer Text", "/seite/7");

    @Autowired
    Notifications notifications;
    @Autowired
    NotificationSettings settings;
    @Autowired
    TestChannels.Recording recording;
    @Autowired
    TransactionTemplate tx;

    private Long userWith(String type, String... channels) {
        var user = createUser(Role.USER, true);
        for (var channel : channels) {
            settings.set(user.id(), type, channel, true);
        }
        return user.id();
    }

    @Test
    void sendsTheShortFormWithTheLinkToUsersWhoSwitchedItOn() throws Exception {
        var anna = userWith(TestChannels.TYPE, TestChannels.RECORDING);
        var bert = createUser(Role.USER, true).id();

        notifications.send(TestChannels.TYPE, List.of(anna, bert), NOTE);

        var sent = recording.await(anna, 1).get(0);
        assertThat(sent.title()).isEqualTo("Titel");
        assertThat(sent.text()).isEqualTo("kurzer Text");
        assertThat(sent.link()).isEqualTo("https://logis.example.org/seite/7");
        // Nothing is sent unasked.
        recording.await(bert, 0);
    }

    @Test
    void mandatoryChannelNeedsNoSwitchAndNoPathMeansNoLink() throws Exception {
        var anna = createUser(Role.USER, true).id();
        notifications.send(TestChannels.MANDATORY_TYPE, List.of(anna), new Notification("Titel", "kurz", "lang", null));
        assertThat(recording.await(anna, 1).get(0).link()).isNull();
    }

    @Test
    void aNotificationOfItsOwnPerUser() throws Exception {
        var anna = userWith(TestChannels.TYPE, TestChannels.RECORDING);
        var carla = userWith(TestChannels.TYPE, TestChannels.RECORDING);
        notifications.send(TestChannels.TYPE, Map.of(anna, new Notification("T", "für Anna", "l", null),
                carla, new Notification("T", "für Carla", "l", null)));
        assertThat(recording.await(anna, 1).get(0).text()).isEqualTo("für Anna");
        assertThat(recording.await(carla, 1).get(0).text()).isEqualTo("für Carla");
    }

    @Test
    void nothingIsSentWhenTheEventIsRolledBack() throws Exception {
        var anna = userWith(TestChannels.TYPE, TestChannels.RECORDING);
        tx.executeWithoutResult(status -> {
            notifications.send(TestChannels.TYPE, List.of(anna), NOTE);
            status.setRollbackOnly();
        });
        recording.await(anna, 0);

        tx.executeWithoutResult(status -> notifications.send(TestChannels.TYPE, List.of(anna), NOTE));
        recording.await(anna, 1);
    }

    @Test
    void aFailingChannelDoesNotStopTheOtherAndIsLoggedWithoutTheText(CapturedOutput output) throws Exception {
        var anna = userWith(TestChannels.TYPE, TestChannels.BROKEN, TestChannels.RECORDING);

        notifications.send(TestChannels.TYPE, List.of(anna), NOTE);

        recording.await(anna, 1);
        assertThat(output.getAll()).contains("Notification " + TestChannels.TYPE + " could not be sent through "
                + TestChannels.BROKEN + " for user " + anna);
        assertThat(output.getAll()).doesNotContain("geheimer Text").doesNotContain("kurzer Text");
    }

    @Test
    void disabledUsersAndUnknownTypesGetNothing() throws Exception {
        var carla = userWith(TestChannels.TYPE, TestChannels.RECORDING);
        var entity = users.findById(carla).orElseThrow();
        entity.setEnabled(false);
        users.save(entity);

        notifications.send(TestChannels.TYPE, List.of(carla), NOTE);
        notifications.send("vacationrental.nothing", List.of(carla), NOTE);

        recording.await(carla, 0);
    }
}
