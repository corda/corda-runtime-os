package net.corda.flow.application.versioning.impl

import net.corda.flow.application.services.VersioningService
import net.corda.flow.application.sessions.FlowSessionInternal
import net.corda.flow.application.versioning.VersionedReceiveFlowFactory
import net.corda.flow.application.versioning.impl.sessions.VersionReceivingFlowSession
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MemberInfo
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@Suppress("MaxLineLength")
class ReceiveVersioningFlowTest {

    private companion object {
        val MEMBER_ONE = MemberX500Name("ONE", "LDN", "GB")

        val PREVIOUSLY_AGREED_VERSION = 1 to linkedMapOf<String, Any>()
        const val COMMON_VERSION = 100

        val MY_FLOW = MyFlow()
    }

    private val flowEngine = mock<FlowEngine>()
    private val memberLookup = mock<MemberLookup>()
    private val serializationService = mock<SerializationService>()
    private val versioningService = mock<VersioningService>()
    private val versionedFlowFactory = mock<VersionedReceiveFlowFactory<String>>()

    private val versionedFlowFactorySessionCaptor = argumentCaptor<FlowSession>()

    private val memberInfo = mock<MemberInfo>()

    private val session = mock<FlowSessionInternal>()

    private val localMember = mock<MemberInfo>()

    private val flow = ReceiveVersioningFlow(versionedFlowFactory, session)

    @BeforeEach
    fun beforeEach() {
        whenever(session.counterparty).thenReturn(MEMBER_ONE)

        whenever(memberLookup.lookup(MEMBER_ONE)).thenReturn(memberInfo)
        whenever(memberLookup.myInfo()).thenReturn(localMember)

        whenever(memberInfo.name).thenReturn(MEMBER_ONE)

        whenever(localMember.platformVersion).thenReturn(180)

        whenever(versionedFlowFactory.versionedInstanceOf).thenReturn(MyFlow::class.java)
        whenever(versionedFlowFactory.create(any(), eq(session))).thenReturn(MY_FLOW)

        whenever(flowEngine.subFlow(MY_FLOW)).thenReturn(MyFlow.RESULT)

        whenever(session.receive(AgreedVersionAndPayload::class.java)).thenReturn(
            AgreedVersionAndPayload(
                AgreedVersion(
                    COMMON_VERSION,
                    linkedMapOf()
                ),
                serializedPayload = byteArrayOf(1, 1, 1, 1)
            )
        )

        flow.flowEngine = flowEngine
        flow.memberLookup = memberLookup
        flow.serializationService = serializationService
        flow.versioningService = versioningService
    }

    @Test
    fun `when there is a previously agreed platform version that version and the original session are passed into the versioned flow factory`() {
        whenever(versioningService.peekCurrentVersioning()).thenReturn(PREVIOUSLY_AGREED_VERSION)
        whenever(versionedFlowFactory.create(any(), versionedFlowFactorySessionCaptor.capture())).thenReturn(MY_FLOW)
        flow.call()
        verify(versionedFlowFactory).create(PREVIOUSLY_AGREED_VERSION.first, session)
        assertThat(versionedFlowFactorySessionCaptor.firstValue).isNotInstanceOf(VersionReceivingFlowSession::class.java)
    }

    @Test
    fun `when there is a previously agreed platform version executes the subFlow returned from the versioned flow factory and returns its result`() {
        whenever(versioningService.peekCurrentVersioning()).thenReturn(PREVIOUSLY_AGREED_VERSION)
        Assertions.assertThat(flow.call()).isEqualTo(MyFlow.RESULT)
        verify(flowEngine).subFlow(MY_FLOW)
    }

    @Test
    fun `when there is no previously agreed platform version the common version is received from the peer session`() {
        whenever(versioningService.peekCurrentVersioning()).thenReturn(null)
        flow.call()
        verify(session).receive(AgreedVersionAndPayload::class.java)
    }

    @Test
    fun `when there is no previously agreed platform version and the common version received from the peer session is less than the local platform version no exception is thrown`() {
        whenever(versioningService.peekCurrentVersioning()).thenReturn(null)
        whenever(localMember.platformVersion).thenReturn(COMMON_VERSION + 100)
        assertDoesNotThrow { flow.call() }
    }

    @Test
    fun `when there is no previously agreed platform version and the common version received from the peer session is equal to the local platform version no exception is thrown`() {
        whenever(versioningService.peekCurrentVersioning()).thenReturn(null)
        whenever(localMember.platformVersion).thenReturn(COMMON_VERSION)
        assertDoesNotThrow { flow.call() }
    }

    @Test
    fun `when there is no previously agreed platform version and the common version received from the peer session is greater than the local platform version an exception is thrown`() {
        whenever(versioningService.peekCurrentVersioning()).thenReturn(null)
        whenever(localMember.platformVersion).thenReturn(COMMON_VERSION - 100)
        assertThatThrownBy { flow.call() }
            .isExactlyInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("but this version is greater than the local platform version")
    }

    @Test
    fun `when there is no previously agreed platform version the common version is added to the flow context`() {
        whenever(versioningService.peekCurrentVersioning()).thenReturn(null)
        flow.call()
        verify(versioningService).setCurrentVersioning(COMMON_VERSION)
    }

    @Test
    fun `when there is no previously agreed platform version and an initial payload is received the peer session is turned into a version receiving session`() {
        whenever(versioningService.peekCurrentVersioning()).thenReturn(null)
        whenever(session.receive(AgreedVersionAndPayload::class.java)).thenReturn(
            AgreedVersionAndPayload(
                AgreedVersion(
                    COMMON_VERSION,
                    linkedMapOf()
                ),
                serializedPayload = byteArrayOf(1, 1, 1, 1)
            )
        )
        whenever(versionedFlowFactory.create(any(), versionedFlowFactorySessionCaptor.capture())).thenReturn(MY_FLOW)
        flow.call()
        assertThat(versionedFlowFactorySessionCaptor.firstValue).isInstanceOf(VersionReceivingFlowSession::class.java)
    }

    @Test
    fun `when there is no previously agreed platform version and no initial payload is received the peer session is not turned into a version receiving session`() {
        whenever(versioningService.peekCurrentVersioning()).thenReturn(null)
        whenever(session.receive(AgreedVersionAndPayload::class.java)).thenReturn(
            AgreedVersionAndPayload(
                AgreedVersion(
                    COMMON_VERSION,
                    linkedMapOf()
                ),
                serializedPayload = null
            )
        )
        whenever(versionedFlowFactory.create(any(), versionedFlowFactorySessionCaptor.capture())).thenReturn(MY_FLOW)
        flow.call()
        assertThat(versionedFlowFactorySessionCaptor.firstValue).isNotInstanceOf(VersionReceivingFlowSession::class.java)
    }

    @Test
    fun `when there is no previously agreed platform version executes the subFlow returned from the versioned flow factory and returns its result`() {
        whenever(versioningService.peekCurrentVersioning()).thenReturn(null)
        whenever(versionedFlowFactory.create(any(), any<VersionReceivingFlowSession>())).thenReturn(MY_FLOW)
        assertThat(flow.call()).isEqualTo(MyFlow.RESULT)
    }

    @Test
    fun `IllegalArgumentExceptions thrown from the versioned flow factory are caught and rethrown`() {
        whenever(versionedFlowFactory.create(any(), any())).thenThrow(IllegalArgumentException("break"))
        assertThatThrownBy { flow.call() }.isExactlyInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("There is no version $COMMON_VERSION")

    }

    @Test
    fun `other exception types thrown from the versioned flow factory are not caught`() {
        whenever(versionedFlowFactory.create(any(), any())).thenThrow(IllegalStateException("break"))
        assertThatThrownBy { flow.call() }.isExactlyInstanceOf(IllegalStateException::class.java).hasMessage("break")
    }

    private class MyFlow : SubFlow<String> {

        companion object {
            const val RESULT = "result"
        }

        override fun call(): String {
            return RESULT
        }
    }
}