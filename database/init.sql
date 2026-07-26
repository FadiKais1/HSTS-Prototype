CREATE DATABASE IF NOT EXISTS hsts_prototype;

USE hsts_prototype;

CREATE TABLE IF NOT EXISTS users (
    user_id INT PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(254) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT chk_users_role CHECK (role IN ('STUDENT', 'TEACHER', 'COORDINATOR', 'PRINCIPAL')),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'BLOCKED'))
);

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

CREATE TABLE IF NOT EXISTS questions (
    question_id INT PRIMARY KEY,
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
    correct_option_number INT
);

INSERT INTO questions (
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
(6, 'What is a primary key in a database?', 'Databases', 'MULTIPLE_CHOICE', 'EASY', 'ACTIVE', '', 'A unique identifier', 'A duplicated field', 'A table name', 'A query result', 1)
ON DUPLICATE KEY UPDATE
    content = VALUES(content),
    topic = VALUES(topic),
    type = VALUES(type),
    difficulty = VALUES(difficulty),
    status = VALUES(status),
    illustration_path = VALUES(illustration_path),
    answer_option_1 = VALUES(answer_option_1),
    answer_option_2 = VALUES(answer_option_2),
    answer_option_3 = VALUES(answer_option_3),
    answer_option_4 = VALUES(answer_option_4),
    correct_option_number = VALUES(correct_option_number);
