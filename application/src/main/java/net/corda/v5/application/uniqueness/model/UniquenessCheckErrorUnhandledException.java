package net.corda.v5.application.uniqueness.model;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Occurs when there's an unhandled exception in the uniqueness checker. */
public interface UniquenessCheckErrorUnhandledException extends UniquenessCheckError {
    @NotNull
    String getUnhandledExceptionType();

    @NotNull
    String getUnhandledExceptionMessage();
}
