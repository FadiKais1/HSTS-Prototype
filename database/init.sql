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
    name VARCHAR(100) NOT NULL,
    description TEXT NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS courses (
    course_id INT AUTO_INCREMENT PRIMARY KEY,
    subject_id INT NOT NULL,
    course_code VARCHAR(30) NOT NULL,
    name VARCHAR(100) NOT NULL,
    grade_level VARCHAR(30) NOT NULL,
    school_year VARCHAR(20) NOT NULL,
    CONSTRAINT uq_courses_subject_code_year
        UNIQUE (subject_id, course_code, school_year),
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
    KEY idx_questions_course_id (course_id),
    KEY idx_questions_created_by_user_id (created_by_user_id),
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

INSERT IGNORE INTO subjects (subject_id, subject_code, name, description)
VALUES (1, 'LEGACY', 'Legacy Prototype', NULL);

INSERT IGNORE INTO courses (
    course_id,
    subject_id,
    course_code,
    name,
    grade_level,
    school_year
)
VALUES (1, 1, 'LEGACY-101', 'Legacy Prototype Course', 'General', '2026');

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
