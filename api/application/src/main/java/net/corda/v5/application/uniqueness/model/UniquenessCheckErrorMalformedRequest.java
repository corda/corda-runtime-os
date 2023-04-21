package net.corda.v5.application.uniqueness.model;

import org.jetbrains.annotations.NotNull;

/** Occurs when data in the received request is invalid. */
public interface UniquenessCheckErrorMalformedRequest extends UniquenessCheckError {
    @NotNull
    String getErrorText();
}
