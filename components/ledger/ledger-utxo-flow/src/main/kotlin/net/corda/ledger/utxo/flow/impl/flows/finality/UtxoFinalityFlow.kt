package net.corda.ledger.utxo.flow.impl.flows.finality

import net.corda.flow.application.services.VersioningService
import net.corda.flow.application.versioning.VersionedSendFlowFactory
import net.corda.ledger.utxo.flow.impl.PluggableNotaryDetails
import net.corda.ledger.utxo.flow.impl.flows.finality.v1.UtxoFinalityFlowV1
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.libs.platform.PlatformVersion.CORDA_5_1
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

@CordaSystemFlow
class UtxoFinalityFlow(
    private val transaction: UtxoSignedTransactionInternal,
    private val sessions: List<FlowSession>,
    private val pluggableNotaryDetails: PluggableNotaryDetails
) : SubFlow<UtxoSignedTransaction> {

    @CordaInject
    lateinit var versioningService: VersioningService

    @Suspendable
    override fun call(): UtxoSignedTransaction {
        return versioningService.versionedSubFlow(
            UtxoFinalityFlowVersionedFlowFactory(transaction, pluggableNotaryDetails),
            sessions
        )
    }
}

class UtxoFinalityFlowVersionedFlowFactory(
    private val transaction: UtxoSignedTransactionInternal,
    private val pluggableNotaryDetails: PluggableNotaryDetails
) : VersionedSendFlowFactory<UtxoSignedTransaction> {

    override val versionedInstanceOf: Class<UtxoFinalityFlow> = UtxoFinalityFlow::class.java

    override fun create(version: Int, sessions: List<FlowSession>): SubFlow<UtxoSignedTransaction> {
        return when {
            version >= CORDA_5_1.value -> UtxoFinalityFlowV1(transaction, sessions, pluggableNotaryDetails)
            version in 1 until CORDA_5_1.value -> throw CordaRuntimeException("Flows cannot be shared between 5.0 and 5.1 vnodes.")
            else -> throw IllegalArgumentException()
        }
    }
}