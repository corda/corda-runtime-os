package net.corda.ledger.utxo.flow.impl.flows.transactionbuilder

import net.corda.flow.application.services.VersioningService
import net.corda.flow.application.versioning.VersionedSendFlowFactory
import net.corda.ledger.utxo.flow.impl.flows.transactionbuilder.v1.SendTransactionBuilderDiffFlowV1
import net.corda.ledger.utxo.flow.impl.transaction.UtxoBaselinedTransactionBuilder
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderContainer
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderInternal
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable

@CordaSystemFlow
class SendTransactionBuilderDiffFlow(
    private val transactionBuilder: UtxoTransactionBuilderContainer,
    private val session: FlowSession
) : SubFlow<Unit> {

    constructor(
        transactionBuilder: UtxoBaselinedTransactionBuilder,
        session: FlowSession
    ) : this(transactionBuilder.diff(), session)

    constructor(
        transactionBuilder: UtxoTransactionBuilderInternal,
        session: FlowSession
    ) : this(transactionBuilder.copy(), session)

    @CordaInject
    lateinit var versioningService: VersioningService

    @Suspendable
    override fun call() {
        return versioningService.versionedSubFlow(
            SendTransactionBuilderDiffFlowVersionedFlowFactory(transactionBuilder),
            listOf(session)
        )
    }
}

class SendTransactionBuilderDiffFlowVersionedFlowFactory(
    private val transactionBuilder: UtxoTransactionBuilderContainer
) : VersionedSendFlowFactory<Unit> {

    override val versionedInstanceOf: Class<SendTransactionBuilderDiffFlow> = SendTransactionBuilderDiffFlow::class.java

    override fun create(version: Int, sessions: List<FlowSession>): SubFlow<Unit> {
        return when {
            version >= 1 -> SendTransactionBuilderDiffFlowV1(transactionBuilder, sessions.single())
            else -> throw IllegalArgumentException()
        }
    }
}
