package net.cordapp.testing.utxo.flows

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger
import net.cordapp.testing.utxo.json.WalletOutput
import net.cordapp.testing.utxo.utils.unspentBlocks

class WalletFlow : RPCStartableFlow {
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
        log.info("WalletFlow flow starting in ${flowEngine.virtualNodeName}...")

        val unspentBlocks = unspentBlocks(persistenceService, serializationService)
        val totalAmount = unspentBlocks.fold(0L) { amount, block -> amount + block.amount }

        log.info("WalletFlow flow computed wallet of ${flowEngine.virtualNodeName}")
        return jsonMarshallingService.format(WalletOutput(totalAmount))
    }
}
