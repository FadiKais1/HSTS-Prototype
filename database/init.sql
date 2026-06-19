CREATE TABLE IF NOT EXISTS questions (
    question_id INTEGER PRIMARY KEY,
    content TEXT NOT NULL,
    topic TEXT,
    type TEXT,
    difficulty TEXT,
    status TEXT,
    illustration_path TEXT,
    answer_option_1 TEXT,
    answer_option_2 TEXT,
    answer_option_3 TEXT,
    answer_option_4 TEXT,
    correct_option_number INTEGER
);


INSERT INTO questions (question_id, content, topic, type, difficulty, status, illustration_path,
    answer_option_1, answer_option_2, answer_option_3, answer_option_4, correct_option_number) VALUES
(1, 'What is 2 + 2?', 'Algebra', 'MULTIPLE_CHOICE', 'EASY', 'ACTIVE', '', '3', '4', '5', '6', 2),
(2, 'Solve: 5x = 20', 'Algebra', 'MULTIPLE_CHOICE', 'EASY', 'ACTIVE', '', 'x = 2', 'x = 4', 'x = 5', 'x = 20', 2),
(3, 'What is the derivative of x^2?', 'Calculus', 'MULTIPLE_CHOICE', 'MEDIUM', 'ACTIVE', '', 'x', '2x', 'x^2', '2', 2),
(4, 'What is the capital of France?', 'General', 'MULTIPLE_CHOICE', 'EASY', 'ACTIVE', '', 'Rome', 'Paris', 'Madrid', 'Berlin', 2),
(5, 'Which OOP concept allows the same method name to behave differently?', 'Programming', 'MULTIPLE_CHOICE', 'MEDIUM', 'ACTIVE', '', 'Encapsulation', 'Inheritance', 'Polymorphism', 'Compilation', 3),
(6, 'What is a primary key in a database?', 'Databases', 'MULTIPLE_CHOICE', 'EASY', 'ACTIVE', '', 'A unique identifier', 'A duplicated field', 'A table name', 'A query result', 1);
