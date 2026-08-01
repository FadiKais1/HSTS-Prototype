package hsts.server.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class BotConversation {
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
                    + "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
    );

    private final int conversationId;
    private final int botId;
    private final int studentUserId;
    private final String providerSubjectId;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private final List<BotMessage> messages;

    private BotConversation(int conversationId, int botId, int studentUserId,
                            String providerSubjectId, LocalDateTime createdAt,
                            LocalDateTime updatedAt,
                            List<BotMessage> messages) {
        if (conversationId < 0) {
            throw new IllegalArgumentException("Bot conversation ID cannot be negative");
        }
        requirePositive(botId, "Bot ID must be positive");
        requirePositive(studentUserId, "Student user ID must be positive");
        this.providerSubjectId = requirePseudonymousSubject(providerSubjectId);
        if (createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("Bot conversation timestamps are required");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    "Bot conversation update time cannot precede creation"
            );
        }
        this.conversationId = conversationId;
        this.botId = botId;
        this.studentUserId = studentUserId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.messages = copyAndValidateMessages(messages, conversationId, updatedAt);
    }

    public static BotConversation create(int botId, int studentUserId,
                                         String providerSubjectId,
                                         LocalDateTime createdAt) {
        return new BotConversation(
                0, botId, studentUserId, providerSubjectId,
                createdAt, createdAt, List.of()
        );
    }

    public static BotConversation rehydrate(int conversationId, int botId,
                                            int studentUserId,
                                            String providerSubjectId,
                                            LocalDateTime createdAt,
                                            LocalDateTime updatedAt,
                                            List<BotMessage> messages) {
        if (conversationId <= 0) {
            throw new IllegalArgumentException(
                    "Persisted Bot conversation ID must be positive"
            );
        }
        return new BotConversation(
                conversationId, botId, studentUserId, providerSubjectId,
                createdAt, updatedAt, messages
        );
    }

    public int getConversationId() { return conversationId; }
    public int getBotId() { return botId; }
    public int getStudentUserId() { return studentUserId; }
    public String getProviderSubjectId() { return providerSubjectId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public List<BotMessage> getMessages() {
        List<BotMessage> copies = new ArrayList<>(messages.size());
        for (BotMessage message : messages) {
            copies.add(message.copy());
        }
        return List.copyOf(copies);
    }

    public void appendMessage(BotMessage message, LocalDateTime timestamp) {
        if (message == null) {
            throw new IllegalArgumentException("Bot message is required");
        }
        if (conversationId <= 0) {
            throw new IllegalStateException(
                    "Messages require a persisted Bot conversation"
            );
        }
        if (message.getConversationId() != conversationId) {
            throw new IllegalArgumentException("Bot message belongs to another conversation");
        }
        if (message.getSequenceNumber() != messages.size() + 1) {
            throw new IllegalArgumentException("Bot message sequence must be contiguous");
        }
        if (message.getMessageId() > 0) {
            for (BotMessage existing : messages) {
                if (existing.getMessageId() == message.getMessageId()) {
                    throw new IllegalArgumentException("Duplicate Bot message ID");
                }
            }
        }
        LocalDateTime mutationTime = requireMutationTime(timestamp);
        if (mutationTime.isBefore(message.getCreatedAt())) {
            throw new IllegalArgumentException(
                    "Conversation update time cannot precede message creation"
            );
        }
        messages.add(message.copy());
        updatedAt = mutationTime;
    }

    BotConversation copy() {
        return new BotConversation(
                conversationId, botId, studentUserId, providerSubjectId,
                createdAt, updatedAt, messages
        );
    }

    private LocalDateTime requireMutationTime(LocalDateTime timestamp) {
        if (timestamp == null) {
            throw new IllegalArgumentException("Conversation mutation time is required");
        }
        if (timestamp.isBefore(updatedAt)) {
            throw new IllegalArgumentException(
                    "Conversation mutation time cannot precede current state"
            );
        }
        return timestamp;
    }

    private static List<BotMessage> copyAndValidateMessages(
            List<BotMessage> supplied, int conversationId,
            LocalDateTime updatedAt
    ) {
        if (supplied == null) {
            throw new IllegalArgumentException("Bot conversation messages are required");
        }
        List<BotMessage> copies = new ArrayList<>(supplied.size());
        Set<Integer> persistedIds = new HashSet<>();
        for (int index = 0; index < supplied.size(); index++) {
            BotMessage message = supplied.get(index);
            if (message == null) {
                throw new IllegalArgumentException(
                        "Bot conversation messages cannot contain null"
                );
            }
            if (message.getSequenceNumber() != index + 1) {
                throw new IllegalArgumentException("Bot message sequence must be contiguous");
            }
            if (conversationId > 0 && message.getConversationId() != conversationId) {
                throw new IllegalArgumentException(
                        "Bot message belongs to another conversation"
                );
            }
            if (message.getMessageId() > 0 && !persistedIds.add(message.getMessageId())) {
                throw new IllegalArgumentException("Duplicate Bot message ID");
            }
            if (message.getCreatedAt().isAfter(updatedAt)) {
                throw new IllegalArgumentException(
                        "This Course Bot conversation is out of date. Please reopen the Course Bot and ask your question again."
                );
            }
            copies.add(message.copy());
        }
        return copies;
    }

    private static String requirePseudonymousSubject(String value) {
        if (value == null || !UUID_PATTERN.matcher(value.trim()).matches()) {
            throw new IllegalArgumentException(
                    "Provider subject ID must be a UUID"
            );
        }
        return UUID.fromString(value.trim()).toString();
    }

    private static int requirePositive(int value, String message) {
        if (value <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
