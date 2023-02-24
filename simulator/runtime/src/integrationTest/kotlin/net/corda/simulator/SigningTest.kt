package net.corda.simulator

import net.corda.simulator.crypto.HsmCategory
import net.corda.simulator.runtime.testflows.HelloFlow
import net.corda.simulator.runtime.testutils.createMember
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SignatureSpec
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class SigningTest {

    companion object {
        class SigningFlow : ClientStartableFlow {
            @CordaInject
            private lateinit var signingService: SigningService

            @CordaInject
            private lateinit var jsonMarshallingService: JsonMarshallingService

            @CordaInject
            private lateinit var memberLookup: MemberLookup

            @Suspendable
            override fun call(requestBody: ClientRequestBody): String {
                val keyHolder = requestBody.getRequestBodyAs(jsonMarshallingService, MemberX500Name::class.java)
                val publicKey = memberLookup.lookup(keyHolder)?.ledgerKeys?.get(0)
                checkNotNull(publicKey)
                signingService.sign("Hello!".toByteArray(), publicKey, SignatureSpec.ECDSA_SHA256)
                return "Signed"
            }
        }
    }

    @Test
    fun `should be able to sign with a key created in another node for the same member`() {
        // Given Alice has two flows
        val simulator = Simulator()
        val alice = createMember("Alice")
        val node1 = simulator.createVirtualNode(alice, HelloFlow::class.java)
        val node2 = simulator.createVirtualNode(alice, SigningFlow::class.java)

        // When she generates a key on one
        node1.generateKey("my-key", HsmCategory.LEDGER, "any-scheme")

        // Then there should be no problem when she signs with the other
        assertDoesNotThrow { node2.callFlow(
            RequestData.create("r1", SigningFlow::class.java, alice)
        )}
    }


    @Test
    fun `should not be able to sign with a key created for another member`() {
        // Given Alice and Bob each have a flow
        val simulator = Simulator()
        val alice = createMember("Alice")
        val bob = createMember("Bob")
        val aliceNode = simulator.createVirtualNode(alice, HelloFlow::class.java)
        val bobNode = simulator.createVirtualNode(bob, SigningFlow::class.java)

        // When Alice generates a key
        aliceNode.generateKey("my-key", HsmCategory.LEDGER, "any-scheme")

        // Then bob should not be able to sign with it
        assertThrows<IllegalStateException> {
            bobNode.callFlow(RequestData.create("r1", SigningFlow::class.java, alice)
        )}
    }
}