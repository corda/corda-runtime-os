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
    private val notary: Party? = null,
    override val timeWindow: TimeWindow? = null,
    override val attachments: List<SecureHash> = listOf(),
    override val commands: List<Command> = listOf(),
    override val signatories: List<PublicKey> = listOf(),
    override val inputStateRefs: List<StateRef> = listOf(),
    override val referenceStateRefs: List<StateRef> = listOf(),
    override val outputStates: List<ContractStateAndEncumbranceTag> = listOf()
) : UtxoTransactionBuilderData {
    override fun getNotary(): Party? {
        return notary
    }
}