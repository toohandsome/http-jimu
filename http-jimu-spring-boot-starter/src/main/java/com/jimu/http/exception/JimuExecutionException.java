package com.jimu.http.exception;

public class JimuExecutionException extends JimuException {
    public JimuExecutionException(String message) {
        super(message);
    }

    public JimuExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
