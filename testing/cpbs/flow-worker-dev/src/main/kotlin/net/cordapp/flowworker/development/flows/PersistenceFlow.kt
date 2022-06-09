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
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.cordapp.flowworker.development.messages.TestFlowInput
import net.cordapp.flowworker.development.messages.TestFlowOutput

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
            val id = UUID.randomUUID()
            val dog = Dog(id, "Penny", Instant.now(), "Alice")
            persistenceService.persist(dog)

            val foundDog = persistenceService.find(Dog::class.java, id)
            log.info("Found Cat: $foundDog")

            val mergeDog = Dog(id, "Penny", Instant.now(), "Bob")
            val updatedDog = persistenceService.merge(mergeDog)
            log.info("Updated Dog: $updatedDog")

            val findDogAfterMerge = persistenceService.find(Dog::class.java, id)
            log.info("Found Dog: $findDogAfterMerge")

            if (findDogAfterMerge != null) {
                persistenceService.remove(findDogAfterMerge)
                log.info("Deleted Dog")
            }

            val dogFindNull = persistenceService.find(Dog::class.java, id)
            log.info("Query for deleted dog returned: $dogFindNull")


            val inputs = jsonMarshallingService.parseJson<TestFlowInput>(jsonArg)
            if(inputs.throwException){
                throw IllegalStateException("Caller requested exception to be raised")
            }

            val foundMemberInfo = if(inputs.memberInfoLookup==null){
                "No member lookup requested."
            }else{
                val lookupResult = memberLookupService.lookup(MemberX500Name.parse(inputs.memberInfoLookup!!))
                lookupResult?.name?.toString() ?: "Failed to find MemberInfo for ${inputs.memberInfoLookup!!}"
            }

            /**
             * For now this is removed to allow others to test while the issue preventing this
             * from working is investigated
            val subFlow = TestGetNodeNameSubFlow()
            val myIdentity = flowEngine.subFlow(subFlow)
            */

            val response = TestFlowOutput(
                inputs.inputValue?:"No input value",
                "dummy",
                foundMemberInfo
            )

            return jsonMarshallingService.formatJson(response)

        } catch (e: Exception) {
            log.error("Unexpected error while processing the flow",e )
            throw e
        }
    }
}
