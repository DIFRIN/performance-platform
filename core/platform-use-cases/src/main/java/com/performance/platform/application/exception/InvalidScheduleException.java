package com.performance.platform.application.exception;

/** Expression de planification invalide (delai, date ou cron). */
public class InvalidScheduleException extends RuntimeException {

    public InvalidScheduleException(String message) {
        super(message);
    }

    public InvalidScheduleException(String message, Throwable cause) {
        super(message, cause);
    }
}
