package net.corda.ledger.utxo.flow.impl.flows.transactiontransmission

import net.corda.flow.application.services.VersioningService
import net.corda.flow.application.versioning.VersionedReceiveFlowFactory
import net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.v1.ReceiveLedgerTransactionFlowV1
import net.corda.libs.platform.PlatformVersion
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import java.lang.IllegalArgumentException

@CordaSystemFlow
class ReceiveLedgerTransactionFlow(
    private val session: FlowSession
) : SubFlow<UtxoLedgerTransaction> {

    @CordaInject
    lateinit var versioningService: VersioningService

    @Suspendable
    override fun call(): UtxoLedgerTransaction {
        return versioningService.versionedSubFlow(
            ReceiveLedgerTransactionFlowVersionedFlowFactory(),
            session
        )
    }
}

class ReceiveLedgerTransactionFlowVersionedFlowFactory : VersionedReceiveFlowFactory<UtxoLedgerTransaction> {

    override val versionedInstanceOf: Class<ReceiveLedgerTransactionFlow> = ReceiveLedgerTransactionFlow::class.java

    override fun create(version: Int, session: FlowSession): SubFlow<UtxoLedgerTransaction> {
        return when {
            version >= PlatformVersion.CORDA_5_2.value -> ReceiveLedgerTransactionFlowV1(session)
            else -> throw IllegalArgumentException("Unsupported version: $version for SendTransactionFlow")
        }
    }
}
