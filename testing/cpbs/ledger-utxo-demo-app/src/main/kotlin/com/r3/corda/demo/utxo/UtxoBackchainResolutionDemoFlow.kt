package com.r3.corda.demo.utxo

import com.r3.corda.demo.utxo.contract.TestCommand
import com.r3.corda.demo.utxo.contract.TestUtxoState
import java.security.PublicKey
import java.time.Instant
import java.time.temporal.ChronoUnit
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
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@InitiatingFlow(protocol = "utxo-backchain-resolution-protocol")
class UtxoBackchainResolutionDemoFlow : ClientStartableFlow {
    data class InputMessage(val input: String, val members: List<String>)

    private companion object {
        val log: Logger = LoggerFactory.getLogger(UtxoBackchainResolutionDemoFlow::class.java)
    }

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @Suppress("LongMethod")
    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("UTXO backchain resolution demo flow starting!!...")
        try {
            val request = requestBody.getRequestBodyAs(jsonMarshallingService, InputMessage::class.java)

            val myInfo = memberLookup.myInfo()

            val members = request.members.map {
                requireNotNull(memberLookup.lookup(MemberX500Name.parse(it))) {
                    "Cannot find member $it"
                }
            }
            require(members.isNotEmpty()) { "Members cannot be empty" }
            log.info("Found members $members")

            val testField = request.input
            val participants = members.map { it.ledgerKeys.first() } + myInfo.ledgerKeys.first()
            val memberNames = members.map { it.name.toString() }

            val sessions = members.map { flowMessaging.initiateFlow(it.name) }

            val notary = notaryLookup.notaryServices.first().name

            val txBuilder = utxoLedgerService.createTransactionBuilder()
            val signedTransaction = txBuilder
                .setNotary(notary)
                .setTimeWindowUntil(Instant.now().plus(10, ChronoUnit.DAYS))
                .addCommand(TestCommand())
                .addSignatories(listOf(myInfo.ledgerKeys.first()))
                .addOutputState(testState(1, testField, participants, memberNames))
                .addOutputState(testState(2, testField, participants, memberNames))
                .addOutputState(testState(3, testField, participants, memberNames))
                .addOutputState(testState(4, testField, participants, memberNames))
                .addOutputState(testState(5, testField, participants, memberNames))
                .toSignedTransaction()

            val ftx1 = utxoLedgerService.finalize(signedTransaction, emptyList()).transaction

            val tx2 = utxoLedgerService.createTransactionBuilder()
                .setNotary(notary)
                .setTimeWindowUntil(Instant.now().plus(10, ChronoUnit.DAYS))
                .addCommand(TestCommand())
                .addSignatories(listOf(myInfo.ledgerKeys.first()))
                .addInputState(StateRef(ftx1.id, 0))
                .addInputState(StateRef(ftx1.id, 1))
                .addOutputState(testState(6, testField, participants, memberNames))
                .toSignedTransaction()

            val ftx2 = utxoLedgerService.finalize(tx2, emptyList()).transaction

            val tx3 = utxoLedgerService.createTransactionBuilder()
                .setNotary(notary)
                .setTimeWindowUntil(Instant.now().plus(10, ChronoUnit.DAYS))
                .addCommand(TestCommand())
                .addSignatories(listOf(myInfo.ledgerKeys.first()))
                .addInputState(StateRef(ftx1.id, 2))
                .addInputState(StateRef(ftx1.id, 3))
                .addOutputState(testState(7, testField, participants, memberNames))
                .toSignedTransaction()

            val ftx3 = utxoLedgerService.finalize(tx3, emptyList()).transaction

            val tx4 = utxoLedgerService.createTransactionBuilder()
                .setNotary(notary)
                .setTimeWindowUntil(Instant.now().plus(10, ChronoUnit.DAYS))
                .addCommand(TestCommand())
                .addSignatories(listOf(myInfo.ledgerKeys.first()))
                .addInputState(StateRef(ftx1.id, 4))
                .addOutputState(testState(8, testField, participants, memberNames))
                .toSignedTransaction()

            val ftx4 = utxoLedgerService.finalize(tx4, emptyList()).transaction

            val tx5 = utxoLedgerService.createTransactionBuilder()
                .setNotary(notary)
                .setTimeWindowUntil(Instant.now().plus(10, ChronoUnit.DAYS))
                .addCommand(TestCommand())
                .addSignatories(listOf(myInfo.ledgerKeys.first()))
                .addInputState(StateRef(ftx2.id, 0))
                .addOutputState(testState(9, testField, participants, memberNames))
                .toSignedTransaction()

            val ftx5 = utxoLedgerService.finalize(tx5, emptyList()).transaction

            val tx6 = utxoLedgerService.createTransactionBuilder()
                .setNotary(notary)
                .setTimeWindowUntil(Instant.now().plus(10, ChronoUnit.DAYS))
                .addCommand(TestCommand())
                .addSignatories(participants)
                .addInputState(StateRef(ftx4.id, 0))
                .addInputState(StateRef(ftx5.id, 0))
                .addOutputState(testState(10, testField, participants, memberNames))
                .toSignedTransaction()

            log.info("TRANSACTION IDS:\n ${
                listOf(
                    ftx1.id,
                    ftx2.id,
                    ftx3.id,
                    ftx4.id,
                    ftx5.id,
                    tx6.id
                ).mapIndexed { index, tx -> "TX_$index = $tx\n" }
            }"
            )

            for (session in sessions) {
                session.send(
                    listOf(
                        1 to ftx1.id,
                        2 to ftx2.id,
                        4 to ftx4.id,
                        5 to ftx5.id,
                        6 to tx6.id
                    )
                )
                session.send(3 to ftx3.id)
            }

            val ftx6 = utxoLedgerService.finalize(tx6, sessions).transaction

            val tx7 = utxoLedgerService.createTransactionBuilder()
                .setNotary(notary)
                .setTimeWindowUntil(Instant.now().plus(10, ChronoUnit.DAYS))
                .addCommand(TestCommand())
                .addSignatories(listOf(myInfo.ledgerKeys.first()))
                .addInputState(StateRef(ftx3.id, 0))
                .addOutputState(testState(11, testField, participants, memberNames))
                .toSignedTransaction()

            val ftx7 = utxoLedgerService.finalize(tx7, emptyList()).transaction

            val tx8 = utxoLedgerService.createTransactionBuilder()
                .setNotary(notary)
                .setTimeWindowUntil(Instant.now().plus(10, ChronoUnit.DAYS))
                .addCommand(TestCommand())
                .addSignatories(listOf(myInfo.ledgerKeys.first()))
                .addInputState(StateRef(ftx6.id, 0))
                .addInputState(StateRef(ftx7.id, 0))
                .addOutputState(testState(12, testField, participants, memberNames))
                .addOutputState(testState(13, testField, participants, memberNames))
                .toSignedTransaction()

            val ftx8 = utxoLedgerService.finalize(tx8, emptyList()).transaction

            val tx9 = utxoLedgerService.createTransactionBuilder()
                .setNotary(notary)
                .setTimeWindowUntil(Instant.now().plus(10, ChronoUnit.DAYS))
                .addCommand(TestCommand())
                .addSignatories(listOf(myInfo.ledgerKeys.first()))
                .addInputState(StateRef(ftx8.id, 0))
                .addOutputState(testState(14, testField, participants, memberNames))
                .toSignedTransaction()

            val ftx9 = utxoLedgerService.finalize(tx9, emptyList()).transaction

            val tx10 = utxoLedgerService.createTransactionBuilder()
                .setNotary(notary)
                .setTimeWindowUntil(Instant.now().plus(10, ChronoUnit.DAYS))
                .addCommand(TestCommand())
                .addSignatories(listOf(myInfo.ledgerKeys.first()))
                .addInputState(StateRef(ftx8.id, 1))
                .addOutputState(testState(15, testField, participants, memberNames))
                .toSignedTransaction()

            val ftx10 = utxoLedgerService.finalize(tx10, emptyList()).transaction

            val tx11 = utxoLedgerService.createTransactionBuilder()
                .setNotary(notary)
                .setTimeWindowUntil(Instant.now().plus(10, ChronoUnit.DAYS))
                .addCommand(TestCommand())
                .addSignatories(participants)
                .addInputState(StateRef(ftx9.id, 0))
                .addInputState(StateRef(ftx10.id, 0))
                .addOutputState(testState(16, testField, participants, memberNames))
                .toSignedTransaction()

            for (session in sessions) {
                session.send(
                    listOf(
                        7 to ftx7.id,
                        8 to ftx8.id,
                        9 to ftx9.id,
                        10 to ftx10.id,
                        11 to tx11.id
                    )
                )
            }

            utxoLedgerService.finalize(tx11, sessions).transaction

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

            // Wait for all flows to finish, either successfully or they'll throw an exception.
            for (session in sessions) {
                session.receive(String::class.java)
            }
            
            return "SUCCESS"
        } catch (e: Exception) {
            log.warn("Failed to process UTXO backchain resolution demo flow for request body '$requestBody' because:'${e.message}'")
            throw e
        }
    }

    private fun testState(identifier: Int, testField: String, participants: List<PublicKey>, memberNames: List<String>): TestUtxoState {
        return TestUtxoState(identifier, testField, participants, memberNames)
    }
}

@InitiatedBy(protocol = "utxo-backchain-resolution-protocol")
class UtxoBackchainResolutionDemoResponderFlow : ResponderFlow {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService


    @Suspendable
    override fun call(session: FlowSession) {
        @Suppress("unchecked_cast")
        val txs = session.receive(List::class.java) as List<Pair<Int, SecureHash>>
        @Suppress("unchecked_cast")
        val tx3 = session.receive(Pair::class.java) as Pair<Int, SecureHash>

        txs.map { (index, id) -> index to utxoLedgerService.findSignedTransaction(id) }
            .forEach { (index, tx) ->
                log.info("PEER TX$index = ${tx?.id}")
                require(tx == null) { "Transaction TX$index should not be resolved at this point"}
            }
        utxoLedgerService.findSignedTransaction(tx3.second).let { tx ->
            log.info("PEER TX${tx3.first} = ${tx?.id}")
            require(tx == null) { "Transaction TX${tx3.first} should not be resolved at this point" }
        }

        utxoLedgerService.receiveFinality(session) {
            log.info("Received finality - ${it.id}")
        }

        txs.map { (index, id) -> index to utxoLedgerService.findSignedTransaction(id) }
            .forEach { (index, tx) ->
                log.info("PEER TX$index = ${tx?.id}")
                requireNotNull(tx) { "Transaction TX$index should be resolved at this point"}
            }
        utxoLedgerService.findSignedTransaction(tx3.second).let { tx ->
            log.info("PEER TX${tx3.first} = ${tx?.id}")
            require(tx == null) { "Transaction TX${tx3.first} should not be resolved at this point" }
        }

        @Suppress("unchecked_cast")
        val txs2 = session.receive(List::class.java) as List<Pair<Int, SecureHash>>

        txs2.map { (index, id) -> index to utxoLedgerService.findSignedTransaction(id) }
            .forEach { (index, tx) ->
                log.info("PEER TX$index = ${tx?.id}")
                require(tx == null) { "Transaction TX$index should not be resolved at this point"}
            }
        utxoLedgerService.findSignedTransaction(tx3.second).let { tx ->
            log.info("PEER TX${tx3.first} = ${tx?.id}")
            require(tx == null) { "Transaction TX${tx3.first} should not be resolved at this point" }
        }

        utxoLedgerService.receiveFinality(session) {
            log.info("Received finality (2) - ${it.id}")
        }

        txs2.map { (index, id) -> index to utxoLedgerService.findSignedTransaction(id) }
            .forEach { (index, tx) ->
                log.info("PEER TX$index = ${tx?.id}")
                requireNotNull(tx) { "Transaction TX$index should be resolved at this point"}
            }
        utxoLedgerService.findSignedTransaction(tx3.second).let { tx ->
            log.info("PEER TX${tx3.first} = ${tx?.id}")
            requireNotNull(tx) { "Transaction TX${tx3.first} should be resolved at this point" }
        }

        session.send("Done")
    }
}
