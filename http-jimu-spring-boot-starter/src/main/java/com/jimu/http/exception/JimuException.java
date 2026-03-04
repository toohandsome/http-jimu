package com.jimu.http.exception;

public class JimuException extends RuntimeException {
    public JimuException(String message) {
        super(message);
    }

    public JimuException(String message, Throwable cause) {
        super(message, cause);
    }
}
