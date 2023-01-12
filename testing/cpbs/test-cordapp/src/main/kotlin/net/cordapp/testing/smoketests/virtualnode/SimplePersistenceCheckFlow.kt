package net.cordapp.testing.smoketests.virtualnode

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.RestRequestBody
import net.corda.v5.application.flows.RestStartableFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.persistence.CordaPersistenceException
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger
import net.cordapp.testing.bundles.dogs.Dog
import java.time.Instant
import java.util.UUID

@Suppress("unused")
class SimplePersistenceCheckFlow : RestStartableFlow {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var persistenceService: PersistenceService

    @Suspendable
    override fun call(requestBody: RestRequestBody): String {
        val id = UUID.randomUUID()
        val dog = Dog(id, "Penny", Instant.now(), "Alice")
        try {
            persistenceService.persist(dog)
        } catch (ex: CordaPersistenceException) {
            log.error("exception $ex")
            return "Could not persist dog"
        }
        log.info("Persisted Dog: $dog")
        return "Could persist dog"
    }
}