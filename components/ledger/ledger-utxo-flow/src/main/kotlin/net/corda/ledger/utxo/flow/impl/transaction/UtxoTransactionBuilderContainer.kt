package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import java.security.PublicKey

@CordaSerializable
data class UtxoTransactionBuilderContainer(
    var notary: Party? = null,
    var timeWindow: TimeWindow? = null,
    val attachments: List<SecureHash> = listOf(),
    val commands: List<Command> = listOf(),
    val signatories: List<PublicKey> = listOf(),
    val inputStateRefs: List<StateRef> = listOf(),
    val referenceStateRefs: List<StateRef> = listOf(),
    val outputStates: List<ContractStateAndEncumbranceTag> = listOf()
)