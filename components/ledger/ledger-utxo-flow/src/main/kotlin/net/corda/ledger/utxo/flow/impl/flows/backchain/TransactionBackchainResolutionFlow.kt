package net.corda.ledger.utxo.flow.impl.flows.backchain

import net.corda.flow.application.services.VersioningService
import net.corda.flow.application.versioning.VersionedReceiveFlowFactory
import net.corda.ledger.utxo.flow.impl.flows.backchain.v1.TransactionBackchainResolutionFlowV1
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash

@CordaSystemFlow
class TransactionBackchainResolutionFlow(
    private val initialTransactionIds: Set<SecureHash>,
    private val session: FlowSession,
) : SubFlow<Unit> {

    @CordaInject
    lateinit var versioningService: VersioningService

    @Suspendable
    override fun call() {
        return versioningService.versionedSubFlow(
            TransactionBackchainResolutionFlowVersionedFlowFactory(initialTransactionIds),
            session
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TransactionBackchainResolutionFlow

        if (initialTransactionIds != other.initialTransactionIds) return false
        if (session != other.session) return false

        return true
    }

    override fun hashCode(): Int {
        var result = initialTransactionIds.hashCode()
        result = 31 * result + session.hashCode()
        return result
    }
}

class TransactionBackchainResolutionFlowVersionedFlowFactory(
    private val initialTransactionIds: Set<SecureHash>
) : VersionedReceiveFlowFactory<Unit> {

    override val versionedInstanceOf: Class<TransactionBackchainResolutionFlow> = TransactionBackchainResolutionFlow::class.java

    override fun create(version: Int, session: FlowSession): SubFlow<Unit> {
        return when {
            version >= 1 -> TransactionBackchainResolutionFlowV1(initialTransactionIds, session)
            else -> throw IllegalArgumentException()
        }
    }
}