package com.r3.corda.notary.plugin.nonvalidating.client

import com.r3.corda.notary.plugin.common.NotarizationResponse
import com.r3.corda.notary.plugin.nonvalidating.api.NonValidatingNotarizationPayload
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

/**
 * The client that is used for the non-validating notary logic. This class is very simple and uses the basic
 * send-and-receive logic, and it will also initiate the server side of the non-validating notary.
 */
@InitiatingFlow(protocol = "com.r3.corda.notary.plugin.nonvalidating", version = [1])
class NonValidatingNotaryClientFlowImpl(
    private val stx: UtxoSignedTransaction,
    private val notaryRepresentative: MemberX500Name
) : PluggableNotaryClientFlow {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    private lateinit var flowMessaging: FlowMessaging

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
        utxoLedgerService: UtxoLedgerService
    ): this(stx, notary) {
        this.flowMessaging = flowMessaging
        this.utxoLedgerService = utxoLedgerService
    }

    /**
     * The main logic of the flow is defined in this function. The execution steps are:
     * 1. Initiating flow with the notary
     * 2. Generating signature for the request payload
     * 3. Creating the payload (using concrete implementation)
     * 4. Sending the request payload to the notary
     * 5. Receiving the response from the notary and returning it or throwing an exception if an error is received
     */
    @Suspendable
    override fun call(): List<DigitalSignatureAndMetadata> {
        if (log.isTraceEnabled) {
            log.trace("Notarizing transaction {} with notary {}", stx.id, notaryRepresentative)
        }

        val session = flowMessaging.initiateFlow(notaryRepresentative)

        val payload = generatePayload(stx)

        if (log.isTraceEnabled) {
            log.trace("Sending notarization request to notary service {} via " +
                        "representative {} for transaction {}", stx.notaryName, notaryRepresentative, stx.id)
        }

        val notarizationResponse = session.sendAndReceive(
            NotarizationResponse::class.java,
            payload
        )

        return when (val error = notarizationResponse.error) {
            null -> {
                if (log.isTraceEnabled) {
                    log.trace("Received notarization response from notary service {} for transaction {}",
                              stx.notaryName, stx.id)
                }
                notarizationResponse.signatures
            }
            else -> {
                if (log.isTraceEnabled) {
                    log.trace("Received notarization error from notary service {}. Error: {}",
                        stx.notaryName, error)
                }
                throw error
            }
        }
    }

    /**
     * This function generates a notarization request and a signature from that given request via serialization.
     * Then attaches that signature to a [NonValidatingNotarizationPayload].
     */
    @Suspendable
    internal fun generatePayload(stx: UtxoSignedTransaction): NonValidatingNotarizationPayload {
        val filteredTx = utxoLedgerService.filterSignedTransaction(stx)
            .withInputStates()
            .withReferenceStates()
            .withOutputStatesSize()
            .withNotary()
            .withTimeWindow()
            .build()

        return NonValidatingNotarizationPayload(filteredTx, stx.notaryKey)
    }
}
