package net.corda.simulator.runtime.ledger.utxo

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import java.security.PublicKey

/**
 * Data class to store Utxo Ledger components
 */
@CordaSerializable
data class UtxoStateLedgerInfo(
    val commands: List<Command>,
    val inputStateRefs: List<StateRef>,
    val referenceStateRefs: List<StateRef>,
    val signatories: List<PublicKey>,
    val timeWindow: TimeWindow,
    val outputStates: List<ContractStateAndEncumbranceTag>,
    val attachments: List<SecureHash>,
    val notaryName: MemberX500Name,
    val notaryKey: PublicKey
)