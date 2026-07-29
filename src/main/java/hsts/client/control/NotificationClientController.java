package hsts.client.control;

import hsts.client.net.Client;
import hsts.common.NotificationDTO;
import hsts.common.NotificationIdPayload;
import hsts.common.Request;
import hsts.common.RequestType;
import hsts.common.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class NotificationClientController {
    private final Function<Request, Response> requestSender;

    public NotificationClientController(Client client) {
        this.requestSender = Objects.requireNonNull(client, "client")::sendRequest;
    }

    NotificationClientController(Function<Request, Response> requestSender) {
        this.requestSender = Objects.requireNonNull(requestSender, "requestSender");
    }

    public CompletableFuture<List<NotificationDTO>> getMyNotifications() {
        return send(RequestType.LIST_MY_NOTIFICATIONS, null, payload -> {
            if (!(payload instanceof List<?> values)) {
                throw new IllegalStateException("Invalid notifications response from server");
            }
            List<NotificationDTO> result = new ArrayList<>();
            for (Object value : values) {
                if (!(value instanceof NotificationDTO notification)) {
                    throw new IllegalStateException(
                            "Invalid notifications response from server"
                    );
                }
                result.add(notification);
            }
            return List.copyOf(result);
        });
    }

    public CompletableFuture<Integer> getUnreadCount() {
        return send(RequestType.GET_UNREAD_NOTIFICATION_COUNT, null, payload -> {
            if (!(payload instanceof Integer count) || count < 0) {
                throw new IllegalStateException(
                        "Invalid unread notification count from server"
                );
            }
            return count;
        });
    }

    public CompletableFuture<NotificationDTO> markAsRead(int notificationId) {
        if (notificationId <= 0) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Notification not found")
            );
        }
        return send(RequestType.MARK_NOTIFICATION_READ,
                new NotificationIdPayload(notificationId), payload -> {
                    if (!(payload instanceof NotificationDTO notification)) {
                        throw new IllegalStateException(
                                "Invalid notification response from server"
                        );
                    }
                    return notification;
                });
    }

    private <T> CompletableFuture<T> send(RequestType type, Object payload,
                                          Function<Object, T> mapper) {
        return CompletableFuture.supplyAsync(() -> {
            Response response = requestSender.apply(new Request(type, payload));
            if (response == null) throw new IllegalStateException("No response from server");
            if (!response.isSuccess()) {
                String message = response.getMessage();
                throw new IllegalStateException(message == null || message.isBlank()
                        ? "Request failed" : message);
            }
            return mapper.apply(response.getPayload());
        });
    }
}
