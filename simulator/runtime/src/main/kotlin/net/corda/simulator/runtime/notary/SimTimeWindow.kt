package net.corda.simulator.runtime.notary

import net.corda.v5.ledger.utxo.TimeWindow
import java.time.Instant

data class SimTimeWindow(
    override val from: Instant?,
    override val until: Instant
):TimeWindow {

    override fun contains(instant: Instant): Boolean {
        TODO("Not yet implemented")
    }
}
