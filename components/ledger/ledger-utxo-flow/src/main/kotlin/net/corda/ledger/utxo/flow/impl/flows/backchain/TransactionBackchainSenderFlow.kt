package net.corda.ledger.utxo.flow.impl.flows.backchain

import net.corda.flow.application.services.VersioningService
import net.corda.flow.application.versioning.VersionedSendFlowFactory
import net.corda.ledger.utxo.flow.impl.flows.backchain.v1.TransactionBackchainSenderFlowV1
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash

@CordaSystemFlow
class TransactionBackchainSenderFlow(private val headTransactionIds: Set<SecureHash>, private val session: FlowSession) : SubFlow<Unit> {

    constructor (headTransactionId: SecureHash, session: FlowSession) : this(setOf(headTransactionId), session)

    @CordaInject
    lateinit var versioningService: VersioningService

    @Suspendable
    override fun call() {
        return versioningService.versionedSubFlow(
            TransactionBackchainSenderFlowVersionedFlowFactory(headTransactionIds),
            listOf(session)
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TransactionBackchainSenderFlow

        if (headTransactionIds != other.headTransactionIds) return false
        if (session != other.session) return false

        return true
    }

    override fun hashCode(): Int {
        var result = headTransactionIds.hashCode()
        result = 31 * result + session.hashCode()
        return result
    }
}

class TransactionBackchainSenderFlowVersionedFlowFactory(
    private val headTransactionIds: Set<SecureHash>
) : VersionedSendFlowFactory<Unit> {

    override val versionedInstanceOf: Class<TransactionBackchainSenderFlow> = TransactionBackchainSenderFlow::class.java

    override fun create(version: Int, sessions: List<FlowSession>): SubFlow<Unit> {
        return when {
            version >= 1 -> TransactionBackchainSenderFlowV1(headTransactionIds, sessions.single())
            else -> throw IllegalArgumentException()
        }
    }
}