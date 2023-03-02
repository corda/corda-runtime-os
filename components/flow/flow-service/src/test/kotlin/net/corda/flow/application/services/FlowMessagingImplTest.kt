package net.corda.flow.application.services

import net.corda.data.flow.state.checkpoint.FlowStackItem
import net.corda.data.flow.state.checkpoint.FlowStackItemSession
import net.corda.flow.ALICE_X500_NAME
import net.corda.flow.BOB_X500_NAME
import net.corda.flow.application.serialization.SerializationServiceInternal
import net.corda.flow.application.sessions.factory.FlowSessionFactory
import net.corda.flow.utils.mutableKeyValuePairList
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowContextPropertiesBuilder
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.membership.MemberInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@Suppress("MaxLineLength")
class FlowMessagingImplTest {

    private companion object {
        const val FLOW_NAME = "flow name"
    }

    private val mockFlowFiberService = MockFlowFiberService()
    private val flowStackService = mockFlowFiberService.flowStack
    private val flowSession = mock<FlowSession>()
    private val serializationService = mock<SerializationServiceInternal>()
    private val memberLookup = mock<MemberLookup>()

    private val flowSessionFactory = mock<FlowSessionFactory>().apply {
        whenever(createInitiatingFlowSession(any(), eq(ALICE_X500_NAME), any())).thenReturn(flowSession)
    }

    private val flowMessaging = FlowMessagingImpl(
        mockFlowFiberService, flowSessionFactory, serializationService, memberLookup
    )

    @BeforeEach
    fun beforeEach() {
        whenever(memberLookup.lookup(ALICE_X500_NAME)).thenReturn(mock<MemberInfo>())
        whenever(memberLookup.lookup(BOB_X500_NAME)).thenReturn(null)
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
    fun `initiateFlow throws an error if trying to establish a flow with non-existent member`() {
        assertThrows<CordaRuntimeException> { flowMessaging.initiateFlow(BOB_X500_NAME) }
    }
}