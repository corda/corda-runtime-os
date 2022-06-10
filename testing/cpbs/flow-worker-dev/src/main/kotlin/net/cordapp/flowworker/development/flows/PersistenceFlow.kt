package net.cordapp.flowworker.development.flows

import java.time.Instant
import java.util.UUID
import net.corda.testing.bundles.dogs.Dog
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.StartableByRPC
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.application.serialization.JsonMarshallingService
import net.corda.v5.application.serialization.parseJson
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger
import net.corda.v5.persistence.CordaPersistenceException
import net.cordapp.flowworker.development.messages.TestFlowInput

/**
 * The Test Flow exercises various basic features of a flow, this flow
 * is used as a basic flow worker smoke test.
 */
@Suppress("unused")
@StartableByRPC
class PersistenceFlow(private val jsonArg: String) : Flow<String> {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var memberLookupService: MemberLookup

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var persistenceService: PersistenceService

    @Suspendable
    override fun call(): String {
        log.info("Starting Test Flow...")
        try {
            val inputs = jsonMarshallingService.parseJson<TestFlowInput>(jsonArg)

            val id = UUID.randomUUID()
            val dog = Dog(id, "Penny", Instant.now(), "Alice")
            persistenceService.persist(dog)
            log.info("Persisted Dog: $dog")

            if (inputs.throwException) {
                try {
                    persistenceService.persist(dog)
                    log.error("Persisted second Dog incorrectly: $dog")

                } catch (e: CordaPersistenceException) {
                    return "Dog operations failed successfully!"
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

            return "Dog operations are complete"

        } catch (e: Exception) {
            log.error("Unexpected error while processing the flow",e )
            throw e
        }
    }
}
