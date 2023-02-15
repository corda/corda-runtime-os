package net.corda.v5.application.uniqueness.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/** Occurs when the specified time is outside the allowed tolerance. */
public interface UniquenessCheckErrorTimeWindowOutOfBounds extends UniquenessCheckError {
    @NotNull
    Instant getEvaluationTimestamp();

    @Nullable
    Instant getTimeWindowLowerBound();

    @NotNull
    Instant getTimeWindowUpperBound();
}
