CREATE DATABASE IF NOT EXISTS hsts_prototype;

USE hsts_prototype;

CREATE TABLE IF NOT EXISTS users (
    user_id INT PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(254) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT chk_users_role
        CHECK (role IN ('STUDENT', 'TEACHER', 'COORDINATOR', 'PRINCIPAL')),
    CONSTRAINT chk_users_status
        CHECK (status IN ('ACTIVE', 'BLOCKED'))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS subjects (
    subject_id INT AUTO_INCREMENT PRIMARY KEY,
    subject_code VARCHAR(30) NOT NULL UNIQUE,
    subject_number CHAR(2) CHARACTER SET ascii COLLATE ascii_bin NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT NULL,
    CONSTRAINT uq_subjects_subject_number UNIQUE (subject_number),
    CONSTRAINT chk_subjects_subject_number
        CHECK (subject_number IS NULL OR subject_number REGEXP '^[0-9]{2}$')
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS courses (
    course_id INT AUTO_INCREMENT PRIMARY KEY,
    subject_id INT NOT NULL,
    course_code VARCHAR(30) NOT NULL,
    course_number CHAR(2) CHARACTER SET ascii COLLATE ascii_bin NULL,
    name VARCHAR(100) NOT NULL,
    grade_level VARCHAR(30) NOT NULL,
    school_year VARCHAR(20) NOT NULL,
    CONSTRAINT uq_courses_subject_code_year
        UNIQUE (subject_id, course_code, school_year),
    CONSTRAINT uq_courses_course_number UNIQUE (course_number),
    CONSTRAINT chk_courses_course_number
        CHECK (course_number IS NULL OR course_number REGEXP '^[0-9]{2}$'),
    CONSTRAINT fk_courses_subject
        FOREIGN KEY (subject_id) REFERENCES subjects (subject_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS teacher_courses (
    teacher_user_id INT NOT NULL,
    course_id INT NOT NULL,
    PRIMARY KEY (teacher_user_id, course_id),
    KEY idx_teacher_courses_course_id (course_id),
    CONSTRAINT fk_teacher_courses_user
        FOREIGN KEY (teacher_user_id) REFERENCES users (user_id),
    CONSTRAINT fk_teacher_courses_course
        FOREIGN KEY (course_id) REFERENCES courses (course_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS questions (
    question_id INT AUTO_INCREMENT PRIMARY KEY,
    content TEXT NOT NULL,
    topic VARCHAR(100),
    type VARCHAR(50),
    difficulty VARCHAR(50),
    status VARCHAR(50),
    illustration_path TEXT,
    answer_option_1 TEXT,
    answer_option_2 TEXT,
    answer_option_3 TEXT,
    answer_option_4 TEXT,
    correct_option_number INT,
    course_id INT NULL,
    created_by_user_id INT NULL,
    current_version_no INT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    question_code CHAR(5) CHARACTER SET ascii COLLATE ascii_bin NULL,
    -- question_code = 2 digit course number + 3 digit question number.
    -- Soft delete. A deleted question disappears from the question bank and
    -- cannot be added to new exams, but its rows and versions survive so that
    -- exams already containing it, and their graded submissions, are unaffected.
    deleted_at DATETIME(6) NULL,
    KEY idx_questions_course_id (course_id),
    KEY idx_questions_created_by_user_id (created_by_user_id),
    CONSTRAINT uq_questions_question_code UNIQUE (question_code),
    CONSTRAINT chk_questions_question_code
        CHECK (question_code IS NULL OR question_code REGEXP '^[0-9]{5}$'),
    CONSTRAINT fk_questions_course
        FOREIGN KEY (course_id) REFERENCES courses (course_id),
    CONSTRAINT fk_questions_creator
        FOREIGN KEY (created_by_user_id) REFERENCES users (user_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS question_versions (
    question_id INT NOT NULL,
    version_no INT NOT NULL,
    content TEXT NOT NULL,
    topic VARCHAR(100) NOT NULL,
    question_type VARCHAR(30) NOT NULL,
    difficulty VARCHAR(20) NOT NULL,
    illustration_path TEXT NULL,
    correct_option_number TINYINT NOT NULL,
    created_by_user_id INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (question_id, version_no),
    KEY idx_question_versions_created_by_user_id (created_by_user_id),
    CONSTRAINT fk_question_versions_question
        FOREIGN KEY (question_id) REFERENCES questions (question_id),
    CONSTRAINT fk_question_versions_creator
        FOREIGN KEY (created_by_user_id) REFERENCES users (user_id),
    CONSTRAINT chk_question_versions_difficulty
        CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    CONSTRAINT chk_question_versions_correct_option
        CHECK (correct_option_number BETWEEN 1 AND 4)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS answer_options (
    question_id INT NOT NULL,
    version_no INT NOT NULL,
    option_number TINYINT NOT NULL,
    option_text TEXT NOT NULL,
    PRIMARY KEY (question_id, version_no, option_number),
    CONSTRAINT fk_answer_options_version
        FOREIGN KEY (question_id, version_no)
        REFERENCES question_versions (question_id, version_no),
    CONSTRAINT chk_answer_options_option_number
        CHECK (option_number BETWEEN 1 AND 4)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS question_version_illustrations (
    question_id INT NOT NULL,
    version_no INT NOT NULL,
    media_type VARCHAR(30) NOT NULL,
    content_bytes MEDIUMBLOB NOT NULL,
    byte_length INT NOT NULL,
    width_pixels INT NOT NULL,
    height_pixels INT NOT NULL,
    content_sha256 CHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (question_id, version_no),
    CONSTRAINT fk_question_version_illustrations_version
        FOREIGN KEY (question_id, version_no)
        REFERENCES question_versions (question_id, version_no) ON DELETE RESTRICT,
    CONSTRAINT chk_question_version_illustrations_media_type
        CHECK (media_type IN ('image/png', 'image/jpeg')),
    CONSTRAINT chk_question_version_illustrations_byte_length
        CHECK (byte_length BETWEEN 1 AND 2097152
               AND byte_length = OCTET_LENGTH(content_bytes)),
    CONSTRAINT chk_question_version_illustrations_width
        CHECK (width_pixels BETWEEN 1 AND 4096),
    CONSTRAINT chk_question_version_illustrations_height
        CHECK (height_pixels BETWEEN 1 AND 4096),
    CONSTRAINT chk_question_version_illustrations_sha256
        CHECK (content_sha256 REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS course_bots (
    bot_id INT NOT NULL AUTO_INCREMENT,
    course_id INT NOT NULL,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_by_user_id INT NOT NULL,
    external_provider VARCHAR(100) NULL,
    external_bot_id VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (bot_id),
    CONSTRAINT uq_course_bots_course UNIQUE (course_id),
    KEY idx_course_bots_creator (created_by_user_id),
    CONSTRAINT fk_course_bots_course
        FOREIGN KEY (course_id) REFERENCES courses (course_id) ON DELETE RESTRICT,
    CONSTRAINT fk_course_bots_creator
        FOREIGN KEY (created_by_user_id) REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT chk_course_bots_name
        CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT chk_course_bots_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS bot_sources (
    source_id INT NOT NULL AUTO_INCREMENT,
    bot_id INT NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    extracted_text MEDIUMTEXT NOT NULL,
    content_sha256 CHAR(64) NOT NULL,
    question_id INT NULL,
    question_version_no INT NULL,
    added_by_user_id INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    external_source_id VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    removed_at DATETIME(6) NULL,
    current_version_no INT NOT NULL DEFAULT 1,
    active_content_sha256 CHAR(64)
        GENERATED ALWAYS AS (
            CASE WHEN status = 'ACTIVE' THEN content_sha256 ELSE NULL END
        ) STORED,
    PRIMARY KEY (source_id),
    CONSTRAINT uq_bot_sources_active_checksum
        UNIQUE (bot_id, active_content_sha256),
    KEY idx_bot_sources_bot_status (bot_id, status),
    KEY idx_bot_sources_added_by_user (added_by_user_id),
    KEY idx_bot_sources_question_version (question_id, question_version_no),
    CONSTRAINT fk_bot_sources_bot
        FOREIGN KEY (bot_id) REFERENCES course_bots (bot_id) ON DELETE RESTRICT,
    CONSTRAINT fk_bot_sources_added_by_user
        FOREIGN KEY (added_by_user_id) REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_bot_sources_question_version
        FOREIGN KEY (question_id, question_version_no)
        REFERENCES question_versions (question_id, version_no),
    CONSTRAINT chk_bot_sources_type
        CHECK (source_type IN ('QUESTION_BANK', 'FREE_TEXT', 'TXT', 'PDF', 'DOCX')),
    CONSTRAINT chk_bot_sources_status
        CHECK (status IN ('ACTIVE', 'REMOVED')),
    CONSTRAINT chk_bot_sources_checksum
        CHECK (content_sha256 REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT chk_bot_sources_question_reference
        CHECK (
            (source_type = 'QUESTION_BANK'
                AND question_id IS NOT NULL
                AND question_version_no IS NOT NULL)
            OR
            (source_type <> 'QUESTION_BANK'
                AND question_id IS NULL
                AND question_version_no IS NULL)
        ),
    CONSTRAINT chk_bot_sources_removal_state
        CHECK (
            (status = 'ACTIVE' AND removed_at IS NULL)
            OR (status = 'REMOVED' AND removed_at IS NOT NULL)
        ),
    CONSTRAINT chk_bot_sources_removed_at
        CHECK (removed_at IS NULL OR removed_at >= created_at),
    CONSTRAINT chk_bot_sources_display_name
        CHECK (CHAR_LENGTH(TRIM(display_name)) > 0),
    CONSTRAINT chk_bot_sources_extracted_text
        CHECK (CHAR_LENGTH(TRIM(extracted_text)) > 0)
) ENGINE=InnoDB;

-- Superseded versions of a bot source. bot_sources always holds the version in
-- use, so every read of a source is unchanged by versioning; this table keeps
-- what each earlier version said and who wrote it.
CREATE TABLE IF NOT EXISTS bot_source_versions (
    source_id INT NOT NULL,
    version_no INT NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    extracted_text MEDIUMTEXT NOT NULL,
    content_sha256 CHAR(64) NOT NULL,
    created_by_user_id INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (source_id, version_no),
    KEY idx_bot_source_versions_created_by (created_by_user_id),
    CONSTRAINT fk_bot_source_versions_source
        FOREIGN KEY (source_id) REFERENCES bot_sources (source_id) ON DELETE CASCADE,
    CONSTRAINT fk_bot_source_versions_user
        FOREIGN KEY (created_by_user_id) REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT chk_bot_source_versions_no CHECK (version_no > 0),
    CONSTRAINT chk_bot_source_versions_checksum
        CHECK (content_sha256 REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS bot_conversations (
    conversation_id INT NOT NULL AUTO_INCREMENT,
    bot_id INT NOT NULL,
    student_user_id INT NOT NULL,
    provider_subject_id CHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (conversation_id),
    CONSTRAINT uq_bot_conversations_bot_student
        UNIQUE (bot_id, student_user_id),
    CONSTRAINT uq_bot_conversations_provider_subject
        UNIQUE (provider_subject_id),
    KEY idx_bot_conversations_student (student_user_id),
    KEY idx_bot_conversations_bot_updated (bot_id, updated_at),
    CONSTRAINT fk_bot_conversations_bot
        FOREIGN KEY (bot_id) REFERENCES course_bots (bot_id) ON DELETE RESTRICT,
    CONSTRAINT fk_bot_conversations_student
        FOREIGN KEY (student_user_id) REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT chk_bot_conversations_provider_subject
        CHECK (provider_subject_id REGEXP
            '^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$')
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS bot_messages (
    message_id INT NOT NULL AUTO_INCREMENT,
    conversation_id INT NOT NULL,
    sequence_no INT NOT NULL,
    question_text TEXT NOT NULL,
    normalized_question VARCHAR(1000) NOT NULL,
    answer_text MEDIUMTEXT NOT NULL,
    answer_status VARCHAR(30) NOT NULL,
    provider_request_id VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (message_id),
    CONSTRAINT uq_bot_messages_conversation_sequence
        UNIQUE (conversation_id, sequence_no),
    KEY idx_bot_messages_conversation_created (conversation_id, created_at),
    KEY idx_bot_messages_normalized_question (normalized_question(191)),
    KEY idx_bot_messages_provider_request (provider_request_id),
    CONSTRAINT fk_bot_messages_conversation
        FOREIGN KEY (conversation_id)
        REFERENCES bot_conversations (conversation_id) ON DELETE RESTRICT,
    CONSTRAINT chk_bot_messages_sequence
        CHECK (sequence_no > 0),
    CONSTRAINT chk_bot_messages_answer_status
        CHECK (answer_status IN ('ANSWERED', 'NO_SUITABLE_ANSWER')),
    CONSTRAINT chk_bot_messages_question
        CHECK (CHAR_LENGTH(TRIM(question_text)) > 0),
    CONSTRAINT chk_bot_messages_normalized_question
        CHECK (CHAR_LENGTH(TRIM(normalized_question)) > 0),
    CONSTRAINT chk_bot_messages_answer
        CHECK (
            answer_status = 'NO_SUITABLE_ANSWER'
            OR CHAR_LENGTH(TRIM(answer_text)) > 0
        )
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS subject_coordinators (
    subject_id INT NOT NULL,
    coordinator_user_id INT NOT NULL,
    assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (subject_id, coordinator_user_id),
    KEY idx_subject_coordinators_coordinator_user_id (coordinator_user_id),
    CONSTRAINT fk_subject_coordinators_subject
        FOREIGN KEY (subject_id) REFERENCES subjects (subject_id),
    CONSTRAINT fk_subject_coordinators_user
        FOREIGN KEY (coordinator_user_id) REFERENCES users (user_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS exams (
    exam_id INT NOT NULL AUTO_INCREMENT,
    exam_code CHAR(6) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    course_id INT NOT NULL,
    created_by_user_id INT NOT NULL,
    current_version_no INT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (exam_id),
    CONSTRAINT uq_exams_exam_code UNIQUE (exam_code),
    KEY idx_exams_course_id (course_id),
    KEY idx_exams_created_by_user_id (created_by_user_id),
    CONSTRAINT fk_exams_course
        FOREIGN KEY (course_id) REFERENCES courses (course_id),
    CONSTRAINT fk_exams_creator
        FOREIGN KEY (created_by_user_id) REFERENCES users (user_id),
    CONSTRAINT chk_exams_exam_code
        CHECK (exam_code REGEXP '^[0-9]{6}$'),
    CONSTRAINT chk_exams_current_version
        CHECK (current_version_no IS NULL OR current_version_no > 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS exam_versions (
    exam_id INT NOT NULL,
    version_no INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    duration_minutes INT NOT NULL,
    cumulative_extension_minutes INT NOT NULL DEFAULT 0,
    teacher_notes TEXT NOT NULL,
    student_instructions TEXT NOT NULL,
    total_score DECIMAL(7,2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version_created_by_user_id INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at DATETIME NULL,
    reviewed_by_user_id INT NULL,
    reviewed_at DATETIME NULL,
    rejection_reason TEXT NULL,
    PRIMARY KEY (exam_id, version_no),
    KEY idx_exam_versions_status (status),
    KEY idx_exam_versions_reviewed_by_user_id (reviewed_by_user_id),
    KEY idx_exam_versions_submitted_at (submitted_at),
    CONSTRAINT fk_exam_versions_exam
        FOREIGN KEY (exam_id) REFERENCES exams (exam_id) ON DELETE CASCADE,
    CONSTRAINT fk_exam_versions_creator
        FOREIGN KEY (version_created_by_user_id) REFERENCES users (user_id),
    CONSTRAINT fk_exam_versions_reviewer
        FOREIGN KEY (reviewed_by_user_id) REFERENCES users (user_id),
    CONSTRAINT chk_exam_versions_version
        CHECK (version_no > 0),
    CONSTRAINT chk_exam_versions_duration
        CHECK (duration_minutes > 0),
    CONSTRAINT chk_exam_versions_total_score
        CHECK (total_score = 100.00),
    CONSTRAINT chk_exam_versions_status
        CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED'))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS exam_version_questions (
    exam_id INT NOT NULL,
    exam_version_no INT NOT NULL,
    order_number INT NOT NULL,
    question_id INT NOT NULL,
    question_version_no INT NOT NULL,
    score DECIMAL(7,2) NOT NULL,
    PRIMARY KEY (exam_id, exam_version_no, order_number),
    CONSTRAINT uq_exam_version_questions_question UNIQUE
        (exam_id, exam_version_no, question_id),
    KEY idx_exam_version_questions_question_version
        (question_id, question_version_no),
    CONSTRAINT fk_exam_version_questions_exam_version
        FOREIGN KEY (exam_id, exam_version_no)
        REFERENCES exam_versions (exam_id, version_no) ON DELETE CASCADE,
    CONSTRAINT fk_exam_version_questions_question_version
        FOREIGN KEY (question_id, question_version_no)
        REFERENCES question_versions (question_id, version_no),
    CONSTRAINT chk_exam_version_questions_order
        CHECK (order_number > 0),
    CONSTRAINT chk_exam_version_questions_question_version
        CHECK (question_version_no > 0),
    CONSTRAINT chk_exam_version_questions_score
        CHECK (score > 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS student_profiles (
    user_id INT NOT NULL,
    identity_number_hash VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_student_profiles_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS student_courses (
    student_user_id INT NOT NULL,
    course_id INT NOT NULL,
    enrolled_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (student_user_id, course_id),
    KEY idx_student_courses_course_id (course_id),
    CONSTRAINT fk_student_courses_student
        FOREIGN KEY (student_user_id) REFERENCES users (user_id),
    CONSTRAINT fk_student_courses_course
        FOREIGN KEY (course_id) REFERENCES courses (course_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS exam_executions (
    execution_id INT NOT NULL AUTO_INCREMENT,
    execution_code CHAR(4) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    exam_id INT NOT NULL,
    exam_version_no INT NOT NULL,
    opening_time DATETIME NOT NULL,
    closing_time DATETIME NOT NULL,
    duration_minutes INT NOT NULL,
    cumulative_extension_minutes INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    created_by_user_id INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    closed_at DATETIME NULL,
    average_score DECIMAL(7,2) NULL,
    median_score DECIMAL(7,2) NULL,
    started_count INT NOT NULL DEFAULT 0,
    submitted_count INT NOT NULL DEFAULT 0,
    auto_submitted_count INT NOT NULL DEFAULT 0,
    PRIMARY KEY (execution_id),
    CONSTRAINT uq_exam_executions_code UNIQUE (execution_code),
    KEY idx_exam_executions_exam_version (exam_id, exam_version_no),
    KEY idx_exam_executions_status_window (status, opening_time, closing_time),
    KEY idx_exam_executions_creator (created_by_user_id),
    CONSTRAINT fk_exam_executions_exam_version
        FOREIGN KEY (exam_id, exam_version_no)
        REFERENCES exam_versions (exam_id, version_no),
    CONSTRAINT fk_exam_executions_creator
        FOREIGN KEY (created_by_user_id) REFERENCES users (user_id),
    CONSTRAINT chk_exam_executions_code
        CHECK (execution_code REGEXP '^[A-Z0-9]{4}$'),
    CONSTRAINT chk_exam_executions_window
        CHECK (opening_time < closing_time),
    CONSTRAINT chk_exam_executions_duration
        CHECK (duration_minutes > 0),
    CONSTRAINT chk_exam_executions_extension
        CHECK (cumulative_extension_minutes >= 0
            AND cumulative_extension_minutes < duration_minutes),
    CONSTRAINT chk_exam_executions_status
        CHECK (status IN ('SCHEDULED', 'OPEN', 'CLOSED')),
    CONSTRAINT chk_exam_executions_counts
        CHECK (started_count >= 0
            AND submitted_count >= 0
            AND auto_submitted_count >= 0),
    CONSTRAINT chk_exam_executions_average_score
        CHECK (average_score IS NULL OR average_score BETWEEN 0 AND 100),
    CONSTRAINT chk_exam_executions_median_score
        CHECK (median_score IS NULL OR median_score BETWEEN 0 AND 100)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS exam_submissions (
    submission_id INT NOT NULL AUTO_INCREMENT,
    execution_id INT NOT NULL,
    student_user_id INT NOT NULL,
    started_at DATETIME NOT NULL,
    submitted_at DATETIME NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    status VARCHAR(32) NOT NULL,
    allocated_duration_minutes INT NOT NULL,
    extra_minutes INT NOT NULL DEFAULT 0,
    extension_reason TEXT NULL,
    actual_duration_minutes INT NULL,
    automatic_score DECIMAL(7,2) NULL,
    final_score DECIMAL(7,2) NULL,
    teacher_feedback TEXT NULL,
    manual_change_reason TEXT NULL,
    reviewed_by_user_id INT NULL,
    reviewed_at DATETIME NULL,
    published_by_user_id INT NULL,
    published_at DATETIME NULL,
    PRIMARY KEY (submission_id),
    CONSTRAINT uq_exam_submissions_execution_student
        UNIQUE (execution_id, student_user_id),
    KEY idx_exam_submissions_student_status (student_user_id, status),
    KEY idx_exam_submissions_execution_status (execution_id, status),
    CONSTRAINT fk_exam_submissions_execution
        FOREIGN KEY (execution_id) REFERENCES exam_executions (execution_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_exam_submissions_student
        FOREIGN KEY (student_user_id) REFERENCES users (user_id),
    CONSTRAINT fk_exam_submissions_reviewer
        FOREIGN KEY (reviewed_by_user_id) REFERENCES users (user_id),
    CONSTRAINT fk_exam_submissions_publisher
        FOREIGN KEY (published_by_user_id) REFERENCES users (user_id),
    CONSTRAINT chk_exam_submissions_status
        CHECK (status IN ('IN_PROGRESS', 'SUBMITTED', 'AUTO_SUBMITTED', 'PUBLISHED')),
    CONSTRAINT chk_exam_submissions_allocated_duration
        CHECK (allocated_duration_minutes > 0),
    CONSTRAINT chk_exam_submissions_extra_minutes
        CHECK (extra_minutes >= 0),
    CONSTRAINT chk_exam_submissions_actual_duration
        CHECK (actual_duration_minutes IS NULL OR actual_duration_minutes >= 0),
    CONSTRAINT chk_exam_submissions_automatic_score
        CHECK (automatic_score IS NULL OR automatic_score BETWEEN 0 AND 100),
    CONSTRAINT chk_exam_submissions_final_score
        CHECK (final_score IS NULL OR final_score BETWEEN 0 AND 100)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS student_answers (
    answer_id INT NOT NULL AUTO_INCREMENT,
    submission_id INT NOT NULL,
    question_id INT NOT NULL,
    question_version_no INT NOT NULL,
    selected_option_number INT NOT NULL,
    answer_content TEXT NULL,
    is_correct BOOLEAN NULL,
    score_received DECIMAL(7,2) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (answer_id),
    CONSTRAINT uq_student_answers_submission_question
        UNIQUE (submission_id, question_id),
    KEY idx_student_answers_question_version (question_id, question_version_no),
    CONSTRAINT fk_student_answers_submission
        FOREIGN KEY (submission_id) REFERENCES exam_submissions (submission_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_student_answers_question_version
        FOREIGN KEY (question_id, question_version_no)
        REFERENCES question_versions (question_id, version_no),
    CONSTRAINT chk_student_answers_selected_option
        CHECK (selected_option_number BETWEEN 1 AND 4),
    CONSTRAINT chk_student_answers_score
        CHECK (score_received IS NULL OR score_received >= 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS submission_time_extensions (
    extension_id INT NOT NULL AUTO_INCREMENT,
    submission_id INT NOT NULL,
    added_minutes INT NOT NULL,
    reason TEXT NOT NULL,
    extended_by_user_id INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (extension_id),
    CONSTRAINT fk_submission_time_extensions_submission
        FOREIGN KEY (submission_id) REFERENCES exam_submissions (submission_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_submission_time_extensions_user
        FOREIGN KEY (extended_by_user_id) REFERENCES users (user_id),
    CONSTRAINT chk_submission_time_extensions_minutes
        CHECK (added_minutes > 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS execution_time_extensions (
    extension_id INT NOT NULL AUTO_INCREMENT,
    execution_id INT NOT NULL,
    added_minutes INT NOT NULL,
    reason TEXT NOT NULL,
    extended_by_user_id INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (extension_id),
    KEY idx_execution_time_extensions_execution_created
        (execution_id, created_at),
    CONSTRAINT fk_execution_time_extensions_execution
        FOREIGN KEY (execution_id) REFERENCES exam_executions (execution_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_execution_time_extensions_user
        FOREIGN KEY (extended_by_user_id) REFERENCES users (user_id),
    CONSTRAINT chk_execution_time_extensions_minutes
        CHECK (added_minutes > 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS notifications (
    notification_id INT NOT NULL AUTO_INCREMENT,
    recipient_user_id INT NOT NULL,
    notification_type VARCHAR(32) NOT NULL,
    title VARCHAR(160) NOT NULL,
    message TEXT NOT NULL,
    related_exam_id INT NULL,
    related_execution_id INT NULL,
    related_submission_id INT NULL,
    deduplication_key VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    read_at DATETIME(6) NULL,
    PRIMARY KEY (notification_id),
    CONSTRAINT uq_notifications_deduplication UNIQUE (deduplication_key),
    KEY idx_notifications_recipient_created (recipient_user_id, created_at),
    KEY idx_notifications_recipient_read (recipient_user_id, read_at),
    CONSTRAINT fk_notifications_recipient
        FOREIGN KEY (recipient_user_id) REFERENCES users (user_id),
    CONSTRAINT chk_notifications_type CHECK (notification_type IN
        ('EXAM_APPROVED', 'EXAM_REJECTED', 'EXAM_SCHEDULED',
         'EXECUTION_EXTENDED', 'GRADE_PUBLISHED',
         'EXAM_SUBMITTED')),
    CONSTRAINT chk_notifications_read_time
        CHECK (read_at IS NULL OR read_at >= created_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS exam_execution_deciles (
    execution_id INT NOT NULL,
    decile_number INT NOT NULL,
    submission_count INT NOT NULL DEFAULT 0,
    PRIMARY KEY (execution_id, decile_number),
    CONSTRAINT fk_exam_execution_deciles_execution
        FOREIGN KEY (execution_id) REFERENCES exam_executions (execution_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_exam_execution_deciles_number
        CHECK (decile_number BETWEEN 1 AND 10),
    CONSTRAINT chk_exam_execution_deciles_count
        CHECK (submission_count >= 0)
) ENGINE=InnoDB;

CREATE TEMPORARY TABLE IF NOT EXISTS question_bank_migration_guard (
    validation_result TINYINT NOT NULL
) ENGINE=InnoDB;

START TRANSACTION;

INSERT IGNORE INTO users (
    user_id,
    full_name,
    email,
    password_hash,
    role,
    status
) VALUES
(1001, 'Development Student', 'student@hsts.local', 'pbkdf2-sha256$210000$s1m+ODv+3/GRzPDxDWDQIQ==$UfEw3AfJ9R+QhlHzQZizsg+AkTubQtLzfpWhIVGxAxw=', 'STUDENT', 'ACTIVE'),
(1002, 'Development Teacher', 'teacher@hsts.local', 'pbkdf2-sha256$210000$GYtRfi/rORWe6PUx0pZRHQ==$aNGS1rjaQfcQ7QiCVmcJlqh1FuBsStSykbPeq8NXE98=', 'TEACHER', 'ACTIVE'),
(1003, 'Development Coordinator', 'coordinator@hsts.local', 'pbkdf2-sha256$210000$z2td5VTlfecXVbzKsSNoNg==$6IRrphVP1zQ7OPFzc75VefR6BoE5DuS+7sAkuketFts=', 'COORDINATOR', 'ACTIVE'),
(1004, 'Development Principal', 'principal@hsts.local', 'pbkdf2-sha256$210000$A9ZKGvD4BYtl7WgvJLOp7Q==$AN9mwp3khV/y69zCc8C539V4DVrtqEcdJ3TlolRQW10=', 'PRINCIPAL', 'ACTIVE');

-- Subject and course numbers are supplied by the external school
-- administration system (requirement 19). The legacy prototype rows take the
-- reserved high numbers so that the demo data can own 01 upwards.
INSERT IGNORE INTO subjects (subject_id, subject_code, subject_number, name, description)
VALUES (1, 'LEGACY', '99', 'Legacy Prototype', NULL);

INSERT IGNORE INTO courses (
    course_id,
    subject_id,
    course_code,
    course_number,
    name,
    grade_level,
    school_year
)
VALUES (1, 1, 'LEGACY-101', '99', 'Legacy Prototype Course', 'General', '2026');

UPDATE subjects SET subject_number = '99'
WHERE subject_id = 1 AND subject_number IS NULL;

UPDATE courses SET course_number = '99'
WHERE course_id = 1 AND course_number IS NULL;

INSERT INTO question_bank_migration_guard (validation_result)
SELECT CASE WHEN
    (SELECT COUNT(*)
     FROM users
     WHERE user_id = 1002 OR email = 'teacher@hsts.local') = 1
    AND EXISTS (
        SELECT 1
        FROM users
        WHERE user_id = 1002
          AND email = 'teacher@hsts.local'
          AND role = 'TEACHER'
    )
    AND (SELECT COUNT(*)
         FROM users
         WHERE user_id = 1003 OR email = 'coordinator@hsts.local') = 1
    AND EXISTS (
        SELECT 1
        FROM users
        WHERE user_id = 1003
          AND email = 'coordinator@hsts.local'
          AND role = 'COORDINATOR'
    )
    AND (SELECT COUNT(*)
         FROM subjects
         WHERE subject_id = 1 OR subject_code = 'LEGACY') = 1
    AND EXISTS (
        SELECT 1
        FROM subjects
        WHERE subject_id = 1
          AND subject_code = 'LEGACY'
          AND name = 'Legacy Prototype'
    )
    AND (SELECT COUNT(*)
         FROM courses
         WHERE course_id = 1
            OR (subject_id = 1
                AND course_code = 'LEGACY-101'
                AND school_year = '2026')) = 1
    AND EXISTS (
        SELECT 1
        FROM courses
        WHERE course_id = 1
          AND subject_id = 1
          AND course_code = 'LEGACY-101'
          AND name = 'Legacy Prototype Course'
          AND grade_level = 'General'
          AND school_year = '2026'
    )
THEN 1 ELSE NULL END;

INSERT IGNORE INTO teacher_courses (teacher_user_id, course_id)
VALUES (1002, 1), (1003, 1);

INSERT INTO student_courses (student_user_id, course_id)
SELECT student.user_id, course.course_id
FROM users student
JOIN courses course ON course.course_id = 1
WHERE student.user_id = 1001
  AND student.role = 'STUDENT'
  AND student.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1
      FROM student_courses existing_enrollment
      WHERE existing_enrollment.student_user_id = student.user_id
        AND existing_enrollment.course_id = course.course_id
  );

INSERT INTO subject_coordinators (
    subject_id,
    coordinator_user_id
)
SELECT
    subject_record.subject_id,
    coordinator.user_id
FROM subjects subject_record
JOIN users coordinator ON coordinator.user_id = 1003
WHERE subject_record.subject_id = 1
  AND subject_record.subject_code = 'LEGACY'
  AND coordinator.email = 'coordinator@hsts.local'
  AND coordinator.role = 'COORDINATOR'
  AND NOT EXISTS (
      SELECT 1
      FROM subject_coordinators existing_assignment
      WHERE existing_assignment.subject_id = subject_record.subject_id
        AND existing_assignment.coordinator_user_id = coordinator.user_id
  );

INSERT IGNORE INTO questions (
    question_id,
    content,
    topic,
    type,
    difficulty,
    status,
    illustration_path,
    answer_option_1,
    answer_option_2,
    answer_option_3,
    answer_option_4,
    correct_option_number
) VALUES
(1, 'What is 2 + 2?', 'Algebra', 'MULTIPLE_CHOICE', 'EASY', 'ACTIVE', '', '3', '4', '5', '6', 2),
(2, 'Solve: 5x = 20', 'Algebra', 'MULTIPLE_CHOICE', 'EASY', 'ACTIVE', '', 'x = 2', 'x = 4', 'x = 5', 'x = 20', 2),
(3, 'What is the derivative of x^2?', 'Calculus', 'MULTIPLE_CHOICE', 'MEDIUM', 'ACTIVE', '', 'x', '2x', 'x^2', '2', 2),
(4, 'What is the capital of France?', 'General', 'MULTIPLE_CHOICE', 'EASY', 'ACTIVE', '', 'Rome', 'Paris', 'Madrid', 'Berlin', 2),
(5, 'Which OOP concept allows the same method name to behave differently?', 'Programming', 'MULTIPLE_CHOICE', 'MEDIUM', 'ACTIVE', '', 'Encapsulation', 'Inheritance', 'Polymorphism', 'Compilation', 3),
(6, 'What is a primary key in a database?', 'Databases', 'MULTIPLE_CHOICE', 'EASY', 'ACTIVE', '', 'A unique identifier', 'A duplicated field', 'A table name', 'A query result', 1);

UPDATE questions SET course_id = 1 WHERE course_id IS NULL;
UPDATE questions SET created_by_user_id = 1002 WHERE created_by_user_id IS NULL;
UPDATE questions SET current_version_no = 1 WHERE current_version_no IS NULL;
UPDATE questions SET created_at = CURRENT_TIMESTAMP(6) WHERE created_at IS NULL;
UPDATE questions SET updated_at = CURRENT_TIMESTAMP(6) WHERE updated_at IS NULL;

INSERT INTO question_versions (
    question_id,
    version_no,
    content,
    topic,
    question_type,
    difficulty,
    illustration_path,
    correct_option_number,
    created_by_user_id,
    created_at
)
SELECT
    q.question_id,
    1,
    q.content,
    COALESCE(NULLIF(q.topic, ''), 'General'),
    COALESCE(NULLIF(q.type, ''), 'MULTIPLE_CHOICE'),
    CASE
        WHEN q.difficulty IN ('EASY', 'MEDIUM', 'HARD') THEN q.difficulty
        ELSE 'EASY'
    END,
    q.illustration_path,
    CASE
        WHEN q.correct_option_number BETWEEN 1 AND 4 THEN q.correct_option_number
        ELSE 1
    END,
    COALESCE(q.created_by_user_id, 1002),
    COALESCE(q.created_at, CURRENT_TIMESTAMP(6))
FROM questions q
WHERE NOT EXISTS (
    SELECT 1
    FROM question_versions existing_version
    WHERE existing_version.question_id = q.question_id
      AND existing_version.version_no = 1
);

INSERT INTO answer_options (
    question_id,
    version_no,
    option_number,
    option_text
)
SELECT
    q.question_id,
    1,
    option_numbers.option_number,
    CASE option_numbers.option_number
        WHEN 1 THEN COALESCE(q.answer_option_1, '')
        WHEN 2 THEN COALESCE(q.answer_option_2, '')
        WHEN 3 THEN COALESCE(q.answer_option_3, '')
        WHEN 4 THEN COALESCE(q.answer_option_4, '')
    END
FROM questions q
CROSS JOIN (
    SELECT 1 AS option_number
    UNION ALL SELECT 2
    UNION ALL SELECT 3
    UNION ALL SELECT 4
) option_numbers
WHERE NOT EXISTS (
    SELECT 1
    FROM answer_options existing_option
    WHERE existing_option.question_id = q.question_id
      AND existing_option.version_no = 1
      AND existing_option.option_number = option_numbers.option_number
);

COMMIT;
