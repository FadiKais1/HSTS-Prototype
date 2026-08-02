package hsts.client.net;

import hsts.common.ServerEvent;
import hsts.common.ServerEventType;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ServerEventBusTest {

    private static ServerEventBus synchronousBus() {
        // Deliver inline so assertions can run without a JavaFX toolkit.
        return new ServerEventBus(Runnable::run);
    }

    private static ServerEvent event(ServerEventType type) {
        return new ServerEvent(type, 0);
    }

    /**
     * The regression that motivated this class. With a single listener slot, a
     * dashboard was displaced by any sub-page that opened afterwards, and the
     * sub-page then cleared the slot for everybody when it closed.
     */
    @Test
    public void aSecondSubscriberDoesNotDisplaceTheFirst() {
        ServerEventBus bus = synchronousBus();
        List<String> received = new ArrayList<>();

        bus.subscribe(e -> received.add("dashboard"));
        bus.subscribe(e -> received.add("gradeReview"));

        bus.publish(event(ServerEventType.NOTIFICATION_CREATED));

        assertEquals(List.of("dashboard", "gradeReview"), received);
    }

    @Test
    public void closingOneSubscriptionLeavesTheOthersReceiving() {
        ServerEventBus bus = synchronousBus();
        List<String> received = new ArrayList<>();

        bus.subscribe(e -> received.add("dashboard"));
        ServerEventBus.Subscription gradeReview =
                bus.subscribe(e -> received.add("gradeReview"));

        gradeReview.close();
        bus.publish(event(ServerEventType.NOTIFICATION_CREATED));

        assertEquals(List.of("dashboard"), received);
        assertFalse(gradeReview.isActive());
        assertEquals(1, bus.subscriberCount());
    }

    @Test
    public void subscribersOnlyHearTheTypesTheyAskedFor() {
        ServerEventBus bus = synchronousBus();
        List<ServerEventType> received = new ArrayList<>();

        bus.subscribe(e -> received.add(e.getType()), ServerEventType.GRADES_PUBLISHED);

        bus.publish(event(ServerEventType.NOTIFICATION_CREATED));
        bus.publish(event(ServerEventType.GRADES_PUBLISHED));

        assertEquals(List.of(ServerEventType.GRADES_PUBLISHED), received);
    }

    @Test
    public void subscribingWithoutTypesReceivesEverything() {
        ServerEventBus bus = synchronousBus();
        List<ServerEventType> received = new ArrayList<>();

        bus.subscribe(e -> received.add(e.getType()));

        bus.publish(event(ServerEventType.NOTIFICATION_CREATED));
        bus.publish(event(ServerEventType.SUBMISSION_RECEIVED));

        assertEquals(
                List.of(ServerEventType.NOTIFICATION_CREATED,
                        ServerEventType.SUBMISSION_RECEIVED),
                received
        );
    }

    @Test
    public void oneFailingSubscriberDoesNotStopTheOthers() {
        ServerEventBus bus = synchronousBus();
        List<String> received = new ArrayList<>();

        bus.subscribe(e -> {
            throw new IllegalStateException("screen is broken");
        });
        bus.subscribe(e -> received.add("healthy"));

        bus.publish(event(ServerEventType.NOTIFICATION_CREATED));

        assertEquals(List.of("healthy"), received);
    }

    @Test
    public void logoutClosesEverySubscription() {
        ServerEventBus bus = synchronousBus();
        List<String> received = new ArrayList<>();

        ServerEventBus.Subscription dashboard = bus.subscribe(e -> received.add("dashboard"));
        ServerEventBus.Subscription notifications =
                bus.subscribe(e -> received.add("notifications"));

        bus.closeAll();
        bus.publish(event(ServerEventType.NOTIFICATION_CREATED));

        assertTrue(received.isEmpty());
        assertFalse(dashboard.isActive());
        assertFalse(notifications.isActive());
        assertEquals(0, bus.subscriberCount());
    }

    @Test
    public void closingTwiceIsHarmless() {
        ServerEventBus bus = synchronousBus();
        ServerEventBus.Subscription subscription = bus.subscribe(e -> { });

        subscription.close();
        subscription.close();

        assertEquals(0, bus.subscriberCount());
    }

    @Test
    public void aNullEventIsIgnored() {
        ServerEventBus bus = synchronousBus();
        List<String> received = new ArrayList<>();
        bus.subscribe(e -> received.add("called"));

        bus.publish(null);

        assertTrue(received.isEmpty());
    }
}
