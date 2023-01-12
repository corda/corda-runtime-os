package net.corda.simulator

import net.corda.simulator.runtime.persistence.GreetingEntity
import net.corda.simulator.runtime.testutils.createMember
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.RestRequestBody
import net.corda.v5.application.flows.RestStartableFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.annotations.Suspendable
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import java.util.UUID

class PersistenceTest {

    companion object {

        class PersistingFlow: RestStartableFlow {
            @CordaInject
            private lateinit var persistenceService: PersistenceService

            @CordaInject
            private lateinit var jsonMarshallingService: JsonMarshallingService

            @Suspendable
            override fun call(requestBody: RestRequestBody): String {
                val greeting = requestBody.getRequestBodyAs(jsonMarshallingService, String::class.java)
                val entity = GreetingEntity(UUID.randomUUID(), greeting)
                persistenceService.persist(entity)
                return entity.id.toString()
            }
        }

        class QueryingFlow: RestStartableFlow {
            @CordaInject
            private lateinit var persistenceService: PersistenceService

            @CordaInject
            private lateinit var jsonMarshallingService: JsonMarshallingService

            @Suspendable
            override fun call(requestBody: RestRequestBody): String {
                val uuid = requestBody.getRequestBodyAs(jsonMarshallingService, String::class.java)
                val id = UUID.fromString(uuid)
                return persistenceService.find(GreetingEntity::class.java, id)?.greeting ?: "Not found"
            }
        }
    }

    @Test
    fun `should be able to persist from a member node and read in another of their nodes`() {
        // Given a member with two nodes
        val simulator = Simulator()
        val alice = createMember("Alice")
        val persistingNode = simulator.createVirtualNode(alice, PersistingFlow::class.java)
        val queryingNode = simulator.createVirtualNode(alice, QueryingFlow::class.java)

        // When we persist in one node and read in the other
        val uuid = persistingNode.callFlow(RequestData.create("r1", PersistingFlow::class.java, "Hello!"))
        val result = queryingNode.callFlow(RequestData.create("r1", QueryingFlow::class.java, uuid))

        // Then the result should be as expected
        assertThat(result, `is`("Hello!"))
    }

    @Test
    fun `should not be able to see an entity persisted in one node from someone elses node`() {
        // Given different members with one node each
        val simulator = Simulator()
        val alice = createMember("Alice")
        val bob = createMember("Bob")
        val persistingNode = simulator.createVirtualNode(alice, PersistingFlow::class.java)
        val queryingNode = simulator.createVirtualNode(bob, QueryingFlow::class.java)

        // When we persist in one node and read in the other
        val uuid = persistingNode.callFlow(RequestData.create("r1", PersistingFlow::class.java, "Hello!"))
        val result = queryingNode.callFlow(RequestData.create("r1", QueryingFlow::class.java, uuid))

        // Then the result should be as expected
        assertThat(result, `is`("Not found"))
    }

}