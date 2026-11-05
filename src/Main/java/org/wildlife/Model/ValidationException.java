package org.wildlife.Model;


/** A domain rule or user input is invalid; the operation must not change state. */
public class ValidationException extends IllegalArgumentException {
    public ValidationException(String message) { super(message); }
}
