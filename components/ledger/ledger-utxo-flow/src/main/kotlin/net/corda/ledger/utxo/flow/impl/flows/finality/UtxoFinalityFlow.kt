package net.corda.ledger.utxo.flow.impl.flows.finality

import net.corda.flow.application.services.VersioningService
import net.corda.flow.application.versioning.VersionedSendFlowFactory
import net.corda.ledger.utxo.flow.impl.flows.finality.v1.UtxoFinalityFlowV1
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.libs.platform.PlatformVersion.CORDA_5_1
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryClientFlow
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

@CordaSystemFlow
class UtxoFinalityFlow(
    private val transaction: UtxoSignedTransactionInternal,
    private val sessions: List<FlowSession>,
    private val pluggableNotaryClientFlow: Class<PluggableNotaryClientFlow>,
    private val serializationService: SerializationService
) : SubFlow<UtxoSignedTransaction> {

    @CordaInject
    lateinit var versioningService: VersioningService

    @Suspendable
    override fun call(): UtxoSignedTransaction {
        return versioningService.versionedSubFlow(
            UtxoFinalityFlowVersionedFlowFactory(transaction, pluggableNotaryClientFlow, serializationService),
            sessions
        )
    }
}

class UtxoFinalityFlowVersionedFlowFactory(
    private val transaction: UtxoSignedTransactionInternal,
    private val pluggableNotaryClientFlow: Class<PluggableNotaryClientFlow>,
    private val serializationService: SerializationService
) : VersionedSendFlowFactory<UtxoSignedTransaction> {

    override val versionedInstanceOf: Class<UtxoFinalityFlow> = UtxoFinalityFlow::class.java

    override fun create(version: Int, sessions: List<FlowSession>): SubFlow<UtxoSignedTransaction> {
        val finalityVersion = when {
            version >= CORDA_5_1.value -> UtxoFinalityVersion.V2
            version in 1 until CORDA_5_1.value -> UtxoFinalityVersion.V1
            else -> throw IllegalArgumentException()
        }
        return UtxoFinalityFlowV1(transaction, sessions, pluggableNotaryClientFlow, serializationService, finalityVersion)
    }
}