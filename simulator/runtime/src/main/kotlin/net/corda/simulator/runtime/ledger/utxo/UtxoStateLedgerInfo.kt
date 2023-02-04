package net.corda.simulator.runtime.ledger.utxo

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import java.security.PublicKey

/**
 * Data class to store Utxo Ledger data
 */
@CordaSerializable
data class UtxoStateLedgerInfo(
    val commands: List<Command>,
    val inputStateRefs: List<StateRef>,
    val notary: Party,
    val referenceStateRefs: List<StateRef>,
    val signatories: List<PublicKey>,
    val timeWindow: TimeWindow,
    val outputStates: List<ContractState>,
    val attachments: List<SecureHash>,
)