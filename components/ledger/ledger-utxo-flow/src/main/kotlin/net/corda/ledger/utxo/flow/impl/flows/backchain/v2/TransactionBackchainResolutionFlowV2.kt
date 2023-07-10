package net.corda.ledger.utxo.flow.impl.flows.backchain.v2

import net.corda.ledger.utxo.flow.impl.flows.backchain.v1.TransactionBackchainResolutionFlowV1
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.crypto.SecureHash
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@CordaSystemFlow
class TransactionBackchainResolutionFlowV2(
    private val initialTransactionIds: Set<SecureHash>,
    private val session: FlowSession,
) : TransactionBackchainResolutionFlowV1(initialTransactionIds, session), SubFlow<Unit> {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TransactionBackchainResolutionFlowV2

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
