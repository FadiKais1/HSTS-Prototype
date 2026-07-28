package hsts.server.repository;

import hsts.common.BotUsageSummaryDTO;
import hsts.common.CommonBotQuestionDTO;
import hsts.common.type.BotSourceStatus;
import hsts.common.type.BotSourceType;
import hsts.common.type.BotStatus;
import hsts.server.entity.BotSource;
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

public class CourseBotRepository {
    private static final int MYSQL_DUPLICATE_KEY_ERROR = 1062;

    private static final String BOT_COLUMNS = """
            cb.bot_id, cb.course_id, cb.name, cb.status,
            cb.created_by_user_id, cb.external_provider, cb.external_bot_id,
            cb.created_at, cb.updated_at
            """;

    private static final String SOURCE_COLUMNS = """
            bs.source_id, bs.bot_id, bs.source_type, bs.display_name,
            bs.extracted_text, bs.content_sha256, bs.question_id,
            bs.question_version_no, bs.added_by_user_id, bs.status,
            bs.external_source_id, bs.created_at, bs.removed_at
            """;

    private static final String TEACHER_BOTS_SQL = """
            SELECT %s
            FROM course_bots cb
            JOIN courses c ON c.course_id = cb.course_id
            JOIN teacher_courses tc
              ON tc.course_id = cb.course_id
             AND tc.teacher_user_id = ?
            ORDER BY c.name ASC, cb.bot_id ASC
            """.formatted(BOT_COLUMNS);

    private static final String TEACHER_BOT_BY_COURSE_SQL = """
            SELECT %s
            FROM course_bots cb
            JOIN teacher_courses tc
              ON tc.course_id = cb.course_id
             AND tc.teacher_user_id = ?
            WHERE cb.course_id = ?
            """.formatted(BOT_COLUMNS);

    private static final String STUDENT_ACTIVE_BOT_BY_COURSE_SQL = """
            SELECT %s
            FROM course_bots cb
            JOIN student_courses sc
              ON sc.course_id = cb.course_id
             AND sc.student_user_id = ?
            JOIN users u
              ON u.user_id = sc.student_user_id
             AND u.role = 'STUDENT'
             AND u.status = 'ACTIVE'
            WHERE cb.course_id = ?
              AND cb.status = 'ACTIVE'
            """.formatted(BOT_COLUMNS);

    private static final String STUDENT_ACTIVE_BOTS_SQL = """
            SELECT %s
            FROM course_bots cb
            JOIN courses c ON c.course_id = cb.course_id
            JOIN student_courses sc
              ON sc.course_id = cb.course_id
             AND sc.student_user_id = ?
            JOIN users u
              ON u.user_id = sc.student_user_id
             AND u.role = 'STUDENT'
             AND u.status = 'ACTIVE'
            WHERE cb.status = 'ACTIVE'
            ORDER BY c.name ASC, cb.bot_id ASC
            """.formatted(BOT_COLUMNS);

    private static final String SOURCES_BY_BOT_SQL = """
            SELECT %s
            FROM bot_sources bs
            WHERE bs.bot_id = ?
            ORDER BY bs.created_at ASC, bs.source_id ASC
            """.formatted(SOURCE_COLUMNS);

    private static final String ACTIVE_SOURCES_BY_BOT_SQL = """
            SELECT %s
            FROM bot_sources bs
            WHERE bs.bot_id = ? AND bs.status = 'ACTIVE'
            ORDER BY bs.created_at ASC, bs.source_id ASC
            """.formatted(SOURCE_COLUMNS);

    private static final String LOCK_ASSIGNED_COURSE_SQL = """
            SELECT c.course_id
            FROM courses c
            JOIN teacher_courses tc
              ON tc.course_id = c.course_id
             AND tc.teacher_user_id = ?
            WHERE c.course_id = ?
            FOR UPDATE
            """;

    private static final String LOCK_BOT_BY_COURSE_SQL = """
            SELECT cb.bot_id
            FROM course_bots cb
            WHERE cb.course_id = ?
            FOR UPDATE
            """;

    private static final String INSERT_BOT_SQL = """
            INSERT INTO course_bots (
                course_id, name, status, created_by_user_id,
                external_provider, external_bot_id, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String LOCK_ASSIGNED_BOT_SQL = """
            SELECT %s
            FROM course_bots cb
            JOIN teacher_courses tc
              ON tc.course_id = cb.course_id
             AND tc.teacher_user_id = ?
            WHERE cb.bot_id = ?
            FOR UPDATE
            """.formatted(BOT_COLUMNS);

    private static final String UPDATE_BOT_CONFIGURATION_SQL = """
            UPDATE course_bots
            SET name = ?, status = ?, updated_at = ?,
                external_provider = ?, external_bot_id = ?
            WHERE bot_id = ? AND updated_at = ?
            """;

    private static final String INSERT_SOURCE_SQL = """
            INSERT INTO bot_sources (
                bot_id, source_type, display_name, extracted_text,
                content_sha256, question_id, question_version_no,
                added_by_user_id, status, external_source_id,
                created_at, removed_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String EXACT_QUESTION_VERSION_COURSE_SQL = """
            SELECT q.question_id
            FROM questions q
            JOIN question_versions qv
              ON qv.question_id = q.question_id
             AND qv.version_no = ?
            WHERE q.question_id = ? AND q.course_id = ?
            """;

    private static final String LOCK_ASSIGNED_SOURCE_SQL = """
            SELECT %s
            FROM bot_sources bs
            JOIN course_bots cb ON cb.bot_id = bs.bot_id
            JOIN teacher_courses tc
              ON tc.course_id = cb.course_id
             AND tc.teacher_user_id = ?
            WHERE bs.source_id = ?
            FOR UPDATE
            """.formatted(SOURCE_COLUMNS);

    private static final String REMOVE_SOURCE_SQL = """
            UPDATE bot_sources
            SET status = 'REMOVED', removed_at = ?
            WHERE source_id = ? AND status = 'ACTIVE'
            """;

    private static final String TEACHER_SOURCES_SQL = """
            SELECT %s
            FROM bot_sources bs
            JOIN course_bots cb ON cb.bot_id = bs.bot_id
            JOIN teacher_courses tc
              ON tc.course_id = cb.course_id
             AND tc.teacher_user_id = ?
            WHERE bs.bot_id = ?
            ORDER BY bs.created_at ASC, bs.source_id ASC
            """.formatted(SOURCE_COLUMNS);

    private static final String ANONYMOUS_USAGE_HEADER_SQL = """
            SELECT cb.bot_id, cb.course_id, cb.name AS bot_name,
                   c.name AS course_name,
                   COUNT(bm.message_id) AS total_questions,
                   MAX(bm.created_at) AS last_activity_at
            FROM course_bots cb
            JOIN courses c ON c.course_id = cb.course_id
            JOIN teacher_courses tc
              ON tc.course_id = cb.course_id
             AND tc.teacher_user_id = ?
            LEFT JOIN bot_conversations bc ON bc.bot_id = cb.bot_id
            LEFT JOIN bot_messages bm ON bm.conversation_id = bc.conversation_id
            WHERE cb.bot_id = ?
            GROUP BY cb.bot_id, cb.course_id, cb.name, c.name
            """;

    private static final String COMMON_QUESTIONS_SQL = """
            SELECT bm.normalized_question, COUNT(*) AS occurrence_count
            FROM bot_messages bm
            JOIN bot_conversations bc ON bc.conversation_id = bm.conversation_id
            JOIN course_bots cb ON cb.bot_id = bc.bot_id
            JOIN teacher_courses tc
              ON tc.course_id = cb.course_id
             AND tc.teacher_user_id = ?
            WHERE cb.bot_id = ?
            GROUP BY bm.normalized_question
            ORDER BY occurrence_count DESC, bm.normalized_question ASC
            LIMIT ?
            """;

    private final DatabaseController databaseController;

    public CourseBotRepository() {
        this(new DatabaseController());
    }

    public CourseBotRepository(DatabaseController databaseController) {
        if (databaseController == null) {
            throw new IllegalArgumentException("Database controller is required");
        }
        this.databaseController = databaseController;
    }

    public List<CourseBot> findAssignedToTeacher(int authenticatedTeacherUserId) {
        requirePositive(authenticatedTeacherUserId, "Teacher user ID must be positive");
        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(TEACHER_BOTS_SQL)) {
            statement.setInt(1, authenticatedTeacherUserId);
            List<BotRow> rows = readBotRows(statement);
            List<CourseBot> bots = new ArrayList<>(rows.size());
            for (BotRow row : rows) {
                bots.add(hydrateBot(row, loadSources(connection, row.botId(), false)));
            }
            return List.copyOf(bots);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load assigned Course Bots", exception);
        }
    }

    public Optional<CourseBot> findByCourseForTeacher(
            int authenticatedTeacherUserId, int courseId
    ) {
        requirePositive(authenticatedTeacherUserId, "Teacher user ID must be positive");
        requirePositive(courseId, "Course ID must be positive");
        try (Connection connection = databaseController.getConnection()) {
            Optional<BotRow> row = findOneBot(
                    connection, TEACHER_BOT_BY_COURSE_SQL,
                    authenticatedTeacherUserId, courseId
            );
            if (row.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(hydrateBot(
                    row.get(), loadSources(connection, row.get().botId(), false)
            ));
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load assigned Course Bot", exception);
        }
    }

    public Optional<CourseBot> findActiveByCourseForStudent(
            int authenticatedStudentUserId, int courseId
    ) {
        requirePositive(authenticatedStudentUserId, "Student user ID must be positive");
        requirePositive(courseId, "Course ID must be positive");
        try (Connection connection = databaseController.getConnection()) {
            Optional<BotRow> row = findOneBot(
                    connection, STUDENT_ACTIVE_BOT_BY_COURSE_SQL,
                    authenticatedStudentUserId, courseId
            );
            if (row.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(hydrateBot(
                    row.get(), loadSources(connection, row.get().botId(), true)
            ));
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load active Course Bot", exception);
        }
    }

    public List<CourseBot> findActiveForStudent(int authenticatedStudentUserId) {
        requirePositive(authenticatedStudentUserId, "Student user ID must be positive");
        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     STUDENT_ACTIVE_BOTS_SQL
             )) {
            statement.setInt(1, authenticatedStudentUserId);
            List<BotRow> rows = readBotRows(statement);
            List<CourseBot> bots = new ArrayList<>(rows.size());
            for (BotRow row : rows) {
                bots.add(hydrateBot(row, loadSources(connection, row.botId(), true)));
            }
            return List.copyOf(bots);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load active Course Bots", exception);
        }
    }

    public CourseBot create(int authenticatedTeacherUserId, CourseBot bot) {
        requirePositive(authenticatedTeacherUserId, "Teacher user ID must be positive");
        requireUnpersistedBot(authenticatedTeacherUserId, bot);

        return inTransaction("Failed to create Course Bot", connection -> {
            if (!exists(connection, LOCK_ASSIGNED_COURSE_SQL,
                    authenticatedTeacherUserId, bot.getCourseId())) {
                throw new IllegalStateException("Course not found or access denied");
            }
            if (exists(connection, LOCK_BOT_BY_COURSE_SQL, bot.getCourseId())) {
                throw new IllegalStateException("Course Bot already exists");
            }

            int botId;
            try (PreparedStatement statement = connection.prepareStatement(
                    INSERT_BOT_SQL, Statement.RETURN_GENERATED_KEYS
            )) {
                bindBotInsert(statement, authenticatedTeacherUserId, bot);
                if (statement.executeUpdate() != 1) {
                    throw new SQLException("Course Bot insert did not affect one row");
                }
                botId = requireGeneratedId(statement, "Course Bot");
            } catch (SQLException exception) {
                if (isNamedDuplicate(exception, "uq_course_bots_course")) {
                    throw new IllegalStateException("Course Bot already exists", exception);
                }
                throw exception;
            }
            return rereadAssignedBot(connection, authenticatedTeacherUserId, botId);
        });
    }

    public CourseBot persistConfiguration(
            int authenticatedTeacherUserId, CourseBot bot
    ) {
        requirePositive(authenticatedTeacherUserId, "Teacher user ID must be positive");
        if (bot == null) {
            throw new IllegalArgumentException("Course Bot is required");
        }
        requirePositive(bot.getBotId(), "Bot ID must be positive");

        return inTransaction("Failed to persist Course Bot configuration", connection -> {
            Optional<BotRow> locked = findOneBot(
                    connection, LOCK_ASSIGNED_BOT_SQL,
                    authenticatedTeacherUserId, bot.getBotId()
            );
            if (locked.isEmpty()) {
                throw new IllegalStateException("Course Bot not found or access denied");
            }
            BotRow current = locked.get();
            validateStableBotState(authenticatedTeacherUserId, bot, current);

            try (PreparedStatement statement = connection.prepareStatement(
                    UPDATE_BOT_CONFIGURATION_SQL
            )) {
                statement.setString(1, bot.getName());
                statement.setString(2, bot.getStatus().name());
                statement.setObject(3, bot.getUpdatedAt());
                setNullableString(statement, 4, bot.getExternalProvider());
                setNullableString(statement, 5, bot.getExternalBotId());
                statement.setInt(6, bot.getBotId());
                statement.setObject(7, current.updatedAt());
                if (statement.executeUpdate() != 1) {
                    throw new IllegalStateException(
                            "Course Bot was modified by another user; reload and try again"
                    );
                }
            }
            return rereadAssignedBot(connection, authenticatedTeacherUserId, bot.getBotId());
        });
    }

    public BotSource addSource(int authenticatedTeacherUserId, BotSource source) {
        requirePositive(authenticatedTeacherUserId, "Teacher user ID must be positive");
        if (source == null) {
            throw new IllegalArgumentException("Bot source is required");
        }
        if (source.getSourceId() != 0) {
            throw new IllegalArgumentException("New Bot source ID must be zero");
        }
        if (source.getAddedByUserId() != authenticatedTeacherUserId) {
            throw new IllegalArgumentException("Bot source actor does not match authenticated user");
        }

        return inTransaction("Failed to add Bot source", connection -> {
            Optional<BotRow> bot = findOneBot(
                    connection, LOCK_ASSIGNED_BOT_SQL,
                    authenticatedTeacherUserId, source.getBotId()
            );
            if (bot.isEmpty()) {
                throw new IllegalStateException("Course Bot not found or access denied");
            }
            validateQuestionSource(connection, bot.get().courseId(), source);

            int sourceId;
            try (PreparedStatement statement = connection.prepareStatement(
                    INSERT_SOURCE_SQL, Statement.RETURN_GENERATED_KEYS
            )) {
                bindSourceInsert(statement, source);
                if (statement.executeUpdate() != 1) {
                    throw new SQLException("Bot source insert did not affect one row");
                }
                sourceId = requireGeneratedId(statement, "Bot source");
            } catch (SQLException exception) {
                if (isNamedDuplicate(exception, "uq_bot_sources_active_checksum")) {
                    throw new IllegalStateException("Bot source already exists", exception);
                }
                throw exception;
            }
            return findSourceById(connection, sourceId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Created Bot source could not be reloaded"
                    ));
        });
    }

    public BotSource persistSourceRemoval(
            int authenticatedTeacherUserId, BotSource source
    ) {
        requirePositive(authenticatedTeacherUserId, "Teacher user ID must be positive");
        if (source == null) {
            throw new IllegalArgumentException("Bot source is required");
        }
        requirePositive(source.getSourceId(), "Bot source ID must be positive");
        if (source.getStatus() != BotSourceStatus.REMOVED || source.getRemovedAt() == null) {
            throw new IllegalArgumentException("Bot source must already be removed");
        }

        return inTransaction("Failed to remove Bot source", connection -> {
            Optional<BotSource> locked = findLockedAssignedSource(
                    connection, authenticatedTeacherUserId, source.getSourceId()
            );
            if (locked.isEmpty()) {
                throw new IllegalStateException("Bot source not found or access denied");
            }
            BotSource current = locked.get();
            validateStableSource(source, current);
            if (current.getStatus() == BotSourceStatus.REMOVED) {
                if (source.getRemovedAt().equals(current.getRemovedAt())) {
                    return current;
                }
                throw new IllegalStateException("Bot source removal conflicts with current state");
            }

            try (PreparedStatement statement = connection.prepareStatement(REMOVE_SOURCE_SQL)) {
                statement.setObject(1, source.getRemovedAt());
                statement.setInt(2, source.getSourceId());
                if (statement.executeUpdate() != 1) {
                    throw new IllegalStateException("Bot source removal conflicts with current state");
                }
            }
            return findSourceById(connection, source.getSourceId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Bot source not found or access denied"
                    ));
        });
    }

    public List<BotSource> findSourcesForTeacher(
            int authenticatedTeacherUserId, int botId
    ) {
        requirePositive(authenticatedTeacherUserId, "Teacher user ID must be positive");
        requirePositive(botId, "Bot ID must be positive");
        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(TEACHER_SOURCES_SQL)) {
            statement.setInt(1, authenticatedTeacherUserId);
            statement.setInt(2, botId);
            return List.copyOf(readSources(statement));
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load assigned Bot sources", exception);
        }
    }

    public BotUsageSummaryDTO findAnonymousUsageForTeacher(
            int authenticatedTeacherUserId, int botId, int commonQuestionLimit
    ) {
        requirePositive(authenticatedTeacherUserId, "Teacher user ID must be positive");
        requirePositive(botId, "Bot ID must be positive");
        if (commonQuestionLimit < 1 || commonQuestionLimit > 100) {
            throw new IllegalArgumentException("Common question limit must be between 1 and 100");
        }
        try (Connection connection = databaseController.getConnection()) {
            UsageHeader header;
            try (PreparedStatement statement = connection.prepareStatement(
                    ANONYMOUS_USAGE_HEADER_SQL
            )) {
                statement.setInt(1, authenticatedTeacherUserId);
                statement.setInt(2, botId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        throw new IllegalStateException("Course Bot not found or access denied");
                    }
                    header = new UsageHeader(
                            resultSet.getInt("bot_id"),
                            resultSet.getInt("course_id"),
                            resultSet.getString("bot_name"),
                            resultSet.getString("course_name"),
                            resultSet.getInt("total_questions"),
                            resultSet.getObject("last_activity_at", LocalDateTime.class)
                    );
                }
            }

            List<CommonBotQuestionDTO> common = new ArrayList<>();
            if (header.totalQuestions() > 0) {
                try (PreparedStatement statement = connection.prepareStatement(
                        COMMON_QUESTIONS_SQL
                )) {
                    statement.setInt(1, authenticatedTeacherUserId);
                    statement.setInt(2, botId);
                    statement.setInt(3, commonQuestionLimit);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        while (resultSet.next()) {
                            common.add(new CommonBotQuestionDTO(
                                    resultSet.getString("normalized_question"),
                                    resultSet.getInt("occurrence_count")
                            ));
                        }
                    }
                }
            }
            return new BotUsageSummaryDTO(
                    header.botId(), header.courseId(), header.botName(),
                    header.courseName(), header.totalQuestions(),
                    header.lastActivityAt(), common
            );
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load anonymous Bot usage", exception);
        }
    }

    private CourseBot rereadAssignedBot(Connection connection, int userId, int botId)
            throws SQLException {
        Optional<BotRow> row = findOneBot(connection, LOCK_ASSIGNED_BOT_SQL, userId, botId);
        if (row.isEmpty()) {
            throw new IllegalStateException("Course Bot not found or access denied");
        }
        return hydrateBot(row.get(), loadSources(connection, botId, false));
    }

    private Optional<BotRow> findOneBot(Connection connection, String sql, int... values)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setInt(index + 1, values[index]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapBotRow(resultSet)) : Optional.empty();
            }
        }
    }

    private List<BotRow> readBotRows(PreparedStatement statement) throws SQLException {
        List<BotRow> rows = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rows.add(mapBotRow(resultSet));
            }
        }
        return rows;
    }

    private BotRow mapBotRow(ResultSet resultSet) throws SQLException {
        return new BotRow(
                resultSet.getInt("bot_id"), resultSet.getInt("course_id"),
                resultSet.getString("name"),
                parseEnum(resultSet.getString("status"), BotStatus.class, "Bot status"),
                resultSet.getInt("created_by_user_id"),
                resultSet.getString("external_provider"),
                resultSet.getString("external_bot_id"),
                requiredTimestamp(resultSet, "created_at", "Bot creation time"),
                requiredTimestamp(resultSet, "updated_at", "Bot update time")
        );
    }

    private CourseBot hydrateBot(BotRow row, List<BotSource> sources) {
        return CourseBot.rehydrate(
                row.botId(), row.courseId(), row.name(), row.status(),
                row.createdByUserId(), row.externalProvider(), row.externalBotId(),
                row.createdAt(), row.updatedAt(), sources
        );
    }

    private List<BotSource> loadSources(Connection connection, int botId, boolean activeOnly)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                activeOnly ? ACTIVE_SOURCES_BY_BOT_SQL : SOURCES_BY_BOT_SQL
        )) {
            statement.setInt(1, botId);
            return readSources(statement);
        }
    }

    private List<BotSource> readSources(PreparedStatement statement) throws SQLException {
        List<BotSource> sources = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                sources.add(mapSource(resultSet));
            }
        }
        return sources;
    }

    private BotSource mapSource(ResultSet resultSet) throws SQLException {
        return BotSource.rehydrate(
                resultSet.getInt("source_id"), resultSet.getInt("bot_id"),
                parseEnum(resultSet.getString("source_type"), BotSourceType.class,
                        "Bot source type"),
                resultSet.getString("display_name"),
                resultSet.getString("extracted_text"),
                resultSet.getString("content_sha256"),
                nullableInteger(resultSet, "question_id"),
                nullableInteger(resultSet, "question_version_no"),
                resultSet.getInt("added_by_user_id"),
                parseEnum(resultSet.getString("status"), BotSourceStatus.class,
                        "Bot source status"),
                resultSet.getString("external_source_id"),
                requiredTimestamp(resultSet, "created_at", "Bot source creation time"),
                resultSet.getObject("removed_at", LocalDateTime.class)
        );
    }

    private Optional<BotSource> findSourceById(Connection connection, int sourceId)
            throws SQLException {
        String sql = "SELECT " + SOURCE_COLUMNS
                + " FROM bot_sources bs WHERE bs.source_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, sourceId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapSource(resultSet)) : Optional.empty();
            }
        }
    }

    private Optional<BotSource> findLockedAssignedSource(
            Connection connection, int userId, int sourceId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                LOCK_ASSIGNED_SOURCE_SQL
        )) {
            statement.setInt(1, userId);
            statement.setInt(2, sourceId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapSource(resultSet)) : Optional.empty();
            }
        }
    }

    private boolean exists(Connection connection, String sql, int... values)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setInt(index + 1, values[index]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private void validateQuestionSource(Connection connection, int courseId, BotSource source)
            throws SQLException {
        if (source.getSourceType() != BotSourceType.QUESTION_BANK) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                EXACT_QUESTION_VERSION_COURSE_SQL
        )) {
            statement.setInt(1, source.getQuestionVersionNo());
            statement.setInt(2, source.getQuestionId());
            statement.setInt(3, courseId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException(
                            "Question version not found or access denied"
                    );
                }
            }
        }
    }

    private void bindBotInsert(
            PreparedStatement statement, int userId, CourseBot bot
    ) throws SQLException {
        statement.setInt(1, bot.getCourseId());
        statement.setString(2, bot.getName());
        statement.setString(3, bot.getStatus().name());
        statement.setInt(4, userId);
        setNullableString(statement, 5, bot.getExternalProvider());
        setNullableString(statement, 6, bot.getExternalBotId());
        statement.setObject(7, bot.getCreatedAt());
        statement.setObject(8, bot.getUpdatedAt());
    }

    private void bindSourceInsert(PreparedStatement statement, BotSource source)
            throws SQLException {
        statement.setInt(1, source.getBotId());
        statement.setString(2, source.getSourceType().name());
        statement.setString(3, source.getDisplayName());
        statement.setString(4, source.getExtractedText());
        statement.setString(5, source.getContentSha256());
        setNullableInteger(statement, 6, source.getQuestionId());
        setNullableInteger(statement, 7, source.getQuestionVersionNo());
        statement.setInt(8, source.getAddedByUserId());
        statement.setString(9, source.getStatus().name());
        setNullableString(statement, 10, source.getExternalSourceId());
        statement.setObject(11, source.getCreatedAt());
        if (source.getRemovedAt() == null) {
            statement.setNull(12, Types.TIMESTAMP);
        } else {
            statement.setObject(12, source.getRemovedAt());
        }
    }

    private void validateStableBotState(int userId, CourseBot bot, BotRow current) {
        if (current.createdByUserId() != userId) {
            throw new IllegalStateException("Course Bot not found or access denied");
        }
        if (bot.getCourseId() != current.courseId()
                || bot.getCreatedByUserId() != current.createdByUserId()
                || !bot.getCreatedAt().equals(current.createdAt())
                || !java.util.Objects.equals(
                        bot.getExternalProvider(), current.externalProvider()
                )
                || !java.util.Objects.equals(bot.getExternalBotId(), current.externalBotId())) {
            throw new IllegalArgumentException("Course Bot stable state does not match persistence");
        }
        if (bot.getUpdatedAt().isBefore(current.updatedAt())) {
            throw new IllegalStateException(
                    "Course Bot was modified by another user; reload and try again"
            );
        }
    }

    private void validateStableSource(BotSource supplied, BotSource current) {
        if (supplied.getBotId() != current.getBotId()
                || supplied.getSourceType() != current.getSourceType()
                || !supplied.getContentSha256().equals(current.getContentSha256())
                || supplied.getAddedByUserId() != current.getAddedByUserId()
                || !supplied.getCreatedAt().equals(current.getCreatedAt())
                || !java.util.Objects.equals(supplied.getQuestionId(), current.getQuestionId())
                || !java.util.Objects.equals(
                        supplied.getQuestionVersionNo(), current.getQuestionVersionNo()
                )) {
            throw new IllegalArgumentException("Bot source stable state does not match persistence");
        }
    }

    private void requireUnpersistedBot(int userId, CourseBot bot) {
        if (bot == null) {
            throw new IllegalArgumentException("Course Bot is required");
        }
        if (bot.getBotId() != 0) {
            throw new IllegalArgumentException("New Course Bot ID must be zero");
        }
        if (bot.getCreatedByUserId() != userId) {
            throw new IllegalArgumentException("Bot creator does not match authenticated user");
        }
        if (!bot.getSources().isEmpty()) {
            throw new IllegalArgumentException("New Course Bot cannot contain persisted sources");
        }
    }

    private <T> T inTransaction(String message, TransactionWork<T> work) {
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
                throw new IllegalStateException(message, exception);
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
            throw new IllegalStateException(message, exception);
        }
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

    private static int requireGeneratedId(PreparedStatement statement, String label)
            throws SQLException {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (!keys.next() || keys.getInt(1) <= 0) {
                throw new SQLException(label + " insert returned no generated ID");
            }
            return keys.getInt(1);
        }
    }

    private static boolean isNamedDuplicate(SQLException exception, String constraint) {
        return exception.getErrorCode() == MYSQL_DUPLICATE_KEY_ERROR
                && exception.getMessage() != null
                && exception.getMessage().contains(constraint);
    }

    private static Integer nullableInteger(ResultSet resultSet, String column)
            throws SQLException {
        Object value = resultSet.getObject(column);
        return value == null ? null : resultSet.getInt(column);
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

    private static <E extends Enum<E>> E parseEnum(
            String value, Class<E> enumType, String label
    ) {
        try {
            return Enum.valueOf(enumType, value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(label + " is invalid", exception);
        }
    }

    private static void setNullableString(
            PreparedStatement statement, int index, String value
    ) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    private static void setNullableInteger(
            PreparedStatement statement, int index, Integer value
    ) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value);
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

    private record BotRow(
            int botId, int courseId, String name, BotStatus status,
            int createdByUserId, String externalProvider, String externalBotId,
            LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
    }

    private record UsageHeader(
            int botId, int courseId, String botName, String courseName,
            int totalQuestions, LocalDateTime lastActivityAt
    ) {
    }

}
