package net.corda.flow.application.services

import net.corda.data.flow.state.checkpoint.FlowStackItem
import net.corda.data.flow.state.checkpoint.FlowStackItemSession
import net.corda.flow.ALICE_X500_NAME
import net.corda.flow.application.serialization.SerializationServiceInternal
import net.corda.flow.application.services.impl.FlowMessagingImpl
import net.corda.flow.application.sessions.FlowSessionInternal
import net.corda.flow.application.sessions.SessionInfo
import net.corda.flow.application.sessions.factory.FlowSessionFactory
import net.corda.flow.application.versioning.impl.sessions.VersionReceivingFlowSession
import net.corda.flow.application.versioning.impl.sessions.VersionSendingFlowSession
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.utils.mutableKeyValuePairList
import net.corda.v5.application.messaging.FlowContextPropertiesBuilder
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.serialization.SerializedBytes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@Suppress("MaxLineLength")
class FlowMessagingImplTest {

    private companion object {
        const val SESSION_ID_ONE = "one"
        const val SESSION_ID_TWO = "two"
        const val SESSION_ID_THREE = "three"
        const val SESSION_ID_FOUR = "four"
        const val SESSION_ID_FIVE = "five"
        const val PAYLOAD_ONE = "payload one"
        const val PAYLOAD_TWO = "payload two"
        const val PAYLOAD_THREE = "payload three"
        const val PAYLOAD_FOUR = "payload four"
        const val PAYLOAD_FIVE = "payload five"
        const val FLOW_NAME = "flow name"

        val SERIALIZED_PAYLOAD_ONE = byteArrayOf(1)
        val SERIALIZED_PAYLOAD_TWO = byteArrayOf(2)
        val SERIALIZED_PAYLOAD_THREE = byteArrayOf(3)
        val SERIALIZED_PAYLOAD_FOUR = byteArrayOf(4)
        val SERIALIZED_PAYLOAD_FIVE = byteArrayOf(5)
        val SERIALIZED_AGREED_VERSION_AND_PAYLOAD_ONE = byteArrayOf(6)
        val SERIALIZED_AGREED_VERSION_AND_PAYLOAD_TWO = byteArrayOf(7)

        val SESSION_INFO_ONE = SessionInfo(SESSION_ID_ONE, MemberX500Name("org", "LDN", "GB"))
        val SESSION_INFO_TWO = SessionInfo(SESSION_ID_TWO, MemberX500Name("org", "LDN", "GB"))
        val SESSION_INFO_THREE = SessionInfo(SESSION_ID_THREE, MemberX500Name("org", "LDN", "GB"))
        val SESSION_INFO_FOUR = SessionInfo(SESSION_ID_FOUR, MemberX500Name("org", "LDN", "GB"))
        val SESSION_INFO_FIVE = SessionInfo(SESSION_ID_FIVE, MemberX500Name("org", "LDN", "GB"))
    }

    private val mockFlowFiberService = MockFlowFiberService()
    private val flowStackService = mockFlowFiberService.flowStack
    private val flowSession = mock<FlowSession>()
    private val serializationService = mock<SerializationServiceInternal>()

    private val flowSessionFactory = mock<FlowSessionFactory>().apply {
        whenever(createInitiatingFlowSession(any(), eq(ALICE_X500_NAME), any())).thenReturn(flowSession)
    }

    private val normalSessionOne = mock<FlowSessionInternal>()
    private val versionSendingSessionOne = mock<VersionSendingFlowSession>()
    private val versionSendingSessionTwo = mock<VersionSendingFlowSession>()
    private val versionReceivingSessionOne = mock<VersionReceivingFlowSession>()
    private val versionReceivingSessionTwo = mock<VersionReceivingFlowSession>()

    private val receiveSuspendCaptor = argumentCaptor<FlowIORequest.Receive>()
    private val sendSuspendCaptor = argumentCaptor<FlowIORequest.Send>()
    private val multiUseCaptor = argumentCaptor<FlowIORequest<*>>()

    private val flowMessaging = FlowMessagingImpl(mockFlowFiberService, flowSessionFactory, serializationService)

    @BeforeEach
    fun beforeEach() {
        whenever(serializationService.deserializeAndCheckType(SERIALIZED_PAYLOAD_ONE, Any::class.java)).thenReturn(PAYLOAD_ONE)
        whenever(serializationService.deserializeAndCheckType(SERIALIZED_PAYLOAD_TWO, Any::class.java)).thenReturn(PAYLOAD_TWO)
        whenever(serializationService.deserializeAndCheckType(SERIALIZED_PAYLOAD_THREE, Any::class.java)).thenReturn(PAYLOAD_THREE)
        whenever(serializationService.deserializeAndCheckType(SERIALIZED_PAYLOAD_FOUR, Any::class.java)).thenReturn(PAYLOAD_FOUR)
        whenever(serializationService.deserializeAndCheckType(SERIALIZED_PAYLOAD_FIVE, Any::class.java)).thenReturn(PAYLOAD_FIVE)

        whenever(serializationService.serialize(PAYLOAD_ONE)).thenReturn(SerializedBytes(SERIALIZED_PAYLOAD_ONE))
        whenever(serializationService.serialize(PAYLOAD_TWO)).thenReturn(SerializedBytes(SERIALIZED_PAYLOAD_TWO))
        whenever(serializationService.serialize(PAYLOAD_THREE)).thenReturn(SerializedBytes(SERIALIZED_PAYLOAD_THREE))

        whenever(normalSessionOne.getSessionInfo()).thenReturn(SESSION_INFO_ONE)
        whenever(versionSendingSessionOne.getSessionInfo()).thenReturn(SESSION_INFO_TWO)
        whenever(versionSendingSessionTwo.getSessionInfo()).thenReturn(SESSION_INFO_THREE)
        whenever(versionReceivingSessionOne.getSessionInfo()).thenReturn(SESSION_INFO_FOUR)
        whenever(versionReceivingSessionTwo.getSessionInfo()).thenReturn(SESSION_INFO_FIVE)

        whenever(normalSessionOne.getSessionId()).thenReturn(SESSION_ID_ONE)
        whenever(versionSendingSessionOne.getSessionId()).thenReturn(SESSION_ID_TWO)
        whenever(versionSendingSessionTwo.getSessionId()).thenReturn(SESSION_ID_THREE)
        whenever(versionReceivingSessionOne.getSessionId()).thenReturn(SESSION_ID_FOUR)
        whenever(versionReceivingSessionTwo.getSessionId()).thenReturn(SESSION_ID_FIVE)
    }

    @Test
    fun `initiateFlow creates an initiating FlowSession when the current flow stack item represents an initiating flow`() {
        whenever(flowStackService.peek()).thenReturn(
            FlowStackItem(
                FLOW_NAME,
                true,
                mutableListOf(),
                mutableKeyValuePairList(),
                mutableKeyValuePairList()
            )
        )
        flowMessaging.initiateFlow(ALICE_X500_NAME)
        verify(flowSessionFactory).createInitiatingFlowSession(any(), eq(ALICE_X500_NAME), eq(null))
    }

    @Test
    fun `initiateFlow builder overload creates an initiating FlowSession passing a context builder`() {
        whenever(flowStackService.peek()).thenReturn(
            FlowStackItem(
                FLOW_NAME,
                true,
                mutableListOf(),
                mutableKeyValuePairList(),
                mutableKeyValuePairList()
            )
        )

        val builder = FlowContextPropertiesBuilder {
            // do nothing
        }

        flowMessaging.initiateFlow(ALICE_X500_NAME, builder)
        verify(flowSessionFactory).createInitiatingFlowSession(any(), eq(ALICE_X500_NAME), eq(builder))
    }

    @Test
    fun `initiateFlow adds the new session id to the current flow stack item (containing no sessions) when the item represents is an initiating flow`() {
        val flowStackItem =
            FlowStackItem(FLOW_NAME, true, mutableListOf(), mutableKeyValuePairList(), mutableKeyValuePairList())
        whenever(flowStackService.peek()).thenReturn(flowStackItem)
        flowMessaging.initiateFlow(ALICE_X500_NAME)
        assertEquals(1, flowStackItem.sessions.size)
    }

    @Test
    fun `initiateFlow adds the new session id to the current flow stack item (containing existing sessions) when the item represents is an initiating flow`() {
        val flowStackItem = FlowStackItem(
            FLOW_NAME,
            true,
            mutableListOf(
                FlowStackItemSession("1", false),
                FlowStackItemSession("2", false),
                FlowStackItemSession("3", false)
            ),
            mutableKeyValuePairList(),
            mutableKeyValuePairList()
        )
        whenever(flowStackService.peek()).thenReturn(flowStackItem)
        flowMessaging.initiateFlow(ALICE_X500_NAME)
        assertEquals(4, flowStackItem.sessions.size)
    }

    @Test
    fun `initiateFlow throws an error when the current flow stack item represents a non-initiating flow`() {
        whenever(flowStackService.peek()).thenReturn(
            FlowStackItem(
                FLOW_NAME,
                false,
                emptyList(),
                mutableKeyValuePairList(),
                mutableKeyValuePairList()
            )
        )
        assertThrows<CordaRuntimeException> { flowMessaging.initiateFlow(ALICE_X500_NAME) }
    }

    @Test
    fun `initiateFlow throws an error if the flow stack is empty`() {
        whenever(flowStackService.peek()).thenReturn(null)
        assertThrows<CordaRuntimeException> { flowMessaging.initiateFlow(ALICE_X500_NAME) }
    }

    @Test
    fun `receiveAll with only non-version receiving sessions suspends the flow and returns the results of the suspension`() {
        whenever(mockFlowFiberService.flowFiber.suspend(any<FlowIORequest.Receive>())).thenReturn(
            mapOf(
                SESSION_ID_ONE to SERIALIZED_PAYLOAD_ONE,
                SESSION_ID_TWO to SERIALIZED_PAYLOAD_TWO
            )
        )
        val results = flowMessaging.receiveAll(Any::class.java, setOf(normalSessionOne, versionSendingSessionOne))
        assertThat(results).isEqualTo(listOf(PAYLOAD_ONE, PAYLOAD_TWO))
        verify(mockFlowFiberService.flowFiber).suspend(any<FlowIORequest.Receive>())
    }

    @Test
    fun `receiveAll with only version receiving sessions that have not received their initial payloads does not suspend the flow and returns the initial payloads`() {
        whenever(versionReceivingSessionOne.getInitialPayloadIfNotAlreadyReceived(Any::class.java)).thenReturn(PAYLOAD_ONE)
        whenever(versionReceivingSessionTwo.getInitialPayloadIfNotAlreadyReceived(Any::class.java)).thenReturn(PAYLOAD_TWO)
        val results = flowMessaging.receiveAll(Any::class.java, setOf(versionReceivingSessionOne, versionReceivingSessionTwo))
        assertThat(results).isEqualTo(listOf(PAYLOAD_ONE, PAYLOAD_TWO))
        verify(mockFlowFiberService.flowFiber, never()).suspend(any<FlowIORequest.Receive>())
    }

    @Test
    fun `receiveAll with only version receiving sessions that have received their initial payload suspends the flow and returns the results of the suspension`() {
        whenever(mockFlowFiberService.flowFiber.suspend(any<FlowIORequest.Receive>())).thenReturn(
            mapOf(
                SESSION_ID_FOUR to SERIALIZED_PAYLOAD_FOUR,
                SESSION_ID_FIVE to SERIALIZED_PAYLOAD_FIVE
            )
        )
        whenever(versionReceivingSessionOne.getInitialPayloadIfNotAlreadyReceived(Any::class.java)).thenReturn(null)
        whenever(versionReceivingSessionTwo.getInitialPayloadIfNotAlreadyReceived(Any::class.java)).thenReturn(null)
        val results = flowMessaging.receiveAll(Any::class.java, setOf(versionReceivingSessionOne, versionReceivingSessionTwo))
        assertThat(results).isEqualTo(listOf(PAYLOAD_FOUR, PAYLOAD_FIVE))
        verify(mockFlowFiberService.flowFiber).suspend(any<FlowIORequest.Receive>())
    }

    @Test
    fun `receiveAll with non-version receiving sessions and version receiving sessions that have not received their initial payloads suspends the flow and returns the results of the suspending the non-version receiving sessions plus the versioning session initial payloads`() {
        whenever(mockFlowFiberService.flowFiber.suspend(receiveSuspendCaptor.capture())).thenReturn(
            mapOf(
                SESSION_ID_ONE to SERIALIZED_PAYLOAD_ONE,
                SESSION_ID_TWO to SERIALIZED_PAYLOAD_TWO
            )
        )
        whenever(versionReceivingSessionOne.getInitialPayloadIfNotAlreadyReceived(Any::class.java)).thenReturn(PAYLOAD_FOUR)
        whenever(versionReceivingSessionTwo.getInitialPayloadIfNotAlreadyReceived(Any::class.java)).thenReturn(PAYLOAD_FIVE)
        val results = flowMessaging.receiveAll(
            Any::class.java,
            setOf(normalSessionOne, versionSendingSessionOne, versionReceivingSessionOne, versionReceivingSessionTwo)
        )
        assertThat(results).isEqualTo(listOf(PAYLOAD_ONE, PAYLOAD_TWO, PAYLOAD_FOUR, PAYLOAD_FIVE))
        verify(mockFlowFiberService.flowFiber).suspend(any<FlowIORequest.Receive>())
        assertThat(receiveSuspendCaptor.firstValue.sessions).containsExactly(SESSION_INFO_ONE, SESSION_INFO_TWO)
    }

    @Test
    fun `receiveAll with non-version receiving sessions and version receiving sessions that have received their initial payloads suspends the flow for all sessions and returns the results of the suspension`() {
        whenever(mockFlowFiberService.flowFiber.suspend(receiveSuspendCaptor.capture())).thenReturn(
            mapOf(
                SESSION_ID_ONE to SERIALIZED_PAYLOAD_ONE,
                SESSION_ID_TWO to SERIALIZED_PAYLOAD_TWO,
                SESSION_ID_FOUR to SERIALIZED_PAYLOAD_FOUR,
                SESSION_ID_FIVE to SERIALIZED_PAYLOAD_FIVE
            )
        )
        whenever(versionReceivingSessionOne.getInitialPayloadIfNotAlreadyReceived(Any::class.java)).thenReturn(null)
        whenever(versionReceivingSessionTwo.getInitialPayloadIfNotAlreadyReceived(Any::class.java)).thenReturn(null)
        val results = flowMessaging.receiveAll(
            Any::class.java,
            setOf(normalSessionOne, versionSendingSessionOne, versionReceivingSessionOne, versionReceivingSessionTwo)
        )
        assertThat(results).isEqualTo(listOf(PAYLOAD_ONE, PAYLOAD_TWO, PAYLOAD_FOUR, PAYLOAD_FIVE))
        verify(mockFlowFiberService.flowFiber).suspend(any<FlowIORequest.Receive>())
        assertThat(receiveSuspendCaptor.firstValue.sessions).containsExactly(
            SESSION_INFO_ONE,
            SESSION_INFO_TWO,
            SESSION_INFO_FOUR,
            SESSION_INFO_FIVE
        )
    }

    @Test
    fun `receiveAll with no sessions does not suspend the flow and returns an empty list`() {
        val results = flowMessaging.receiveAll(Any::class.java, emptySet())
        assertThat(results).isEqualTo(emptyList<Any>())
        verify(mockFlowFiberService.flowFiber, never()).suspend(any<FlowIORequest.Receive>())
    }

    @Test
    fun `receiveAll with only version sending sessions that have not sent their initial payloads suspends the flow with an extra send`() {
        whenever(versionSendingSessionOne.getVersioningPayloadToSend()).thenReturn(SERIALIZED_AGREED_VERSION_AND_PAYLOAD_ONE)
        whenever(versionSendingSessionTwo.getVersioningPayloadToSend()).thenReturn(SERIALIZED_AGREED_VERSION_AND_PAYLOAD_TWO)
        whenever(mockFlowFiberService.flowFiber.suspend(multiUseCaptor.capture())).thenReturn(
            Unit,
            mapOf(
                SESSION_ID_TWO to SERIALIZED_PAYLOAD_ONE,
                SESSION_ID_THREE to SERIALIZED_PAYLOAD_TWO
            )
        )
        val results = flowMessaging.receiveAll(Any::class.java, setOf(versionSendingSessionOne, versionSendingSessionTwo))
        assertThat(results).isEqualTo(listOf(PAYLOAD_ONE, PAYLOAD_TWO))
        verify(mockFlowFiberService.flowFiber).suspend(any<FlowIORequest.Send>())
        assertThat(multiUseCaptor.firstValue).isExactlyInstanceOf(FlowIORequest.Send::class.java)
        assertThat((multiUseCaptor.firstValue as FlowIORequest.Send).sessionPayloads).containsExactlyEntriesOf(
            mapOf(
                SESSION_INFO_TWO to SERIALIZED_AGREED_VERSION_AND_PAYLOAD_ONE,
                SESSION_INFO_THREE to SERIALIZED_AGREED_VERSION_AND_PAYLOAD_TWO
            )
        )
    }

    @Test
    fun `receiveAll with only version sending sessions that have sent their initial payloads does not suspend the flow with an extra send`() {
        whenever(versionSendingSessionOne.getVersioningPayloadToSend()).thenReturn(null)
        whenever(versionSendingSessionTwo.getVersioningPayloadToSend()).thenReturn(null)
        whenever(mockFlowFiberService.flowFiber.suspend(any<FlowIORequest.Receive>())).thenReturn(
            mapOf(
                SESSION_ID_TWO to SERIALIZED_PAYLOAD_ONE,
                SESSION_ID_THREE to SERIALIZED_PAYLOAD_TWO
            )
        )
        val results = flowMessaging.receiveAll(Any::class.java, setOf(versionSendingSessionOne, versionSendingSessionTwo))
        assertThat(results).isEqualTo(listOf(PAYLOAD_ONE, PAYLOAD_TWO))
        verify(mockFlowFiberService.flowFiber, never()).suspend(any<FlowIORequest.Send>())
    }

    @Test
    fun `receiveAll with non-version sending sessions and version sending sessions that have not sent their initial payloads suspends the flow with an extra send`() {
        whenever(versionSendingSessionOne.getVersioningPayloadToSend()).thenReturn(SERIALIZED_AGREED_VERSION_AND_PAYLOAD_ONE)
        whenever(versionSendingSessionTwo.getVersioningPayloadToSend()).thenReturn(SERIALIZED_AGREED_VERSION_AND_PAYLOAD_TWO)
        whenever(versionReceivingSessionOne.getInitialPayloadIfNotAlreadyReceived(Any::class.java)).thenReturn(null)
        whenever(versionReceivingSessionTwo.getInitialPayloadIfNotAlreadyReceived(Any::class.java)).thenReturn(PAYLOAD_FIVE)
        whenever(mockFlowFiberService.flowFiber.suspend(multiUseCaptor.capture())).thenReturn(
            Unit,
            mapOf(
                SESSION_ID_ONE to SERIALIZED_PAYLOAD_ONE,
                SESSION_ID_TWO to SERIALIZED_PAYLOAD_TWO,
                SESSION_ID_THREE to SERIALIZED_PAYLOAD_THREE,
                SESSION_ID_FOUR to SERIALIZED_PAYLOAD_FOUR,
            )
        )
        val results = flowMessaging.receiveAll(
            Any::class.java,
            setOf(
                normalSessionOne,
                versionSendingSessionOne,
                versionSendingSessionTwo,
                versionReceivingSessionOne,
                versionReceivingSessionTwo
            )
        )
        assertThat(results).isEqualTo(listOf(PAYLOAD_ONE, PAYLOAD_TWO, PAYLOAD_THREE, PAYLOAD_FOUR, PAYLOAD_FIVE))
        verify(mockFlowFiberService.flowFiber).suspend(any<FlowIORequest.Send>())
        assertThat(multiUseCaptor.firstValue).isExactlyInstanceOf(FlowIORequest.Send::class.java)
        assertThat((multiUseCaptor.firstValue as FlowIORequest.Send).sessionPayloads).containsExactlyEntriesOf(
            mapOf(
                SESSION_INFO_TWO to SERIALIZED_AGREED_VERSION_AND_PAYLOAD_ONE,
                SESSION_INFO_THREE to SERIALIZED_AGREED_VERSION_AND_PAYLOAD_TWO
            )
        )
    }

    @Test
    fun `receiveAll with no version sending sessions does not suspend the flow with an extra send`() {
        whenever(mockFlowFiberService.flowFiber.suspend(any<FlowIORequest.Receive>())).thenReturn(
            mapOf(
                SESSION_ID_ONE to SERIALIZED_PAYLOAD_ONE,
                SESSION_ID_FOUR to SERIALIZED_PAYLOAD_FOUR
            )
        )
        val results = flowMessaging.receiveAll(Any::class.java, setOf(normalSessionOne, versionReceivingSessionOne))
        assertThat(results).isEqualTo(listOf(PAYLOAD_ONE, PAYLOAD_FOUR))
        verify(mockFlowFiberService.flowFiber, never()).suspend(any<FlowIORequest.Send>())
    }

    @Test
    fun `receiveAllMap with only non-version receiving sessions suspends the flow and returns the results of the suspension`() {
        whenever(mockFlowFiberService.flowFiber.suspend(any<FlowIORequest.Receive>())).thenReturn(
            mapOf(
                SESSION_ID_ONE to SERIALIZED_PAYLOAD_ONE,
                SESSION_ID_TWO to SERIALIZED_PAYLOAD_TWO
            )
        )
        val results = flowMessaging.receiveAllMap(mapOf(normalSessionOne to Any::class.java, versionSendingSessionOne to Any::class.java))
        assertThat(results).isEqualTo(mapOf(normalSessionOne to PAYLOAD_ONE, versionSendingSessionOne to PAYLOAD_TWO))
        verify(mockFlowFiberService.flowFiber).suspend(any<FlowIORequest.Receive>())
    }

    @Test
    fun `receiveAllMap with only version receiving sessions that have not received their initial payloads does not suspend the flow and returns the initial payloads`() {
        whenever(versionReceivingSessionOne.getInitialPayloadIfNotAlreadyReceived(Any::class.java)).thenReturn(PAYLOAD_ONE)
        whenever(versionReceivingSessionTwo.getInitialPayloadIfNotAlreadyReceived(Any::class.java)).thenReturn(PAYLOAD_TWO)
        val results =
            flowMessaging.receiveAllMap(mapOf(versionReceivingSessionOne to Any::class.java, versionReceivingSessionTwo to Any::class.java))
        assertThat(results).isEqualTo(mapOf(versionReceivingSessionOne to PAYLOAD_ONE, versionReceivingSessionTwo to PAYLOAD_TWO))
        verify(mockFlowFiberService.flowFiber, never()).suspend(any<FlowIORequest.Receive>())
    }

    @Test
    fun `receiveAllMap with only version receiving sessions that have received their initial payload suspends the flow and returns the results of the suspension`() {
        whenever(mockFlowFiberService.flowFiber.suspend(any<FlowIORequest.Receive>())).thenReturn(
            mapOf(
                SESSION_ID_FOUR to SERIALIZED_PAYLOAD_FOUR,
                SESSION_ID_FIVE to SERIALIZED_PAYLOAD_FIVE
            )
        )
        whenever(versionReceivingSessionOne.getInitialPayloadIfNotAlreadyReceived(Any::class.java)).thenReturn(null)
        whenever(versionReceivingSessionTwo.getInitialPayloadIfNotAlreadyReceived(Any::class.java)).thenReturn(null)
        val results =
            flowMessaging.receiveAllMap(mapOf(versionReceivingSessionOne to Any::class.java, versionReceivingSessionTwo to Any::class.java))
        assertThat(results).isEqualTo(mapOf(versionReceivingSessionOne to PAYLOAD_FOUR, versionReceivingSessionTwo to PAYLOAD_FIVE))
        verify(mockFlowFiberService.flowFiber).suspend(any<FlowIORequest.Receive>())
    }

    @Test
    fun `receiveAllMap with non-version receiving sessions and version receiving sessions that have not received their initial payloads suspends the flow and returns the results of the suspending the non-version receiving sessions plus the versioning session initial payloads`() {
        whenever(mockFlowFiberService.flowFiber.suspend(receiveSuspendCaptor.capture())).thenReturn(
            mapOf(
                SESSION_ID_ONE to SERIALIZED_PAYLOAD_ONE,
                SESSION_ID_TWO to SERIALIZED_PAYLOAD_TWO
            )
        )
        whenever(versionReceivingSessionOne.getInitialPayloadIfNotAlreadyReceived(Any::class.java)).thenReturn(PAYLOAD_FOUR)
        whenever(versionReceivingSessionTwo.getInitialPayloadIfNotAlreadyReceived(Any::class.java)).thenReturn(PAYLOAD_FIVE)
        val results = flowMessaging.receiveAllMap(
            mapOf(
                normalSessionOne to Any::class.java,
                versionSendingSessionOne to Any::class.java,
                versionReceivingSessionOne to Any::class.java,
                versionReceivingSessionTwo to Any::class.java
            )
        )
        assertThat(results).isEqualTo(
            mapOf(
                normalSessionOne to PAYLOAD_ONE,
                versionSendingSessionOne to PAYLOAD_TWO,
                versionReceivingSessionOne to PAYLOAD_FOUR,
                versionReceivingSessionTwo to PAYLOAD_FIVE
            )
        )
        verify(mockFlowFiberService.flowFiber).suspend(any<FlowIORequest.Receive>())
        assertThat(receiveSuspendCaptor.firstValue.sessions).containsExactly(SESSION_INFO_ONE, SESSION_INFO_TWO)
    }

    @Test
    fun `receiveAllMap with non-version receiving sessions and version receiving sessions that have received their initial payloads suspends the flow for all sessions and returns the results of the suspension`() {
        whenever(mockFlowFiberService.flowFiber.suspend(receiveSuspendCaptor.capture())).thenReturn(
            mapOf(
                SESSION_ID_ONE to SERIALIZED_PAYLOAD_ONE,
                SESSION_ID_TWO to SERIALIZED_PAYLOAD_TWO,
                SESSION_ID_FOUR to SERIALIZED_PAYLOAD_FOUR,
                SESSION_ID_FIVE to SERIALIZED_PAYLOAD_FIVE
            )
        )
        whenever(versionReceivingSessionOne.getInitialPayloadIfNotAlreadyReceived(Any::class.java)).thenReturn(null)
        whenever(versionReceivingSessionTwo.getInitialPayloadIfNotAlreadyReceived(Any::class.java)).thenReturn(null)
        val results = flowMessaging.receiveAllMap(
            mapOf(
                normalSessionOne to Any::class.java,
                versionSendingSessionOne to Any::class.java,
                versionReceivingSessionOne to Any::class.java,
                versionReceivingSessionTwo to Any::class.java
            )
        )
        assertThat(results).isEqualTo(
            mapOf(
                normalSessionOne to PAYLOAD_ONE,
                versionSendingSessionOne to PAYLOAD_TWO,
                versionReceivingSessionOne to PAYLOAD_FOUR,
                versionReceivingSessionTwo to PAYLOAD_FIVE
            )
        )
        verify(mockFlowFiberService.flowFiber).suspend(any<FlowIORequest.Receive>())
        assertThat(receiveSuspendCaptor.firstValue.sessions).containsExactly(
            SESSION_INFO_ONE,
            SESSION_INFO_TWO,
            SESSION_INFO_FOUR,
            SESSION_INFO_FIVE
        )
    }

    @Test
    fun `receiveAllMap with no sessions does not suspend the flow and returns an empty list`() {
        val results = flowMessaging.receiveAllMap(emptyMap())
        assertThat(results).isEqualTo(emptyMap<FlowSession, Any>())
        verify(mockFlowFiberService.flowFiber, never()).suspend(any<FlowIORequest.Receive>())
    }

    @Test
    fun `receiveAllMap with only version sending sessions that have not sent their initial payloads suspends the flow with an extra send`() {
        whenever(versionSendingSessionOne.getVersioningPayloadToSend()).thenReturn(SERIALIZED_AGREED_VERSION_AND_PAYLOAD_ONE)
        whenever(versionSendingSessionTwo.getVersioningPayloadToSend()).thenReturn(SERIALIZED_AGREED_VERSION_AND_PAYLOAD_TWO)
        whenever(mockFlowFiberService.flowFiber.suspend(multiUseCaptor.capture())).thenReturn(
            Unit,
            mapOf(
                SESSION_ID_TWO to SERIALIZED_PAYLOAD_ONE,
                SESSION_ID_THREE to SERIALIZED_PAYLOAD_TWO
            )
        )
        val results = flowMessaging.receiveAllMap(
            mapOf(
                versionSendingSessionOne to Any::class.java,
                versionSendingSessionTwo to Any::class.java
            )
        )
        assertThat(results).isEqualTo(mapOf(versionSendingSessionOne to PAYLOAD_ONE, versionSendingSessionTwo to PAYLOAD_TWO))
        verify(mockFlowFiberService.flowFiber).suspend(any<FlowIORequest.Send>())
        assertThat(multiUseCaptor.firstValue).isExactlyInstanceOf(FlowIORequest.Send::class.java)
        assertThat((multiUseCaptor.firstValue as FlowIORequest.Send).sessionPayloads).containsExactlyEntriesOf(
            mapOf(
                SESSION_INFO_TWO to SERIALIZED_AGREED_VERSION_AND_PAYLOAD_ONE,
                SESSION_INFO_THREE to SERIALIZED_AGREED_VERSION_AND_PAYLOAD_TWO
            )
        )
    }

    @Test
    fun `receiveAllMap with only version sending sessions that have sent their initial payloads does not suspend the flow with an extra send`() {
        whenever(versionSendingSessionOne.getVersioningPayloadToSend()).thenReturn(null)
        whenever(versionSendingSessionTwo.getVersioningPayloadToSend()).thenReturn(null)
        whenever(mockFlowFiberService.flowFiber.suspend(any<FlowIORequest.Receive>())).thenReturn(
            mapOf(
                SESSION_ID_TWO to SERIALIZED_PAYLOAD_ONE,
                SESSION_ID_THREE to SERIALIZED_PAYLOAD_TWO
            )
        )
        val results = flowMessaging.receiveAllMap(
            mapOf(
                versionSendingSessionOne to Any::class.java,
                versionSendingSessionTwo to Any::class.java
            )
        )
        assertThat(results).isEqualTo(mapOf(versionSendingSessionOne to PAYLOAD_ONE, versionSendingSessionTwo to PAYLOAD_TWO))
        verify(mockFlowFiberService.flowFiber, never()).suspend(any<FlowIORequest.Send>())
    }

    @Test
    fun `receiveAllMap with non-version sending sessions and version sending sessions that have not sent their initial payloads suspends the flow with an extra send`() {
        whenever(versionSendingSessionOne.getVersioningPayloadToSend()).thenReturn(SERIALIZED_AGREED_VERSION_AND_PAYLOAD_ONE)
        whenever(versionSendingSessionTwo.getVersioningPayloadToSend()).thenReturn(SERIALIZED_AGREED_VERSION_AND_PAYLOAD_TWO)
        whenever(versionReceivingSessionOne.getInitialPayloadIfNotAlreadyReceived(Any::class.java)).thenReturn(null)
        whenever(versionReceivingSessionTwo.getInitialPayloadIfNotAlreadyReceived(Any::class.java)).thenReturn(PAYLOAD_FIVE)
        whenever(mockFlowFiberService.flowFiber.suspend(multiUseCaptor.capture())).thenReturn(
            Unit,
            mapOf(
                SESSION_ID_ONE to SERIALIZED_PAYLOAD_ONE,
                SESSION_ID_TWO to SERIALIZED_PAYLOAD_TWO,
                SESSION_ID_THREE to SERIALIZED_PAYLOAD_THREE,
                SESSION_ID_FOUR to SERIALIZED_PAYLOAD_FOUR,
            )
        )
        val results = flowMessaging.receiveAllMap(
            mapOf(
                normalSessionOne to Any::class.java,
                versionSendingSessionOne to Any::class.java,
                versionSendingSessionTwo to Any::class.java,
                versionReceivingSessionOne to Any::class.java,
                versionReceivingSessionTwo to Any::class.java
            )
        )
        assertThat(results).isEqualTo(
            mapOf(
                normalSessionOne to PAYLOAD_ONE,
                versionSendingSessionOne to PAYLOAD_TWO,
                versionSendingSessionTwo to PAYLOAD_THREE,
                versionReceivingSessionOne to PAYLOAD_FOUR,
                versionReceivingSessionTwo to PAYLOAD_FIVE,
            )
        )
        verify(mockFlowFiberService.flowFiber).suspend(any<FlowIORequest.Send>())
        assertThat(multiUseCaptor.firstValue).isExactlyInstanceOf(FlowIORequest.Send::class.java)
        assertThat((multiUseCaptor.firstValue as FlowIORequest.Send).sessionPayloads).containsExactlyEntriesOf(
            mapOf(
                SESSION_INFO_TWO to SERIALIZED_AGREED_VERSION_AND_PAYLOAD_ONE,
                SESSION_INFO_THREE to SERIALIZED_AGREED_VERSION_AND_PAYLOAD_TWO
            )
        )
    }

    @Test
    fun `receiveAllMap with no version sending sessions does not suspend the flow with an extra send`() {
        whenever(mockFlowFiberService.flowFiber.suspend(any<FlowIORequest.Receive>())).thenReturn(
            mapOf(
                SESSION_ID_ONE to SERIALIZED_PAYLOAD_ONE,
                SESSION_ID_FOUR to SERIALIZED_PAYLOAD_FOUR
            )
        )
        val results = flowMessaging.receiveAllMap(
            mapOf(
                normalSessionOne to Any::class.java,
                versionReceivingSessionOne to Any::class.java
            )
        )
        assertThat(results).isEqualTo(mapOf(normalSessionOne to PAYLOAD_ONE, versionReceivingSessionOne to PAYLOAD_FOUR))
        verify(mockFlowFiberService.flowFiber, never()).suspend(any<FlowIORequest.Send>())
    }

    @Test
    fun `sendAll with only non-version sending sessions suspends the flow to send the input payload`() {
        whenever(mockFlowFiberService.flowFiber.suspend(sendSuspendCaptor.capture())).thenReturn(Unit)
        flowMessaging.sendAll(PAYLOAD_ONE, setOf(normalSessionOne, versionReceivingSessionOne))
        verify(mockFlowFiberService.flowFiber).suspend(any<FlowIORequest.Send>())
        assertThat(sendSuspendCaptor.firstValue.sessionPayloads).containsExactlyEntriesOf(
            mapOf(
                SESSION_INFO_ONE to SERIALIZED_PAYLOAD_ONE,
                SESSION_INFO_FOUR to SERIALIZED_PAYLOAD_ONE
            )
        )
    }

    @Test
    fun `sendAll with only version sending sessions that have not sent their initial payloads suspends the flow to send the input payloads and additional versioning information`() {
        whenever(mockFlowFiberService.flowFiber.suspend(sendSuspendCaptor.capture())).thenReturn(Unit)
        whenever(versionSendingSessionOne.getPayloadToSend(SERIALIZED_PAYLOAD_ONE)).thenReturn(SERIALIZED_AGREED_VERSION_AND_PAYLOAD_ONE)
        whenever(versionSendingSessionTwo.getPayloadToSend(SERIALIZED_PAYLOAD_ONE)).thenReturn(SERIALIZED_AGREED_VERSION_AND_PAYLOAD_TWO)
        flowMessaging.sendAll(PAYLOAD_ONE, setOf(versionSendingSessionOne, versionSendingSessionTwo))
        verify(mockFlowFiberService.flowFiber).suspend(any<FlowIORequest.Send>())
        assertThat(sendSuspendCaptor.firstValue.sessionPayloads).containsExactlyEntriesOf(
            mapOf(
                SESSION_INFO_TWO to SERIALIZED_AGREED_VERSION_AND_PAYLOAD_ONE,
                SESSION_INFO_THREE to SERIALIZED_AGREED_VERSION_AND_PAYLOAD_TWO
            )
        )
    }

    @Test
    fun `sendAll with only version sending sessions that have sent their initial payloads suspends the flow to send the input payload`() {
        whenever(mockFlowFiberService.flowFiber.suspend(sendSuspendCaptor.capture())).thenReturn(Unit)
        whenever(versionSendingSessionOne.getPayloadToSend(SERIALIZED_PAYLOAD_ONE)).thenReturn(SERIALIZED_PAYLOAD_ONE)
        whenever(versionSendingSessionTwo.getPayloadToSend(SERIALIZED_PAYLOAD_ONE)).thenReturn(SERIALIZED_PAYLOAD_ONE)
        flowMessaging.sendAll(PAYLOAD_ONE, setOf(versionSendingSessionOne, versionSendingSessionTwo))
        verify(mockFlowFiberService.flowFiber).suspend(any<FlowIORequest.Send>())
        assertThat(sendSuspendCaptor.firstValue.sessionPayloads).containsExactlyEntriesOf(
            mapOf(
                SESSION_INFO_TWO to SERIALIZED_PAYLOAD_ONE,
                SESSION_INFO_THREE to SERIALIZED_PAYLOAD_ONE
            )
        )
    }

    @Test
    fun `sendAll with non-version sending sessions and version sending sessions that have not sent their initial payloads suspends the flow to send the input payload for the non-version sending sessions and additional versioning information for the version sending sessions`() {
        whenever(mockFlowFiberService.flowFiber.suspend(sendSuspendCaptor.capture())).thenReturn(Unit)
        whenever(versionSendingSessionOne.getPayloadToSend(SERIALIZED_PAYLOAD_ONE)).thenReturn(SERIALIZED_AGREED_VERSION_AND_PAYLOAD_ONE)
        whenever(versionSendingSessionTwo.getPayloadToSend(SERIALIZED_PAYLOAD_ONE)).thenReturn(SERIALIZED_AGREED_VERSION_AND_PAYLOAD_TWO)
        flowMessaging.sendAll(PAYLOAD_ONE, setOf(normalSessionOne, versionReceivingSessionOne, versionSendingSessionOne, versionSendingSessionTwo))
        verify(mockFlowFiberService.flowFiber).suspend(any<FlowIORequest.Send>())
        assertThat(sendSuspendCaptor.firstValue.sessionPayloads).containsExactlyEntriesOf(
            mapOf(
                SESSION_INFO_ONE to SERIALIZED_PAYLOAD_ONE,
                SESSION_INFO_FOUR to SERIALIZED_PAYLOAD_ONE,
                SESSION_INFO_TWO to SERIALIZED_AGREED_VERSION_AND_PAYLOAD_ONE,
                SESSION_INFO_THREE to SERIALIZED_AGREED_VERSION_AND_PAYLOAD_TWO
            )
        )
    }

    @Test
    fun `sendAll with non-version sending sessions and version sending sessions that have sent their initial payloads suspends the flow to send the input payload for all sessions`() {
        whenever(mockFlowFiberService.flowFiber.suspend(sendSuspendCaptor.capture())).thenReturn(Unit)
        whenever(versionSendingSessionOne.getPayloadToSend(SERIALIZED_PAYLOAD_ONE)).thenReturn(SERIALIZED_PAYLOAD_ONE)
        whenever(versionSendingSessionTwo.getPayloadToSend(SERIALIZED_PAYLOAD_ONE)).thenReturn(SERIALIZED_PAYLOAD_ONE)
        flowMessaging.sendAll(PAYLOAD_ONE, setOf(normalSessionOne, versionReceivingSessionOne, versionSendingSessionOne, versionSendingSessionTwo))
        verify(mockFlowFiberService.flowFiber).suspend(any<FlowIORequest.Send>())
        assertThat(sendSuspendCaptor.firstValue.sessionPayloads).containsExactlyEntriesOf(
            mapOf(
                SESSION_INFO_ONE to SERIALIZED_PAYLOAD_ONE,
                SESSION_INFO_FOUR to SERIALIZED_PAYLOAD_ONE,
                SESSION_INFO_TWO to SERIALIZED_PAYLOAD_ONE,
                SESSION_INFO_THREE to SERIALIZED_PAYLOAD_ONE
            )
        )
    }

    @Test
    fun `sendAll with no sessions does not suspend the flow`() {
        flowMessaging.sendAll(PAYLOAD_ONE, setOf())
        verify(mockFlowFiberService.flowFiber, never()).suspend(any<FlowIORequest.Send>())
    }

    @Test
    fun `sendAllMap with only non-version sending sessions suspends the flow to send the input payloads`() {
        whenever(mockFlowFiberService.flowFiber.suspend(sendSuspendCaptor.capture())).thenReturn(Unit)
        flowMessaging.sendAllMap(mapOf(normalSessionOne to PAYLOAD_ONE, versionReceivingSessionOne to PAYLOAD_TWO))
        verify(mockFlowFiberService.flowFiber).suspend(any<FlowIORequest.Send>())
        assertThat(sendSuspendCaptor.firstValue.sessionPayloads).containsExactlyEntriesOf(
            mapOf(
                SESSION_INFO_ONE to SERIALIZED_PAYLOAD_ONE,
                SESSION_INFO_FOUR to SERIALIZED_PAYLOAD_TWO
            )
        )
    }

    @Test
    fun `sendAllMap with only version sending sessions that have not sent their initial payloads suspends the flow to send the input payloads and additional versioning information`() {
        whenever(mockFlowFiberService.flowFiber.suspend(sendSuspendCaptor.capture())).thenReturn(Unit)
        whenever(versionSendingSessionOne.getPayloadToSend(SERIALIZED_PAYLOAD_ONE)).thenReturn(SERIALIZED_AGREED_VERSION_AND_PAYLOAD_ONE)
        whenever(versionSendingSessionTwo.getPayloadToSend(SERIALIZED_PAYLOAD_TWO)).thenReturn(SERIALIZED_AGREED_VERSION_AND_PAYLOAD_TWO)
        flowMessaging.sendAllMap(mapOf(versionSendingSessionOne to PAYLOAD_ONE, versionSendingSessionTwo to PAYLOAD_TWO))
        verify(mockFlowFiberService.flowFiber).suspend(any<FlowIORequest.Send>())
        assertThat(sendSuspendCaptor.firstValue.sessionPayloads).containsExactlyEntriesOf(
            mapOf(
                SESSION_INFO_TWO to SERIALIZED_AGREED_VERSION_AND_PAYLOAD_ONE,
                SESSION_INFO_THREE to SERIALIZED_AGREED_VERSION_AND_PAYLOAD_TWO
            )
        )
    }

    @Test
    fun `sendAllMap with only version sending sessions that have sent their initial payloads suspends the flow to send the input payloads`() {
        whenever(mockFlowFiberService.flowFiber.suspend(sendSuspendCaptor.capture())).thenReturn(Unit)
        whenever(versionSendingSessionOne.getPayloadToSend(SERIALIZED_PAYLOAD_ONE)).thenReturn(SERIALIZED_PAYLOAD_ONE)
        whenever(versionSendingSessionTwo.getPayloadToSend(SERIALIZED_PAYLOAD_TWO)).thenReturn(SERIALIZED_PAYLOAD_TWO)
        flowMessaging.sendAllMap(mapOf(versionSendingSessionOne to PAYLOAD_ONE, versionSendingSessionTwo to PAYLOAD_TWO))
        verify(mockFlowFiberService.flowFiber).suspend(any<FlowIORequest.Send>())
        assertThat(sendSuspendCaptor.firstValue.sessionPayloads).containsExactlyEntriesOf(
            mapOf(
                SESSION_INFO_TWO to SERIALIZED_PAYLOAD_ONE,
                SESSION_INFO_THREE to SERIALIZED_PAYLOAD_TWO
            )
        )
    }

    @Test
    fun `sendAllMap with non-version sending sessions and version sending sessions that have not sent their initial payloads suspends the flow to send the inputs payload for the non-version sending sessions and additional versioning information for the version sending sessions`() {
        whenever(mockFlowFiberService.flowFiber.suspend(sendSuspendCaptor.capture())).thenReturn(Unit)
        whenever(versionSendingSessionOne.getPayloadToSend(SERIALIZED_PAYLOAD_TWO)).thenReturn(SERIALIZED_AGREED_VERSION_AND_PAYLOAD_ONE)
        whenever(versionSendingSessionTwo.getPayloadToSend(SERIALIZED_PAYLOAD_THREE)).thenReturn(SERIALIZED_AGREED_VERSION_AND_PAYLOAD_TWO)
        flowMessaging.sendAllMap(
            mapOf(
                normalSessionOne to PAYLOAD_ONE,
                versionReceivingSessionOne to PAYLOAD_TWO,
                versionSendingSessionOne to PAYLOAD_TWO,
                versionSendingSessionTwo to PAYLOAD_THREE
            )
        )
        verify(mockFlowFiberService.flowFiber).suspend(any<FlowIORequest.Send>())
        assertThat(sendSuspendCaptor.firstValue.sessionPayloads).containsExactlyEntriesOf(
            mapOf(
                SESSION_INFO_ONE to SERIALIZED_PAYLOAD_ONE,
                SESSION_INFO_FOUR to SERIALIZED_PAYLOAD_TWO,
                SESSION_INFO_TWO to SERIALIZED_AGREED_VERSION_AND_PAYLOAD_ONE,
                SESSION_INFO_THREE to SERIALIZED_AGREED_VERSION_AND_PAYLOAD_TWO
            )
        )
    }

    @Test
    fun `sendAllMap with non-version sending sessions and version sending sessions that have sent their initial payloads suspends the flow to send the input payloads for all sessions`() {
        whenever(mockFlowFiberService.flowFiber.suspend(sendSuspendCaptor.capture())).thenReturn(Unit)
        whenever(versionSendingSessionOne.getPayloadToSend(SERIALIZED_PAYLOAD_TWO)).thenReturn(SERIALIZED_PAYLOAD_TWO)
        whenever(versionSendingSessionTwo.getPayloadToSend(SERIALIZED_PAYLOAD_THREE)).thenReturn(SERIALIZED_PAYLOAD_THREE)
        flowMessaging.sendAllMap(
            mapOf(
                normalSessionOne to PAYLOAD_ONE,
                versionReceivingSessionOne to PAYLOAD_TWO,
                versionSendingSessionOne to PAYLOAD_TWO,
                versionSendingSessionTwo to PAYLOAD_THREE
            )
        )
        verify(mockFlowFiberService.flowFiber).suspend(any<FlowIORequest.Send>())
        assertThat(sendSuspendCaptor.firstValue.sessionPayloads).containsExactlyEntriesOf(
            mapOf(
                SESSION_INFO_ONE to SERIALIZED_PAYLOAD_ONE,
                SESSION_INFO_FOUR to SERIALIZED_PAYLOAD_TWO,
                SESSION_INFO_TWO to SERIALIZED_PAYLOAD_TWO,
                SESSION_INFO_THREE to SERIALIZED_PAYLOAD_THREE
            )
        )
    }

    @Test
    fun `sendAllMap with no sessions does not suspend the flow`() {
        flowMessaging.sendAllMap(emptyMap())
        verify(mockFlowFiberService.flowFiber, never()).suspend(any<FlowIORequest.Send>())
    }
}