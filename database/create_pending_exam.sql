USE hsts_prototype;

-- A second version of Algebra Midterm, awaiting coordinator approval.
INSERT INTO exam_versions (
    exam_id, version_no, title, duration_minutes, cumulative_extension_minutes,
    teacher_notes, student_instructions, total_score, status,
    version_created_by_user_id, created_at, submitted_at,
    reviewed_by_user_id, reviewed_at, rejection_reason
) VALUES (
    1, 2, 'Algebra Midterm (Revised)', 60, 0,
    'Revised after feedback: question 3 reworded.',
    'Answer all questions. Each question has exactly one correct answer.',
    100.00, 'PENDING_APPROVAL', 1002, NOW(), NOW(), NULL, NULL, NULL
);

-- Reuse the same questions as version 1.
INSERT INTO exam_version_questions (
    exam_id, exam_version_no, order_number, question_id, question_version_no, score
)
SELECT exam_id, 2, order_number, question_id, question_version_no, score
FROM exam_version_questions
WHERE exam_id = 1 AND exam_version_no = 1;

UPDATE exams SET current_version_no = 2 WHERE exam_id = 1;

SELECT exam_id, version_no, title, status, version_created_by_user_id
FROM exam_versions WHERE exam_id = 1;
