package net.corda.ledger.consensual.flow.impl.flows.finality

import net.corda.flow.application.services.VersioningService
import net.corda.flow.application.versioning.VersionedReceiveFlowFactory
import net.corda.ledger.consensual.flow.impl.flows.finality.v1.ConsensualReceiveFinalityFlowV1
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionValidator

@CordaSystemFlow
class ConsensualReceiveFinalityFlow(
    private val session: FlowSession,
    private val validator: ConsensualTransactionValidator
) : SubFlow<ConsensualSignedTransaction> {

    @CordaInject
    lateinit var versioningService: VersioningService

    @Suspendable
    override fun call(): ConsensualSignedTransaction {
        return versioningService.versionedSubFlow(
            ConsensualReceiveFinalityFlowVersionedFlowFactory(validator),
            session
        )
    }
}

class ConsensualReceiveFinalityFlowVersionedFlowFactory(
    private val validator: ConsensualTransactionValidator
) : VersionedReceiveFlowFactory<ConsensualSignedTransaction> {

    override val versionedInstanceOf: Class<ConsensualReceiveFinalityFlow> = ConsensualReceiveFinalityFlow::class.java

    override fun create(version: Int, session: FlowSession): SubFlow<ConsensualSignedTransaction> {
        return when {
            version >= 1 -> ConsensualReceiveFinalityFlowV1(session, validator)
            else -> throw IllegalArgumentException()
        }
    }
}