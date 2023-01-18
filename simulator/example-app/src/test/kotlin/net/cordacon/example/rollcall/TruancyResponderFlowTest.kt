package net.cordacon.example.rollcall

import net.corda.simulator.RequestData
import net.corda.simulator.Simulator
import net.corda.simulator.crypto.HsmCategory
import net.corda.simulator.exceptions.ResponderFlowException
import net.corda.simulator.factories.SerializationServiceFactory
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.exceptions.CryptoSignatureException
import net.cordacon.example.utils.createMember
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.isA
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TruancyResponderFlowTest {

    @Test
    fun `should verify records sent against the key and persist them`() {
        val alice = createMember("alice")
        val bob = createMember("bob")
        val charlie = createMember("charlie")

        val simulator = Simulator()
        val serializationService = SerializationServiceFactory.create()

        val initiatingFlow = object: RPCStartableFlow {
            @CordaInject
            lateinit var flowMessaging: FlowMessaging

            @CordaInject
            lateinit var signingService: SigningService

            @CordaInject
            lateinit var memberLookup: MemberLookup

            override fun call(requestBody: RPCRequestData): String {
                val session = flowMessaging.initiateFlow(charlie)

                val absentees = listOf(bob)
                session.send(TruancyRecord(absentees, signingService.sign(
                    serializationService.serialize(absentees).bytes,
                    memberLookup.myInfo().ledgerKeys[0],
                    SignatureSpec.ECDSA_SHA256
                )))
                return ""
            }
        }

        val initiatingNode = simulator.createInstanceNode(alice, "truancy-record", initiatingFlow)
        val truancyNode = simulator.createVirtualNode(charlie, TruancyResponderFlow::class.java)

        initiatingNode.generateKey("my-key", HsmCategory.LEDGER, "any-scheme")
        initiatingNode.callFlow(RequestData.IGNORED)

        val truancyRecords = truancyNode.getPersistenceService().findAll(TruancyEntity::class.java).execute()
        assertThat(truancyRecords.size, `is`(1))
        assertThat(truancyRecords[0].name, `is`(bob.toString()))
    }

    @Test
    fun `should throw an error if the record sent is not properly signed`() {
        val alice = createMember("alice")
        val bob = createMember("bob")
        val charlie = createMember("charlie")

        val simulator = Simulator()

        val initiatingFlow = object: RPCStartableFlow {
            @CordaInject
            lateinit var flowMessaging: FlowMessaging

            @CordaInject
            lateinit var signingService: SigningService

            @CordaInject
            lateinit var memberLookup: MemberLookup

            override fun call(requestBody: RPCRequestData): String {
                val session = flowMessaging.initiateFlow(charlie)

                session.send(TruancyRecord(listOf(bob), signingService.sign(
                    "Not the right bytes!".toByteArray(),
                    memberLookup.myInfo().ledgerKeys[0],
                    SignatureSpec.ECDSA_SHA256
                )))
                return ""
            }
        }

        val initiatingNode = simulator.createInstanceNode(alice, "truancy-record", initiatingFlow)
        simulator.createVirtualNode(charlie, TruancyResponderFlow::class.java)

        initiatingNode.generateKey("my-key", HsmCategory.LEDGER, "any-scheme")

        assertThrows<ResponderFlowException> {
            initiatingNode.callFlow(RequestData.IGNORED)
        }.also {
            assertThat(it.cause, isA(CryptoSignatureException::class.java))
        }
    }
}