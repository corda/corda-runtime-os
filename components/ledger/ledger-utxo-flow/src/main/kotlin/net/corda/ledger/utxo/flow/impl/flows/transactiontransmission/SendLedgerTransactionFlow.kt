package net.corda.ledger.utxo.flow.impl.flows.transactiontransmission

import net.corda.flow.application.services.VersioningService
import net.corda.flow.application.versioning.VersionedSendFlowFactory
import net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.v1.SendLedgerTransactionFlowV1
import net.corda.libs.platform.PlatformVersion
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import java.lang.IllegalArgumentException

@CordaSystemFlow
class SendLedgerTransactionFlow(
    private val transaction: UtxoSignedTransaction,
    private val sessions: List<FlowSession>
) : SubFlow<Unit> {

    @CordaInject
    lateinit var versioningService: VersioningService

    @Suspendable
    override fun call() {
        return versioningService.versionedSubFlow(
            SendLedgerTransactionFlowVersionedFlowFactory(
                transaction
            ),
            sessions
        )
    }
}

class SendLedgerTransactionFlowVersionedFlowFactory(
    private val transaction: UtxoSignedTransaction
) : VersionedSendFlowFactory<Unit> {

    override val versionedInstanceOf: Class<SendLedgerTransactionFlow> = SendLedgerTransactionFlow::class.java

    override fun create(version: Int, sessions: List<FlowSession>): SubFlow<Unit> {
        return when {
            version >= PlatformVersion.CORDA_5_2.value -> SendLedgerTransactionFlowV1(
                transaction,
                sessions
            )
            else -> throw IllegalArgumentException("Unsupported version: $version for SendAsLedgerTransactionFlow")
        }
    }
}
