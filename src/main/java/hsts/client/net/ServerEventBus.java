package hsts.client.net;

import hsts.common.ServerEvent;
import hsts.common.ServerEventType;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Delivers unsolicited server events to every interested screen.
 *
 * <p>This replaces the earlier single listener slot on {@link Client}. That slot
 * allowed only one subscriber, so opening a second screen silently displaced the
 * first, and closing any screen cleared the slot for everybody. The visible
 * symptom was a dashboard badge that stopped updating once the user had visited
 * and left any sub-page.</p>
 *
 * <p>Each subscriber receives a {@link Subscription} handle and closes only its
 * own registration, so screens can no longer interfere with one another.</p>
 *
 * <p>Subscribers declare the event types they care about, so a screen is not
 * woken by changes it does not display.</p>
 *
 * <h2>Threading</h2>
 * <p>Events arrive on the OCSF reader thread. The bus hands each event to a
 * {@code dispatcher} supplied at construction, which is responsible for moving
 * work onto the UI thread. Production wiring passes {@code Platform::runLater};
 * tests pass {@code Runnable::run} to keep delivery synchronous.</p>
 */
public class ServerEventBus {

    /**
     * A single screen's registration. Closing it removes that screen and
     * nothing else. Closing twice is harmless.
     */
    public static final class Subscription implements AutoCloseable {
        private final ServerEventBus bus;
        private final Set<ServerEventType> types;
        private final Consumer<ServerEvent> handler;
        private volatile boolean active = true;

        private Subscription(ServerEventBus bus, Set<ServerEventType> types,
                             Consumer<ServerEvent> handler) {
            this.bus = bus;
            this.types = types;
            this.handler = handler;
        }

        private boolean wants(ServerEventType type) {
            return active && (types.isEmpty() || types.contains(type));
        }

        /** Whether this subscription is still receiving events. */
        public boolean isActive() {
            return active;
        }

        @Override
        public void close() {
            if (!active) {
                return;
            }
            active = false;
            bus.subscriptions.remove(this);
        }
    }

    private final CopyOnWriteArrayList<Subscription> subscriptions =
            new CopyOnWriteArrayList<>();
    private final Consumer<Runnable> dispatcher;

    /**
     * @param dispatcher moves delivery onto the thread screens may safely touch;
     *                   {@code Platform::runLater} in production
     */
    public ServerEventBus(Consumer<Runnable> dispatcher) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher is required");
    }

    /**
     * Subscribes to the named event types.
     *
     * @param handler invoked on the dispatcher thread for each matching event
     * @param types   the types of interest; passing none subscribes to all
     * @return a handle the caller closes when its screen is hidden
     */
    public Subscription subscribe(Consumer<ServerEvent> handler, ServerEventType... types) {
        Objects.requireNonNull(handler, "handler is required");

        Set<ServerEventType> selected = types == null || types.length == 0
                ? EnumSet.noneOf(ServerEventType.class)
                : EnumSet.copyOf(List.of(types));

        Subscription subscription = new Subscription(this, selected, handler);
        subscriptions.add(subscription);
        return subscription;
    }

    /**
     * Delivers an event to every interested subscriber.
     *
     * <p>One subscriber throwing must not stop the others, and must never break
     * the transport, so failures are contained per subscriber.</p>
     */
    public void publish(ServerEvent event) {
        if (event == null) {
            return;
        }

        for (Subscription subscription : subscriptions) {
            if (!subscription.wants(event.getType())) {
                continue;
            }

            dispatcher.accept(() -> {
                if (!subscription.active) {
                    return;
                }
                try {
                    subscription.handler.accept(event);
                } catch (RuntimeException exception) {
                    System.err.println(
                            "Server event subscriber failed: " + exception.getMessage()
                    );
                }
            });
        }
    }

    /**
     * The dispatcher used in production: delivery on the JavaFX application
     * thread.
     *
     * <p>Falls back to running inline when the JavaFX runtime is not started,
     * so headless tests and tooling can use a real bus without initialising a
     * toolkit.</p>
     */
    public static Consumer<Runnable> uiDispatcher() {
        return task -> {
            try {
                if (javafx.application.Platform.isFxApplicationThread()) {
                    task.run();
                } else {
                    javafx.application.Platform.runLater(task);
                }
            } catch (IllegalStateException toolkitNotStarted) {
                task.run();
            }
        };
    }

    /**
     * Closes every subscription.
     *
     * <p>Used at logout so that events pushed for the outgoing session can
     * never reach a detached screen, or be handled under the next user's
     * session. Individual screens must use {@link Subscription#close()}
     * instead; this is deliberately the only global operation.</p>
     */
    public void closeAll() {
        for (Subscription subscription : subscriptions) {
            subscription.close();
        }
        subscriptions.clear();
    }

    /** Number of live subscriptions. Intended for tests and diagnostics. */
    public int subscriberCount() {
        return subscriptions.size();
    }
}
