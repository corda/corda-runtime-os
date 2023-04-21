package net.corda.v5.application.uniqueness.model;

/**
 * Representation of errors that can be raised by the uniqueness checker. These errors are returned
 * by the uniqueness checker back to the uniqueness client service, which propagates the errors to
 * callers.
 */
public interface UniquenessCheckError {
}
