package hsts.common;

import java.io.Serializable;

public enum RequestType implements Serializable {
    GET_ALL_QUESTIONS,
    GET_QUESTION_BY_ID,
    UPDATE_QUESTION
}
