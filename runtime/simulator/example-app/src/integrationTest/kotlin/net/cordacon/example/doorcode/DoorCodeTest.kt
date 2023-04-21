package net.cordacon.example.doorcode


import net.corda.crypto.core.parseSecureHash
import net.corda.simulator.RequestData
import net.corda.simulator.Simulator
import net.corda.simulator.crypto.HsmCategory
import net.corda.simulator.factories.JsonMarshallingServiceFactory
import net.corda.v5.application.marshalling.json.JsonDeserializer
import net.corda.v5.application.marshalling.json.JsonNodeReader
import net.corda.v5.crypto.SecureHash
import net.cordacon.example.utils.createMember
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test

class DoorCodeTest {

    @Test
    fun `should get signatures from everyone who needs to sign`() {

        val jsonService = JsonMarshallingServiceFactory.create(
            customJsonDeserializers = mapOf(SecureHashDeserializer to SecureHash::class.java)
        )

        // Given Alice, Bob and Charlie all live in the house
        val simulator = Simulator()
        val alice = createMember("Alice")
        val bob = createMember("Bob")
        val charlie = createMember("Charlie")

        val nodes = listOf(alice, bob, charlie).map {
            val node = simulator.createVirtualNode(it, DoorCodeChangeFlow::class.java,
                DoorCodeChangeResponderFlow::class.java)
            node.generateKey("${it.commonName}-door-code-change-key", HsmCategory.LEDGER, "any-scheme")
            node
        }

        // When we request them all to sign off on the new door code
        val requestData = RequestData.create(
            "r1",
            DoorCodeChangeFlow::class.java,
            DoorCodeChangeRequest(DoorCode("1234"), listOf(alice, bob, charlie))
        )

        // Then the door code should be changed
        val result = jsonService.parse(nodes[0].callFlow(requestData), DoorCodeChangeResult::class.java)
        assertThat(result.newDoorCode, `is`(DoorCode("1234")))
        assertThat(result.signedBy, `is`(setOf(alice, bob, charlie)))

        // And the result should have been persisted for all participants
        val transactionId = result.txId

        val charlieQueryNode = simulator.createVirtualNode(alice, DoorCodeQueryFlow::class.java)
        val queryRequestData = RequestData.create(
            "r1",
            DoorCodeQueryFlow::class.java,
            DoorCodeQuery(transactionId)
        )

        val queryResponse = JsonMarshallingServiceFactory.create().parse(
            charlieQueryNode.callFlow(queryRequestData),
            DoorCodeQueryResponse::class.java
        )
        assertThat(queryResponse.signatories, `is`(setOf(alice, bob, charlie)))
        assertThat(queryResponse.doorCode, `is`(DoorCode("1234")))
    }
}

internal object SecureHashDeserializer : JsonDeserializer<SecureHash> {
    override fun deserialize(jsonRoot: JsonNodeReader): SecureHash {
        return parseSecureHash(jsonRoot.asText())
    }
}