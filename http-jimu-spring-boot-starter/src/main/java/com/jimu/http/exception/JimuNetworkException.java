package com.jimu.http.exception;

public class JimuNetworkException extends JimuException {
    public JimuNetworkException(String message) {
        super(message);
    }

    public JimuNetworkException(String message, Throwable cause) {
        super(message, cause);
    }
}
