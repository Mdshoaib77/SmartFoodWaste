package com.foodwaste.service;

/** Business-rule or database failure with a message that is safe to show to the user. */
public class ServiceException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ServiceException(String message) { super(message); }

    public ServiceException(String message, Throwable cause) { super(message, cause); }
}
