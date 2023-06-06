package net.corda.ledger.utxo.flow.impl.flows.finality

import net.corda.flow.application.services.VersioningService
import net.corda.flow.application.versioning.VersionedReceiveFlowFactory
import net.corda.ledger.utxo.flow.impl.flows.finality.v1.UtxoReceiveFinalityFlowV1
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionValidator

@CordaSystemFlow
class UtxoReceiveFinalityFlow(
    private val session: FlowSession,
    private val validator: UtxoTransactionValidator
) : SubFlow<UtxoSignedTransaction> {

    @CordaInject
    lateinit var versioningService: VersioningService

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @Suspendable
    override fun call(): UtxoSignedTransaction {
        return versioningService.versionedSubFlow(
            UtxoReceiveFinalityFlowVersionedFlowFactory(validator, flowMessaging),
            session
        )
    }
}

class UtxoReceiveFinalityFlowVersionedFlowFactory(
    private val validator: UtxoTransactionValidator,
    private val flowMessaging: FlowMessaging
) : VersionedReceiveFlowFactory<UtxoSignedTransaction> {

    override val versionedInstanceOf: Class<UtxoReceiveFinalityFlow> = UtxoReceiveFinalityFlow::class.java

    override fun create(version: Int, session: FlowSession): SubFlow<UtxoSignedTransaction> {
        return when {
            version >= 1 -> UtxoReceiveFinalityFlowV1(session, validator, flowMessaging)
            else -> throw IllegalArgumentException()
        }
    }
}