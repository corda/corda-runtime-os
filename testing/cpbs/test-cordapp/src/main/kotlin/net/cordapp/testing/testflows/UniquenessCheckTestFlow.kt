package net.cordapp.testing.testflows

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.RestRequestBody
import net.corda.v5.application.flows.RestStartableFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultFailure
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultSuccess
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.days
import net.corda.v5.base.util.seconds
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.uniqueness.client.LedgerUniquenessCheckerClientService
import java.time.Instant
import java.util.*

@Suppress("unused")
@InitiatingFlow(protocol = "uniqueness_protocol")
class UniquenessCheckTestFlow : RestStartableFlow {

    private companion object {
        val log = contextLogger()

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

    private val random = Random(0)

    @Suspendable
    override fun call(requestBody: RestRequestBody): String {

        val issueTxId = newRandomSecureHash()

        log.info("Calling Uniqueness check for an issuance transaction with 5 output states, ID: $issueTxId")

        val issuanceResult = uniquenessClient.requestUniquenessCheck(
            issueTxId.toString(),
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

        val consumeTxId = newRandomSecureHash()

        log.info("Calling Uniqueness check for a consume transaction with 1 input state, ID: $consumeTxId")

        val consumeResult = uniquenessClient.requestUniquenessCheck(
            consumeTxId.toString(),
            listOf("$issueTxId:0"),
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

    private fun newRandomSecureHash(): SecureHash {
        return SecureHash(
            DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name,
            ByteArray(32).also(random::nextBytes)
        )
    }
}
