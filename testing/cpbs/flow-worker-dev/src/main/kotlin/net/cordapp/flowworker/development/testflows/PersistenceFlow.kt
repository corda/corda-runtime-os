package net.cordapp.flowworker.development.testflows

import java.time.Instant
import java.util.UUID
import net.cordapp.testing.bundles.dogs.Dog
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.getRequestBodyAs
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.persistence.CordaPersistenceException
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger
import net.cordapp.flowworker.development.testflows.messages.TestFlowInput

/**
 * The PersistenceFlow exercises various basic db interactions in a flow.
 */
@Suppress("unused")
class PersistenceFlow : RPCStartableFlow {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var persistenceService: PersistenceService

    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        log.info("Starting Test Flow...")
        try {
            val inputs = requestBody.getRequestBodyAs<TestFlowInput>(jsonMarshallingService)

            val id = UUID.randomUUID()
            val dog = Dog(id, "Penny", Instant.now(), "Alice")
            persistenceService.persist(dog)
            log.info("Persisted Dog: $dog")

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

            val mergeDog = Dog(id, "Penny", Instant.now(), "Bob")
            val updatedDog = persistenceService.merge(mergeDog)
            log.info("Updated Dog: $updatedDog")

            val findDogAfterMerge = persistenceService.find(Dog::class.java, id)
            log.info("Found Updated Dog: $findDogAfterMerge")

            if (findDogAfterMerge != null && inputs.inputValue == "delete") {
                persistenceService.remove(findDogAfterMerge)
                log.info("Deleted Dog")

                val dogFindNull = persistenceService.find(Dog::class.java, id)
                log.info("Query for deleted dog returned: $dogFindNull")
            }

            return jsonMarshallingService.format("Dog operations are complete")

        } catch (e: Exception) {
            log.error("Unexpected error while processing the flow", e)
            throw e
        }
    }
}
