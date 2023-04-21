package net.corda.v5.application.uniqueness.model;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/**
 * Representation of the result of a uniqueness check request.
 */
public interface UniquenessCheckResult {
    /**
     * @return The timestamp when the request was processed.
    */
    @NotNull
    Instant getResultTimestamp();
}
