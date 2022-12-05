package net.cordapp.demo.utxo

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.flows.getRequestBodyAs
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.receive
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import java.security.PublicKey
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Example consensual flow. Currently, does almost nothing other than verify that
 * we can inject the ledger service. Eventually it should do a two-party IOUState
 * agreement.
 */

@InitiatingFlow("utxo-backchain-resolution-protocol")
class UtxoBackchainResolutionDemoFlow : RPCStartableFlow {
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

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @Suppress("DEPRECATION", "LongMethod")
    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        log.info("UTXO flow demo starting...")
        try {
            val request = requestBody.getRequestBodyAs<InputMessage>(jsonMarshallingService)

            val myInfo = memberLookup.myInfo()
            val members = request.members.map { memberLookup.lookup(MemberX500Name.parse(it))!! }

            val testState = TestState(
                request.input,
                members.map { it.ledgerKeys.first() } + myInfo.ledgerKeys.first()
            )

            val sessions = members.map { flowMessaging.initiateFlow(it.name) }

            val txBuilder = utxoLedgerService.getTransactionBuilder()
            val signedTransaction = txBuilder
                .setNotary(Party(members.first().name, members.first().ledgerKeys.first()))
                .setTimeWindowUntil(Instant.now().plus(10, ChronoUnit.DAYS))
                .addOutputState(testState)
                .addOutputState(testState)
                .addOutputState(testState)
                .addOutputState(testState)
                .addOutputState(testState)
                .toSignedTransaction(myInfo.ledgerKeys.first())

            val tx2 = utxoLedgerService.getTransactionBuilder()
                .setNotary(Party(members.first().name, members.first().ledgerKeys.first()))
                .setTimeWindowUntil(Instant.now().plus(10, ChronoUnit.DAYS))
                .addInputState(StateRef(signedTransaction.id, 0))
                .addInputState(StateRef(signedTransaction.id, 1))
                .addOutputState(testState)
                .toSignedTransaction(myInfo.ledgerKeys.first())

            val tx3 = utxoLedgerService.getTransactionBuilder()
                .setNotary(Party(members.first().name, members.first().ledgerKeys.first()))
                .setTimeWindowUntil(Instant.now().plus(10, ChronoUnit.DAYS))
                .addInputState(StateRef(signedTransaction.id, 2))
                .addInputState(StateRef(signedTransaction.id, 3))
                .addOutputState(testState)
                .toSignedTransaction(myInfo.ledgerKeys.first())

            val tx4 = utxoLedgerService.getTransactionBuilder()
                .setNotary(Party(members.first().name, members.first().ledgerKeys.first()))
                .setTimeWindowUntil(Instant.now().plus(10, ChronoUnit.DAYS))
                .addInputState(StateRef(signedTransaction.id, 4))
                .addOutputState(testState)
                .toSignedTransaction(myInfo.ledgerKeys.first())

            val tx5 = utxoLedgerService.getTransactionBuilder()
                .setNotary(Party(members.first().name, members.first().ledgerKeys.first()))
                .setTimeWindowUntil(Instant.now().plus(10, ChronoUnit.DAYS))
                .addInputState(StateRef(tx2.id, 1))
                .addOutputState(testState)
                .toSignedTransaction(myInfo.ledgerKeys.first())

            val tx6 = utxoLedgerService.getTransactionBuilder()
                .setNotary(Party(members.first().name, members.first().ledgerKeys.first()))
                .setTimeWindowUntil(Instant.now().plus(10, ChronoUnit.DAYS))
                .addInputState(StateRef(tx4.id, 0))
                .addInputState(StateRef(tx5.id, 0))
                .addOutputState(testState)
                .toSignedTransaction(myInfo.ledgerKeys.first())

            val tx7 = utxoLedgerService.getTransactionBuilder()
                .setNotary(Party(members.first().name, members.first().ledgerKeys.first()))
                .setTimeWindowUntil(Instant.now().plus(10, ChronoUnit.DAYS))
                .addInputState(StateRef(tx3.id, 0))
                .addOutputState(testState)
                .toSignedTransaction(myInfo.ledgerKeys.first())

            val tx8 = utxoLedgerService.getTransactionBuilder()
                .setNotary(Party(members.first().name, members.first().ledgerKeys.first()))
                .setTimeWindowUntil(Instant.now().plus(10, ChronoUnit.DAYS))
                .addInputState(StateRef(tx6.id, 0))
                .addInputState(StateRef(tx7.id, 0))
                .addOutputState(testState)
                .addOutputState(testState)
                .toSignedTransaction(myInfo.ledgerKeys.first())

            val tx9 = utxoLedgerService.getTransactionBuilder()
                .setNotary(Party(members.first().name, members.first().ledgerKeys.first()))
                .setTimeWindowUntil(Instant.now().plus(10, ChronoUnit.DAYS))
                .addInputState(StateRef(tx8.id, 0))
                .addOutputState(testState)
                .toSignedTransaction(myInfo.ledgerKeys.first())

            val tx10 = utxoLedgerService.getTransactionBuilder()
                .setNotary(Party(members.first().name, members.first().ledgerKeys.first()))
                .setTimeWindowUntil(Instant.now().plus(10, ChronoUnit.DAYS))
                .addInputState(StateRef(tx8.id, 1))
                .addOutputState(testState)
                .toSignedTransaction(myInfo.ledgerKeys.first())

            val tx11 = utxoLedgerService.getTransactionBuilder()
                .setNotary(Party(members.first().name, members.first().ledgerKeys.first()))
                .setTimeWindowUntil(Instant.now().plus(10, ChronoUnit.DAYS))
                .addInputState(StateRef(tx9.id, 0))
                .addInputState(StateRef(tx10.id, 0))
                .addOutputState(testState)
                .toSignedTransaction(myInfo.ledgerKeys.first())

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
                session.send(listOf(signedTransaction, tx2, tx3, tx4, tx5, tx6, tx7, tx8, tx9, tx10, tx11))
            }

            log.info("RESOLVING TX BACKCHAIN original_id=${signedTransaction.id} new_tx_id=${tx6.id}")

            utxoLedgerService.resolveBackchain(tx6, sessions.first())

            utxoLedgerService.persistTransaction(tx6)

            log.info("RESOLVED TX6 BACKCHAIN")

            var a = utxoLedgerService.findSignedTransaction(signedTransaction.id)
            var b = utxoLedgerService.findSignedTransaction(tx2.id)
            var c = utxoLedgerService.findSignedTransaction(tx3.id)
            var d = utxoLedgerService.findSignedTransaction(tx4.id)
            var e = utxoLedgerService.findSignedTransaction(tx5.id)
            var f = utxoLedgerService.findSignedTransaction(tx6.id)
            var g = utxoLedgerService.findSignedTransaction(tx7.id)
            var h = utxoLedgerService.findSignedTransaction(tx8.id)
            log.info("Existence of tx1 = $a")
            log.info("Existence of tx2 = $b")
            log.info("Existence of tx3 = $c")
            log.info("Existence of tx4 = $d")
            log.info("Existence of tx5 = $e")
            log.info("Existence of tx6 = $f")
            log.info("Existence of tx7 = $g")
            log.info("Existence of tx8 = $h")

            utxoLedgerService.resolveBackchain(tx11, sessions.first())

            utxoLedgerService.persistTransaction(tx11)

            log.info("RESOLVED TX11 BACKCHAIN")

            a = utxoLedgerService.findSignedTransaction(signedTransaction.id)
            b = utxoLedgerService.findSignedTransaction(tx2.id)
            c = utxoLedgerService.findSignedTransaction(tx3.id)
            d = utxoLedgerService.findSignedTransaction(tx4.id)
            e = utxoLedgerService.findSignedTransaction(tx5.id)
            f = utxoLedgerService.findSignedTransaction(tx6.id)
            g = utxoLedgerService.findSignedTransaction(tx7.id)
            h = utxoLedgerService.findSignedTransaction(tx8.id)
            val i = utxoLedgerService.findSignedTransaction(tx9.id)
            val j = utxoLedgerService.findSignedTransaction(tx10.id)
            val k = utxoLedgerService.findSignedTransaction(tx11.id)

            log.info("Existence of tx1 (2) = $a")
            log.info("Existence of tx2 (2) = $b")
            log.info("Existence of tx3 (2) = $c")
            log.info("Existence of tx4 (2) = $d")
            log.info("Existence of tx5 (2) = $e")
            log.info("Existence of tx6 (2) = $f")
            log.info("Existence of tx7 (2) = $g")
            log.info("Existence of tx8 (2) = $h")
            log.info("Existence of tx9 (2) = $i")
            log.info("Existence of tx10 (2) = $j")
            log.info("Existence of tx11 (2) = $k")

            return "SUCCESS"
        } catch (e: Exception) {
            log.warn("Failed to process consensual flow for request body '$requestBody' because:'${e.message}'")
            throw e
        }
    }
}

@InitiatedBy("utxo-backchain-resolution-protocol")
class UtxoBackchainResolutionDemoResponderFlow : ResponderFlow {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @Suppress("DEPRECATION")
    @Suspendable
    override fun call(session: FlowSession) {
        val txs = session.receive<List<UtxoSignedTransaction>>()
        log.info("RECEIVED TXs ON PEER")
        for (tx in txs) {
            utxoLedgerService.persistTransaction(tx)
        }
        log.info("RESOLVING ON PEER")
        utxoLedgerService.sendBackchain(session)
        log.info("FINISHED SENDING BACKCHAIN")

        log.info("RESOLVING ON PEER (2)")
        utxoLedgerService.sendBackchain(session)
        log.info("FINISHED SENDING BACKCHAIN (2)")
    }
}
