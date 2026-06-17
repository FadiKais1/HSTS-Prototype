CREATE TABLE IF NOT EXISTS questions (
    question_id INTEGER PRIMARY KEY,
    content TEXT NOT NULL,
    topic TEXT,
    type TEXT,
    difficulty TEXT,
    status TEXT
);

DELETE FROM questions;

INSERT INTO questions (question_id, content, topic, type, difficulty, status) VALUES
(1, 'What is 2 + 2?', 'Algebra', 'MULTIPLE_CHOICE', 'EASY', 'ACTIVE'),
(2, 'Solve: 5x = 20', 'Algebra', 'OPEN', 'EASY', 'ACTIVE'),
(3, 'What is the derivative of x^2?', 'Calculus', 'OPEN', 'MEDIUM', 'ACTIVE'),
(4, 'What is the capital of France?', 'General', 'MULTIPLE_CHOICE', 'EASY', 'ACTIVE'),
(5, 'Explain polymorphism in OOP.', 'Programming', 'OPEN', 'MEDIUM', 'ACTIVE'),
(6, 'What is a primary key in a database?', 'Databases', 'OPEN', 'EASY', 'ACTIVE');
