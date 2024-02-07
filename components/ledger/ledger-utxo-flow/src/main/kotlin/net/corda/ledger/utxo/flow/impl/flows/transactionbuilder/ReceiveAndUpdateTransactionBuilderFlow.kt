package net.corda.ledger.utxo.flow.impl.flows.transactionbuilder

import net.corda.flow.application.services.VersioningService
import net.corda.flow.application.versioning.VersionedReceiveFlowFactory
import net.corda.ledger.utxo.flow.impl.flows.transactionbuilder.v1.ReceiveAndUpdateTransactionBuilderFlowV1
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderInternal
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.NotarySignatureVerificationService
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import net.corda.v5.membership.GroupParametersLookup

@CordaSystemFlow
class ReceiveAndUpdateTransactionBuilderFlow(
    private val session: FlowSession,
    private val originalTransactionBuilder: UtxoTransactionBuilderInternal,
    private val notaryLookup: NotaryLookup,
    private val groupParametersLookup: GroupParametersLookup,
    private val notarySignatureVerificationService: NotarySignatureVerificationService
) : SubFlow<UtxoTransactionBuilder> {

    @CordaInject
    lateinit var versioningService: VersioningService

    @Suspendable
    override fun call(): UtxoTransactionBuilder {
        return versioningService.versionedSubFlow(
            ReceiveAndUpdateTransactionBuilderFlowVersionedFlowFactory(
                originalTransactionBuilder,
                notaryLookup,
                groupParametersLookup,
                notarySignatureVerificationService
            ),
            session
        )
    }
}

class ReceiveAndUpdateTransactionBuilderFlowVersionedFlowFactory(
    private val originalTransactionBuilder: UtxoTransactionBuilderInternal,
    private val notaryLookup: NotaryLookup,
    private val groupParametersLookup: GroupParametersLookup,
    private val notarySignatureVerificationService: NotarySignatureVerificationService
) : VersionedReceiveFlowFactory<UtxoTransactionBuilder> {

    override val versionedInstanceOf: Class<ReceiveAndUpdateTransactionBuilderFlow> = ReceiveAndUpdateTransactionBuilderFlow::class.java

    override fun create(version: Int, session: FlowSession): SubFlow<UtxoTransactionBuilder> {
        return when {
            version >= 1 -> ReceiveAndUpdateTransactionBuilderFlowV1(
                session,
                originalTransactionBuilder,
                notaryLookup,
                groupParametersLookup,
                notarySignatureVerificationService
            )
            else -> throw IllegalArgumentException()
        }
    }
}
