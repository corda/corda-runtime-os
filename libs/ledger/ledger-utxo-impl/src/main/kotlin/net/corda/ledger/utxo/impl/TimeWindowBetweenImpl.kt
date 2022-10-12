package net.corda.ledger.utxo.impl

import net.corda.v5.ledger.utxo.TimeWindow
import java.time.Instant

/**
 * Represents a fully bounded time window.
 *
 * @property from The boundary at which the time window begins.
 * @property until The boundary at which the time window ends.
 */
data class TimeWindowBetweenImpl(override val from: Instant, override val until: Instant) : TimeWindow {

    init {
        require(from < until) { "from must be earlier than until." }
    }

    override fun contains(instant: Instant): Boolean {
        return instant >= from && instant < until
    }

    override fun toString(): String {
        return "$from to $until"
    }
}
