package hsts.server.repository;

import hsts.common.ExamAttemptDTO;
import hsts.common.ExecutionSubmissionSummaryDTO;
import hsts.common.PublishedExamQuestionReviewDTO;
import hsts.common.PublishedExamReviewDTO;
import hsts.common.PublishedGradeDTO;
import hsts.common.PublishedGradeSummaryDTO;
import hsts.common.StudentAnswerDTO;
import hsts.common.StudentExamQuestionDTO;
import hsts.common.SubmissionAnswerReviewDTO;
import hsts.common.SubmissionReviewDTO;
import hsts.common.type.ExecutionStatus;
import hsts.common.type.PublishedAnswerOutcome;
import hsts.common.type.SubmissionStatus;
import hsts.server.entity.ExamExecution;
import hsts.server.entity.ExamSubmission;
import hsts.server.entity.StudentAnswer;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class ExamSubmissionRepository {
    private static final int MYSQL_DUPLICATE_KEY_ERROR = 1062;

    private static final String LOCK_AVAILABLE_EXECUTION_SQL = """
            SELECT execution.execution_id,
                   execution.execution_code,
                   execution.exam_id,
                   execution.exam_version_no,
                   execution.opening_time,
                   execution.closing_time,
                   execution.duration_minutes,
                   execution.status,
                   version.title AS exam_title,
                   version.student_instructions
            FROM exam_executions execution
            JOIN exam_versions version
              ON version.exam_id = execution.exam_id
             AND version.version_no = execution.exam_version_no
            JOIN exams exam ON exam.exam_id = execution.exam_id
            JOIN users student
              ON student.user_id = ?
             AND student.role = 'STUDENT'
             AND student.status = 'ACTIVE'
            JOIN student_courses enrollment
              ON enrollment.student_user_id = student.user_id
             AND enrollment.course_id = exam.course_id
            WHERE execution.execution_id = ?
            FOR UPDATE
            """;

    private static final String LOCK_EXECUTION_SUBMISSION_SQL = """
            SELECT submission.submission_id,
                   submission.student_user_id,
                   submission.started_at,
                   submission.status,
                   submission.allocated_duration_minutes,
                   submission.extra_minutes
            FROM exam_submissions submission
            WHERE submission.execution_id = ?
              AND submission.student_user_id = ?
            FOR UPDATE
            """;

    private static final String IN_PROGRESS_FOR_STUDENT_AND_COURSE_SQL = """
            SELECT EXISTS (
                SELECT 1
                FROM exam_submissions submission
                JOIN exam_executions execution
                  ON execution.execution_id = submission.execution_id
                JOIN exams exam ON exam.exam_id = execution.exam_id
                WHERE submission.student_user_id = ?
                  AND exam.course_id = ?
                  AND submission.status = 'IN_PROGRESS'
            ) AS in_progress
            """;

    private static final String INSERT_SUBMISSION_SQL = """
            INSERT INTO exam_submissions (
                execution_id,
                student_user_id,
                started_at,
                submitted_at,
                status,
                allocated_duration_minutes,
                extra_minutes,
                extension_reason,
                actual_duration_minutes,
                automatic_score,
                final_score,
                teacher_feedback,
                manual_change_reason,
                reviewed_by_user_id,
                reviewed_at,
                published_by_user_id,
                published_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String INCREMENT_STARTED_COUNT_SQL = """
            UPDATE exam_executions
            SET started_count = started_count + 1
            WHERE execution_id = ?
            """;

    private static final String ACTIVE_ATTEMPT_SQL = """
            SELECT submission.submission_id,
                   submission.student_user_id,
                   submission.started_at,
                   submission.status,
                   submission.allocated_duration_minutes,
                   submission.extra_minutes,
                   execution.execution_id,
                   execution.execution_code,
                   execution.exam_id,
                   execution.exam_version_no,
                   execution.opening_time,
                   execution.closing_time,
                   execution.duration_minutes,
                   execution.status AS execution_status,
                   version.title AS exam_title,
                   version.student_instructions
            FROM exam_submissions submission
            JOIN exam_executions execution
              ON execution.execution_id = submission.execution_id
            JOIN exam_versions version
              ON version.exam_id = execution.exam_id
             AND version.version_no = execution.exam_version_no
            JOIN users student
              ON student.user_id = submission.student_user_id
             AND student.role = 'STUDENT'
             AND student.status = 'ACTIVE'
            WHERE submission.submission_id = ?
              AND submission.student_user_id = ?
              AND submission.status = 'IN_PROGRESS'
            """;

    private static final String LOCK_STUDENT_SUBMISSION_SQL = """
            SELECT submission.submission_id,
                   submission.student_user_id,
                   submission.started_at,
                   submission.status,
                   submission.allocated_duration_minutes,
                   submission.extra_minutes,
                   execution.execution_id,
                   execution.execution_code,
                   execution.exam_id,
                   execution.exam_version_no,
                   execution.opening_time,
                   execution.closing_time,
                   execution.duration_minutes,
                   execution.status AS execution_status,
                   version.title AS exam_title,
                   version.student_instructions
            FROM exam_submissions submission
            JOIN exam_executions execution
              ON execution.execution_id = submission.execution_id
            JOIN exam_versions version
              ON version.exam_id = execution.exam_id
             AND version.version_no = execution.exam_version_no
            JOIN users student
              ON student.user_id = submission.student_user_id
             AND student.role = 'STUDENT'
             AND student.status = 'ACTIVE'
            WHERE submission.submission_id = ?
              AND submission.student_user_id = ?
            FOR UPDATE
            """;

    private static final String LOCK_INTERNAL_SUBMISSION_SQL = """
            SELECT submission.submission_id,
                   submission.student_user_id,
                   submission.started_at,
                   submission.status,
                   submission.allocated_duration_minutes,
                   submission.extra_minutes,
                   execution.execution_id,
                   execution.execution_code,
                   execution.exam_id,
                   execution.exam_version_no,
                   execution.opening_time,
                   execution.closing_time,
                   execution.duration_minutes,
                   execution.status AS execution_status,
                   version.title AS exam_title,
                   version.student_instructions
            FROM exam_submissions submission
            JOIN exam_executions execution
              ON execution.execution_id = submission.execution_id
            JOIN exam_versions version
              ON version.exam_id = execution.exam_id
             AND version.version_no = execution.exam_version_no
            WHERE submission.submission_id = ?
            FOR UPDATE
            """;

    private static final String SAFE_QUESTIONS_SQL = """
            SELECT selection.question_id,
                   selection.question_version_no,
                   selection.order_number,
                   selection.score,
                   version.content,
                   version.topic,
                   version.difficulty,
                   version.illustration_path,
                   qvi.media_type AS illustration_media_type,
                   qvi.content_bytes AS illustration_content_bytes,
                   qvi.byte_length AS illustration_byte_length,
                   qvi.width_pixels AS illustration_width_pixels,
                   qvi.height_pixels AS illustration_height_pixels,
                   qvi.content_sha256 AS illustration_content_sha256,
                   qvi.created_at AS illustration_created_at,
                   option_1.option_text AS answer_option_1,
                   option_2.option_text AS answer_option_2,
                   option_3.option_text AS answer_option_3,
                   option_4.option_text AS answer_option_4
            FROM exam_version_questions selection
            JOIN question_versions version
              ON version.question_id = selection.question_id
             AND version.version_no = selection.question_version_no
            LEFT JOIN question_version_illustrations qvi
              ON qvi.question_id = version.question_id
             AND qvi.version_no = version.version_no
            JOIN answer_options option_1
              ON option_1.question_id = version.question_id
             AND option_1.version_no = version.version_no
             AND option_1.option_number = 1
            JOIN answer_options option_2
              ON option_2.question_id = version.question_id
             AND option_2.version_no = version.version_no
             AND option_2.option_number = 2
            JOIN answer_options option_3
              ON option_3.question_id = version.question_id
             AND option_3.version_no = version.version_no
             AND option_3.option_number = 3
            JOIN answer_options option_4
              ON option_4.question_id = version.question_id
             AND option_4.version_no = version.version_no
             AND option_4.option_number = 4
            WHERE selection.exam_id = ?
              AND selection.exam_version_no = ?
            ORDER BY selection.order_number ASC
            """;

    private static final String SAFE_ANSWERS_SQL = """
            SELECT answer.question_id,
                   answer.selected_option_number,
                   answer.updated_at
            FROM student_answers answer
            JOIN exam_submissions submission
              ON submission.submission_id = answer.submission_id
            WHERE answer.submission_id = ?
              AND submission.student_user_id = ?
            ORDER BY answer.question_id ASC
            """;

    private static final String LOCK_EXAM_QUESTION_SQL = """
            SELECT selection.question_version_no
            FROM exam_version_questions selection
            WHERE selection.exam_id = ?
              AND selection.exam_version_no = ?
              AND selection.question_id = ?
            FOR UPDATE
            """;

    private static final String UPSERT_ANSWER_SQL = """
            INSERT INTO student_answers (
                submission_id,
                question_id,
                question_version_no,
                selected_option_number,
                answer_content,
                is_correct,
                score_received,
                created_at,
                updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                question_version_no = VALUES(question_version_no),
                selected_option_number = VALUES(selected_option_number),
                answer_content = NULL,
                is_correct = NULL,
                score_received = NULL,
                updated_at = VALUES(updated_at)
            """;

    private static final String INCREMENT_SUBMITTED_COUNT_SQL = """
            UPDATE exam_executions
            SET submitted_count = submitted_count + 1
            WHERE execution_id = ?
            """;

    private static final String INCREMENT_AUTO_SUBMITTED_COUNT_SQL = """
            UPDATE exam_executions
            SET auto_submitted_count = auto_submitted_count + 1
            WHERE execution_id = ?
            """;

    private static final String LOCK_MANAGER_EXTENSION_SQL = """
            SELECT submission.submission_id,
                   submission.started_at,
                   submission.status,
                   submission.allocated_duration_minutes,
                   submission.extra_minutes
            FROM exam_submissions submission
            JOIN exam_executions execution
              ON execution.execution_id = submission.execution_id
            JOIN exams exam ON exam.exam_id = execution.exam_id
            JOIN users manager
              ON manager.user_id = ?
             AND manager.role IN ('TEACHER', 'COORDINATOR')
             AND manager.status = 'ACTIVE'
            JOIN teacher_courses assignment
              ON assignment.teacher_user_id = manager.user_id
             AND assignment.course_id = exam.course_id
            WHERE submission.submission_id = ?
            FOR UPDATE
            """;

    private static final String INSERT_EXTENSION_AUDIT_SQL = """
            INSERT INTO submission_time_extensions (
                submission_id,
                added_minutes,
                reason,
                extended_by_user_id,
                created_at
            )
            VALUES (?, ?, ?, ?, ?)
            """;

    private static final String SUBMISSION_ENTITY_SELECT = """
            SELECT submission.submission_id,
                   submission.execution_id,
                   execution.exam_id,
                   execution.exam_version_no,
                   submission.student_user_id,
                   submission.started_at,
                   submission.submitted_at,
                   submission.status,
                   submission.allocated_duration_minutes,
                   submission.extra_minutes,
                   submission.extension_reason,
                   submission.actual_duration_minutes,
                   submission.automatic_score,
                   submission.final_score,
                   submission.teacher_feedback,
                   submission.manual_change_reason,
                   submission.reviewed_by_user_id,
                   submission.reviewed_at,
                   submission.published_by_user_id,
                   submission.published_at,
                   submission.created_at,
                   submission.updated_at
            FROM exam_submissions submission
            JOIN exam_executions execution
              ON execution.execution_id = submission.execution_id
            """;

    private static final String STUDENT_SUBMISSION_ENTITY_SQL =
            SUBMISSION_ENTITY_SELECT + """
            JOIN users student
              ON student.user_id = ?
             AND student.role = 'STUDENT'
             AND student.status = 'ACTIVE'
            WHERE submission.submission_id = ?
              AND submission.student_user_id = student.user_id
            """;

    private static final String ACTIVE_STUDENT_SUBMISSION_ENTITY_SQL =
            STUDENT_SUBMISSION_ENTITY_SQL + """
              AND submission.status = 'IN_PROGRESS'
            """;

    private static final String MANAGER_SUBMISSION_ENTITY_SQL =
            SUBMISSION_ENTITY_SELECT + """
            JOIN exams exam ON exam.exam_id = execution.exam_id
            JOIN users manager
              ON manager.user_id = ?
             AND manager.role IN ('TEACHER', 'COORDINATOR')
             AND manager.status = 'ACTIVE'
            JOIN teacher_courses assignment
              ON assignment.teacher_user_id = manager.user_id
             AND assignment.course_id = exam.course_id
            WHERE submission.submission_id = ?
            """;

    private static final String MANAGER_RESULT_SCOPE_SQL = """
            JOIN exams exam ON exam.exam_id = execution.exam_id
            JOIN courses course ON course.course_id = exam.course_id
            JOIN users manager
              ON manager.user_id = ?
             AND manager.status = 'ACTIVE'
            WHERE submission.submission_id = ?
              AND (
                    (manager.role = 'TEACHER' AND EXISTS (
                        SELECT 1
                        FROM teacher_courses teacher_assignment
                        WHERE teacher_assignment.teacher_user_id = manager.user_id
                          AND teacher_assignment.course_id = exam.course_id
                    ))
                 OR (manager.role = 'COORDINATOR' AND EXISTS (
                        SELECT 1
                        FROM subject_coordinators coordinator_assignment
                        WHERE coordinator_assignment.coordinator_user_id = manager.user_id
                          AND coordinator_assignment.subject_id = course.subject_id
                    ))
              )
            """;

    private static final String MANAGER_RESULT_ENTITY_SQL =
            SUBMISSION_ENTITY_SELECT + MANAGER_RESULT_SCOPE_SQL;

    private static final String LOCK_MANAGER_RESULT_ENTITY_SQL =
            MANAGER_RESULT_ENTITY_SQL + """
            FOR UPDATE
            """;

    private static final String INTERNAL_SUBMISSION_ENTITY_SQL =
            SUBMISSION_ENTITY_SELECT + """
            WHERE submission.submission_id = ?
            """;

    private static final String EXPIRED_SUBMISSION_ENTITIES_SQL =
            SUBMISSION_ENTITY_SELECT + """
            WHERE submission.status = 'IN_PROGRESS'
              AND TIMESTAMPADD(
                    MINUTE,
                    submission.allocated_duration_minutes + submission.extra_minutes,
                    submission.started_at
                  ) <= ?
            ORDER BY TIMESTAMPADD(
                         MINUTE,
                         submission.allocated_duration_minutes
                             + submission.extra_minutes,
                         submission.started_at
                     ) ASC,
                     submission.submission_id ASC
            """;

    private static final String SUBMISSION_ANSWERS_ENTITY_SQL = """
            SELECT answer.answer_id,
                   answer.submission_id,
                   answer.question_id,
                   answer.question_version_no,
                   answer.selected_option_number,
                   answer.answer_content,
                   answer.is_correct,
                   answer.score_received,
                   answer.created_at,
                   answer.updated_at,
                   selection.order_number
            FROM student_answers answer
            JOIN exam_submissions submission
              ON submission.submission_id = answer.submission_id
            JOIN exam_executions execution
              ON execution.execution_id = submission.execution_id
            JOIN exam_version_questions selection
              ON selection.exam_id = execution.exam_id
             AND selection.exam_version_no = execution.exam_version_no
             AND selection.question_id = answer.question_id
             AND selection.question_version_no = answer.question_version_no
            WHERE answer.submission_id = ?
            ORDER BY selection.order_number ASC,
                     answer.answer_id ASC
            """;

    private static final String UPDATE_ENTITY_GRADED_ANSWER_SQL = """
            UPDATE student_answers
            SET is_correct = ?,
                score_received = ?,
                updated_at = ?
            WHERE answer_id = ?
              AND submission_id = ?
              AND question_id = ?
              AND question_version_no = ?
            """;

    private static final String FINALIZE_ENTITY_SUBMISSION_SQL = """
            UPDATE exam_submissions
            SET submitted_at = ?,
                status = ?,
                actual_duration_minutes = ?,
                automatic_score = ?,
                final_score = ?,
                updated_at = ?
            WHERE submission_id = ?
              AND status = 'IN_PROGRESS'
            """;

    private static final String UPDATE_ENTITY_EXTENSION_SQL = """
            UPDATE exam_submissions
            SET extra_minutes = ?,
                extension_reason = ?,
                updated_at = ?
            WHERE submission_id = ?
              AND status = 'IN_PROGRESS'
              AND extra_minutes = ?
            """;

    private static final String UPDATE_REVIEW_SQL = """
            UPDATE exam_submissions
            SET status = ?,
                final_score = ?,
                teacher_feedback = ?,
                manual_change_reason = ?,
                reviewed_by_user_id = ?,
                reviewed_at = ?,
                updated_at = ?
            WHERE submission_id = ?
              AND status = ?
              AND automatic_score = ?
              AND final_score = ?
              AND reviewed_by_user_id IS NULL
              AND reviewed_at IS NULL
              AND published_by_user_id IS NULL
              AND published_at IS NULL
              AND updated_at = ?
            """;

    private static final String UPDATE_PUBLICATION_SQL = """
            UPDATE exam_submissions
            SET status = ?,
                published_by_user_id = ?,
                published_at = ?,
                updated_at = ?
            WHERE submission_id = ?
              AND status = ?
              AND automatic_score = ?
              AND final_score = ?
              AND reviewed_by_user_id = ?
              AND reviewed_at = ?
              AND teacher_feedback <=> ?
              AND manual_change_reason <=> ?
              AND published_by_user_id IS NULL
              AND published_at IS NULL
              AND updated_at = ?
            """;

    private static final String MANAGER_READ_JOINS_SQL = """
            JOIN exams exam ON exam.exam_id = execution.exam_id
            JOIN courses course ON course.course_id = exam.course_id
            JOIN users manager
              ON manager.user_id = ?
             AND manager.status = 'ACTIVE'
            """;

    private static final String MANAGER_READ_AUTHORIZATION_SQL = """
              AND (
                    (manager.role = 'TEACHER' AND EXISTS (
                        SELECT 1
                        FROM teacher_courses teacher_assignment
                        WHERE teacher_assignment.teacher_user_id = manager.user_id
                          AND teacher_assignment.course_id = exam.course_id
                    ))
                 OR (manager.role = 'COORDINATOR' AND EXISTS (
                        SELECT 1
                        FROM subject_coordinators coordinator_assignment
                        WHERE coordinator_assignment.coordinator_user_id = manager.user_id
                          AND coordinator_assignment.subject_id = course.subject_id
                    ))
              )
            """;

    private static final String MANAGER_SUBMISSION_SUMMARIES_SQL = """
            SELECT submission.submission_id,
                   submission.execution_id,
                   execution.exam_id,
                   execution.exam_version_no,
                   version.title AS exam_title,
                   submission.student_user_id,
                   student.full_name AS student_name,
                   submission.status,
                   submission.automatic_score,
                   submission.final_score,
                   submission.started_at,
                   submission.submitted_at,
                   submission.reviewed_at,
                   submission.published_at
            FROM exam_submissions submission
            JOIN exam_executions execution
              ON execution.execution_id = submission.execution_id
            JOIN exam_versions version
              ON version.exam_id = execution.exam_id
             AND version.version_no = execution.exam_version_no
            JOIN users student
              ON student.user_id = submission.student_user_id
            """ + MANAGER_READ_JOINS_SQL + """
            WHERE execution.execution_id = ?
            """ + MANAGER_READ_AUTHORIZATION_SQL + """
            ORDER BY student.full_name ASC,
                     submission.student_user_id ASC,
                     submission.submission_id ASC
            """;

    private static final String MANAGER_SUBMISSION_REVIEW_SQL = """
            SELECT submission.submission_id,
                   submission.execution_id,
                   execution.exam_id,
                   execution.exam_version_no,
                   version.title AS exam_title,
                   submission.student_user_id,
                   student.full_name AS student_name,
                   submission.status,
                   submission.automatic_score,
                   submission.final_score,
                   submission.teacher_feedback,
                   submission.manual_change_reason,
                   submission.reviewed_by_user_id,
                   submission.started_at,
                   submission.submitted_at,
                   submission.reviewed_at,
                   submission.updated_at,
                   submission.published_by_user_id,
                   submission.published_at
            FROM exam_submissions submission
            JOIN exam_executions execution
              ON execution.execution_id = submission.execution_id
            JOIN exam_versions version
              ON version.exam_id = execution.exam_id
             AND version.version_no = execution.exam_version_no
            JOIN users student
              ON student.user_id = submission.student_user_id
            """ + MANAGER_READ_JOINS_SQL + """
            WHERE submission.submission_id = ?
            """ + MANAGER_READ_AUTHORIZATION_SQL;

    private static final String PRINCIPAL_SUBMISSION_SUMMARIES_SQL = """
            SELECT submission.submission_id, submission.execution_id,
                   execution.exam_id, execution.exam_version_no,
                   version.title AS exam_title, submission.student_user_id,
                   student.full_name AS student_name, submission.status,
                   submission.automatic_score, submission.final_score,
                   submission.started_at, submission.submitted_at,
                   submission.reviewed_at, submission.published_at
            FROM exam_submissions submission
            JOIN exam_executions execution
              ON execution.execution_id = submission.execution_id
            JOIN exam_versions version
              ON version.exam_id = execution.exam_id
             AND version.version_no = execution.exam_version_no
            JOIN users student ON student.user_id = submission.student_user_id
            WHERE execution.execution_id = ?
            ORDER BY student.full_name ASC,
                     submission.student_user_id ASC,
                     submission.submission_id ASC
            """;

    private static final String PRINCIPAL_SUBMISSION_REVIEW_SQL = """
            SELECT submission.submission_id, submission.execution_id,
                   execution.exam_id, execution.exam_version_no,
                   version.title AS exam_title, submission.student_user_id,
                   student.full_name AS student_name, submission.status,
                   submission.automatic_score, submission.final_score,
                   submission.teacher_feedback, submission.manual_change_reason,
                   submission.reviewed_by_user_id, submission.started_at,
                   submission.submitted_at, submission.reviewed_at,
                   submission.updated_at, submission.published_by_user_id,
                   submission.published_at
            FROM exam_submissions submission
            JOIN exam_executions execution
              ON execution.execution_id = submission.execution_id
            JOIN exam_versions version
              ON version.exam_id = execution.exam_id
             AND version.version_no = execution.exam_version_no
            JOIN users student ON student.user_id = submission.student_user_id
            WHERE submission.submission_id = ?
            """;

    private static final String SUBMISSION_ANSWER_REVIEW_SQL = """
            SELECT selection.question_id,
                   selection.question_version_no,
                   selection.order_number,
                   question_version.content AS question_content,
                   qvi.media_type AS illustration_media_type,
                   qvi.content_bytes AS illustration_content_bytes,
                   qvi.byte_length AS illustration_byte_length,
                   qvi.width_pixels AS illustration_width_pixels,
                   qvi.height_pixels AS illustration_height_pixels,
                   qvi.content_sha256 AS illustration_content_sha256,
                   qvi.created_at AS illustration_created_at,
                   answer.answer_id,
                   answer.question_version_no AS answered_question_version_no,
                   selected_option.option_text AS selected_option_text,
                   answer.is_correct,
                   answer.score_received AS awarded_score,
                   selection.score AS maximum_score
            FROM exam_version_questions selection
            JOIN question_versions question_version
              ON question_version.question_id = selection.question_id
             AND question_version.version_no = selection.question_version_no
            LEFT JOIN question_version_illustrations qvi
              ON qvi.question_id = question_version.question_id
             AND qvi.version_no = question_version.version_no
            LEFT JOIN student_answers answer
              ON answer.submission_id = ?
             AND answer.question_id = selection.question_id
            LEFT JOIN answer_options selected_option
              ON selected_option.question_id = selection.question_id
             AND selected_option.version_no = selection.question_version_no
             AND selected_option.option_number = answer.selected_option_number
            WHERE selection.exam_id = ?
              AND selection.exam_version_no = ?
            ORDER BY selection.order_number ASC
            """;

    private static final String STUDENT_PUBLISHED_SUMMARIES_SQL = """
            SELECT submission.submission_id,
                   submission.execution_id,
                   execution.exam_id,
                   execution.exam_version_no,
                   version.title AS exam_title,
                   course.name AS course_name,
                   submission.final_score,
                   submission.submitted_at,
                   submission.published_at
            FROM exam_submissions submission
            JOIN exam_executions execution
              ON execution.execution_id = submission.execution_id
            JOIN exam_versions version
              ON version.exam_id = execution.exam_id
             AND version.version_no = execution.exam_version_no
            JOIN exams exam ON exam.exam_id = execution.exam_id
            JOIN courses course ON course.course_id = exam.course_id
            JOIN users student
              ON student.user_id = ?
             AND student.role = 'STUDENT'
             AND student.status = 'ACTIVE'
            WHERE submission.student_user_id = student.user_id
              AND submission.status = 'PUBLISHED'
              AND submission.final_score IS NOT NULL
              AND submission.published_at IS NOT NULL
            ORDER BY submission.published_at DESC,
                     submission.submission_id DESC
            """;

    private static final String STUDENT_PUBLISHED_GRADE_SQL = """
            SELECT submission.submission_id,
                   submission.execution_id,
                   execution.exam_id,
                   execution.exam_version_no,
                   version.title AS exam_title,
                   course.name AS course_name,
                   submission.status,
                   submission.final_score,
                   submission.teacher_feedback,
                   submission.submitted_at,
                   submission.reviewed_at,
                   submission.published_at
            FROM exam_submissions submission
            JOIN exam_executions execution
              ON execution.execution_id = submission.execution_id
            JOIN exam_versions version
              ON version.exam_id = execution.exam_id
             AND version.version_no = execution.exam_version_no
            JOIN exams exam ON exam.exam_id = execution.exam_id
            JOIN courses course ON course.course_id = exam.course_id
            JOIN users student
              ON student.user_id = ?
             AND student.role = 'STUDENT'
             AND student.status = 'ACTIVE'
            WHERE submission.submission_id = ?
              AND submission.student_user_id = student.user_id
              AND submission.status = 'PUBLISHED'
              AND submission.final_score IS NOT NULL
              AND submission.published_at IS NOT NULL
            """;

    private static final String STUDENT_PUBLISHED_REVIEW_HEADER_SQL = """
            SELECT submission.submission_id, submission.execution_id,
                   execution.execution_code, execution.exam_id,
                   execution.exam_version_no, version.title AS exam_title,
                   exam.course_id, course.name AS course_name,
                   submission.status, submission.final_score,
                   submission.teacher_feedback, submission.submitted_at,
                   submission.reviewed_at, submission.published_at,
                   (SELECT COUNT(*)
                      FROM exam_version_questions selection
                     WHERE selection.exam_id = execution.exam_id
                       AND selection.exam_version_no = execution.exam_version_no)
                       AS expected_question_count,
                   (SELECT COUNT(*)
                      FROM student_answers answer
                      LEFT JOIN exam_version_questions selection
                        ON selection.exam_id = execution.exam_id
                       AND selection.exam_version_no = execution.exam_version_no
                       AND selection.question_id = answer.question_id
                       AND selection.question_version_no = answer.question_version_no
                     WHERE answer.submission_id = submission.submission_id
                       AND selection.question_id IS NULL)
                       AS invalid_answer_count
            FROM exam_submissions submission
            JOIN exam_executions execution
              ON execution.execution_id = submission.execution_id
            JOIN exam_versions version
              ON version.exam_id = execution.exam_id
             AND version.version_no = execution.exam_version_no
            JOIN exams exam ON exam.exam_id = execution.exam_id
            JOIN courses course ON course.course_id = exam.course_id
            JOIN users student
              ON student.user_id = ?
             AND student.role = 'STUDENT'
             AND student.status = 'ACTIVE'
            WHERE submission.submission_id = ?
              AND submission.student_user_id = student.user_id
              AND submission.status = 'PUBLISHED'
              AND submission.final_score IS NOT NULL
              AND submission.submitted_at IS NOT NULL
              AND submission.published_at IS NOT NULL
            """;

    private static final String STUDENT_PUBLISHED_REVIEW_QUESTIONS_SQL = """
            SELECT selection.order_number, selection.question_id,
                   selection.question_version_no, selection.score AS maximum_score,
                   question_version.content, question_version.topic,
                   question_version.difficulty,
                   question_version.question_type,
                   question_version.illustration_path,
                   qvi.media_type AS illustration_media_type,
                   qvi.content_bytes AS illustration_content_bytes,
                   qvi.byte_length AS illustration_byte_length,
                   qvi.width_pixels AS illustration_width_pixels,
                   qvi.height_pixels AS illustration_height_pixels,
                   qvi.content_sha256 AS illustration_content_sha256,
                   qvi.created_at AS illustration_created_at,
                   question_version.correct_option_number,
                   (SELECT COUNT(*)
                      FROM answer_options counted_option
                     WHERE counted_option.question_id = selection.question_id
                       AND counted_option.version_no = selection.question_version_no)
                       AS option_count,
                   option_1.option_text AS answer_option_1,
                   option_2.option_text AS answer_option_2,
                   option_3.option_text AS answer_option_3,
                   option_4.option_text AS answer_option_4,
                   answer.answer_id,
                   answer.question_version_no AS answered_question_version_no,
                   answer.selected_option_number,
                   answer.is_correct,
                   answer.score_received AS awarded_score
            FROM exam_version_questions selection
            JOIN question_versions question_version
              ON question_version.question_id = selection.question_id
             AND question_version.version_no = selection.question_version_no
            LEFT JOIN question_version_illustrations qvi
              ON qvi.question_id = question_version.question_id
             AND qvi.version_no = question_version.version_no
            JOIN answer_options option_1
              ON option_1.question_id = selection.question_id
             AND option_1.version_no = selection.question_version_no
             AND option_1.option_number = 1
            JOIN answer_options option_2
              ON option_2.question_id = selection.question_id
             AND option_2.version_no = selection.question_version_no
             AND option_2.option_number = 2
            JOIN answer_options option_3
              ON option_3.question_id = selection.question_id
             AND option_3.version_no = selection.question_version_no
             AND option_3.option_number = 3
            JOIN answer_options option_4
              ON option_4.question_id = selection.question_id
             AND option_4.version_no = selection.question_version_no
             AND option_4.option_number = 4
            LEFT JOIN student_answers answer
              ON answer.submission_id = ?
             AND answer.question_id = selection.question_id
             AND answer.question_version_no = selection.question_version_no
            WHERE selection.exam_id = ?
              AND selection.exam_version_no = ?
            ORDER BY selection.order_number ASC
            """;

    private final DatabaseController databaseController;
    private final ReportRepository reportRepository;

    public ExamSubmissionRepository() {
        this(new DatabaseController());
    }

    public ExamSubmissionRepository(DatabaseController databaseController) {
        this.databaseController = databaseController;
        this.reportRepository = new ReportRepository(databaseController);
    }

    public boolean existsInProgressForStudentAndCourse(
            int authenticatedStudentUserId, int courseId
    ) {
        if (authenticatedStudentUserId <= 0) {
            throw new IllegalArgumentException("Student user ID must be positive");
        }
        if (courseId <= 0) {
            throw new IllegalArgumentException("Course ID must be positive");
        }
        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     IN_PROGRESS_FOR_STUDENT_AND_COURSE_SQL
             )) {
            statement.setInt(1, authenticatedStudentUserId);
            statement.setInt(2, courseId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getBoolean("in_progress");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Failed to check active exam submission", exception
            );
        }
    }

    public ExamSubmission startOrResume(int authenticatedStudentUserId,
                                        ExamExecution execution,
                                        LocalDateTime currentTime) {
        Objects.requireNonNull(execution, "Exam execution is required");
        return startOrResumeInternal(
                authenticatedStudentUserId,
                execution.getExecutionId(),
                execution,
                currentTime,
                (connection, submission) -> loadInternalEntity(
                        connection,
                        submission.submissionId
                )
        );
    }

    private <T> T startOrResumeInternal(
            int authenticatedStudentId,
            int executionId,
            ExamExecution suppliedExecution,
            LocalDateTime now,
            SubmissionResultLoader<T> resultLoader
    ) {
        Objects.requireNonNull(now, "Server time is required");
        return executeInTransaction("Failed to start exam attempt", connection -> {
            LockedExecution execution = lockAvailableExecution(
                    connection,
                    authenticatedStudentId,
                    executionId
            ).orElseThrow(() -> new IllegalStateException("Execution not available"));
            if (suppliedExecution != null) {
                requireMatchingExecution(suppliedExecution, execution);
            }

            Optional<SubmissionRecord> existing = findExecutionSubmission(
                    connection,
                    execution,
                    authenticatedStudentId
            );
            if (existing.isPresent()) {
                requireResumableAttempt(existing.get(), now);
                return resultLoader.load(connection, existing.get());
            }

            requireNewAttemptAvailable(execution, now);

            final int submissionId;
            try {
                submissionId = insertSubmission(
                        connection,
                        authenticatedStudentId,
                        execution,
                        now
                );
            } catch (SQLException exception) {
                if (!isSubmissionCollision(exception)) {
                    throw exception;
                }

                Optional<SubmissionRecord> racedSubmission = findExecutionSubmission(
                        connection,
                        execution,
                        authenticatedStudentId
                );
                if (racedSubmission.isEmpty()) {
                    throw exception;
                }
                requireResumableAttempt(racedSubmission.get(), now);
                return resultLoader.load(connection, racedSubmission.get());
            }

            incrementStartedCount(connection, executionId);
            SubmissionRecord created = new SubmissionRecord(
                    submissionId,
                    execution,
                    authenticatedStudentId,
                    now,
                    SubmissionStatus.IN_PROGRESS,
                    execution.durationMinutes,
                    0
            );
            return resultLoader.load(connection, created);
        });
    }

    public Optional<ExamSubmission> findEntityForStudent(
            int authenticatedStudentUserId,
            int submissionId
    ) {
        return findSubmissionEntity(
                STUDENT_SUBMISSION_ENTITY_SQL,
                authenticatedStudentUserId,
                submissionId,
                "Failed to load student exam submission entity"
        );
    }

    public Optional<ExamSubmission> findActiveEntityForStudent(
            int authenticatedStudentUserId,
            int submissionId
    ) {
        return findSubmissionEntity(
                ACTIVE_STUDENT_SUBMISSION_ENTITY_SQL,
                authenticatedStudentUserId,
                submissionId,
                "Failed to load active student exam submission entity"
        );
    }

    public Optional<ExamSubmission> findEntityForManager(
            int authenticatedManagerUserId,
            int submissionId
    ) {
        return findSubmissionEntity(
                MANAGER_SUBMISSION_ENTITY_SQL,
                authenticatedManagerUserId,
                submissionId,
                "Failed to load manager exam submission entity"
        );
    }

    public List<ExecutionSubmissionSummaryDTO> findSummariesForManager(
            int authenticatedManagerUserId,
            int executionId
    ) {
        List<ExecutionSubmissionSummaryDTO> summaries = new ArrayList<>();
        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     MANAGER_SUBMISSION_SUMMARIES_SQL
             )) {
            statement.setInt(1, authenticatedManagerUserId);
            statement.setInt(2, executionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    summaries.add(mapExecutionSubmissionSummary(resultSet));
                }
            }
            return List.copyOf(summaries);
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Failed to load execution submissions",
                    exception
            );
        }
    }

    public Optional<SubmissionReviewDTO> findReviewForManager(
            int authenticatedManagerUserId,
            int submissionId
    ) {
        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     MANAGER_SUBMISSION_REVIEW_SQL
             )) {
            statement.setInt(1, authenticatedManagerUserId);
            statement.setInt(2, submissionId);
            ReviewHeader header;
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                header = mapReviewHeader(resultSet);
                if (resultSet.next()) {
                    throw new IllegalArgumentException(
                            "Duplicate submission review projection: " + submissionId
                    );
                }
            }
            return Optional.of(header.toDto(loadSubmissionAnswerReviews(
                    connection,
                    header.submissionId(),
                    header.examId(),
                    header.examVersionNo()
            )));
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Failed to load submission for review",
                    exception
            );
        }
    }

    public List<ExecutionSubmissionSummaryDTO> findSummariesForPrincipal(
            int executionId
    ) {
        requirePositivePrincipalId(executionId, "Execution ID must be positive");
        List<ExecutionSubmissionSummaryDTO> summaries = new ArrayList<>();
        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     PRINCIPAL_SUBMISSION_SUMMARIES_SQL
             )) {
            statement.setInt(1, executionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    summaries.add(mapExecutionSubmissionSummary(resultSet));
                }
            }
            return List.copyOf(summaries);
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Failed to load Principal execution results", exception
            );
        }
    }

    public Optional<SubmissionReviewDTO> findReviewForPrincipal(int submissionId) {
        requirePositivePrincipalId(submissionId, "Submission ID must be positive");
        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     PRINCIPAL_SUBMISSION_REVIEW_SQL
             )) {
            statement.setInt(1, submissionId);
            ReviewHeader header;
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                header = mapReviewHeader(resultSet);
                if (resultSet.next()) {
                    throw new IllegalArgumentException(
                            "Duplicate submission review projection: " + submissionId
                    );
                }
            }
            return Optional.of(header.toDto(loadSubmissionAnswerReviews(
                    connection, header.submissionId(), header.examId(),
                    header.examVersionNo()
            )));
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Failed to load Principal submission result", exception
            );
        }
    }

    private static void requirePositivePrincipalId(int value, String message) {
        if (value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    public List<PublishedGradeSummaryDTO> findPublishedSummariesForStudent(
            int authenticatedStudentUserId
    ) {
        List<PublishedGradeSummaryDTO> summaries = new ArrayList<>();
        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     STUDENT_PUBLISHED_SUMMARIES_SQL
             )) {
            statement.setInt(1, authenticatedStudentUserId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    summaries.add(mapPublishedGradeSummary(resultSet));
                }
            }
            return List.copyOf(summaries);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load published grades", exception);
        }
    }

    public Optional<PublishedGradeDTO> findPublishedGradeForStudent(
            int authenticatedStudentUserId,
            int submissionId
    ) {
        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     STUDENT_PUBLISHED_GRADE_SQL
             )) {
            statement.setInt(1, authenticatedStudentUserId);
            statement.setInt(2, submissionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                PublishedGradeDTO grade = mapPublishedGrade(resultSet);
                if (resultSet.next()) {
                    throw new IllegalArgumentException(
                            "Duplicate published grade projection: " + submissionId
                    );
                }
                return Optional.of(grade);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load published grade", exception);
        }
    }

    public Optional<PublishedExamReviewDTO> findPublishedExamReviewForStudent(
            int authenticatedStudentUserId, int submissionId
    ) {
        requirePositivePublishedReviewId(
                authenticatedStudentUserId, "Student user ID must be positive"
        );
        requirePositivePublishedReviewId(
                submissionId, "Submission ID must be positive"
        );
        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     STUDENT_PUBLISHED_REVIEW_HEADER_SQL
             )) {
            statement.setInt(1, authenticatedStudentUserId);
            statement.setInt(2, submissionId);
            PublishedReviewHeader header;
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) return Optional.empty();
                header = mapPublishedReviewHeader(resultSet);
                if (resultSet.next()) {
                    throw new IllegalArgumentException(
                            "Duplicate published exam review: " + submissionId
                    );
                }
            }
            return Optional.of(header.toDto(loadPublishedReviewQuestions(
                    connection, header
            )));
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Failed to load published exam review", exception
            );
        }
    }

    public List<ExamSubmission> findExpiredInProgressEntities(
            LocalDateTime currentTime
    ) {
        Objects.requireNonNull(currentTime, "Server time is required");
        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     EXPIRED_SUBMISSION_ENTITIES_SQL
             )) {
            statement.setObject(1, currentTime);
            List<SubmissionEntityData> rows = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(mapSubmissionEntityData(resultSet));
                }
            }
            List<ExamSubmission> submissions = new ArrayList<>(rows.size());
            for (SubmissionEntityData row : rows) {
                submissions.add(row.rehydrate(loadAnswerEntities(
                        connection,
                        row.submissionId
                )));
            }
            return List.copyOf(submissions);
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Failed to load expired exam submission entities",
                    exception
            );
        }
    }

    public Optional<ExamAttemptDTO> findActiveForStudent(int authenticatedStudentId,
                                                          int submissionId,
                                                          LocalDateTime now) {
        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(ACTIVE_ATTEMPT_SQL)) {
            statement.setInt(1, submissionId);
            statement.setInt(2, authenticatedStudentId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                SubmissionRecord submission = mapCompleteSubmission(resultSet);
                return Optional.of(loadAttempt(
                        connection,
                        authenticatedStudentId,
                        submission,
                        now
                ));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load active exam attempt", exception);
        }
    }

    public ExamSubmission persistAnswer(
            int authenticatedStudentUserId,
            ExamSubmission submission,
            StudentAnswer answer,
            LocalDateTime currentTime
    ) {
        Objects.requireNonNull(submission, "Exam submission is required");
        Objects.requireNonNull(answer, "Student answer is required");
        if (submission.getSubmissionId() <= 0) {
            throw new IllegalArgumentException("Persisted submission ID must be positive");
        }
        if (submission.getStudentUserId() != authenticatedStudentUserId) {
            throw new IllegalArgumentException("Exam attempt not found: "
                    + submission.getSubmissionId());
        }
        if (answer.getSubmissionId() != submission.getSubmissionId()) {
            throw new IllegalArgumentException(
                    "Student answer belongs to a different submission"
            );
        }
        if (answer.isGraded()) {
            throw new IllegalArgumentException("Saved answer must be ungraded");
        }

        AnswerCommand command = new AnswerCommand(
                submission.getSubmissionId(),
                answer.getQuestionId(),
                answer.getQuestionVersionNo(),
                answer.getSelectedOptionNumber()
        );
        return saveAnswerInternal(
                authenticatedStudentUserId,
                command,
                submission,
                currentTime,
                (connection, locked) -> loadInternalEntity(
                        connection,
                        locked.submissionId
                )
        );
    }

    private <T> T saveAnswerInternal(
            int authenticatedStudentId,
            AnswerCommand command,
            ExamSubmission suppliedSubmission,
            LocalDateTime now,
            SubmissionResultLoader<T> resultLoader
    ) {
        Objects.requireNonNull(now, "Server time is required");
        return executeInTransaction("Failed to save exam answer", connection -> {
            SubmissionRecord submission = lockStudentSubmission(
                    connection,
                    authenticatedStudentId,
                    command.submissionId
            ).orElseThrow(() -> new IllegalArgumentException(
                    "Exam attempt not found: " + command.submissionId
            ));

            requireInProgress(submission);
            requireBeforeDeadline(submission, now);
            if (suppliedSubmission != null) {
                requireMatchingSubmission(suppliedSubmission, submission);
                if (suppliedSubmission.getStatus()
                        != SubmissionStatus.IN_PROGRESS) {
                    throw new IllegalStateException(
                            "Exam attempt already submitted"
                    );
                }
                if (!suppliedSubmission.isEditable(now)) {
                    throw new IllegalStateException("Exam time has expired");
                }
            }
            int questionVersionNo = lockExamQuestionVersion(
                    connection,
                    submission,
                    command.questionId
            ).orElseThrow(() -> new IllegalArgumentException(
                    "Question not found in exam: " + command.questionId
            ));
            if (command.questionVersionNo != null
                    && command.questionVersionNo != questionVersionNo) {
                throw new IllegalArgumentException(
                        "Question answer version does not match the submission"
                );
            }
            if (command.selectedOptionNumber < 1
                    || command.selectedOptionNumber > 4) {
                throw new IllegalArgumentException("Invalid answer option");
            }

            upsertAnswer(
                    connection,
                    command.submissionId,
                    command.questionId,
                    questionVersionNo,
                    command.selectedOptionNumber,
                    now
            );
            return resultLoader.load(connection, submission);
        });
    }

    public ExamSubmission persistStudentSubmission(
            int authenticatedStudentUserId,
            ExamSubmission submission
    ) {
        Objects.requireNonNull(submission, "Exam submission is required");
        if (submission.getStudentUserId() != authenticatedStudentUserId) {
            throw new IllegalArgumentException("Exam attempt not found: "
                    + submission.getSubmissionId());
        }
        requirePersistableFinalState(submission, false);

        return executeInTransaction(
                "Failed to persist student exam submission",
                connection -> {
                    SubmissionRecord source = lockStudentSubmission(
                            connection,
                            authenticatedStudentUserId,
                            submission.getSubmissionId()
                    ).orElseThrow(() -> new IllegalArgumentException(
                            "Exam attempt not found: " + submission.getSubmissionId()
                    ));
                    requireInProgress(source);
                    requireMatchingSubmission(submission, source);
                    persistFinalizedEntity(connection, source, submission);
                    return loadInternalEntity(connection, submission.getSubmissionId());
                }
        );
    }

    public ExamSubmission persistAutomaticSubmission(
            ExamSubmission submission
    ) {
        Objects.requireNonNull(submission, "Exam submission is required");
        requirePersistableFinalState(submission, true);

        return executeInTransaction(
                "Failed to persist automatic exam submission",
                connection -> {
                    SubmissionRecord source = lockInternalSubmission(
                            connection,
                            submission.getSubmissionId()
                    ).orElseThrow(() -> new IllegalArgumentException(
                            "Exam attempt not found: " + submission.getSubmissionId()
                    ));
                    requireInProgress(source);
                    requireMatchingSubmission(submission, source);
                    if (deadline(source).isAfter(submission.getSubmittedAt())) {
                        throw new IllegalStateException("Exam time has not expired");
                    }
                    persistFinalizedEntity(connection, source, submission);
                    return loadInternalEntity(connection, submission.getSubmissionId());
                }
        );
    }

    public ExamSubmission persistExtension(
            int authenticatedManagerUserId,
            ExamSubmission submission,
            int addedMinutes,
            String reason,
            LocalDateTime currentTime
    ) {
        Objects.requireNonNull(submission, "Exam submission is required");
        Objects.requireNonNull(currentTime, "Server time is required");
        if (addedMinutes <= 0) {
            throw new IllegalArgumentException("Extra minutes must be positive");
        }
        String normalizedReason = requireText(reason, "Extension reason is required");
        if (submission.getStatus() != SubmissionStatus.IN_PROGRESS) {
            throw new IllegalStateException("Exam attempt already submitted");
        }
        if (!normalizedReason.equals(submission.getExtensionReason())) {
            throw new IllegalArgumentException(
                    "Extension reason does not match submission state"
            );
        }
        if (!currentTime.equals(submission.getUpdatedAt())) {
            throw new IllegalArgumentException(
                    "Extension timestamp does not match submission state"
            );
        }
        int previousExtraMinutes;
        try {
            previousExtraMinutes = Math.subtractExact(
                    submission.getExtraMinutes(),
                    addedMinutes
            );
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Extension minutes do not match submission state",
                    exception
            );
        }
        if (previousExtraMinutes < 0) {
            throw new IllegalArgumentException(
                    "Extension minutes do not match submission state"
            );
        }

        return executeInTransaction("Failed to extend submission time", connection -> {
            ExtensionTarget target = lockExtensionTarget(
                    connection,
                    authenticatedManagerUserId,
                    submission.getSubmissionId()
            ).orElseThrow(() -> new IllegalArgumentException(
                    "Exam attempt not found: " + submission.getSubmissionId()
            ));
            if (target.status != SubmissionStatus.IN_PROGRESS) {
                throw new IllegalStateException("Exam attempt already submitted");
            }
            if (!currentTime.isBefore(target.deadline())) {
                throw new IllegalStateException("Exam time has expired");
            }
            if (!submission.getStartedAt().equals(target.startedAt)
                    || submission.getAllocatedDurationMinutes()
                    != target.allocatedDurationMinutes
                    || target.extraMinutes != previousExtraMinutes
                    || !submission.getEffectiveDeadline().equals(
                            target.deadline().plusMinutes(addedMinutes)
                    )) {
                throw new IllegalStateException(
                        "Extension state does not match persisted submission"
                );
            }

            updateEntityExtension(
                    connection,
                    submission,
                    previousExtraMinutes,
                    normalizedReason
            );
            insertExtensionAudit(
                    connection,
                    authenticatedManagerUserId,
                    submission.getSubmissionId(),
                    addedMinutes,
                    normalizedReason,
                    currentTime
            );
            return loadInternalEntity(connection, submission.getSubmissionId());
        });
    }

    public ExamSubmission persistReview(int managerUserId,
                                        ExamSubmission submission) {
        Objects.requireNonNull(submission, "Exam submission is required");
        requirePersistedSubmissionId(submission);
        requireReviewEntityState(managerUserId, submission);

        return executeInTransaction(
                "Failed to persist submission review",
                "Failed to reload persisted submission review",
                connection -> {
                    SubmissionEntityData source = lockManagerResultSubmission(
                            connection,
                            managerUserId,
                            submission.getSubmissionId()
                    ).orElseThrow(() -> examAttemptNotFound(
                            submission.getSubmissionId()
                    ));
                    requireMatchingSubmissionIdentity(submission, source);
                    requireReviewSourceState(source, submission);
                    updateReview(connection, source, submission);
                    return submission.getSubmissionId();
                },
                (connection, submissionId) -> reloadManagerResult(
                        connection, managerUserId, submissionId
                )
        );
    }

    public ExamSubmission persistPublication(int managerUserId,
                                             ExamSubmission submission) {
        Objects.requireNonNull(submission, "Exam submission is required");
        requirePersistedSubmissionId(submission);
        requirePublicationEntityState(managerUserId, submission);

        return executeInTransaction(
                "Failed to persist submission publication",
                "Failed to reload persisted submission publication",
                connection -> {
                    SubmissionEntityData source = lockManagerResultSubmission(
                            connection,
                            managerUserId,
                            submission.getSubmissionId()
                    ).orElseThrow(() -> examAttemptNotFound(
                            submission.getSubmissionId()
                    ));
                    requireMatchingSubmissionIdentity(submission, source);
                    if (source.status == SubmissionStatus.PUBLISHED) {
                        requireMatchingPublishedState(source, submission);
                        reportRepository.refreshExecutionStatistics(
                                connection,
                                source.executionId
                        );
                        return submission.getSubmissionId();
                    }
                    requirePublicationSourceState(source, submission);
                    updatePublication(connection, source, submission);
                    reportRepository.refreshExecutionStatistics(
                            connection,
                            source.executionId
                    );
                    return submission.getSubmissionId();
                },
                (connection, submissionId) -> reloadManagerResult(
                        connection, managerUserId, submissionId
                )
        );
    }

    private Optional<LockedExecution> lockAvailableExecution(
            Connection connection,
            int authenticatedStudentId,
            int executionId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                LOCK_AVAILABLE_EXECUTION_SQL
        )) {
            statement.setInt(1, authenticatedStudentId);
            statement.setInt(2, executionId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new LockedExecution(
                        resultSet.getInt("execution_id"),
                        resultSet.getString("execution_code"),
                        resultSet.getInt("exam_id"),
                        resultSet.getInt("exam_version_no"),
                        resultSet.getString("exam_title"),
                        resultSet.getString("student_instructions"),
                        resultSet.getObject("opening_time", LocalDateTime.class),
                        resultSet.getObject("closing_time", LocalDateTime.class),
                        resultSet.getInt("duration_minutes"),
                        ExecutionStatus.valueOf(resultSet.getString("status"))
                ));
            }
        }
    }

    private Optional<SubmissionRecord> findExecutionSubmission(
            Connection connection,
            LockedExecution execution,
            int authenticatedStudentId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                LOCK_EXECUTION_SUBMISSION_SQL
        )) {
            statement.setInt(1, execution.executionId);
            statement.setInt(2, authenticatedStudentId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new SubmissionRecord(
                        resultSet.getInt("submission_id"),
                        execution,
                        resultSet.getInt("student_user_id"),
                        resultSet.getObject("started_at", LocalDateTime.class),
                        SubmissionStatus.valueOf(resultSet.getString("status")),
                        resultSet.getInt("allocated_duration_minutes"),
                        resultSet.getInt("extra_minutes")
                ));
            }
        }
    }

    private void requireResumableAttempt(SubmissionRecord submission,
                                         LocalDateTime now) {
        if (submission.status != SubmissionStatus.IN_PROGRESS) {
            throw new IllegalStateException("Exam attempt already submitted");
        }
        if (!now.isBefore(deadline(submission))) {
            throw new IllegalStateException("Execution not available");
        }
    }

    private void requireNewAttemptAvailable(LockedExecution execution, LocalDateTime now) {
        boolean validStatus = execution.status == ExecutionStatus.SCHEDULED
                || execution.status == ExecutionStatus.OPEN;
        if (!validStatus
                || now.isBefore(execution.openingTime)
                || !now.isBefore(execution.closingTime)) {
            throw new IllegalStateException("Execution not available");
        }
    }

    private int insertSubmission(Connection connection, int authenticatedStudentId,
                                 LockedExecution execution, LocalDateTime now)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_SUBMISSION_SQL,
                Statement.RETURN_GENERATED_KEYS
        )) {
            statement.setInt(1, execution.executionId);
            statement.setInt(2, authenticatedStudentId);
            statement.setObject(3, now);
            statement.setNull(4, Types.TIMESTAMP);
            statement.setString(5, SubmissionStatus.IN_PROGRESS.name());
            statement.setInt(6, execution.durationMinutes);
            statement.setInt(7, 0);
            statement.setNull(8, Types.LONGVARCHAR);
            statement.setNull(9, Types.INTEGER);
            statement.setNull(10, Types.DECIMAL);
            statement.setNull(11, Types.DECIMAL);
            statement.setNull(12, Types.LONGVARCHAR);
            statement.setNull(13, Types.LONGVARCHAR);
            statement.setNull(14, Types.INTEGER);
            statement.setNull(15, Types.TIMESTAMP);
            statement.setNull(16, Types.INTEGER);
            statement.setNull(17, Types.TIMESTAMP);

            if (statement.executeUpdate() != 1) {
                throw new SQLException("Submission insert did not affect exactly one row");
            }
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (!generatedKeys.next()) {
                    throw new SQLException("Submission insert did not return a generated key");
                }
                int submissionId = generatedKeys.getInt(1);
                if (submissionId <= 0) {
                    throw new SQLException("Submission insert returned an invalid generated key");
                }
                return submissionId;
            }
        }
    }

    private void incrementStartedCount(Connection connection, int executionId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                INCREMENT_STARTED_COUNT_SQL
        )) {
            statement.setInt(1, executionId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Execution start count update failed");
            }
        }
    }

    private Optional<SubmissionRecord> lockStudentSubmission(
            Connection connection,
            int authenticatedStudentId,
            int submissionId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                LOCK_STUDENT_SUBMISSION_SQL
        )) {
            statement.setInt(1, submissionId);
            statement.setInt(2, authenticatedStudentId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(mapCompleteSubmission(resultSet))
                        : Optional.empty();
            }
        }
    }

    private Optional<SubmissionRecord> lockInternalSubmission(Connection connection,
                                                               int submissionId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                LOCK_INTERNAL_SUBMISSION_SQL
        )) {
            statement.setInt(1, submissionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(mapCompleteSubmission(resultSet))
                        : Optional.empty();
            }
        }
    }

    private SubmissionRecord mapCompleteSubmission(ResultSet resultSet)
            throws SQLException {
        LockedExecution execution = new LockedExecution(
                resultSet.getInt("execution_id"),
                resultSet.getString("execution_code"),
                resultSet.getInt("exam_id"),
                resultSet.getInt("exam_version_no"),
                resultSet.getString("exam_title"),
                resultSet.getString("student_instructions"),
                resultSet.getObject("opening_time", LocalDateTime.class),
                resultSet.getObject("closing_time", LocalDateTime.class),
                resultSet.getInt("duration_minutes"),
                ExecutionStatus.valueOf(resultSet.getString("execution_status"))
        );
        return new SubmissionRecord(
                resultSet.getInt("submission_id"),
                execution,
                resultSet.getInt("student_user_id"),
                resultSet.getObject("started_at", LocalDateTime.class),
                SubmissionStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("allocated_duration_minutes"),
                resultSet.getInt("extra_minutes")
        );
    }

    private ExamAttemptDTO loadAttempt(Connection connection, int authenticatedStudentId,
                                       SubmissionRecord submission, LocalDateTime now)
            throws SQLException {
        List<StudentExamQuestionDTO> questions = loadSafeQuestions(
                connection,
                submission.execution.examId,
                submission.execution.examVersionNo
        );
        List<StudentAnswerDTO> answers = loadSafeAnswers(
                connection,
                authenticatedStudentId,
                submission.submissionId
        );
        LocalDateTime deadline = deadline(submission);
        long remainingSeconds = Math.max(0L, Duration.between(now, deadline).getSeconds());

        return new ExamAttemptDTO(
                submission.submissionId,
                submission.execution.executionId,
                submission.execution.executionCode,
                submission.execution.examId,
                submission.execution.examVersionNo,
                submission.execution.examTitle,
                submission.execution.studentInstructions,
                submission.startedAt,
                deadline,
                submission.allocatedDurationMinutes,
                submission.extraMinutes,
                remainingSeconds,
                submission.status,
                questions,
                answers
        );
    }

    private List<StudentExamQuestionDTO> loadSafeQuestions(Connection connection,
                                                           int examId,
                                                           int examVersionNo)
            throws SQLException {
        List<StudentExamQuestionDTO> questions = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SAFE_QUESTIONS_SQL)) {
            statement.setInt(1, examId);
            statement.setInt(2, examVersionNo);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    questions.add(new StudentExamQuestionDTO(
                            resultSet.getInt("question_id"),
                            resultSet.getInt("question_version_no"),
                            resultSet.getInt("order_number"),
                            resultSet.getBigDecimal("score").doubleValue(),
                            resultSet.getString("content"),
                            resultSet.getString("topic"),
                            resultSet.getString("difficulty"),
                            resultSet.getString("illustration_path"),
                            resultSet.getString("answer_option_1"),
                            resultSet.getString("answer_option_2"),
                            resultSet.getString("answer_option_3"),
                            resultSet.getString("answer_option_4"),
                            QuestionIllustrationJdbcSupport.readDto(resultSet)
                    ));
                }
            }
        }
        return questions;
    }

    private List<StudentAnswerDTO> loadSafeAnswers(Connection connection,
                                                   int authenticatedStudentId,
                                                   int submissionId)
            throws SQLException {
        List<StudentAnswerDTO> answers = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SAFE_ANSWERS_SQL)) {
            statement.setInt(1, submissionId);
            statement.setInt(2, authenticatedStudentId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    answers.add(new StudentAnswerDTO(
                            resultSet.getInt("question_id"),
                            resultSet.getObject("selected_option_number", Integer.class),
                            resultSet.getObject("updated_at", LocalDateTime.class)
                    ));
                }
            }
        }
        return answers;
    }

    private Optional<ExamSubmission> findSubmissionEntity(
            String sql,
            int authenticatedUserId,
            int submissionId,
            String failureMessage
    ) {
        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, authenticatedUserId);
            statement.setInt(2, submissionId);
            return loadSubmissionEntity(connection, statement);
        } catch (SQLException exception) {
            throw new IllegalStateException(failureMessage, exception);
        }
    }

    private Optional<SubmissionEntityData> lockManagerResultSubmission(
            Connection connection,
            int managerUserId,
            int submissionId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                LOCK_MANAGER_RESULT_ENTITY_SQL
        )) {
            statement.setInt(1, managerUserId);
            statement.setInt(2, submissionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                SubmissionEntityData data = mapSubmissionEntityData(resultSet);
                if (resultSet.next()) {
                    throw new IllegalStateException(
                            "Duplicate persisted exam submission: " + submissionId
                    );
                }
                return Optional.of(data);
            }
        }
    }

    private ExamSubmission reloadManagerResult(Connection connection,
                                               int managerUserId,
                                               int submissionId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                     MANAGER_RESULT_ENTITY_SQL
             )) {
            statement.setInt(1, managerUserId);
            statement.setInt(2, submissionId);
            return loadSubmissionEntity(connection, statement).orElseThrow(() ->
                    new IllegalStateException(
                            "Persisted exam submission could not be reloaded: "
                                    + submissionId
                    )
            );
        }
    }

    private ExamSubmission loadInternalEntity(Connection connection,
                                               int submissionId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                INTERNAL_SUBMISSION_ENTITY_SQL
        )) {
            statement.setInt(1, submissionId);
            return loadSubmissionEntity(connection, statement).orElseThrow(() ->
                    new IllegalStateException(
                            "Persisted exam submission could not be reloaded: "
                                    + submissionId
                    )
            );
        }
    }

    private Optional<ExamSubmission> loadSubmissionEntity(
            Connection connection,
            PreparedStatement statement
    ) throws SQLException {
        SubmissionEntityData data;
        try (ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return Optional.empty();
            }
            data = mapSubmissionEntityData(resultSet);
            if (resultSet.next()) {
                throw new IllegalStateException(
                        "Duplicate persisted exam submission: " + data.submissionId
                );
            }
        }
        return Optional.of(data.rehydrate(loadAnswerEntities(
                connection,
                data.submissionId
        )));
    }

    private SubmissionEntityData mapSubmissionEntityData(ResultSet resultSet)
            throws SQLException {
        int submissionId = resultSet.getInt("submission_id");
        SubmissionStatus status = parseSubmissionStatus(
                resultSet.getString("status"),
                submissionId
        );
        return new SubmissionEntityData(
                submissionId,
                resultSet.getInt("execution_id"),
                resultSet.getInt("exam_id"),
                resultSet.getInt("exam_version_no"),
                resultSet.getInt("student_user_id"),
                resultSet.getObject("started_at", LocalDateTime.class),
                resultSet.getObject("submitted_at", LocalDateTime.class),
                status,
                resultSet.getInt("allocated_duration_minutes"),
                resultSet.getInt("extra_minutes"),
                resultSet.getString("extension_reason"),
                resultSet.getObject("actual_duration_minutes", Integer.class),
                resultSet.getBigDecimal("automatic_score"),
                resultSet.getBigDecimal("final_score"),
                resultSet.getString("teacher_feedback"),
                resultSet.getString("manual_change_reason"),
                resultSet.getObject("reviewed_by_user_id", Integer.class),
                resultSet.getObject("reviewed_at", LocalDateTime.class),
                resultSet.getObject("published_by_user_id", Integer.class),
                resultSet.getObject("published_at", LocalDateTime.class),
                resultSet.getObject("created_at", LocalDateTime.class),
                resultSet.getObject("updated_at", LocalDateTime.class)
        );
    }

    private ExecutionSubmissionSummaryDTO mapExecutionSubmissionSummary(
            ResultSet resultSet
    ) throws SQLException {
        int submissionId = resultSet.getInt("submission_id");
        return new ExecutionSubmissionSummaryDTO(
                submissionId,
                resultSet.getInt("execution_id"),
                resultSet.getInt("exam_id"),
                resultSet.getInt("exam_version_no"),
                resultSet.getString("exam_title"),
                resultSet.getInt("student_user_id"),
                resultSet.getString("student_name"),
                parseSubmissionStatus(resultSet.getString("status"), submissionId),
                readStoredScore(resultSet, "automatic_score", false,
                        "Submission automatic score", submissionId),
                readStoredScore(resultSet, "final_score", false,
                        "Submission final score", submissionId),
                resultSet.getObject("started_at", LocalDateTime.class),
                resultSet.getObject("submitted_at", LocalDateTime.class),
                resultSet.getObject("reviewed_at", LocalDateTime.class),
                resultSet.getObject("published_at", LocalDateTime.class)
        );
    }

    private ReviewHeader mapReviewHeader(ResultSet resultSet) throws SQLException {
        int submissionId = resultSet.getInt("submission_id");
        Integer reviewerId = resultSet.getObject("reviewed_by_user_id", Integer.class);
        Integer publisherId = resultSet.getObject("published_by_user_id", Integer.class);
        LocalDateTime updatedAt = resultSet.getObject(
                "updated_at", LocalDateTime.class
        );
        if (updatedAt == null) {
            throw new IllegalArgumentException(
                    "Submission updated timestamp is invalid: " + submissionId
            );
        }
        return new ReviewHeader(
                submissionId,
                resultSet.getInt("execution_id"),
                resultSet.getInt("exam_id"),
                resultSet.getInt("exam_version_no"),
                resultSet.getString("exam_title"),
                resultSet.getInt("student_user_id"),
                resultSet.getString("student_name"),
                parseSubmissionStatus(resultSet.getString("status"), submissionId),
                readStoredScore(resultSet, "automatic_score", false,
                        "Submission automatic score", submissionId),
                readStoredScore(resultSet, "final_score", false,
                        "Submission final score", submissionId),
                resultSet.getString("teacher_feedback"),
                resultSet.getString("manual_change_reason"),
                reviewerId == null ? 0 : reviewerId,
                resultSet.getObject("started_at", LocalDateTime.class),
                resultSet.getObject("submitted_at", LocalDateTime.class),
                resultSet.getObject("reviewed_at", LocalDateTime.class),
                updatedAt,
                publisherId == null ? 0 : publisherId,
                resultSet.getObject("published_at", LocalDateTime.class)
        );
    }

    private List<SubmissionAnswerReviewDTO> loadSubmissionAnswerReviews(
            Connection connection,
            int submissionId,
            int examId,
            int examVersionNo
    ) throws SQLException {
        List<SubmissionAnswerReviewDTO> answers = new ArrayList<>();
        Set<Integer> orderNumbers = new HashSet<>();
        Set<String> questionVersions = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
                SUBMISSION_ANSWER_REVIEW_SQL
        )) {
            statement.setInt(1, submissionId);
            statement.setInt(2, examId);
            statement.setInt(3, examVersionNo);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    int questionId = resultSet.getInt("question_id");
                    int questionVersionNo = resultSet.getInt("question_version_no");
                    int orderNumber = resultSet.getInt("order_number");
                    String identity = questionId + ":" + questionVersionNo;
                    if (orderNumber <= 0 || !orderNumbers.add(orderNumber)) {
                        throw new IllegalArgumentException(
                                "Submission review question order is invalid: "
                                        + submissionId
                        );
                    }
                    if (!questionVersions.add(identity)) {
                        throw new IllegalArgumentException(
                                "Submission review question identity is duplicated: "
                                        + submissionId
                        );
                    }

                    Integer answerId = resultSet.getObject("answer_id", Integer.class);
                    String selectedOptionText = null;
                    Boolean correct = null;
                    BigDecimal awardedScore = null;
                    if (answerId != null) {
                        Integer answeredVersion = resultSet.getObject(
                                "answered_question_version_no",
                                Integer.class
                        );
                        selectedOptionText = resultSet.getString("selected_option_text");
                        if (answeredVersion == null
                                || answeredVersion != questionVersionNo
                                || selectedOptionText == null) {
                            throw new IllegalArgumentException(
                                    "Submission answer version is invalid: " + questionId
                            );
                        }
                        correct = resultSet.getObject("is_correct", Boolean.class);
                        awardedScore = readStoredScore(
                                resultSet,
                                "awarded_score",
                                false,
                                "Submission answer score",
                                submissionId
                        );
                    }
                    answers.add(new SubmissionAnswerReviewDTO(
                            questionId,
                            questionVersionNo,
                            orderNumber,
                            resultSet.getString("question_content"),
                            selectedOptionText,
                            correct,
                            awardedScore,
                            readMaximumScore(resultSet, submissionId),
                            QuestionIllustrationJdbcSupport.readDto(resultSet)
                    ));
                }
            }
        }
        return List.copyOf(answers);
    }

    private PublishedGradeSummaryDTO mapPublishedGradeSummary(ResultSet resultSet)
            throws SQLException {
        int submissionId = resultSet.getInt("submission_id");
        return new PublishedGradeSummaryDTO(
                submissionId,
                resultSet.getInt("execution_id"),
                resultSet.getInt("exam_id"),
                resultSet.getInt("exam_version_no"),
                resultSet.getString("exam_title"),
                resultSet.getString("course_name"),
                readStoredScore(resultSet, "final_score", true,
                        "Published final score", submissionId),
                resultSet.getObject("submitted_at", LocalDateTime.class),
                requirePublishedAt(resultSet, submissionId)
        );
    }

    private PublishedGradeDTO mapPublishedGrade(ResultSet resultSet)
            throws SQLException {
        int submissionId = resultSet.getInt("submission_id");
        SubmissionStatus status = parseSubmissionStatus(
                resultSet.getString("status"),
                submissionId
        );
        if (status != SubmissionStatus.PUBLISHED) {
            throw new IllegalArgumentException(
                    "Published grade status is invalid: " + submissionId
            );
        }
        return new PublishedGradeDTO(
                submissionId,
                resultSet.getInt("execution_id"),
                resultSet.getInt("exam_id"),
                resultSet.getInt("exam_version_no"),
                resultSet.getString("exam_title"),
                resultSet.getString("course_name"),
                status,
                readStoredScore(resultSet, "final_score", true,
                        "Published final score", submissionId),
                resultSet.getString("teacher_feedback"),
                resultSet.getObject("submitted_at", LocalDateTime.class),
                resultSet.getObject("reviewed_at", LocalDateTime.class),
                requirePublishedAt(resultSet, submissionId)
        );
    }

    private PublishedReviewHeader mapPublishedReviewHeader(ResultSet resultSet)
            throws SQLException {
        int submissionId = resultSet.getInt("submission_id");
        if (parseSubmissionStatus(resultSet.getString("status"), submissionId)
                != SubmissionStatus.PUBLISHED) {
            throw new IllegalArgumentException(
                    "Published exam review status is invalid: " + submissionId
            );
        }
        int expectedQuestionCount = resultSet.getInt("expected_question_count");
        if (expectedQuestionCount <= 0) {
            throw new IllegalArgumentException(
                    "Published exam review questions are missing: " + submissionId
            );
        }
        if (resultSet.getInt("invalid_answer_count") != 0) {
            throw new IllegalArgumentException(
                    "Published exam review answer identity is invalid: " + submissionId
            );
        }
        LocalDateTime submittedAt = resultSet.getObject(
                "submitted_at", LocalDateTime.class
        );
        if (submittedAt == null) {
            throw new IllegalArgumentException(
                    "Published exam submission timestamp is missing: " + submissionId
            );
        }
        return new PublishedReviewHeader(
                submissionId,
                resultSet.getInt("execution_id"),
                resultSet.getString("execution_code"),
                resultSet.getInt("exam_id"),
                resultSet.getInt("exam_version_no"),
                resultSet.getString("exam_title"),
                resultSet.getInt("course_id"),
                resultSet.getString("course_name"),
                readStoredScore(resultSet, "final_score", true,
                        "Published final score", submissionId),
                resultSet.getString("teacher_feedback"),
                submittedAt,
                resultSet.getObject("reviewed_at", LocalDateTime.class),
                requirePublishedAt(resultSet, submissionId),
                expectedQuestionCount
        );
    }

    private List<PublishedExamQuestionReviewDTO> loadPublishedReviewQuestions(
            Connection connection, PublishedReviewHeader header
    ) throws SQLException {
        List<PublishedExamQuestionReviewDTO> questions = new ArrayList<>();
        Set<Integer> orders = new HashSet<>();
        Set<String> identities = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
                STUDENT_PUBLISHED_REVIEW_QUESTIONS_SQL
        )) {
            statement.setInt(1, header.submissionId());
            statement.setInt(2, header.examId());
            statement.setInt(3, header.examVersionNo());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    int order = resultSet.getInt("order_number");
                    int questionId = resultSet.getInt("question_id");
                    int versionNo = resultSet.getInt("question_version_no");
                    String identity = questionId + ":" + versionNo;
                    if (order <= 0 || !orders.add(order)) {
                        throw new IllegalArgumentException(
                                "Published exam question order is invalid: "
                                        + header.submissionId()
                        );
                    }
                    if (!identities.add(identity)) {
                        throw new IllegalArgumentException(
                                "Published exam question identity is duplicated: "
                                        + header.submissionId()
                        );
                    }
                    if (resultSet.getInt("option_count") != 4) {
                        throw new IllegalArgumentException(
                                "Published exam answer options are invalid: " + questionId
                        );
                    }
                    questions.add(mapPublishedReviewQuestion(
                            resultSet, header.submissionId()
                    ));
                }
            }
        }
        if (questions.size() != header.expectedQuestionCount()) {
            throw new IllegalArgumentException(
                    "Published exam review question snapshot is incomplete: "
                            + header.submissionId()
            );
        }
        for (int index = 0; index < questions.size(); index++) {
            if (questions.get(index).getOrderNumber() != index + 1) {
                throw new IllegalArgumentException(
                        "Published exam question order is not contiguous: "
                                + header.submissionId()
                );
            }
        }
        return List.copyOf(questions);
    }

    private PublishedExamQuestionReviewDTO mapPublishedReviewQuestion(
            ResultSet resultSet, int submissionId
    ) throws SQLException {
        int questionId = resultSet.getInt("question_id");
        int questionVersionNo = resultSet.getInt("question_version_no");
        int correctOption = resultSet.getInt("correct_option_number");
        BigDecimal maximumScore = readMaximumScore(resultSet, submissionId);
        Integer answerId = resultSet.getObject("answer_id", Integer.class);
        Integer selectedOption = null;
        BigDecimal awardedScore;
        PublishedAnswerOutcome outcome;
        if (answerId == null) {
            awardedScore = BigDecimal.ZERO.setScale(maximumScore.scale());
            outcome = PublishedAnswerOutcome.UNANSWERED;
        } else {
            Integer answeredVersion = resultSet.getObject(
                    "answered_question_version_no", Integer.class
            );
            selectedOption = resultSet.getObject(
                    "selected_option_number", Integer.class
            );
            Boolean persistedCorrect = resultSet.getObject("is_correct", Boolean.class);
            awardedScore = readStoredScore(
                    resultSet, "awarded_score", true,
                    "Published answer score", submissionId
            );
            if (answeredVersion == null || answeredVersion != questionVersionNo
                    || selectedOption == null || persistedCorrect == null) {
                throw new IllegalArgumentException(
                        "Published answer state is incomplete: " + questionId
                );
            }
            boolean selectionIsCorrect = selectedOption == correctOption;
            if (persistedCorrect != selectionIsCorrect) {
                throw new IllegalArgumentException(
                        "Published answer correctness is inconsistent: " + questionId
                );
            }
            if (selectionIsCorrect
                    && awardedScore.compareTo(maximumScore) != 0) {
                throw new IllegalArgumentException(
                        "Published correct-answer score is inconsistent: " + questionId
                );
            }
            if (!selectionIsCorrect
                    && awardedScore.compareTo(BigDecimal.ZERO) != 0) {
                throw new IllegalArgumentException(
                        "Published incorrect-answer score is inconsistent: " + questionId
                );
            }
            outcome = selectionIsCorrect
                    ? PublishedAnswerOutcome.CORRECT
                    : PublishedAnswerOutcome.INCORRECT;
        }
        return new PublishedExamQuestionReviewDTO(
                resultSet.getInt("order_number"), questionId, questionVersionNo,
                resultSet.getString("content"), resultSet.getString("topic"),
                resultSet.getString("difficulty"),
                resultSet.getString("question_type"),
                resultSet.getString("illustration_path"),
                List.of(resultSet.getString("answer_option_1"),
                        resultSet.getString("answer_option_2"),
                        resultSet.getString("answer_option_3"),
                        resultSet.getString("answer_option_4")),
                selectedOption, correctOption, outcome, awardedScore, maximumScore,
                QuestionIllustrationJdbcSupport.readDto(resultSet)
        );
    }

    private static void requirePositivePublishedReviewId(int value, String message) {
        if (value <= 0) throw new IllegalArgumentException(message);
    }

    private SubmissionStatus parseSubmissionStatus(String value, int submissionId) {
        try {
            return SubmissionStatus.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalArgumentException(
                    "Submission status is invalid: " + submissionId,
                    exception
            );
        }
    }

    private BigDecimal readStoredScore(ResultSet resultSet, String column,
                                       boolean required, String label,
                                       int submissionId) throws SQLException {
        BigDecimal score = resultSet.getBigDecimal(column);
        if (score == null) {
            if (required) {
                throw new IllegalArgumentException(
                        label + " is missing: " + submissionId
                );
            }
            return null;
        }
        if (score.scale() > 2
                || score.compareTo(BigDecimal.ZERO) < 0
                || score.compareTo(new BigDecimal("100.00")) > 0) {
            throw new IllegalArgumentException(
                    label + " is invalid: " + submissionId
            );
        }
        return score;
    }

    private BigDecimal readMaximumScore(ResultSet resultSet, int submissionId)
            throws SQLException {
        BigDecimal maximum = readStoredScore(
                resultSet,
                "maximum_score",
                true,
                "Submission question maximum score",
                submissionId
        );
        if (maximum.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Submission question maximum score is invalid: " + submissionId
            );
        }
        return maximum;
    }

    private LocalDateTime requirePublishedAt(ResultSet resultSet, int submissionId)
            throws SQLException {
        LocalDateTime publishedAt = resultSet.getObject(
                "published_at",
                LocalDateTime.class
        );
        if (publishedAt == null) {
            throw new IllegalArgumentException(
                    "Published grade timestamp is missing: " + submissionId
            );
        }
        return publishedAt;
    }

    private List<StudentAnswer> loadAnswerEntities(Connection connection,
                                                   int submissionId)
            throws SQLException {
        List<StudentAnswer> answers = new ArrayList<>();
        int previousOrder = 0;
        try (PreparedStatement statement = connection.prepareStatement(
                SUBMISSION_ANSWERS_ENTITY_SQL
        )) {
            statement.setInt(1, submissionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Integer orderNumber = resultSet.getObject(
                            "order_number",
                            Integer.class
                    );
                    if (orderNumber == null || orderNumber <= previousOrder) {
                        throw new IllegalArgumentException(
                                "Submission answer order is invalid: " + submissionId
                        );
                    }
                    previousOrder = orderNumber;
                    answers.add(StudentAnswer.rehydrate(
                            resultSet.getInt("answer_id"),
                            resultSet.getInt("submission_id"),
                            resultSet.getInt("question_id"),
                            resultSet.getInt("question_version_no"),
                            resultSet.getInt("selected_option_number"),
                            resultSet.getString("answer_content"),
                            resultSet.getObject("is_correct", Boolean.class),
                            resultSet.getBigDecimal("score_received"),
                            resultSet.getObject("created_at", LocalDateTime.class),
                            resultSet.getObject("updated_at", LocalDateTime.class)
                    ));
                }
            }
        }
        return answers;
    }

    private void requireInProgress(SubmissionRecord submission) {
        if (submission.status != SubmissionStatus.IN_PROGRESS) {
            throw new IllegalStateException("Exam attempt already submitted");
        }
    }

    private void requireBeforeDeadline(SubmissionRecord submission, LocalDateTime now) {
        if (!now.isBefore(deadline(submission))) {
            throw new IllegalStateException("Exam time has expired");
        }
    }

    private void requireMatchingExecution(ExamExecution supplied,
                                          LockedExecution persisted) {
        if (supplied.getExecutionId() != persisted.executionId
                || supplied.getExamId() != persisted.examId
                || supplied.getExamVersionNo() != persisted.examVersionNo
                || supplied.getDurationMinutes() != persisted.durationMinutes
                || !supplied.getExecutionCode().equals(persisted.executionCode)
                || !supplied.getOpeningTime().equals(persisted.openingTime)
                || !supplied.getClosingTime().equals(persisted.closingTime)) {
            throw new IllegalStateException(
                    "Execution state does not match supplied entity"
            );
        }
    }

    private void requirePersistedSubmissionId(ExamSubmission submission) {
        if (submission.getSubmissionId() <= 0) {
            throw new IllegalArgumentException("Persisted submission ID must be positive");
        }
    }

    private void requireReviewEntityState(int managerUserId,
                                          ExamSubmission submission) {
        if (!isFinalizedUnpublishedStatus(submission.getStatus())
                || submission.getAutomaticScoreValue().isEmpty()
                || submission.getServerFinalScore().isEmpty()
                || submission.getReviewedByUserId() == null
                || submission.getReviewedAt() == null
                || submission.getPublishedByUserId() != null
                || submission.getPublishedAt() != null) {
            throw new IllegalStateException("Submission review transition is incomplete");
        }
        if (submission.getReviewedByUserId() != managerUserId) {
            throw new IllegalArgumentException(
                    "Review actor does not match authenticated manager"
            );
        }
        if (!submission.getReviewedAt().equals(submission.getUpdatedAt())) {
            throw new IllegalStateException("Submission review timestamp is inconsistent");
        }
    }

    private void requirePublicationEntityState(int managerUserId,
                                               ExamSubmission submission) {
        if (submission.getStatus() != SubmissionStatus.PUBLISHED
                || submission.getAutomaticScoreValue().isEmpty()
                || submission.getServerFinalScore().isEmpty()
                || submission.getReviewedByUserId() == null
                || submission.getReviewedAt() == null
                || submission.getPublishedByUserId() == null
                || submission.getPublishedAt() == null) {
            throw new IllegalStateException("Submission publication transition is incomplete");
        }
        if (submission.getPublishedByUserId() != managerUserId) {
            throw new IllegalArgumentException(
                    "Publication actor does not match authenticated manager"
            );
        }
        if (!submission.getPublishedAt().equals(submission.getUpdatedAt())) {
            throw new IllegalStateException(
                    "Submission publication timestamp is inconsistent"
            );
        }
    }

    private void requireMatchingSubmissionIdentity(ExamSubmission supplied,
                                                   SubmissionEntityData persisted) {
        if (supplied.getSubmissionId() != persisted.submissionId
                || supplied.getExecutionId() != persisted.executionId
                || supplied.getExamId() != persisted.examId
                || supplied.getExamVersionNo() != persisted.examVersionNo
                || supplied.getStudentUserId() != persisted.studentUserId) {
            throw new IllegalStateException(
                    "Submission state does not match persisted attempt"
            );
        }
    }

    private void requireReviewSourceState(SubmissionEntityData source,
                                          ExamSubmission submission) {
        if (!isFinalizedUnpublishedStatus(source.status)
                || source.automaticScore == null
                || source.finalScore == null
                || source.reviewedByUserId != null
                || source.reviewedAt != null
                || source.teacherFeedback != null
                || source.manualChangeReason != null
                || source.publishedByUserId != null
                || source.publishedAt != null) {
            throw new IllegalStateException("Submission is not awaiting review");
        }
        BigDecimal suppliedAutomatic = submission.getAutomaticScoreValue().orElseThrow();
        if (source.status != submission.getStatus()
                || !Objects.equals(source.submittedAt, submission.getSubmittedAt())
                || !Objects.equals(source.actualDurationMinutes,
                        submission.getActualDurationMinutes())
                || !Objects.equals(source.automaticScore, suppliedAutomatic)
                || !Objects.equals(source.finalScore, source.automaticScore)) {
            throw new IllegalStateException("Submission review state changed");
        }
    }

    private void requirePublicationSourceState(SubmissionEntityData source,
                                               ExamSubmission submission) {
        if (!isFinalizedUnpublishedStatus(source.status)
                || source.automaticScore == null
                || source.finalScore == null
                || source.reviewedByUserId == null
                || source.reviewedAt == null
                || source.publishedByUserId != null
                || source.publishedAt != null) {
            throw new IllegalStateException("Submission is not ready for publication");
        }
        if (!matchingReviewAndGradeState(source, submission)
                || !Objects.equals(source.updatedAt, submission.getReviewedAt())) {
            throw new IllegalStateException("Submission publication state changed");
        }
    }

    private void requireMatchingPublishedState(SubmissionEntityData source,
                                               ExamSubmission submission) {
        if (!matchingReviewAndGradeState(source, submission)
                || !Objects.equals(source.publishedByUserId,
                        submission.getPublishedByUserId())
                || !Objects.equals(source.publishedAt, submission.getPublishedAt())
                || !Objects.equals(source.updatedAt, submission.getUpdatedAt())) {
            throw new IllegalStateException("Submission publication state changed");
        }
    }

    private boolean matchingReviewAndGradeState(SubmissionEntityData source,
                                                ExamSubmission submission) {
        return Objects.equals(source.submittedAt, submission.getSubmittedAt())
                && Objects.equals(source.actualDurationMinutes,
                        submission.getActualDurationMinutes())
                && Objects.equals(source.automaticScore,
                        submission.getAutomaticScoreValue().orElse(null))
                && Objects.equals(source.finalScore,
                        submission.getServerFinalScore().orElse(null))
                && Objects.equals(source.teacherFeedback,
                        submission.getTeacherFeedback())
                && Objects.equals(source.manualChangeReason,
                        submission.getManualChangeReason())
                && Objects.equals(source.reviewedByUserId,
                        submission.getReviewedByUserId())
                && Objects.equals(source.reviewedAt, submission.getReviewedAt());
    }

    private boolean isFinalizedUnpublishedStatus(SubmissionStatus status) {
        return status == SubmissionStatus.SUBMITTED
                || status == SubmissionStatus.AUTO_SUBMITTED;
    }

    private IllegalArgumentException examAttemptNotFound(int submissionId) {
        return new IllegalArgumentException("Exam attempt not found: " + submissionId);
    }

    private void requireMatchingSubmission(ExamSubmission supplied,
                                           SubmissionRecord persisted) {
        if (supplied.getSubmissionId() != persisted.submissionId
                || supplied.getExecutionId() != persisted.execution.executionId
                || supplied.getExamId() != persisted.execution.examId
                || supplied.getExamVersionNo()
                != persisted.execution.examVersionNo
                || supplied.getStudentUserId() != persisted.studentUserId
                || !supplied.getStartedAt().equals(persisted.startedAt)
                || supplied.getAllocatedDurationMinutes()
                != persisted.allocatedDurationMinutes) {
            throw new IllegalStateException(
                    "Submission state does not match persisted attempt"
            );
        }
    }

    private void requirePersistableFinalState(ExamSubmission submission,
                                              boolean automatic) {
        if (submission.getSubmissionId() <= 0) {
            throw new IllegalArgumentException("Persisted submission ID must be positive");
        }
        SubmissionStatus status = submission.getStatus();
        if (automatic) {
            if (status != SubmissionStatus.AUTO_SUBMITTED) {
                throw new IllegalArgumentException(
                        "Automatic submission must be AUTO_SUBMITTED"
                );
            }
        } else if (status != SubmissionStatus.SUBMITTED
                && status != SubmissionStatus.AUTO_SUBMITTED) {
            throw new IllegalArgumentException(
                    "Student submission must be finalized"
            );
        }
        if (submission.getSubmittedAt() == null
                || submission.getActualDurationMinutes() == null) {
            throw new IllegalArgumentException(
                    "Finalized submission state is incomplete"
            );
        }
        if (submission.getReviewedByUserId() != null
                || submission.getReviewedAt() != null
                || submission.getPublishedByUserId() != null
                || submission.getPublishedAt() != null
                || submission.getStatus() == SubmissionStatus.PUBLISHED) {
            throw new IllegalArgumentException(
                    "Finalization persistence cannot publish or review results"
            );
        }
    }

    private Optional<Integer> lockExamQuestionVersion(Connection connection,
                                                      SubmissionRecord submission,
                                                      int questionId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                LOCK_EXAM_QUESTION_SQL
        )) {
            statement.setInt(1, submission.execution.examId);
            statement.setInt(2, submission.execution.examVersionNo);
            statement.setInt(3, questionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(resultSet.getInt("question_version_no"))
                        : Optional.empty();
            }
        }
    }

    private void upsertAnswer(Connection connection, int submissionId,
                              int questionId, int questionVersionNo,
                              int selectedOptionNumber, LocalDateTime now)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_ANSWER_SQL)) {
            statement.setInt(1, submissionId);
            statement.setInt(2, questionId);
            statement.setInt(3, questionVersionNo);
            statement.setInt(4, selectedOptionNumber);
            statement.setNull(5, Types.LONGVARCHAR);
            statement.setNull(6, Types.BOOLEAN);
            statement.setNull(7, Types.DECIMAL);
            statement.setObject(8, now);
            statement.setObject(9, now);
            int affectedRows = statement.executeUpdate();
            if (affectedRows < 1 || affectedRows > 2) {
                throw new SQLException("Answer save did not affect one logical row");
            }
        }
    }

    private void incrementFinalizationCounter(Connection connection, int executionId,
                                              SubmissionStatus finalStatus)
            throws SQLException {
        String sql = finalStatus == SubmissionStatus.SUBMITTED
                ? INCREMENT_SUBMITTED_COUNT_SQL
                : INCREMENT_AUTO_SUBMITTED_COUNT_SQL;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, executionId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Execution submission count update failed");
            }
        }
    }

    private Optional<ExtensionTarget> lockExtensionTarget(
            Connection connection,
            int authenticatedManagerId,
            int submissionId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                LOCK_MANAGER_EXTENSION_SQL
        )) {
            statement.setInt(1, authenticatedManagerId);
            statement.setInt(2, submissionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new ExtensionTarget(
                        resultSet.getObject("started_at", LocalDateTime.class),
                        SubmissionStatus.valueOf(resultSet.getString("status")),
                        resultSet.getInt("allocated_duration_minutes"),
                        resultSet.getInt("extra_minutes")
                ));
            }
        }
    }

    private void insertExtensionAudit(Connection connection, int authenticatedManagerId,
                                      int submissionId, int addedMinutes,
                                      String reason, LocalDateTime now)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_EXTENSION_AUDIT_SQL
        )) {
            statement.setInt(1, submissionId);
            statement.setInt(2, addedMinutes);
            statement.setString(3, reason);
            statement.setInt(4, authenticatedManagerId);
            statement.setObject(5, now);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Submission extension audit insert failed");
            }
        }
    }

    private void updateEntityExtension(Connection connection,
                                       ExamSubmission submission,
                                       int previousExtraMinutes,
                                       String normalizedReason)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                UPDATE_ENTITY_EXTENSION_SQL
        )) {
            statement.setInt(1, submission.getExtraMinutes());
            statement.setString(2, normalizedReason);
            statement.setObject(3, submission.getUpdatedAt());
            statement.setInt(4, submission.getSubmissionId());
            statement.setInt(5, previousExtraMinutes);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException(
                        "Extension state does not match persisted submission"
                );
            }
        }
    }

    private void updateReview(Connection connection,
                              SubmissionEntityData source,
                              ExamSubmission submission) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                UPDATE_REVIEW_SQL
        )) {
            statement.setString(1, submission.getStatus().name());
            statement.setBigDecimal(
                    2,
                    submission.getServerFinalScore().orElseThrow()
            );
            statement.setString(3, submission.getTeacherFeedback());
            statement.setString(4, submission.getManualChangeReason());
            statement.setInt(5, submission.getReviewedByUserId());
            statement.setObject(6, submission.getReviewedAt());
            statement.setObject(7, submission.getUpdatedAt());
            statement.setInt(8, submission.getSubmissionId());
            statement.setString(9, source.status.name());
            statement.setBigDecimal(10, source.automaticScore);
            statement.setBigDecimal(11, source.finalScore);
            statement.setObject(12, source.updatedAt);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("Submission review state changed");
            }
        }
    }

    private void updatePublication(Connection connection,
                                   SubmissionEntityData source,
                                   ExamSubmission submission) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                UPDATE_PUBLICATION_SQL
        )) {
            statement.setString(1, submission.getStatus().name());
            statement.setInt(2, submission.getPublishedByUserId());
            statement.setObject(3, submission.getPublishedAt());
            statement.setObject(4, submission.getUpdatedAt());
            statement.setInt(5, submission.getSubmissionId());
            statement.setString(6, source.status.name());
            statement.setBigDecimal(7, source.automaticScore);
            statement.setBigDecimal(8, source.finalScore);
            statement.setInt(9, source.reviewedByUserId);
            statement.setObject(10, source.reviewedAt);
            statement.setString(11, source.teacherFeedback);
            statement.setString(12, source.manualChangeReason);
            statement.setObject(13, source.updatedAt);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("Submission publication state changed");
            }
        }
    }

    private void persistFinalizedEntity(Connection connection,
                                        SubmissionRecord source,
                                        ExamSubmission submission)
            throws SQLException {
        persistEntityAnswerGrades(connection, source, submission);
        try (PreparedStatement statement = connection.prepareStatement(
                FINALIZE_ENTITY_SUBMISSION_SQL
        )) {
            statement.setObject(1, submission.getSubmittedAt());
            statement.setString(2, submission.getStatus().name());
            statement.setInt(3, submission.getActualDurationMinutes());
            setNullableBigDecimal(
                    statement,
                    4,
                    submission.getAutomaticScoreValue().orElse(null)
            );
            setNullableBigDecimal(
                    statement,
                    5,
                    submission.getServerFinalScore().orElse(null)
            );
            statement.setObject(6, submission.getUpdatedAt());
            statement.setInt(7, submission.getSubmissionId());
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("Exam attempt already submitted");
            }
        }
        incrementFinalizationCounter(
                connection,
                source.execution.executionId,
                submission.getStatus()
        );
    }

    private void persistEntityAnswerGrades(Connection connection,
                                           SubmissionRecord source,
                                           ExamSubmission submission)
            throws SQLException {
        for (StudentAnswer answer : submission.getStudentAnswers()) {
            if (answer.getAnswerId() <= 0
                    || answer.getSubmissionId() != submission.getSubmissionId()) {
                throw new IllegalArgumentException(
                        "Submission contains an unpersisted student answer"
                );
            }
            int persistedVersion = lockExamQuestionVersion(
                    connection,
                    source,
                    answer.getQuestionId()
            ).orElseThrow(() -> new IllegalArgumentException(
                    "Question not found in exam: " + answer.getQuestionId()
            ));
            if (persistedVersion != answer.getQuestionVersionNo()) {
                throw new IllegalArgumentException(
                        "Question answer version does not match the submission"
                );
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    UPDATE_ENTITY_GRADED_ANSWER_SQL
            )) {
                Boolean correctness = answer.getCorrectness().orElse(null);
                BigDecimal score = answer.getScoreReceivedValue().orElse(null);
                if (correctness == null) {
                    statement.setNull(1, Types.BOOLEAN);
                    statement.setNull(2, Types.DECIMAL);
                } else {
                    statement.setBoolean(1, correctness);
                    statement.setBigDecimal(2, score);
                }
                statement.setObject(3, answer.getUpdatedAt());
                statement.setInt(4, answer.getAnswerId());
                statement.setInt(5, answer.getSubmissionId());
                statement.setInt(6, answer.getQuestionId());
                statement.setInt(7, answer.getQuestionVersionNo());
                if (statement.executeUpdate() != 1) {
                    throw new SQLException(
                            "Persisted answer grading update failed"
                    );
                }
            }
        }
    }

    private void setNullableBigDecimal(PreparedStatement statement, int index,
                                       BigDecimal value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.DECIMAL);
        } else {
            statement.setBigDecimal(index, value);
        }
    }

    private LocalDateTime deadline(SubmissionRecord submission) {
        return submission.startedAt.plusMinutes(
                (long) submission.allocatedDurationMinutes + submission.extraMinutes
        );
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private boolean isSubmissionCollision(SQLException exception) {
        String message = exception.getMessage();
        return exception.getErrorCode() == MYSQL_DUPLICATE_KEY_ERROR
                && "23000".equals(exception.getSQLState())
                && message != null
                && message.toLowerCase(Locale.ROOT)
                .contains("uq_exam_submissions_execution_student");
    }

    private <T> T executeInTransaction(String failureMessage,
                                       TransactionOperation<T> operation) {
        return executeInTransaction(
                failureMessage,
                failureMessage,
                operation,
                (connection, result) -> result
        );
    }

    private <T, R> R executeInTransaction(
            String failureMessage,
            String postCommitFailureMessage,
            TransactionOperation<T> operation,
            PostCommitOperation<T, R> postCommitOperation
    ) {
        try (Connection connection = databaseController.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            boolean transactionStarted = false;
            boolean transactionCommitted = false;
            boolean restorationAttempted = false;
            boolean postCommitStarted = false;
            Throwable transactionFailure = null;

            try {
                connection.setAutoCommit(false);
                transactionStarted = true;
                T result = operation.execute(connection);
                connection.commit();
                transactionCommitted = true;
                restorationAttempted = true;
                connection.setAutoCommit(originalAutoCommit);
                postCommitStarted = true;
                return postCommitOperation.execute(connection, result);
            } catch (SQLException exception) {
                transactionFailure = exception;
                if (transactionStarted && !transactionCommitted) {
                    rollbackWithSuppressed(connection, exception);
                }
                throw new IllegalStateException(
                        postCommitStarted ? postCommitFailureMessage : failureMessage,
                        exception
                );
            } catch (RuntimeException exception) {
                transactionFailure = exception;
                if (transactionStarted && !transactionCommitted) {
                    rollbackWithSuppressed(connection, exception);
                }
                throw exception;
            } finally {
                if (!restorationAttempted) {
                    restoreAutoCommit(connection, originalAutoCommit, transactionFailure);
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(failureMessage, exception);
        }
    }

    private void rollbackWithSuppressed(Connection connection, Throwable originalFailure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            originalFailure.addSuppressed(rollbackFailure);
        }
    }

    private void restoreAutoCommit(Connection connection, boolean originalAutoCommit,
                                   Throwable originalFailure) throws SQLException {
        try {
            connection.setAutoCommit(originalAutoCommit);
        } catch (SQLException restorationFailure) {
            if (originalFailure != null) {
                originalFailure.addSuppressed(restorationFailure);
                return;
            }
            throw restorationFailure;
        }
    }

    @FunctionalInterface
    private interface TransactionOperation<T> {
        T execute(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    private interface PostCommitOperation<T, R> {
        R execute(Connection connection, T result) throws SQLException;
    }

    @FunctionalInterface
    private interface SubmissionResultLoader<T> {
        T load(Connection connection, SubmissionRecord submission)
                throws SQLException;
    }

    private static final class AnswerCommand {
        private final int submissionId;
        private final int questionId;
        private final Integer questionVersionNo;
        private final int selectedOptionNumber;

        private AnswerCommand(int submissionId, int questionId,
                              Integer questionVersionNo,
                              int selectedOptionNumber) {
            this.submissionId = submissionId;
            this.questionId = questionId;
            this.questionVersionNo = questionVersionNo;
            this.selectedOptionNumber = selectedOptionNumber;
        }
    }

    private record ReviewHeader(
            int submissionId,
            int executionId,
            int examId,
            int examVersionNo,
            String examTitle,
            int studentUserId,
            String studentName,
            SubmissionStatus status,
            BigDecimal automaticScore,
            BigDecimal finalScore,
            String teacherFeedback,
            String adjustmentReason,
            int reviewerUserId,
            LocalDateTime startedAt,
            LocalDateTime submittedAt,
            LocalDateTime reviewedAt,
            LocalDateTime updatedAt,
            int publisherUserId,
            LocalDateTime publishedAt
    ) {
        private SubmissionReviewDTO toDto(
                List<SubmissionAnswerReviewDTO> answers
        ) {
            return new SubmissionReviewDTO(
                    submissionId,
                    executionId,
                    examId,
                    examVersionNo,
                    examTitle,
                    studentUserId,
                    studentName,
                    status,
                    automaticScore,
                    finalScore,
                    teacherFeedback,
                    adjustmentReason,
                    reviewerUserId,
                    startedAt,
                    submittedAt,
                    reviewedAt,
                    updatedAt,
                    publisherUserId,
                    publishedAt,
                    answers
            );
        }
    }

    private record PublishedReviewHeader(
            int submissionId,
            int executionId,
            String executionCode,
            int examId,
            int examVersionNo,
            String examTitle,
            int courseId,
            String courseName,
            BigDecimal finalScore,
            String teacherFeedback,
            LocalDateTime submittedAt,
            LocalDateTime reviewedAt,
            LocalDateTime publishedAt,
            int expectedQuestionCount
    ) {
        private PublishedExamReviewDTO toDto(
                List<PublishedExamQuestionReviewDTO> questions
        ) {
            return new PublishedExamReviewDTO(
                    submissionId, executionId, executionCode,
                    examId, examVersionNo, examTitle, courseId, courseName,
                    finalScore, teacherFeedback, submittedAt, reviewedAt,
                    publishedAt, questions
            );
        }
    }

    private static final class SubmissionEntityData {
        private final int submissionId;
        private final int executionId;
        private final int examId;
        private final int examVersionNo;
        private final int studentUserId;
        private final LocalDateTime startedAt;
        private final LocalDateTime submittedAt;
        private final SubmissionStatus status;
        private final int allocatedDurationMinutes;
        private final int extraMinutes;
        private final String extensionReason;
        private final Integer actualDurationMinutes;
        private final BigDecimal automaticScore;
        private final BigDecimal finalScore;
        private final String teacherFeedback;
        private final String manualChangeReason;
        private final Integer reviewedByUserId;
        private final LocalDateTime reviewedAt;
        private final Integer publishedByUserId;
        private final LocalDateTime publishedAt;
        private final LocalDateTime createdAt;
        private final LocalDateTime updatedAt;

        private SubmissionEntityData(
                int submissionId,
                int executionId,
                int examId,
                int examVersionNo,
                int studentUserId,
                LocalDateTime startedAt,
                LocalDateTime submittedAt,
                SubmissionStatus status,
                int allocatedDurationMinutes,
                int extraMinutes,
                String extensionReason,
                Integer actualDurationMinutes,
                BigDecimal automaticScore,
                BigDecimal finalScore,
                String teacherFeedback,
                String manualChangeReason,
                Integer reviewedByUserId,
                LocalDateTime reviewedAt,
                Integer publishedByUserId,
                LocalDateTime publishedAt,
                LocalDateTime createdAt,
                LocalDateTime updatedAt
        ) {
            this.submissionId = submissionId;
            this.executionId = executionId;
            this.examId = examId;
            this.examVersionNo = examVersionNo;
            this.studentUserId = studentUserId;
            this.startedAt = startedAt;
            this.submittedAt = submittedAt;
            this.status = status;
            this.allocatedDurationMinutes = allocatedDurationMinutes;
            this.extraMinutes = extraMinutes;
            this.extensionReason = extensionReason;
            this.actualDurationMinutes = actualDurationMinutes;
            this.automaticScore = automaticScore;
            this.finalScore = finalScore;
            this.teacherFeedback = teacherFeedback;
            this.manualChangeReason = manualChangeReason;
            this.reviewedByUserId = reviewedByUserId;
            this.reviewedAt = reviewedAt;
            this.publishedByUserId = publishedByUserId;
            this.publishedAt = publishedAt;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        private ExamSubmission rehydrate(List<StudentAnswer> answers) {
            return ExamSubmission.rehydrate(
                    submissionId,
                    executionId,
                    examId,
                    examVersionNo,
                    studentUserId,
                    startedAt,
                    submittedAt,
                    status,
                    allocatedDurationMinutes,
                    extraMinutes,
                    extensionReason,
                    actualDurationMinutes,
                    automaticScore,
                    finalScore,
                    teacherFeedback,
                    manualChangeReason,
                    reviewedByUserId,
                    reviewedAt,
                    publishedByUserId,
                    publishedAt,
                    createdAt,
                    updatedAt,
                    answers
            );
        }
    }

    private static final class LockedExecution {
        private final int executionId;
        private final String executionCode;
        private final int examId;
        private final int examVersionNo;
        private final String examTitle;
        private final String studentInstructions;
        private final LocalDateTime openingTime;
        private final LocalDateTime closingTime;
        private final int durationMinutes;
        private final ExecutionStatus status;

        private LockedExecution(int executionId, String executionCode, int examId,
                                int examVersionNo, String examTitle,
                                String studentInstructions, LocalDateTime openingTime,
                                LocalDateTime closingTime, int durationMinutes,
                                ExecutionStatus status) {
            this.executionId = executionId;
            this.executionCode = executionCode;
            this.examId = examId;
            this.examVersionNo = examVersionNo;
            this.examTitle = examTitle;
            this.studentInstructions = studentInstructions;
            this.openingTime = openingTime;
            this.closingTime = closingTime;
            this.durationMinutes = durationMinutes;
            this.status = status;
        }
    }

    private static final class SubmissionRecord {
        private final int submissionId;
        private final LockedExecution execution;
        private final int studentUserId;
        private final LocalDateTime startedAt;
        private final SubmissionStatus status;
        private final int allocatedDurationMinutes;
        private final int extraMinutes;

        private SubmissionRecord(int submissionId, LockedExecution execution,
                                 int studentUserId,
                                 LocalDateTime startedAt, SubmissionStatus status,
                                 int allocatedDurationMinutes, int extraMinutes) {
            this.submissionId = submissionId;
            this.execution = execution;
            this.studentUserId = studentUserId;
            this.startedAt = startedAt;
            this.status = status;
            this.allocatedDurationMinutes = allocatedDurationMinutes;
            this.extraMinutes = extraMinutes;
        }

    }

    private static final class ExtensionTarget {
        private final LocalDateTime startedAt;
        private final SubmissionStatus status;
        private final int allocatedDurationMinutes;
        private final int extraMinutes;

        private ExtensionTarget(LocalDateTime startedAt, SubmissionStatus status,
                                int allocatedDurationMinutes, int extraMinutes) {
            this.startedAt = startedAt;
            this.status = status;
            this.allocatedDurationMinutes = allocatedDurationMinutes;
            this.extraMinutes = extraMinutes;
        }

        private LocalDateTime deadline() {
            return startedAt.plusMinutes((long) allocatedDurationMinutes + extraMinutes);
        }
    }
}
