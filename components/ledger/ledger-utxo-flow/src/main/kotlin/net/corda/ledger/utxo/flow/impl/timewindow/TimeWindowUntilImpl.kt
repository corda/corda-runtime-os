package net.corda.ledger.utxo.flow.impl.timewindow

import net.corda.v5.ledger.utxo.TimeWindow
import java.time.Instant

/**
 * Represents an unbounded time window that tends towards negative infinity.
 *
 * @constructor Creates a new instance of the [TimeWindowUntilImpl] data class.
 * @property until The boundary at which the time window ends.
 */
data class TimeWindowUntilImpl(private val until: Instant) : TimeWindow {

    override fun getFrom(): Instant {
        return Instant.MIN
    }

    override fun getUntil(): Instant {
        return until
    }

    /**
     * Determines whether the current [TimeWindow] contains the specified [Instant].
     *
     * @param instant The [Instant] to check is contained within the current [TimeWindow].
     * @return Returns true if the current [TimeWindow] contains the specified [Instant]; otherwise, false.
     */
    override fun contains(instant: Instant): Boolean {
        return instant <= until
    }

    /**
     * Returns a string that represents the current object.
     *
     * @return Returns a string that represents the current object.
     */
    override fun toString(): String {
        return "infinity to $until"
    }
}
