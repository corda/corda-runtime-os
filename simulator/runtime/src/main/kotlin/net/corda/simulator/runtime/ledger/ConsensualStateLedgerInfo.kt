package net.corda.simulator.runtime.ledger

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.consensual.ConsensualState
import java.security.PublicKey
import java.time.Instant

@CordaSerializable
data class ConsensualStateLedgerInfo(
    val states: List<ConsensualState>,
    val timestamp: Instant
) {
    val requiredSigningKeys: Set<PublicKey> = states.flatMap { it.participants }.toSet()
}