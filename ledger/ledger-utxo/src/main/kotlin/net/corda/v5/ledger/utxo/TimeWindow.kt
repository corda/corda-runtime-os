package net.corda.v5.ledger.utxo

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.DoNotImplement
import java.time.Instant

/**
 * Defines an interval on a timeline; not a single, instantaneous point.
 *
 * There is no such thing as "exact" time in distributed systems, due to the underlying physics involved, and other
 * issues such as network latency. A time window represents an approximation of an instant with a margin of tolerance,
 * and may be fully bounded.
 *
 * @property from The boundary at which the time window begins.
 * @property until The boundary at which the time window ends.
 */
@DoNotImplement
@CordaSerializable
interface TimeWindow {
    val from: Instant
    val until: Instant

    /**
     * Determines whether the current [TimeWindow] contains the specified [Instant].
     *
     * @param instant The [Instant] to check is contained within the current [TimeWindow].
     * @return Returns true if the current [TimeWindow] contains the specified [Instant]; otherwise, false.
     */
    operator fun contains(instant: Instant): Boolean
}
