package net.cordapp.testing.utxo.flows

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.getRequestBodyAs
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.cordapp.testing.chat.storeBlock
import net.cordapp.testing.utxo.json.SendCoinsParameter
import net.cordapp.testing.utxo.utils.backChainBlocksFor
import net.cordapp.testing.utxo.utils.blocksForSpend
import net.cordapp.testing.utxo.utils.remainderFromSpend
import net.cordapp.testing.utxo.utils.unspentBlocks

class SendCoinsFlow : RPCStartableFlow {
    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var persistenceService: PersistenceService

    @CordaInject
    lateinit var serializationService: SerializationService

    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        log.info("SendCoinsFlow starting in ${flowEngine.virtualNodeName}...")

        val inputs = requestBody.getRequestBodyAs<SendCoinsParameter>(jsonMarshallingService)
        inputs.recipientX500Name ?: throw IllegalArgumentException("Recipient X500 name not supplied")
        inputs.amount ?: throw IllegalArgumentException("Amount not supplied")

        val unspentBlocks = unspentBlocks(persistenceService, serializationService)
        val unspentBlocksToSpend = blocksForSpend(unspentBlocks, inputs.amount)
        val remainderBlock = remainderFromSpend(unspentBlocksToSpend, inputs.amount, flowEngine.virtualNodeName)

        // Get the backchain blocks for any blocks we intend to spend
        val backChains = backChainBlocksFor(unspentBlocksToSpend, persistenceService, serializationService)

        // Send the backchain required for the receiving party to validate, they do not need to know about the remainder
        flowEngine.subFlow(
            SendChainSubFlow(
                MemberX500Name.parse(inputs.recipientX500Name),
                inputBlocksToNewTransaction = unspentBlocksToSpend,
                backChainBlocks = backChains,
                inputs.amount
            )
        )

        // Store the new unspent coins
        storeBlock(
            block = remainderBlock,
            persistenceService = persistenceService,
            serializationService = serializationService
        )

        log.info("SendCoinsFlow complete for ${flowEngine.virtualNodeName} to ${inputs.recipientX500Name}")
        return ""
    }
}
