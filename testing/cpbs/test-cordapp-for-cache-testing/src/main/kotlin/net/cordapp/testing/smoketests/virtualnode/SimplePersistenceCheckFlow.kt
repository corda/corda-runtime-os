package net.cordapp.testing.smoketests.virtualnode

import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.persistence.CordaPersistenceException
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.annotations.Suspendable
import net.cordapp.testing.bundles.fish.Fish
import net.cordapp.testing.bundles.fish.Owner
import org.slf4j.LoggerFactory
import java.util.UUID

@Suppress("unused")
class SimplePersistenceCheckFlow : ClientStartableFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

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
            log.error("exception $ex")
            return "Could not persist fish"
        }
        log.info("Persisted Fish: $fish")
        return "Could persist ${fish.name}"
    }
}
