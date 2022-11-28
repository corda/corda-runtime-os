package net.cordacon.example.doorcode

import net.corda.simulator.HoldingIdentity
import net.corda.simulator.RequestData
import net.corda.simulator.Simulator
import net.corda.simulator.crypto.HsmCategory
import net.corda.simulator.factories.JsonMarshallingServiceFactory
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test

class DoorCodeTest {

    @Test
    fun `should get signatures from everyone who needs to sign`() {
        // Given Alice, Bob and Charlie all live in the house
        val simulator = Simulator()
        val alice = HoldingIdentity.create("Alice")
        val bob = HoldingIdentity.create("Bob")
        val charlie = HoldingIdentity.create("Charlie")

        val nodes = listOf(alice, bob, charlie).map {
            val node = simulator.createVirtualNode(it, DoorCodeChangeFlow::class.java,
                DoorCodeChangeResponderFlow::class.java)
            node.generateKey("${it.member.commonName}-door-code-change-key", HsmCategory.LEDGER, "any-scheme")
            node
        }

        // When we request them all to sign off on the new door code
        val requestData = RequestData.create(
            "r1",
            DoorCodeChangeFlow::class.java,
            DoorCodeChangeRequest(DoorCode("1234"), listOf(bob.member, charlie.member))
        )

        // Then the door code should be changed
        val jsonService = JsonMarshallingServiceFactory.create()
        val result = jsonService.parse(nodes[0].callFlow(requestData), DoorCodeChangeResult::class.java)
        assertThat(result.newDoorCode, `is`(DoorCode("1234")))
        assertThat(result.signedBy, `is`(setOf(alice.member, bob.member, charlie.member)))
    }
}