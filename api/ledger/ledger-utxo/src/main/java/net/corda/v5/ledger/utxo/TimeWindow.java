package net.corda.v5.ledger.utxo;


import net.corda.v5.base.annotations.CordaSerializable;
import net.corda.v5.base.annotations.DoNotImplement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * Defines an interval on a timeline; not a single, instantaneous point.
 * <p>
 * There is no such thing as "exact" time in distributed systems, due to the underlying physics involved, and other
 * issues such as network latency. A time window represents an approximation of an instant with a margin of tolerance,
 * and may be fully bounded.
 */
@DoNotImplement
@CordaSerializable
public interface TimeWindow {

    /**
     * Gets the boundary at which the time window begins.
     *
     * @return Returns the boundary at which the time window begins.
     */
    @Nullable
    Instant getFrom();

    /**
     * Gets the boundary at which the time window ends.
     *
     * @return Returns the boundary at which the time window ends.
     */
    @NotNull
    Instant getUntil();

    /**
     * Determines whether the current {@link TimeWindow} contains the specified {@link Instant}.
     *
     * @param instant The {@link Instant} to check is contained within the current {@link TimeWindow}.
     * @return Returns true if the current {@link TimeWindow} contains the specified {@link Instant}; otherwise, false.
     */
    boolean contains(@NotNull Instant instant);
}
