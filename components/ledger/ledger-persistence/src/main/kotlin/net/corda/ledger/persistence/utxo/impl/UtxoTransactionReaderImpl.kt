package net.corda.ledger.persistence.utxo.impl

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.PersistTransaction
import net.corda.ledger.common.data.transaction.CordaPackageSummary
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.persistence.utxo.UtxoTransactionReader
import net.corda.persistence.common.exceptions.NullParameterException
import net.corda.persistence.common.getSerializationService
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.application.serialization.deserialize
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.PrivacySalt
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef

class UtxoTransactionReaderImpl(
    sandbox: SandboxGroupContext,
    private val externalEventContext: ExternalEventContext,
    private val transaction: PersistTransaction
) : UtxoTransactionReader {

    private companion object {
        const val CORDA_ACCOUNT = "corda.account"
    }

    private val serializer = sandbox.getSerializationService()
    private val signedTransaction = serializer.deserialize<SignedTransactionContainer>(transaction.transaction.array())

    override val id: SecureHash
        get() = signedTransaction.id

    override val account: String
        get() = externalEventContext.contextProperties.items.find { it.key == CORDA_ACCOUNT }?.value
            ?: throw NullParameterException("Flow external event context property '${CORDA_ACCOUNT}' not set")

    override val status: String
        get() = transaction.status

    override val privacySalt: PrivacySalt
        get() = signedTransaction.wireTransaction.privacySalt

    override val rawGroupLists: List<List<ByteArray>>
        get() = signedTransaction.wireTransaction.componentGroupLists

    override val cpkMetadata: List<CordaPackageSummary>
        get() = signedTransaction.wireTransaction.metadata.getCpkMetadata()

    override fun getProducedStates(): List<StateAndRef<ContractState>> {
        TODO("Not yet implemented")
    }

    override fun getConsumedStates(): List<StateAndRef<ContractState>> {
        TODO("Not yet implemented")
    }
}
