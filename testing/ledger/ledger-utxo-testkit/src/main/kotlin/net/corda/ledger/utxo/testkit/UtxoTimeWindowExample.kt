package net.corda.ledger.utxo.testkit

import java.time.Instant
import net.corda.ledger.lib.utxo.flow.impl.timewindow.TimeWindowBetweenImpl

val utxoTimeWindowExample = TimeWindowBetweenImpl(Instant.MIN, Instant.MAX)
