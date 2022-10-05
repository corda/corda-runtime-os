package net.cordapp.testing.calculator

import java.util.UUID
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger

class CreateNewCalculatorFlow : RPCStartableFlow {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var persistenceService: PersistenceService

    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        log.info("Creating new calculator v2...")

        val req = requestBody.getRequestBodyAs(jsonMarshallingService, CreateNewCalculatorRequest::class.java)

        persistenceService.persist(
            with(req){
                CalculatorEntity(
                    UUID.randomUUID().toString(),
                    numberFormat,
                    scientific,
                    graphing,
                    resolution
                )
            }
        )

        return "true"
    }
}
