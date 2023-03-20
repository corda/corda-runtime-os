package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import java.security.PublicKey
import kotlin.reflect.jvm.internal.impl.descriptors.Visibilities.Public

@CordaSerializable
data class UtxoTransactionBuilderContainer(
    private val notaryName: MemberX500Name? = null,
    private val notaryKey: PublicKey? = null,
    override val timeWindow: TimeWindow? = null,
    override val attachments: List<SecureHash> = listOf(),
    override val commands: List<Command> = listOf(),
    override val signatories: List<PublicKey> = listOf(),
    override val inputStateRefs: List<StateRef> = listOf(),
    override val referenceStateRefs: List<StateRef> = listOf(),
    override val outputStates: List<ContractStateAndEncumbranceTag> = listOf()
) : UtxoTransactionBuilderData {
    override fun getNotaryName(): MemberX500Name? {
        return notaryName
    }

    override fun getNotaryKey(): PublicKey? {
        return notaryKey
    }
}