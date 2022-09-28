package net.corda.v5.ledger.utxo

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.DoNotImplement
import java.time.Duration
import java.time.Instant

/**
 * Defines an interval on a timeline; not a single, instantaneous point.
 *
 * There is no such thing as "exact" time in distributed systems, due to the underlying physics involved, and other
 * issues such as network latency. A time window represents an approximation of an instant with a margin of tolerance,
 * and may be fully bounded, or unbounded towards negative or positive infinity.
 *
 * @property from The boundary at which the time window begins, or null if the time window is unbounded towards negative infinity.
 * @property until The boundary at which the time window ends, or null if the time window is unbounded towards positive infinity.
 * @property midpoint The midpoint between a fully bounded time window, or null if the time window is unbounded towards positive or negative infinity.
 * @property duration The duration of a fully bounded time window, or null if the time window is unbounded towards positive or negative infinity.
 */
@DoNotImplement
@CordaSerializable
interface TimeWindow {
    val from: Instant?
    val until: Instant?
    val midpoint: Instant?
    val duration: Duration? get() = if (from == null || until == null) null else Duration.between(from, until)

    /**
     * Determines whether the current [TimeWindow] contains the specified [Instant].
     *
     * @param instant The [Instant] to check is contained within the current [TimeWindow].
     * @return Returns true if the current [TimeWindow] contains the specified [Instant]; otherwise, false.
     */
    operator fun contains(instant: Instant): Boolean
}