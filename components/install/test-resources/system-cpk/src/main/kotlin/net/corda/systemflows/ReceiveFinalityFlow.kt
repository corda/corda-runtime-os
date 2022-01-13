package net.corda.systemflows

import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.FlowSession
import net.corda.v5.application.flows.flowservices.FlowEngine
import net.corda.v5.application.injection.CordaInject
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.services.StatesToRecord
import net.corda.v5.ledger.transactions.SignedTransaction

class ReceiveFinalityFlow @JvmOverloads constructor(
    private val otherSideSession: FlowSession,
    private val expectedTxId: SecureHash? = null,
    private val statesToRecord: StatesToRecord = StatesToRecord.ONLY_RELEVANT
) : Flow<SignedTransaction> {
    @CordaInject
    lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(): SignedTransaction {
        return flowEngine.subFlow(object : ReceiveTransactionFlow(
            otherSideSession,
            checkSufficientSignatures = true,
            statesToRecord = statesToRecord
        ) {
            override fun checkBeforeRecording(stx: SignedTransaction) {
                require(expectedTxId == null || expectedTxId == stx.id) {
                    "We expected to receive transaction with ID $expectedTxId but instead got ${stx.id}. Transaction was" +
                            "not recorded and nor its states sent to the vault."
                }
            }
        })
    }
}
