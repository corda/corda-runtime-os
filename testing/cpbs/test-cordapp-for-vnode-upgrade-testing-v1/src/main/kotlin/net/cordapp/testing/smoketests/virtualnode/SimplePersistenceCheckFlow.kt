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
        val fish = Fish(UUID.randomUUID(), "Floaty", "Black", Owner(UUID.randomUUID(), "alice", 22))

        try {
            persistenceService.persist(fish)
        } catch (ex: CordaPersistenceException) {
            return "Could not persist fish"
        }
        return "Could persist fish"
    }
}
