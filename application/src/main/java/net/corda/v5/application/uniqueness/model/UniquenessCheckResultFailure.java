package net.corda.v5.application.uniqueness.model;

import org.jetbrains.annotations.NotNull;

/**
 * This result will be returned by the uniqueness checker if the request was unsuccessful.
 */
public interface UniquenessCheckResultFailure extends UniquenessCheckResult {
    /**
     * @return Specific details about why the request was unsuccessful.
     */
    @NotNull
    UniquenessCheckError getError();
}
