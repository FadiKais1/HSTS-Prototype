package hsts.common;

import java.io.Serializable;

public enum RequestType implements Serializable {
    GET_ALL_QUESTIONS,
    GET_QUESTION_BY_ID,
    UPDATE_QUESTION,
    LOGIN,
    LOGOUT,
    GET_MY_COURSES,
    LIST_QUESTIONS,
    CREATE_QUESTION,
    ACTIVATE_QUESTION,
    DEACTIVATE_QUESTION,
    GET_QUESTION_HISTORY
}
