package net.corda.ledger.utxo.flow.impl.timewindow

import net.corda.v5.ledger.utxo.TimeWindow
import java.time.Instant

/**
 * Represents an unbounded time window that tends towards positive infinity.
 *
 * @constructor Creates a new instance of the [TimeWindowFromImpl] data class.
 * @property from The boundary at which the time window begins.
 * @property until Always [Instant.MAX] as this time window tends towards positive infinity.
 */
data class TimeWindowFromImpl(private val from: Instant) : TimeWindow {

    override fun getFrom(): Instant {
        return from
    }

    override fun getUntil(): Instant {
        return Instant.MAX
    }

    /**
     * Determines whether the current [TimeWindow] contains the specified [Instant].
     *
     * @param instant The [Instant] to check is contained within the current [TimeWindow].
     * @return Returns true if the current [TimeWindow] contains the specified [Instant]; otherwise, false.
     */
    override fun contains(instant: Instant): Boolean {
        return instant >= from
    }

    /**
     * Returns a string that represents the current object.
     *
     * @return Returns a string that represents the current object.
     */
    override fun toString(): String {
        return "$from to infinity"
    }
}
