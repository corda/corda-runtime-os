package net.cordapp.testing.utxo.flows

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.receive
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger
import net.cordapp.testing.chat.storeBlock
import net.cordapp.testing.utxo.Block
import net.cordapp.testing.utxo.messages.Transaction

@InitiatedBy(protocol = "sendcoins")
class OnTxReceivedResponderFlow : ResponderFlow {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var serializationService: SerializationService

    @CordaInject
    lateinit var persistenceService: PersistenceService

    @Suspendable
    override fun call(session: FlowSession) {
        val thisVirtualNodeName = flowEngine.virtualNodeName.toString()
        log.info("OnTxReceivedResponderFlow in {$thisVirtualNodeName}...")

        val sender = session.counterparty.toString()
        val transaction = session.receive<Transaction>()

        // Store a new block which is this vnodes coins
        val receivedCoins =
            Block(
                transaction.inputBlocksToNewTransaction.map { it.hashCode().toLong() },
                flowEngine.virtualNodeName.toString(),
                transaction.amount
            )

        // Store the blocks we were sent which were involved in this transaction
        transaction.backChainBlocks.forEach {
            storeBlock(it, persistenceService, serializationService)
        }

        // @@@ This is where we would validate the backchain, we have all the inputs to the transaction and every block
        // involved in every backchain for us to look at

        storeBlock(receivedCoins, persistenceService, serializationService)

        log.info("Added incoming message from $sender to message store")
    }
}
