package hsts.server.repository;

import hsts.common.type.BotAnswerStatus;
import hsts.common.type.BotStatus;
import hsts.server.entity.BotConversation;
import hsts.server.entity.BotMessage;
import hsts.server.entity.CourseBot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BotConversationRepository {
    private static final int MYSQL_DUPLICATE_KEY_ERROR = 1062;

    private static final String CONVERSATION_COLUMNS = """
            bc.conversation_id, bc.bot_id, bc.student_user_id,
            bc.provider_subject_id, bc.created_at, bc.updated_at
            """;

    private static final String PERSONAL_HISTORY_SQL = """
            SELECT %s
            FROM bot_conversations bc
            JOIN course_bots cb ON cb.bot_id = bc.bot_id
            JOIN student_courses sc
              ON sc.course_id = cb.course_id
             AND sc.student_user_id = ?
            JOIN users u
              ON u.user_id = sc.student_user_id
             AND u.role = 'STUDENT'
             AND u.status = 'ACTIVE'
            WHERE bc.bot_id = ?
              AND bc.student_user_id = ?
              AND cb.status = 'ACTIVE'
            """.formatted(CONVERSATION_COLUMNS);

    private static final String ACTIVE_STUDENT_BOT_SQL = """
            SELECT cb.bot_id, cb.course_id, cb.status
            FROM course_bots cb
            JOIN student_courses sc
              ON sc.course_id = cb.course_id
             AND sc.student_user_id = ?
            JOIN users u
              ON u.user_id = sc.student_user_id
             AND u.role = 'STUDENT'
             AND u.status = 'ACTIVE'
            WHERE cb.bot_id = ? AND cb.status = 'ACTIVE'
            FOR UPDATE
            """;

    private static final String LOCK_PERSONAL_CONVERSATION_SQL = """
            SELECT %s
            FROM bot_conversations bc
            JOIN course_bots cb ON cb.bot_id = bc.bot_id
            JOIN student_courses sc
              ON sc.course_id = cb.course_id
             AND sc.student_user_id = ?
            JOIN users u
              ON u.user_id = sc.student_user_id
             AND u.role = 'STUDENT'
             AND u.status = 'ACTIVE'
            WHERE bc.bot_id = ?
              AND bc.student_user_id = ?
              AND cb.status = 'ACTIVE'
            FOR UPDATE
            """.formatted(CONVERSATION_COLUMNS);

    private static final String LOCK_CONVERSATION_BY_ID_SQL = """
            SELECT %s
            FROM bot_conversations bc
            JOIN course_bots cb ON cb.bot_id = bc.bot_id
            JOIN student_courses sc
              ON sc.course_id = cb.course_id
             AND sc.student_user_id = ?
            JOIN users u
              ON u.user_id = sc.student_user_id
             AND u.role = 'STUDENT'
             AND u.status = 'ACTIVE'
            WHERE bc.conversation_id = ?
              AND bc.student_user_id = ?
              AND cb.status = 'ACTIVE'
            FOR UPDATE
            """.formatted(CONVERSATION_COLUMNS);

    private static final String INSERT_CONVERSATION_SQL = """
            INSERT INTO bot_conversations (
                bot_id, student_user_id, provider_subject_id, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?)
            """;

    private static final String MESSAGE_COLUMNS = """
            bm.message_id, bm.conversation_id, bm.sequence_no,
            bm.question_text, bm.normalized_question, bm.answer_text,
            bm.answer_status, bm.provider_request_id, bm.created_at
            """;

    private static final String MESSAGES_SQL = """
            SELECT %s
            FROM bot_messages bm
            WHERE bm.conversation_id = ?
            ORDER BY bm.sequence_no ASC
            """.formatted(MESSAGE_COLUMNS);

    private static final String INSERT_MESSAGE_SQL = """
            INSERT INTO bot_messages (
                conversation_id, sequence_no, question_text,
                normalized_question, answer_text, answer_status,
                provider_request_id, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String UPDATE_CONVERSATION_TIME_SQL = """
            UPDATE bot_conversations
            SET updated_at = ?
            WHERE conversation_id = ? AND updated_at = ?
            """;

    private final DatabaseController databaseController;

    public BotConversationRepository() {
        this(new DatabaseController());
    }

    public BotConversationRepository(DatabaseController databaseController) {
        if (databaseController == null) {
            throw new IllegalArgumentException("Database controller is required");
        }
        this.databaseController = databaseController;
    }

    public Optional<BotConversation> findHistoryForStudent(
            int authenticatedStudentUserId, int botId
    ) {
        requirePositive(authenticatedStudentUserId, "Student user ID must be positive");
        requirePositive(botId, "Bot ID must be positive");
        try (Connection connection = databaseController.getConnection()) {
            Optional<ConversationRow> row = findConversation(
                    connection, PERSONAL_HISTORY_SQL,
                    authenticatedStudentUserId, botId, authenticatedStudentUserId
            );
            if (row.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(hydrateConversation(
                    row.get(), loadMessages(connection, row.get().conversationId())
            ));
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load personal Bot history", exception);
        }
    }

    public BotConversation getOrCreateForStudent(
            int authenticatedStudentUserId,
            CourseBot activeBot,
            String providerSubjectId,
            LocalDateTime currentTime
    ) {
        requirePositive(authenticatedStudentUserId, "Student user ID must be positive");
        validateCreationInput(activeBot, providerSubjectId, currentTime);

        try {
            return inTransaction("Failed to create Bot conversation", connection -> {
                Optional<Integer> persistedCourseId = findActiveStudentBotCourse(
                        connection, authenticatedStudentUserId, activeBot.getBotId()
                );
                if (persistedCourseId.isEmpty()
                        || persistedCourseId.get() != activeBot.getCourseId()) {
                    throw new IllegalStateException("Course Bot not found or access denied");
                }
                Optional<ConversationRow> existing = findConversation(
                        connection, LOCK_PERSONAL_CONVERSATION_SQL,
                        authenticatedStudentUserId,
                        activeBot.getBotId(),
                        authenticatedStudentUserId
                );
                if (existing.isPresent()) {
                    return hydrateConversation(
                            existing.get(),
                            loadMessages(connection, existing.get().conversationId())
                    );
                }

                int conversationId;
                try (PreparedStatement statement = connection.prepareStatement(
                        INSERT_CONVERSATION_SQL, Statement.RETURN_GENERATED_KEYS
                )) {
                    statement.setInt(1, activeBot.getBotId());
                    statement.setInt(2, authenticatedStudentUserId);
                    statement.setString(3, providerSubjectId.trim());
                    statement.setObject(4, currentTime);
                    statement.setObject(5, currentTime);
                    if (statement.executeUpdate() != 1) {
                        throw new SQLException("Bot conversation insert did not affect one row");
                    }
                    conversationId = requireGeneratedId(statement, "Bot conversation");
                } catch (SQLException exception) {
                    if (isConversationUniqueRace(exception)) {
                        throw new NamedConversationRace(exception);
                    }
                    throw exception;
                }
                return BotConversation.rehydrate(
                        conversationId, activeBot.getBotId(), authenticatedStudentUserId,
                        providerSubjectId, currentTime, currentTime, List.of()
                );
            });
        } catch (NamedConversationRace race) {
            return findHistoryForStudent(authenticatedStudentUserId, activeBot.getBotId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Failed to create Bot conversation", race.getCause()
                    ));
        }
    }

    public BotConversation appendMessage(
            int authenticatedStudentUserId,
            BotConversation conversation,
            BotMessage message
    ) {
        requirePositive(authenticatedStudentUserId, "Student user ID must be positive");
        if (conversation == null) {
            throw new IllegalArgumentException("Bot conversation is required");
        }
        if (message == null) {
            throw new IllegalArgumentException("Bot message is required");
        }
        requirePositive(conversation.getConversationId(), "Conversation ID must be positive");
        if (message.getMessageId() != 0) {
            throw new IllegalArgumentException("New Bot message ID must be zero");
        }

        return inTransaction("Failed to append Bot message", connection -> {
            Optional<ConversationRow> locked = findConversation(
                    connection, LOCK_CONVERSATION_BY_ID_SQL,
                    authenticatedStudentUserId,
                    conversation.getConversationId(),
                    authenticatedStudentUserId
            );
            if (locked.isEmpty()) {
                throw new IllegalStateException("Bot conversation not found or access denied");
            }
            ConversationRow current = locked.get();
            validateStableConversation(authenticatedStudentUserId, conversation, current);
            List<BotMessage> persistedMessages = loadMessages(
                    connection, current.conversationId()
            );
            int expectedSequence = persistedMessages.size() + 1;
            if (message.getConversationId() != current.conversationId()
                    || message.getSequenceNumber() != expectedSequence) {
                throw new IllegalStateException("Bot message sequence conflict");
            }
            if (message.getCreatedAt().isBefore(current.updatedAt())) {
                throw new IllegalArgumentException(
                        "Bot message creation time cannot precede conversation state"
                );
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    INSERT_MESSAGE_SQL, Statement.RETURN_GENERATED_KEYS
            )) {
                statement.setInt(1, message.getConversationId());
                statement.setInt(2, message.getSequenceNumber());
                statement.setString(3, message.getQuestionText());
                statement.setString(4, message.getNormalizedQuestion());
                statement.setString(5, message.getAnswerText());
                statement.setString(6, message.getAnswerStatus().name());
                if (message.getProviderRequestId() == null) {
                    statement.setNull(7, Types.VARCHAR);
                } else {
                    statement.setString(7, message.getProviderRequestId());
                }
                statement.setObject(8, message.getCreatedAt());
                if (statement.executeUpdate() != 1) {
                    throw new SQLException("Bot message insert did not affect one row");
                }
                requireGeneratedId(statement, "Bot message");
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    UPDATE_CONVERSATION_TIME_SQL
            )) {
                statement.setObject(1, message.getCreatedAt());
                statement.setInt(2, current.conversationId());
                statement.setObject(3, current.updatedAt());
                if (statement.executeUpdate() != 1) {
                    throw new IllegalStateException("Bot conversation was modified; reload");
                }
            }

            // Re-read the conversation rather than assuming the value written.
            // The column is DATETIME(6) and the database may round a more precise
            // timestamp, so the stored instant is the only authoritative one; using
            // the in-memory value could leave a message looking newer than its own
            // conversation and make rehydration reject it.
            ConversationRow stored = findConversation(
                    connection, LOCK_CONVERSATION_BY_ID_SQL,
                    authenticatedStudentUserId,
                    current.conversationId(),
                    authenticatedStudentUserId
            ).orElseThrow(() -> new IllegalStateException(
                    "Bot conversation not found or access denied"
            ));
            return hydrateConversation(stored, loadMessages(connection, current.conversationId()));
        });
    }

    private Optional<ConversationRow> findConversation(
            Connection connection, String sql, int... parameters
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setInt(index + 1, parameters[index]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(mapConversationRow(resultSet))
                        : Optional.empty();
            }
        }
    }

    private Optional<Integer> findActiveStudentBotCourse(
            Connection connection, int userId, int botId
    )
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                ACTIVE_STUDENT_BOT_SQL
        )) {
            statement.setInt(1, userId);
            statement.setInt(2, botId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(resultSet.getInt("course_id"))
                        : Optional.empty();
            }
        }
    }

    private List<BotMessage> loadMessages(Connection connection, int conversationId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(MESSAGES_SQL)) {
            statement.setInt(1, conversationId);
            List<BotMessage> messages = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    messages.add(BotMessage.rehydrate(
                            resultSet.getInt("message_id"),
                            resultSet.getInt("conversation_id"),
                            resultSet.getInt("sequence_no"),
                            resultSet.getString("question_text"),
                            resultSet.getString("normalized_question"),
                            resultSet.getString("answer_text"),
                            parseAnswerStatus(resultSet.getString("answer_status")),
                            resultSet.getString("provider_request_id"),
                            requiredTimestamp(resultSet, "created_at", "Bot message creation time")
                    ));
                }
            }
            return messages;
        }
    }

    private ConversationRow mapConversationRow(ResultSet resultSet) throws SQLException {
        return new ConversationRow(
                resultSet.getInt("conversation_id"),
                resultSet.getInt("bot_id"),
                resultSet.getInt("student_user_id"),
                resultSet.getString("provider_subject_id"),
                requiredTimestamp(resultSet, "created_at", "Conversation creation time"),
                requiredTimestamp(resultSet, "updated_at", "Conversation update time")
        );
    }

    private BotConversation hydrateConversation(
            ConversationRow row, List<BotMessage> messages
    ) {
        return BotConversation.rehydrate(
                row.conversationId(), row.botId(), row.studentUserId(),
                row.providerSubjectId(), row.createdAt(), row.updatedAt(), messages
        );
    }

    private void validateCreationInput(
            CourseBot activeBot, String providerSubjectId, LocalDateTime currentTime
    ) {
        if (activeBot == null) {
            throw new IllegalArgumentException("Course Bot is required");
        }
        requirePositive(activeBot.getBotId(), "Bot ID must be positive");
        if (activeBot.getStatus() != BotStatus.ACTIVE) {
            throw new IllegalStateException("Course Bot is inactive");
        }
        if (providerSubjectId == null) {
            throw new IllegalArgumentException("Provider subject ID is required");
        }
        try {
            java.util.UUID.fromString(providerSubjectId.trim());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Provider subject ID must be a UUID", exception);
        }
        if (currentTime == null) {
            throw new IllegalArgumentException("Conversation creation time is required");
        }
    }

    private void validateStableConversation(
            int userId, BotConversation supplied, ConversationRow current
    ) {
        if (current.studentUserId() != userId
                || supplied.getStudentUserId() != current.studentUserId()
                || supplied.getBotId() != current.botId()
                || !supplied.getProviderSubjectId().equals(current.providerSubjectId())
                || !supplied.getCreatedAt().equals(current.createdAt())
                || !supplied.getUpdatedAt().equals(current.updatedAt())) {
            throw new IllegalStateException("Bot conversation not found or access denied");
        }
    }

    private <T> T inTransaction(String errorMessage, TransactionWork<T> work) {
        try (Connection connection = databaseController.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            boolean transactionStarted = false;
            Throwable failure = null;
            try {
                connection.setAutoCommit(false);
                transactionStarted = true;
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException exception) {
                failure = exception;
                if (transactionStarted) {
                    rollbackWithSuppressed(connection, exception);
                }
                throw new IllegalStateException(errorMessage, exception);
            } catch (RuntimeException exception) {
                failure = exception;
                if (transactionStarted) {
                    rollbackWithSuppressed(connection, exception);
                }
                throw exception;
            } finally {
                restoreAutoCommit(connection, originalAutoCommit, failure);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(errorMessage, exception);
        }
    }

    private static boolean isConversationUniqueRace(SQLException exception) {
        return exception.getErrorCode() == MYSQL_DUPLICATE_KEY_ERROR
                && exception.getMessage() != null
                && exception.getMessage().contains("uq_bot_conversations_bot_student");
    }

    private static int requireGeneratedId(PreparedStatement statement, String label)
            throws SQLException {
        try (ResultSet resultSet = statement.getGeneratedKeys()) {
            if (!resultSet.next() || resultSet.getInt(1) <= 0) {
                throw new SQLException(label + " insert returned no generated ID");
            }
            return resultSet.getInt(1);
        }
    }

    private static BotAnswerStatus parseAnswerStatus(String value) {
        try {
            return BotAnswerStatus.valueOf(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Bot answer status is invalid", exception);
        }
    }

    private static LocalDateTime requiredTimestamp(
            ResultSet resultSet, String column, String label
    ) throws SQLException {
        LocalDateTime value = resultSet.getObject(column, LocalDateTime.class);
        if (value == null) {
            throw new IllegalArgumentException(label + " is missing");
        }
        return value;
    }

    private static void rollbackWithSuppressed(Connection connection, Throwable failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private static void restoreAutoCommit(
            Connection connection, boolean original, Throwable failure
    ) throws SQLException {
        try {
            connection.setAutoCommit(original);
        } catch (SQLException restorationFailure) {
            if (failure != null) {
                failure.addSuppressed(restorationFailure);
            } else {
                throw restorationFailure;
            }
        }
    }

    private static void requirePositive(int value, String message) {
        if (value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    @FunctionalInterface
    private interface TransactionWork<T> {
        T run(Connection connection) throws SQLException;
    }

    private record ConversationRow(
            int conversationId, int botId, int studentUserId,
            String providerSubjectId, LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    private static final class NamedConversationRace extends RuntimeException {
        private NamedConversationRace(SQLException cause) {
            super(cause);
        }
    }
}
