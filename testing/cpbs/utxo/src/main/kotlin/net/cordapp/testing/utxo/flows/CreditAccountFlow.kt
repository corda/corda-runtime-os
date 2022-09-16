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
import net.corda.v5.base.util.contextLogger
import net.cordapp.testing.chat.storeBlock
import net.cordapp.testing.utxo.Block
import net.cordapp.testing.utxo.json.CreditAccountParameter

class CreditAccountFlow : RPCStartableFlow {
    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var serializationService: SerializationService

    @CordaInject
    lateinit var persistenceService: PersistenceService

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        log.info("CreditAccountFlow outgoing flow starting in ${flowEngine.virtualNodeName}...")

        val inputs = requestBody.getRequestBodyAs<CreditAccountParameter>(jsonMarshallingService)
        
        inputs.amount ?: throw IllegalArgumentException("Amount not supplied")

        // To issue money we create a block with no inputs and the correct owner. Only the owner can spend this and
        // needs to know about it at this point. Other members will find out about it when it's the subject of a
        // spend like any other utx.
        val block = Block(owner = flowEngine.virtualNodeName.toString(), inputs = emptyList(), amount = inputs.amount)
        storeBlock(block = block, persistenceService = persistenceService, serializationService = serializationService)

        log.info("CreditAccountFlow to ${flowEngine.virtualNodeName} with ${inputs.amount} completed")
        return ""
    }
}
