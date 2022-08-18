package net.cordacon.example

import net.corda.testutils.FakeCorda
import net.corda.testutils.HoldingIdentity
import net.corda.testutils.services.SimpleJsonMarshallingService
import net.corda.testutils.tools.RPCRequestDataWrapper
import net.corda.testutils.tools.ResponderMock
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.UntrustworthyData
import net.corda.v5.base.types.MemberX500Name
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RollCallFlowTest {

    @Test
    fun `should request each student to respond to roll call`() {
        // Given a teacher with an initiating flow
        val teacherId = HoldingIdentity.create("Teach")
        val corda = FakeCorda()
        val teacherVNodeinfo = corda.createVirtualNode(teacherId, RollCallFlow::class.java)

        // And a list of students who will respond predictably
        val students = listOf("Alpha", "Beta", "Gamma").map {
            Pair(it, "CN=${it}, OU=VIth Form, O=Grange Hill, L=London, C=GB")
        }.toMap()

        val responses = mapOf(
            "Alpha" to "Yep",
            "Beta" to "Here",
            "Gamma" to "Yo"
        )

        val responder = ResponderMock<RollCallRequest, RollCallResponse>()
        responses.forEach {
            val student = students[it.key]!!
            responder.whenever(RollCallRequest(student), listOf(RollCallResponse(it.value)))
            corda.createVirtualNode(HoldingIdentity(MemberX500Name.parse(student)), "roll-call", responder)
        }

        // When we contact the students
        val result = corda.callFlow(teacherVNodeinfo, RPCRequestDataWrapper.fromData(
            "r1",
            RollCallFlow::class.java,
            RollCallInitiationRequest(students.values.toList())))

        // Then their responses should be tallied in the report
        assertThat(result, `is`("""
            TEACH: Alpha?
            ALPHA: Yep
            TEACH: Beta?
            BETA: Here
            TEACH: Gamma?
            GAMMA: Yo
            
        """.trimIndent().replace("\n", System.lineSeparator())))
    }

    @Test
    fun `should retry twice if any student fails to respond`() {
        // Given a teacher with an initiating flow
        val teacherId = HoldingIdentity.create("Teach")
        val corda = FakeCorda()
        val teacherVNodeinfo = corda.createVirtualNode(teacherId, RollCallFlow::class.java)

        // And a student who will respond with empty string repeatedly
        val studentId = "CN=Zammo, OU=VIth Form, O=Grange Hill, L=London, C=GB"
        val responder = ResponderMock<RollCallRequest, RollCallResponse>()
        responder.whenever(RollCallRequest(studentId), listOf(RollCallResponse("")))

        // ...for both kinds of flow
        corda.createVirtualNode(HoldingIdentity(MemberX500Name.parse(studentId)), "roll-call", responder)
        corda.createVirtualNode(HoldingIdentity(MemberX500Name.parse(studentId)), "absence-call", responder)

        // When we contact the student
        val result = corda.callFlow(teacherVNodeinfo, RPCRequestDataWrapper.fromData(
            "r1",
            RollCallFlow::class.java,
            RollCallInitiationRequest(listOf(studentId))))

        // Then it should just be the teacher calling into empty air
        assertThat(result, `is`("""
            TEACH: Zammo?
            TEACH: Zammo?
            TEACH: Zammo?
            
        """.trimIndent().replace("\n", System.lineSeparator())))
    }

    @Test
    fun `should use the AbsenceFlow to do the retry`() {
        // Given a teacher with an initiating flow
        val teacherId = HoldingIdentity.create("Teach")
        val flow = RollCallFlow()
        flow.flowMessaging = mock()
        flow.flowEngine = mock()
        flow.persistenceService = mock()
        flow.jsonMarshallingService = SimpleJsonMarshallingService()

        whenever(flow.flowEngine.virtualNodeName).thenReturn(teacherId.member)

        // And a student who we'll mimic being absent
        val studentId = HoldingIdentity.create("Zammo")

        val flowSession = mock<FlowSession>()
        whenever(flowSession.counterparty).thenReturn(studentId.member)
        whenever(flow.flowMessaging.initiateFlow(studentId.member)).thenReturn(flowSession)
        whenever(flowSession.sendAndReceive<RollCallResponse>(any(), any()))
            .thenReturn(UntrustworthyData(RollCallResponse("")))

        // And a flow engine which will return an empty response for our subflow too
        whenever(flow.flowEngine.subFlow(any<AbsenceSubFlow>())).thenReturn("")

        // When we call the flow for a student who is absent
        flow.call(RPCRequestDataWrapper.fromData(
            "r1",
            RollCallFlow::class.java,
            RollCallInitiationRequest(listOf(studentId.member.toString()))).toRPCRequestData())

        // Then the subflow should have been called twice
        verify(flow.flowEngine, times(2)).subFlow(any<AbsenceSubFlow>())
    }
}