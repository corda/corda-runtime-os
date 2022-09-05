package net.cordapp.flowworker.development.testflows

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.days
import net.corda.v5.base.util.seconds
import net.corda.v5.ledger.utxo.uniqueness.client.LedgerUniquenessCheckerClientService
import net.corda.v5.ledger.utxo.uniqueness.model.UniquenessCheckResultFailure
import net.corda.v5.ledger.utxo.uniqueness.model.UniquenessCheckResultSuccess
import java.time.Instant

@Suppress("unused")
@InitiatingFlow(protocol = "uniqueness_protocol")
class UniquenessCheckTestFlow : RPCStartableFlow {

    private companion object {
        val log = contextLogger()

        const val DUMMY_HASH = "SHA-256:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FA"
        const val DUMMY_HASH_2 = "SHA-256:1D2BE493659AB94A692697F10CA321D5FFC54FCE53B33B6518E87717977BD953"
        const val SUCCESS_MESSAGE = "Uniqueness Check operation complete."

        val TX_VALIDITY = 5.days.toMillis()
        val RESPONSE_TIMEOUT = 10.seconds
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var memberLookupService: MemberLookup

    @CordaInject
    lateinit var uniquenessClient: LedgerUniquenessCheckerClientService

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @Suspendable
    override fun call(requestBody: RPCRequestData): String {

        log.info("Calling Uniqueness check for an issuance transaction with 5 output states, ID: $DUMMY_HASH")

        val issuanceResult = uniquenessClient.requestUniquenessCheck(
            DUMMY_HASH,
            emptyList(),
            emptyList(),
            5,
            null,
            Instant.now().plusMillis(TX_VALIDITY)
        )

        if (issuanceResult.result is UniquenessCheckResultSuccess) {
            log.info("Uniqueness check for issuance transaction was successful, " +
                    "signature: ${issuanceResult.signature}")
        } else {
            log.error("Uniqueness check for issuance transaction was unsuccessful, " +
                    "reason: ${(issuanceResult as UniquenessCheckResultFailure).error}")
            throw CordaRuntimeException("Uniqueness check for issuance transaction was unsuccessful")
        }

        log.info("Calling Uniqueness check for a consume transaction with 1 input state, ID: $DUMMY_HASH_2")

        val consumeResult = uniquenessClient.requestUniquenessCheck(
            DUMMY_HASH_2,
            listOf("$DUMMY_HASH:0"),
            emptyList(),
            0,
            null,
            Instant.now().plusMillis(TX_VALIDITY)
        )

        if (consumeResult.result is UniquenessCheckResultSuccess) {
            log.info("Uniqueness check for consume transaction was successful, " +
                    "signature: ${consumeResult.signature}")
        } else {
            log.error("Uniqueness check for consume transaction was unsuccessful, " +
                    "reason: ${(consumeResult as UniquenessCheckResultFailure).error}")
            throw CordaRuntimeException("Uniqueness check for issuance transaction was unsuccessful")
        }

        return jsonMarshallingService.format(SUCCESS_MESSAGE)
    }


}