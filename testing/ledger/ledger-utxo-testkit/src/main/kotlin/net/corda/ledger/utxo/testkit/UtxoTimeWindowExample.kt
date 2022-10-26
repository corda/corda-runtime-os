package net.corda.ledger.utxo.testkit

import net.corda.ledger.utxo.impl.timewindow.TimeWindowBetweenImpl
import java.time.Instant

val utxoTimeWindowExample = TimeWindowBetweenImpl(Instant.MIN, Instant.MAX)