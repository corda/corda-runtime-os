package net.corda.ledger.utxo.flow.impl.flows.transactiontransmission

import net.corda.flow.application.services.VersioningService
import net.corda.flow.application.versioning.VersionedReceiveFlowFactory
import net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.v1.ReceiveSignedTransactionFlowV1
import net.corda.libs.platform.PlatformVersion
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import java.lang.IllegalArgumentException

@CordaSystemFlow
class ReceiveSignedTransactionFlow(
    private val session: FlowSession
) : SubFlow<UtxoSignedTransaction> {

    @CordaInject
    lateinit var versioningService: VersioningService

    @Suspendable
    override fun call(): UtxoSignedTransaction {
        return versioningService.versionedSubFlow(
            ReceiveSignedTransactionFlowVersionedFlowFactory(),
            session
        )
    }
}

class ReceiveSignedTransactionFlowVersionedFlowFactory : VersionedReceiveFlowFactory<UtxoSignedTransaction> {

    override val versionedInstanceOf: Class<ReceiveSignedTransactionFlow> = ReceiveSignedTransactionFlow::class.java

    override fun create(version: Int, session: FlowSession): SubFlow<UtxoSignedTransaction> {
        return when {
            version >= PlatformVersion.CORDA_5_2.value -> ReceiveSignedTransactionFlowV1(session)
            else -> throw IllegalArgumentException("Unsupported version: $version for SendTransactionFlow")
        }
    }
}
