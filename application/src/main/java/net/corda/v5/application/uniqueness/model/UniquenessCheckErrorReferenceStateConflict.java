package net.corda.v5.application.uniqueness.model;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Occurs when one or more reference states have already been consumed by another transaction. */
public interface UniquenessCheckErrorReferenceStateConflict extends UniquenessCheckError {
    /** Specifies which reference states have already been consumed in another transaction. */
    @NotNull
    List<UniquenessCheckStateDetails> getConflictingStates();
}
