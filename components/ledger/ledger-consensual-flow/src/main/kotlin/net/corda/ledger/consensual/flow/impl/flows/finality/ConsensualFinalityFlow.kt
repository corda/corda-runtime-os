package net.corda.ledger.consensual.flow.impl.flows.finality

import net.corda.flow.application.services.VersioningService
import net.corda.flow.application.versioning.VersionedSendFlowFactory
import net.corda.ledger.consensual.flow.impl.flows.finality.v1.ConsensualFinalityFlowV1
import net.corda.ledger.consensual.flow.impl.transaction.ConsensualSignedTransactionInternal
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction

@CordaSystemFlow
class ConsensualFinalityFlow(
    private val transaction: ConsensualSignedTransactionInternal,
    private val sessions: List<FlowSession>
) : SubFlow<ConsensualSignedTransaction> {

    @CordaInject
    lateinit var versioningService: VersioningService

    @Suspendable
    override fun call(): ConsensualSignedTransaction {
        return versioningService.versionedSubFlow(
            ConsensualFinalityFlowVersionedFlowFactory(transaction),
            sessions
        )
    }
}

class ConsensualFinalityFlowVersionedFlowFactory(
    private val transaction: ConsensualSignedTransactionInternal,
) : VersionedSendFlowFactory<ConsensualSignedTransaction> {

    override val versionedInstanceOf: Class<ConsensualFinalityFlow> = ConsensualFinalityFlow::class.java

    override fun create(version: Int, sessions: List<FlowSession>): SubFlow<ConsensualSignedTransaction> {
        return when {
            version >= 1 -> ConsensualFinalityFlowV1(transaction, sessions)
            else -> throw IllegalArgumentException()
        }
    }
}