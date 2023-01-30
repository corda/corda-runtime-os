package net.cordapp.testing.smoketests.virtualnode

import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.RestRequestBody
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.persistence.CordaPersistenceException
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.annotations.Suspendable
import net.cordapp.testing.bundles.dogs.Dog
import org.slf4j.LoggerFactory
import java.time.Instant
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