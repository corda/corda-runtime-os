package net.cordacon.example.rollcall

import net.corda.crypto.core.InvalidParamsException
import net.corda.simulator.RequestData
import net.corda.simulator.Simulator
import net.corda.simulator.crypto.HsmCategory
import net.corda.simulator.exceptions.ResponderFlowException
import net.corda.simulator.factories.SerializationServiceFactory
import net.corda.v5.application.crypto.SignatureSpecService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
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

        val initiatingFlow = object : ClientStartableFlow {
            @CordaInject
            lateinit var flowMessaging: FlowMessaging

            @CordaInject
            lateinit var signingService: SigningService

            @CordaInject
            lateinit var memberLookup: MemberLookup

            @CordaInject
            lateinit var signatureSpecService: SignatureSpecService

            override fun call(requestBody: ClientRequestBody): String {
                val session = flowMessaging.initiateFlow(charlie)
                val myKey = requireNotNull(memberLookup.myInfo().ledgerKeys[0])

                val signatureSpec = signatureSpecService.defaultSignatureSpec(myKey)
                    ?: throw IllegalStateException("Default signature spec not found for key")

                val absentees = listOf(bob)
                val signature = signingService.sign(
                    serializationService.serialize(absentees).bytes,
                    myKey,
                    signatureSpec
                )
                session.send(TruancyRecord(absentees, signature, signatureSpec))
                return ""
            }
        }

        val initiatingNode = simulator.createInstanceNode(alice, "truancy-record", initiatingFlow)
        val truancyNode = simulator.createVirtualNode(charlie, TruancyResponderFlow::class.java)

        initiatingNode.generateKey("my-key", HsmCategory.LEDGER, "any-scheme")
        initiatingNode.callFlow(RequestData.IGNORED)

        val truancyRecords = truancyNode.getPersistenceService().findAll(TruancyEntity::class.java).execute().results
        assertThat(truancyRecords.size, `is`(1))
        assertThat(truancyRecords[0].name, `is`(bob.toString()))
    }

    @Test
    fun `should throw an error if the record sent is not properly signed`() {
        val alice = createMember("alice")
        val bob = createMember("bob")
        val charlie = createMember("charlie")

        val simulator = Simulator()

        val initiatingFlow = object : ClientStartableFlow {
            @CordaInject
            lateinit var flowMessaging: FlowMessaging

            @CordaInject
            lateinit var signingService: SigningService

            @CordaInject
            lateinit var memberLookup: MemberLookup

            @CordaInject
            lateinit var signatureSpecService: SignatureSpecService

            override fun call(requestBody: ClientRequestBody): String {
                val session = flowMessaging.initiateFlow(charlie)

                val myKey = memberLookup.myInfo().ledgerKeys[0]

                val signatureSpec = signatureSpecService.defaultSignatureSpec(myKey)
                    ?: throw InvalidParamsException("Default signature spec not found for key")

                val signature = signingService.sign(
                    "Not the right bytes!".toByteArray(),
                    myKey,
                    signatureSpec
                )

                session.send(TruancyRecord(listOf(bob), signature, signatureSpec))
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