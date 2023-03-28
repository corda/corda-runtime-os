package net.corda.simulator.runtime.notary

import net.corda.v5.ledger.utxo.TimeWindow
import java.time.Instant

data class SimTimeWindow(
    private val from: Instant?,
    private val until: Instant
):TimeWindow {
    override fun getFrom(): Instant? {
        return from
    }

    override fun getUntil(): Instant {
        return until
    }

    override fun contains(instant: Instant): Boolean {
        TODO("Not yet implemented")
    }
}
