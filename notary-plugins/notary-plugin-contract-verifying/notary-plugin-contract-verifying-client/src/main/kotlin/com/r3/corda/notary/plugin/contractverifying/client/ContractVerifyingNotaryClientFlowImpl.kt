package com.r3.corda.notary.plugin.contractverifying.client

import com.r3.corda.notary.plugin.common.NotarizationResponse
import com.r3.corda.notary.plugin.contractverifying.api.ContractVerifyingNotarizationPayload
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryClientFlow
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import org.slf4j.LoggerFactory

@InitiatingFlow(protocol = "com.r3.corda.notary.plugin.contractverifying", version = [1])
class ContractVerifyingNotaryClientFlowImpl(
    private val signedTransaction: UtxoSignedTransaction,
    private val notaryRepresentative: MemberX500Name
) : PluggableNotaryClientFlow {
    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    private lateinit var flowMessaging: FlowMessaging

    @CordaInject
    private lateinit var digestService: DigestService

    @CordaInject
    private lateinit var utxoLedgerService: UtxoLedgerService

    /**
     * Constructor used for testing to initialize the necessary services
     */
    @VisibleForTesting
    internal constructor(
        stx: UtxoSignedTransaction,
        notary: MemberX500Name,
        flowMessaging: FlowMessaging,
        utxoLedgerService: UtxoLedgerService,
        digestService: DigestService
    ): this(stx, notary) {
        this.flowMessaging = flowMessaging
        this.utxoLedgerService = utxoLedgerService
        this.digestService = digestService
    }

    @Suspendable
    override fun call(): List<DigitalSignatureAndMetadata> {
        if (logger.isTraceEnabled) {
            logger.trace("Notarizing transaction {} with notary {}", signedTransaction.id, notaryRepresentative)
        }

        val session = flowMessaging.initiateFlow(notaryRepresentative)
        val payload = createPayload()
        val notarizationResponse = session.sendAndReceive(
            NotarizationResponse::class.java,
            payload
        )

        return when (val error = notarizationResponse.error) {
            null -> {
                if (logger.isTraceEnabled) {
                    logger.trace(
                        "Received notarization response from notary service {} for transaction {}",
                        signedTransaction.notaryName, signedTransaction.id
                    )
                }
                notarizationResponse.signatures
            }
            else -> {
                if (logger.isTraceEnabled) {
                    logger.trace(
                        "Received notarization error from notary service {}. Error: {}",
                        signedTransaction.notaryName, error
                    )
                }
                throw error
            }
        }
    }

    @Suspendable
    internal fun createPayload(): ContractVerifyingNotarizationPayload {
        val filteredTxAndSignatures = utxoLedgerService.findFilteredTransactionsAndSignatures(signedTransaction)
        return ContractVerifyingNotarizationPayload(signedTransaction, filteredTxAndSignatures.values.toList())
    }
}