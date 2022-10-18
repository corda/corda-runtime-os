package net.cordapp.testing.testflows.ledger

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.application.serialization.deserialize
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.ledger.consensual.ConsensualLedgerService
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import java.security.PublicKey

@Suppress("unused")
class ConsensualSignedTransactionSerializationFlow : RPCStartableFlow {
    data class ResultMessage(val serializedLength: Int)

    class TestConsensualState(
        val testField: String, override val participants: List<PublicKey>
    ) : ConsensualState {
        override fun verify(ledgerTransaction: ConsensualLedgerTransaction) {}
    }

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var consensualLedgerService: ConsensualLedgerService

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var serializationService: SerializationService

    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        log.info("ConsensualSignedTransactionSerializationFlow starting...")
        try {
            val member = memberLookup.myInfo()

            val testConsensualState = TestConsensualState("test", listOf(member.ledgerKeys.first()))
            val txBuilder = consensualLedgerService.getTransactionBuilder()
            val signedTransaction = txBuilder
                .withStates(testConsensualState)
                .sign(memberLookup.myInfo().ledgerKeys.first())

            log.info("Original signed Tx id: ${signedTransaction.id}")

            val serialized = serializationService.serialize(signedTransaction)
            log.debug("Serialized Tx: $serialized")

            val deserialized = serializationService.deserialize(serialized)
            log.info("Deserialized signed Tx id: ${deserialized.id}")

            if (deserialized.id != signedTransaction.id) {
                log.warn("Deserialized tx Id != original tx Id (${deserialized.id} != ${signedTransaction.id}")
                throw CordaRuntimeException(
                    "Deserialized tx Id != original tx Id (${deserialized.id} != ${signedTransaction.id}"
                )
            }

            val resultMessage = ResultMessage(serialized.toString().length)
            log.info("Success! Serialized: $resultMessage")
            return jsonMarshallingService.format(resultMessage)
        } catch (e: Exception) {
            log.warn(
                "Failed to process ConsensualSignedTransactionSerializationFlow for request body " + "'$requestBody' because:'${e.message}'"
            )
            throw e
        }
    }
}
