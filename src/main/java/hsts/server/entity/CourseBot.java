package hsts.server.entity;

import hsts.common.type.BotSourceStatus;
import hsts.common.type.BotStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CourseBot {
    private final int botId;
    private final int courseId;
    private String name;
    private BotStatus status;
    private final int createdByUserId;
    private final String externalProvider;
    private final String externalBotId;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private final List<BotSource> sources;

    private CourseBot(int botId, int courseId, String name, BotStatus status,
                      int createdByUserId, String externalProvider,
                      String externalBotId, LocalDateTime createdAt,
                      LocalDateTime updatedAt, List<BotSource> sources) {
        if (botId < 0) {
            throw new IllegalArgumentException("Course Bot ID cannot be negative");
        }
        requirePositive(courseId, "Course ID must be positive");
        requirePositive(createdByUserId, "Bot creator ID must be positive");
        this.name = requireNonBlank(name, "Bot name is required");
        if (status == null) {
            throw new IllegalArgumentException("Bot status is required");
        }
        if (createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("Bot timestamps are required");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Bot update time cannot precede creation");
        }
        this.botId = botId;
        this.courseId = courseId;
        this.status = status;
        this.createdByUserId = createdByUserId;
        this.externalProvider = normalizeNullable(externalProvider);
        this.externalBotId = normalizeNullable(externalBotId);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.sources = copyAndValidateSources(sources, botId, updatedAt);
    }

    public static CourseBot create(int courseId, String name,
                                   int createdByUserId,
                                   String externalProvider,
                                   String externalBotId,
                                   LocalDateTime createdAt) {
        return new CourseBot(
                0, courseId, name, BotStatus.ACTIVE, createdByUserId,
                externalProvider, externalBotId, createdAt, createdAt,
                List.of()
        );
    }

    public static CourseBot rehydrate(int botId, int courseId, String name,
                                      BotStatus status, int createdByUserId,
                                      String externalProvider,
                                      String externalBotId,
                                      LocalDateTime createdAt,
                                      LocalDateTime updatedAt,
                                      List<BotSource> sources) {
        if (botId <= 0) {
            throw new IllegalArgumentException("Persisted Course Bot ID must be positive");
        }
        return new CourseBot(
                botId, courseId, name, status, createdByUserId,
                externalProvider, externalBotId, createdAt, updatedAt, sources
        );
    }

    public int getBotId() { return botId; }
    public int getCourseId() { return courseId; }
    public String getName() { return name; }
    public BotStatus getStatus() { return status; }
    public int getCreatedByUserId() { return createdByUserId; }
    public String getExternalProvider() { return externalProvider; }
    public String getExternalBotId() { return externalBotId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public List<BotSource> getSources() {
        List<BotSource> copies = new ArrayList<>(sources.size());
        for (BotSource source : sources) {
            copies.add(source.copy());
        }
        return List.copyOf(copies);
    }

    public void activate(LocalDateTime timestamp) {
        changeStatus(BotStatus.ACTIVE, timestamp);
    }

    public void deactivate(LocalDateTime timestamp) {
        changeStatus(BotStatus.INACTIVE, timestamp);
    }

    public void rename(String newName, LocalDateTime timestamp) {
        String normalized = requireNonBlank(newName, "Bot name is required");
        LocalDateTime mutationTime = requireMutationTime(timestamp);
        if (!name.equals(normalized)) {
            name = normalized;
            updatedAt = mutationTime;
        }
    }

    public void addSource(BotSource source, LocalDateTime timestamp) {
        BotSource copy = requireMatchingSource(source);
        requireNoDuplicateSourceId(copy.getSourceId(), -1);
        LocalDateTime mutationTime = requireMutationTime(timestamp);
        if (mutationTime.isBefore(copy.getCreatedAt())) {
            throw new IllegalArgumentException("Bot update time cannot precede source creation");
        }
        sources.add(copy);
        updatedAt = mutationTime;
    }

    public void replaceSource(BotSource replacement, LocalDateTime timestamp) {
        BotSource copy = requireMatchingSource(replacement);
        if (copy.getSourceId() <= 0) {
            throw new IllegalArgumentException("Replacement source ID must be positive");
        }
        int targetIndex = -1;
        for (int index = 0; index < sources.size(); index++) {
            if (sources.get(index).getSourceId() == copy.getSourceId()) {
                targetIndex = index;
                break;
            }
        }
        if (targetIndex < 0) {
            throw new IllegalArgumentException("Matching Bot source was not found");
        }
        if (copy.getStatus() != BotSourceStatus.REMOVED) {
            throw new IllegalArgumentException(
                    "Replacement source must represent a controlled removal"
            );
        }
        LocalDateTime mutationTime = requireMutationTime(timestamp);
        if (copy.getRemovedAt().isAfter(mutationTime)) {
            throw new IllegalArgumentException("Bot update time cannot precede source removal");
        }
        requireNoDuplicateSourceId(copy.getSourceId(), targetIndex);
        sources.set(targetIndex, copy);
        updatedAt = mutationTime;
    }

    private void changeStatus(BotStatus target, LocalDateTime timestamp) {
        LocalDateTime mutationTime = requireMutationTime(timestamp);
        if (status != target) {
            status = target;
            updatedAt = mutationTime;
        }
    }

    private LocalDateTime requireMutationTime(LocalDateTime timestamp) {
        if (timestamp == null) {
            throw new IllegalArgumentException("Bot mutation time is required");
        }
        if (timestamp.isBefore(updatedAt)) {
            throw new IllegalArgumentException("Bot mutation time cannot precede current state");
        }
        return timestamp;
    }

    private BotSource requireMatchingSource(BotSource source) {
        if (source == null) {
            throw new IllegalArgumentException("Bot source is required");
        }
        if (botId <= 0) {
            throw new IllegalStateException("Sources require a persisted Course Bot");
        }
        if (source.getBotId() != botId) {
            throw new IllegalArgumentException("Bot source belongs to another Bot");
        }
        return source.copy();
    }

    private void requireNoDuplicateSourceId(int sourceId, int excludedIndex) {
        if (sourceId <= 0) {
            return;
        }
        for (int index = 0; index < sources.size(); index++) {
            if (index != excludedIndex && sources.get(index).getSourceId() == sourceId) {
                throw new IllegalArgumentException("Duplicate Bot source ID");
            }
        }
    }

    private static List<BotSource> copyAndValidateSources(
            List<BotSource> supplied, int botId, LocalDateTime updatedAt
    ) {
        if (supplied == null) {
            throw new IllegalArgumentException("Bot sources are required");
        }
        List<BotSource> copies = new ArrayList<>(supplied.size());
        Set<Integer> persistedIds = new HashSet<>();
        for (BotSource source : supplied) {
            if (source == null) {
                throw new IllegalArgumentException("Bot sources cannot contain null");
            }
            if (botId > 0 && source.getBotId() != botId) {
                throw new IllegalArgumentException("Bot source belongs to another Bot");
            }
            if (source.getSourceId() > 0 && !persistedIds.add(source.getSourceId())) {
                throw new IllegalArgumentException("Duplicate Bot source ID");
            }
            LocalDateTime sourceTime = source.getRemovedAt() == null
                    ? source.getCreatedAt() : source.getRemovedAt();
            if (sourceTime.isAfter(updatedAt)) {
                throw new IllegalArgumentException(
                        "Bot source state cannot follow Bot update time"
                );
            }
            copies.add(source.copy());
        }
        return copies;
    }

    private static int requirePositive(int value, String message) {
        if (value <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
