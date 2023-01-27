package net.cordapp.demo.utxo

import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.flows.RestRequestBody
import net.corda.v5.application.flows.getRequestBodyAs
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.receive
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import org.slf4j.LoggerFactory
import java.security.PublicKey
import java.time.Instant
import java.time.temporal.ChronoUnit

@InitiatingFlow("utxo-backchain-resolution-protocol")
class UtxoBackchainResolutionDemoFlow : ClientStartableFlow {
    data class InputMessage(val input: String, val members: List<String>)

    @BelongsToContract(TestContract::class)
    class TestState(
        val testField: String,
        override val participants: List<PublicKey>
    ) : ContractState


    class TestContract : Contract {
        override fun verify(transaction: UtxoLedgerTransaction) {

        }
    }

    class TestCommand : Command

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @Suppress("LongMethod")
    @Suspendable
    override fun call(requestBody: RestRequestBody): String {
        log.info("UTXO backchain resolution demo flow starting!!...")
        try {
            val request = requestBody.getRequestBodyAs<InputMessage>(jsonMarshallingService)

            val myInfo = memberLookup.myInfo()
            val members = request.members.map {
                requireNotNull(memberLookup.lookup(MemberX500Name.parse(it))) {
                    "Cannot find member $it"
                }
            }
            log.info("Found members $members")

            val testState = TestState(
                request.input,
                members.map { requireNotNull(it.ledgerKeys.firstOrNull()) { "Cannot find any ledger keys for member $it" } }
            )

            val sessions = members.map { flowMessaging.initiateFlow(it.name) }

            val txBuilder = utxoLedgerService.getTransactionBuilder()
            val signedTransaction = txBuilder
                .setNotary(Party(members.first().name, members.first().ledgerKeys.first()))
                .setTimeWindowUntil(Instant.now().plus(10, ChronoUnit.DAYS))
                .addCommand(TestCommand())
                .addSignatories(listOf(myInfo.ledgerKeys.first()))
                .addOutputState(testState)
                .addOutputState(testState)
                .addOutputState(testState)
                .addOutputState(testState)
                .addOutputState(testState)
                .toSignedTransaction()

            val tx2 = utxoLedgerService.getTransactionBuilder()
                .setNotary(Party(members.first().name, members.first().ledgerKeys.first()))
                .setTimeWindowUntil(Instant.now().plus(10, ChronoUnit.DAYS))
                .addCommand(TestCommand())
                .addSignatories(listOf(myInfo.ledgerKeys.first()))
                .addInputState(StateRef(signedTransaction.id, 0))
                .addInputState(StateRef(signedTransaction.id, 1))
                .addOutputState(testState)
                .toSignedTransaction()

            val tx3 = utxoLedgerService.getTransactionBuilder()
                .setNotary(Party(members.first().name, members.first().ledgerKeys.first()))
                .setTimeWindowUntil(Instant.now().plus(10, ChronoUnit.DAYS))
                .addCommand(TestCommand())
                .addSignatories(listOf(myInfo.ledgerKeys.first()))
                .addInputState(StateRef(signedTransaction.id, 2))
                .addInputState(StateRef(signedTransaction.id, 3))
                .addOutputState(testState)
                .toSignedTransaction()

            val tx4 = utxoLedgerService.getTransactionBuilder()
                .setNotary(Party(members.first().name, members.first().ledgerKeys.first()))
                .setTimeWindowUntil(Instant.now().plus(10, ChronoUnit.DAYS))
                .addCommand(TestCommand())
                .addSignatories(listOf(myInfo.ledgerKeys.first()))
                .addInputState(StateRef(signedTransaction.id, 4))
                .addOutputState(testState)
                .toSignedTransaction()

            val tx5 = utxoLedgerService.getTransactionBuilder()
                .setNotary(Party(members.first().name, members.first().ledgerKeys.first()))
                .setTimeWindowUntil(Instant.now().plus(10, ChronoUnit.DAYS))
                .addCommand(TestCommand())
                .addSignatories(listOf(myInfo.ledgerKeys.first()))
                .addInputState(StateRef(tx2.id, 1))
                .addOutputState(testState)
                .toSignedTransaction()

            val tx6 = utxoLedgerService.getTransactionBuilder()
                .setNotary(Party(members.first().name, members.first().ledgerKeys.first()))
                .setTimeWindowUntil(Instant.now().plus(10, ChronoUnit.DAYS))
                .addCommand(TestCommand())
                .addSignatories(testState.participants)
                .addInputState(StateRef(tx4.id, 0))
                .addInputState(StateRef(tx5.id, 0))
                .addOutputState(testState)
                .toSignedTransaction()

            val tx7 = utxoLedgerService.getTransactionBuilder()
                .setNotary(Party(members.first().name, members.first().ledgerKeys.first()))
                .setTimeWindowUntil(Instant.now().plus(10, ChronoUnit.DAYS))
                .addCommand(TestCommand())
                .addSignatories(listOf(myInfo.ledgerKeys.first()))
                .addInputState(StateRef(tx3.id, 0))
                .addOutputState(testState)
                .toSignedTransaction()

            val tx8 = utxoLedgerService.getTransactionBuilder()
                .setNotary(Party(members.first().name, members.first().ledgerKeys.first()))
                .setTimeWindowUntil(Instant.now().plus(10, ChronoUnit.DAYS))
                .addCommand(TestCommand())
                .addSignatories(listOf(myInfo.ledgerKeys.first()))
                .addInputState(StateRef(tx6.id, 0))
                .addInputState(StateRef(tx7.id, 0))
                .addOutputState(testState)
                .addOutputState(testState)
                .toSignedTransaction()

            val tx9 = utxoLedgerService.getTransactionBuilder()
                .setNotary(Party(members.first().name, members.first().ledgerKeys.first()))
                .setTimeWindowUntil(Instant.now().plus(10, ChronoUnit.DAYS))
                .addCommand(TestCommand())
                .addSignatories(listOf(myInfo.ledgerKeys.first()))
                .addInputState(StateRef(tx8.id, 0))
                .addOutputState(testState)
                .toSignedTransaction()

            val tx10 = utxoLedgerService.getTransactionBuilder()
                .setNotary(Party(members.first().name, members.first().ledgerKeys.first()))
                .setTimeWindowUntil(Instant.now().plus(10, ChronoUnit.DAYS))
                .addCommand(TestCommand())
                .addSignatories(listOf(myInfo.ledgerKeys.first()))
                .addInputState(StateRef(tx8.id, 1))
                .addOutputState(testState)
                .toSignedTransaction()

            val tx11 = utxoLedgerService.getTransactionBuilder()
                .setNotary(Party(members.first().name, members.first().ledgerKeys.first()))
                .setTimeWindowUntil(Instant.now().plus(10, ChronoUnit.DAYS))
                .addCommand(TestCommand())
                .addSignatories(testState.participants)
                .addInputState(StateRef(tx9.id, 0))
                .addInputState(StateRef(tx10.id, 0))
                .addOutputState(testState)
                .toSignedTransaction()

            /*
                    tx5
                    /   \
                tx2       \
                /           \         tx9
            tx1             tx6 \    /   \
               \          /       tx8     tx11
                tx3 --- /--- tx7 /  \    /
               \      /              tx10
                tx4 /

             */

            for (session in sessions) {
                session.send(
                    listOf(
                        signedTransaction.id,
                        tx2.id,
                        tx3.id,
                        tx4.id,
                        tx5.id,
                        tx6.id,
                        tx7.id,
                        tx8.id,
                        tx9.id,
                        tx10.id,
                        tx11.id
                    )
                )
            }

            utxoLedgerService.finalize(signedTransaction, emptyList())
            utxoLedgerService.finalize(tx2, emptyList())
            utxoLedgerService.finalize(tx3, emptyList())
            utxoLedgerService.finalize(tx4, emptyList())
            utxoLedgerService.finalize(tx5, emptyList())

            utxoLedgerService.finalize(tx6, sessions)

            utxoLedgerService.finalize(tx7, emptyList())
            utxoLedgerService.finalize(tx8, emptyList())
            utxoLedgerService.finalize(tx9, emptyList())
            utxoLedgerService.finalize(tx10, emptyList())

            utxoLedgerService.finalize(tx11, sessions)
            return "SUCCESS"
        } catch (e: Exception) {
            log.warn("Failed to process UTXO backchain resolution demo flow for request body '$requestBody' because:'${e.message}'")
            throw e
        }
    }
}

@InitiatedBy("utxo-backchain-resolution-protocol")
class UtxoBackchainResolutionDemoResponderFlow : ResponderFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService


    @Suspendable
    override fun call(session: FlowSession) {
        val txs = session.receive<List<SecureHash>>()
        txs.map { utxoLedgerService.findSignedTransaction(it) }
            .forEachIndexed { index, tx ->
                log.info("PEER TX${index + 1} = ${tx?.id}")
            }

        utxoLedgerService.receiveFinality(session) {
            log.info("Received finality - ${it.id}")
        }

        txs.map { utxoLedgerService.findSignedTransaction(it) }
            .forEachIndexed { index, tx ->
                log.info("PEER TX${index + 1} = ${tx?.id}")
            }

        utxoLedgerService.receiveFinality(session) {
            log.info("Received finality (2) - ${it.id}")
        }

        txs.map { utxoLedgerService.findSignedTransaction(it) }
            .forEachIndexed { index, tx ->
                log.info("PEER TX${index + 1} = ${tx?.id}")
            }
    }
}
