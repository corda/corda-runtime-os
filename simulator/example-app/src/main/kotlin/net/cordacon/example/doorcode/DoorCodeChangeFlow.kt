package net.cordacon.example.doorcode

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.consensual.ConsensualLedgerService
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import org.slf4j.LoggerFactory
import java.security.PublicKey

/**
 * A flow to ensure that everyone living in a building gets the new door code before it's changed.
 */
@InitiatingFlow(protocol = "door-code")
class DoorCodeChangeFlow : ClientStartableFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var consensualLedgerService: ConsensualLedgerService

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        val changeRequest = requestBody.getRequestBodyAs(jsonMarshallingService, DoorCodeChangeRequest::class.java)
        val participants = changeRequest.participants
        val newDoorCode = changeRequest.newDoorCode

        val doorCodeState = DoorCodeConsensualState(newDoorCode, participants.map { getPublicKey(it) })

        val txBuilder = consensualLedgerService.getTransactionBuilder()
        val signedTransaction = txBuilder
            .withStates(doorCodeState)
            .toSignedTransaction()

        val sessions = initiateSessions(participants.minus(memberLookup.myInfo().name))
        val result = consensualLedgerService.finalize(signedTransaction, sessions)

        val output = DoorCodeChangeResult(
            result.id,
            newDoorCode,
            result.signatures.map { getMemberFromSignature(it) }.toSet()
        )

        return jsonMarshallingService.format(output)
    }

    @Suspendable
    private fun getMemberFromSignature(signature: DigitalSignatureAndMetadata) =
        memberLookup.lookup(signature.by)?.name ?: error("Member for consensual signature not found")

    @Suspendable
    private fun initiateSessions(participants: List<MemberX500Name>) =
        participants.filterNot { it == memberLookup.myInfo().name }.map { flowMessaging.initiateFlow(it) }

    @Suspendable
    private fun getPublicKey(member: MemberX500Name): PublicKey {
        val memberInfo = memberLookup.lookup(member) ?: error("Member \"$member\" not found")
        return memberInfo.ledgerKeys.firstOrNull() ?: error("Member \"$member\" does not have any ledger keys")
    }
}

@InitiatedBy(protocol = "door-code")
class DoorCodeChangeResponderFlow : ResponderFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var consensualLedgerService: ConsensualLedgerService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @Suspendable
    override fun call(session: FlowSession) {

        val finalizedSignedTransaction = consensualLedgerService.receiveFinality(session) {
            val doorCodeState = it.states[0] as DoorCodeConsensualState
            log.info("\"${memberLookup.myInfo().name}\" got the new door code ${doorCodeState.code}")
        }
        val requiredSignatories = finalizedSignedTransaction.toLedgerTransaction().requiredSignatories
        val actualSignatories = finalizedSignedTransaction.signatures.map { it.by }.toSet()
        check(requiredSignatories == actualSignatories) {
            "Signatories were not as expected. Expected:\n    " + requiredSignatories.joinToString("\n    ") +
                    "and got:\n    " + actualSignatories.joinToString("\n    ")
        }
        log.info("Finished responder flow - $finalizedSignedTransaction")
    }
}

class DoorCodeQueryFlow : ClientStartableFlow {
    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var serializationService: SerializationService

    @CordaInject
    lateinit var consensualLedgerService: ConsensualLedgerService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        val txId = requestBody.getRequestBodyAs(jsonMarshallingService, DoorCodeQuery::class.java).txId
        val tx = consensualLedgerService.findSignedTransaction(txId)

        checkNotNull(tx) { "No consensual ledger transaction was persisted for provided id" }

        return jsonMarshallingService.format(
            DoorCodeQueryResponse(
                (tx.toLedgerTransaction().states[0] as DoorCodeConsensualState).code,
                tx.signatures.map { checkNotNull(memberLookup.lookup(it.by)?.name) }.toSet()
            )
        )
    }
}

@CordaSerializable
data class DoorCode(val code: String)

@CordaSerializable
data class DoorCodeChangeRequest(val newDoorCode: DoorCode, val participants: List<MemberX500Name>)

@CordaSerializable
data class DoorCodeChangeResult(val txId: SecureHash, val newDoorCode: DoorCode, val signedBy: Set<MemberX500Name>)

@CordaSerializable
data class DoorCodeQuery(val txId: SecureHash)

@CordaSerializable
data class DoorCodeQueryResponse(
    val doorCode: DoorCode,
    val signatories: Set<MemberX500Name>
)

@CordaSerializable
class DoorCodeConsensualState(val code: DoorCode, private val participants: List<PublicKey>) : ConsensualState {

    override fun getParticipants(): List<PublicKey> {
        return participants
    }

    override fun verify(ledgerTransaction: ConsensualLedgerTransaction) {}
}
