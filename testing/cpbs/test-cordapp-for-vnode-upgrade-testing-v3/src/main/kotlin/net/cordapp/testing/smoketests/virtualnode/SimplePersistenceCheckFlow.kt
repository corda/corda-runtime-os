package net.cordapp.testing.smoketests.virtualnode

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.persistence.CordaPersistenceException
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.annotations.Suspendable
import java.util.UUID

@Suppress("unused")
class SimplePersistenceCheckFlow : ClientStartableFlow {

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var persistenceService: PersistenceService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        val egg = Egg(UUID.randomUUID(), "Oval")

        try {
            persistenceService.persist(egg)
        } catch (ex: CordaPersistenceException) {
            return "Could not persist egg"
        }
        return "Could persist egg"
    }
}
