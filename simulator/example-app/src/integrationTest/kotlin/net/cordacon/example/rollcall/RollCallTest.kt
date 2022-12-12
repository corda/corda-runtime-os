package net.cordacon.example.rollcall

import net.corda.simulator.HoldingIdentity
import net.corda.simulator.RequestData
import net.corda.simulator.Simulator
import net.corda.simulator.crypto.HsmCategory
import net.corda.simulator.factories.SimulatorConfigurationBuilder
import net.corda.v5.base.types.MemberX500Name
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.time.Duration
import java.util.concurrent.TimeUnit

@Timeout(value=5, unit=TimeUnit.MINUTES)
class RollCallTest {

    @Test
    fun `should get roll call from multiple recipients`() {
        // Given a RollCallFlow that's been uploaded to Corda for a teacher
        val simulator = Simulator(SimulatorConfigurationBuilder.create()
            .withTimeout(Duration.ofMinutes(2))
            .withPollInterval(Duration.ofMillis(50))
            .build()
        )
        val teacher = MemberX500Name.parse(
            "CN=Ben Stein, OU=Economics, O=Glenbrook North High School, L=Chicago, C=US"
        )
        val teacherId = HoldingIdentity.create(teacher)
        val teacherVNode = simulator.createVirtualNode(teacherId, RollCallFlow::class.java)

        // And a key to sign the absence record with
        teacherVNode.generateKey("teacher-key", HsmCategory.LEDGER, "Any scheme will do")

        // and recipients with the responder flow and a flow to respond to absence sub-flow when someone is absent
        // (they return an empty string)
        val students = listOf("Albers", "Anderson", "Anheiser", "Busch", "Bueller"). map {
            "CN=$it, OU=Economics, O=Glenbrook North High School, L=Chicago, C=US"
        }
        students.forEach { simulator.createVirtualNode(
            HoldingIdentity.create(MemberX500Name.parse(it)),
            RollCallResponderFlow::class.java,
            AbsenceCallResponderFlow::class.java) }

        // And a truanting authority who will be sent the signed absence record
        val truantingAuth = MemberX500Name.parse(
            "O=TruantAuth, L=Chicago, C=US"
        )
        val truantAuthVNode = simulator.createVirtualNode(
            HoldingIdentity.create(truantingAuth),
            TruancyResponderFlow::class.java
        )

        // When we invoke the roll call in Corda
        val response = teacherVNode.callFlow(
            RequestData.create(
                "r1",
                RollCallFlow::class.java,
                RollCallInitiationRequest(truantingAuth)
        ))

        // Then we should get the response back
        assertThat(response, `is`("""
            BEN STEIN: Albers?
            ALBERS: Here!
            BEN STEIN: Anderson?
            ANDERSON: Here!
            BEN STEIN: Anheiser?
            ANHEISER: Here!
            BEN STEIN: Busch?
            BUSCH: Here!
            BEN STEIN: Bueller?
            BEN STEIN: Bueller?
            BEN STEIN: Bueller?
            
        """.trimIndent().replace("\n", System.lineSeparator())))

        // And Ferris Bueller's absence should have been signed and sent to the truanting authority
        // Then persisted
        val persistence = truantAuthVNode.getPersistenceService()
        val absenceResponses = persistence.findAll(TruancyEntity::class.java)
        assertThat(absenceResponses.execute().map { it.name },
            `is`(listOf("CN=Bueller, OU=Economics, O=Glenbrook North High School, L=Chicago, C=US")))

        simulator.close()
    }
}