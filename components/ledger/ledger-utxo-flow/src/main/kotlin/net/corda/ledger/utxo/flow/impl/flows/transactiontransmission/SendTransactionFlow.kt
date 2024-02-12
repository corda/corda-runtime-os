package net.corda.ledger.utxo.flow.impl.flows.transactiontransmission

import net.corda.flow.application.services.VersioningService
import net.corda.flow.application.versioning.VersionedSendFlowFactory
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainSenderFlow
import net.corda.ledger.utxo.flow.impl.flows.backchain.dependencies
import net.corda.ledger.utxo.flow.impl.flows.transactionbuilder.v1.SendTransactionBuilderDiffFlowV1
import net.corda.ledger.utxo.flow.impl.flows.transactionbuilder.v2.SendTransactionBuilderDiffFlowV2
import net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.v1.SendTransactionFlowV1
import net.corda.ledger.utxo.flow.impl.transaction.UtxoBaselinedTransactionBuilder
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderContainer
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderInternal
import net.corda.libs.platform.PlatformVersion
import net.corda.libs.platform.PlatformVersion.CORDA_5_2
import net.corda.sandbox.CordaSystemFlow
import net.corda.utilities.trace
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import org.slf4j.LoggerFactory
import java.lang.IllegalArgumentException

@CordaSystemFlow
class SendTransactionFlow(
    private val transaction: UtxoSignedTransaction,
    private val sessions: List<FlowSession>
) : SubFlow<Unit> {

    @CordaInject
    lateinit var versioningService: VersioningService

    @Suspendable
    override fun call() {
        return versioningService.versionedSubFlow(
            SendTransactionFlowVersionedFlowFactory(
                transaction,
            ),
            sessions
        )
    }
}

class SendTransactionFlowVersionedFlowFactory(
    private val transaction: UtxoSignedTransaction
) : VersionedSendFlowFactory<Unit> {

    override val versionedInstanceOf: Class<SendTransactionFlow> = SendTransactionFlow::class.java

    override fun create(version: Int, sessions: List<FlowSession>): SubFlow<Unit> {
        return when {
            version >= CORDA_5_2.value -> SendTransactionFlowV1(
                transaction,
                sessions
            )
            else -> throw IllegalArgumentException("Unsupported version: $version for SendTransactionFlow")
        }
    }
}
