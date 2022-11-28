package net.cordacon.example.doorcode

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.v5.ledger.consensual.ConsensualLedgerService
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import java.security.PublicKey

/**
 * A flow to ensure that everyone living in a building gets the new door code before it's changed.
 */
@InitiatingFlow("door-code")
class DoorCodeChangeFlow : RPCStartableFlow {

    private companion object {
        val log = contextLogger()
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
    override fun call(requestBody: RPCRequestData): String {
        val changeRequest = requestBody.getRequestBodyAs(jsonMarshallingService, DoorCodeChangeRequest::class.java)
        val participants = changeRequest.participants
        val newDoorCode = changeRequest.newDoorCode

        val doorCodeState = DoorCodeConsensualState(newDoorCode, participants.map { getPublicKey(it) })

        val txBuilder = consensualLedgerService.getTransactionBuilder()
        @Suppress("DEPRECATION")
        val signedTransaction = txBuilder
            .withStates(doorCodeState)
            .toSignedTransaction(memberLookup.myInfo().ledgerKeys.first())

        val result = consensualLedgerService.finalize(signedTransaction, initiateSessions(participants))

        val output = DoorCodeChangeResult(newDoorCode, result.signatures.map { getMemberFromSignature(it) }.toSet())

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

@InitiatedBy("door-code")
class DoorCodeChangeResponderFlow : ResponderFlow {

    private companion object {
        val log = contextLogger()
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
        val actualSignatories = finalizedSignedTransaction.signatures.map {it.by}
        check(requiredSignatories == actualSignatories)
        log.info("Finished responder flow - $finalizedSignedTransaction")
    }
}

@CordaSerializable
data class DoorCode(val code: String)

@CordaSerializable
data class DoorCodeChangeRequest(val newDoorCode: DoorCode, val participants: List<MemberX500Name>)

@CordaSerializable
data class DoorCodeChangeResult(val newDoorCode: DoorCode, val signedBy: Set<MemberX500Name>)

@CordaSerializable
class DoorCodeConsensualState(val code: DoorCode, override val participants: List<PublicKey>) : ConsensualState {
    override fun verify(ledgerTransaction: ConsensualLedgerTransaction) {}
}