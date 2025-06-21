package com.LegMeat.rbo.Exceptions;

public class ExternalCommandError extends RuntimeException {
    public ExternalCommandError(String message) {
        super(message);
    }
}
