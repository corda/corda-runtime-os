package net.corda.ledger.utxo.flow.impl.flows.finality

import net.corda.flow.application.services.VersioningService
import net.corda.flow.application.versioning.VersionedSendFlowFactory
import net.corda.ledger.utxo.flow.impl.flows.finality.v1.UtxoFinalityFlowV1
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryClientFlow
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

@CordaSystemFlow
class UtxoFinalityFlow(
    private val transaction: UtxoSignedTransactionInternal,
    private val sessions: List<FlowSession>,
    private val pluggableNotaryClientFlow: Class<PluggableNotaryClientFlow>
) : SubFlow<UtxoSignedTransaction> {

    @CordaInject
    lateinit var versioningService: VersioningService

    @Suspendable
    override fun call(): UtxoSignedTransaction {
        return versioningService.versionedSubFlow(
            UtxoFinalityFlowVersionedFlowFactory(transaction, pluggableNotaryClientFlow),
            sessions
        )
    }
}

class UtxoFinalityFlowVersionedFlowFactory(
    private val transaction: UtxoSignedTransactionInternal,
    private val pluggableNotaryClientFlow: Class<PluggableNotaryClientFlow>
) : VersionedSendFlowFactory<UtxoSignedTransaction> {

    override val versionedInstanceOf: Class<UtxoFinalityFlow> = UtxoFinalityFlow::class.java

    override fun create(version: Int, sessions: List<FlowSession>): SubFlow<UtxoSignedTransaction> {
        return when {
            version >= 1 -> UtxoFinalityFlowV1(transaction, sessions, pluggableNotaryClientFlow)
            else -> throw IllegalArgumentException()
        }
    }
}