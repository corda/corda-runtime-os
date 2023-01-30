package net.cordapp.testing.testflows

import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.RestRequestBody
import net.corda.v5.application.flows.getRequestBodyAs
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.persistence.CordaPersistenceException
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.annotations.Suspendable
import net.cordapp.testing.bundles.dogs.Dog
import net.cordapp.testing.testflows.messages.TestFlowInput
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

/**
 * The PersistenceFlow exercises various basic db interactions in a flow.
 */
@Suppress("unused")
class PersistenceFlow : ClientStartableFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var persistenceService: PersistenceService

    @Suspendable
    override fun call(requestBody: RestRequestBody): String {
        log.info("Starting Test Flow...")
        try {
            val inputs = requestBody.getRequestBodyAs<TestFlowInput>(jsonMarshallingService)

            persistenceService.persist(123)
            persistenceService.remove(123)

            val id = UUID.randomUUID()
            val dog = Dog(id, "Penny", Instant.now(), "Alice")
            persistenceService.persist(dog)
            log.info("Persisted Dog: $dog")

            val id2 = UUID.randomUUID()
            val dog2 = Dog(id2, "Lenard", Instant.now(), "Alice")
            persistenceService.persist(listOf(dog2))
            log.info("Persisted Dog (bulk): $dog2")

            if (inputs.throwException) {
                try {
                    persistenceService.persist(dog)
                    log.error("Persisted second Dog incorrectly: $dog")

                } catch (e: CordaPersistenceException) {
                    return jsonMarshallingService.format("Dog operations failed successfully!")
                }
            }

            val foundDog = persistenceService.find(Dog::class.java, id)
            log.info("Found Dog: $foundDog")
            val foundDogs = persistenceService.find(Dog::class.java, listOf(id, id2))
            log.info("Found Dogs (bulk): $foundDogs")

            log.info("Launching name query")
            val namedQueryDog = persistenceService.query("Dog.summon", Dog::class.java)
                .setLimit(100)
                .setOffset(0)
                .setParameter("name", "Penny")
            val queriedDogs = namedQueryDog.execute()
            log.info("Query for Penny returned the following dogs: $queriedDogs")

            log.info("Launching findAll")
            val findAllQuery = persistenceService.findAll(Dog::class.java)
                .setLimit(100)
                .setOffset(0)
            val allDogs = findAllQuery.execute()
            log.info("findAll returned the following dogs: $allDogs")

            val mergeDog = Dog(id, "Penny", Instant.now(), "Bob")
            val updatedDog = persistenceService.merge(mergeDog)
            log.info("Updated Dog: $updatedDog")
            val mergeDog2 = Dog(id2, "Lenard", Instant.now(), "Bob")
            val updatedDog2 = persistenceService.merge(listOf(mergeDog2))
            log.info("Updated Dog (bulk): $updatedDog2")

            val findDogAfterMerge = persistenceService.find(Dog::class.java, id)
            log.info("Found Updated Dog: $findDogAfterMerge")
            val findDogsAfterMerge = persistenceService.find(Dog::class.java, listOf(id, id2))
            log.info("Found Updated Dogs (bulk): $findDogsAfterMerge")

            if (findDogAfterMerge != null && inputs.inputValue == "delete") {
                persistenceService.remove(findDogAfterMerge)
                log.info("Deleted Dog")
                persistenceService.remove(findDogsAfterMerge)
                log.info("Deleted Dogs (bulk)")

                val findDeletedDogs = persistenceService.find(Dog::class.java, listOf(id, id2))
                log.info("Query for deleted dog returned: $findDeletedDogs")
            }

            return jsonMarshallingService.format("Dog operations are complete")

        } catch (e: Exception) {
            log.error("Unexpected error while processing the flow", e)
            throw e
        }
    }
}
