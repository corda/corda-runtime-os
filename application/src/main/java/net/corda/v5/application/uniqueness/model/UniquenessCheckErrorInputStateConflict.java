package net.corda.v5.application.uniqueness.model;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Occurs when one or more input states have already been consumed by another transaction. */
public interface UniquenessCheckErrorInputStateConflict extends UniquenessCheckError {
    /** Specifies which states have already been consumed in another transaction. */
    @NotNull
    List<UniquenessCheckStateDetails> getConflictingStates();
}
