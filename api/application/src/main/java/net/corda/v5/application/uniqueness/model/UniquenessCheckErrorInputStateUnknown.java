package net.corda.v5.application.uniqueness.model;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Occurs when one or more input states are not known to the uniqueness checker. */
public interface UniquenessCheckErrorInputStateUnknown extends UniquenessCheckError {
    /** Specifies which states are not known to the uniqueness checker. */
    @NotNull
    List<UniquenessCheckStateRef> getUnknownStates();
}
