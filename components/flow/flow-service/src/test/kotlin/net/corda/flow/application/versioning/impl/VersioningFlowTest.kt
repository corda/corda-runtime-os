package net.corda.flow.application.versioning.impl

import net.corda.flow.application.services.VersioningService
import net.corda.flow.application.sessions.FlowSessionInternal
import net.corda.flow.application.versioning.VersionedSendFlowFactory
import net.corda.flow.application.versioning.impl.sessions.VersionSendingFlowSession
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MemberInfo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@Suppress("MaxLineLength")
class VersioningFlowTest {

    private companion object {
        val MEMBER_ONE = MemberX500Name("ONE", "LDN", "GB")
        val MEMBER_TWO = MemberX500Name("TWO", "LDN", "GB")
        val MEMBER_THREE = MemberX500Name("THREE", "LDN", "GB")

        val PREVIOUSLY_AGREED_VERSION = 1 to linkedMapOf<String, Any>()
        const val LOWEST_COMMON_VERSION = 100

        val MY_FLOW = MyFlow()
    }

    private val flowEngine = mock<FlowEngine>()
    private val memberLookup = mock<MemberLookup>()
    private val serializationService = mock<SerializationService>()
    private val versioningService = mock<VersioningService>()
    private val versionedFlowFactory = mock<VersionedSendFlowFactory<String>>()

    private val versionedFlowFactorySessionCaptor = argumentCaptor<List<FlowSession>>()

    private val sessionOne = mock<FlowSessionInternal>()
    private val sessionTwo = mock<FlowSessionInternal>()
    private val sessionThree = mock<FlowSessionInternal>()

    private val localMember = mock<MemberInfo>()
    private val memberInfoOne = mock<MemberInfo>()
    private val memberInfoTwo = mock<MemberInfo>()
    private val memberInfoThree = mock<MemberInfo>()

    private val flow = VersioningFlow(versionedFlowFactory, listOf(sessionOne, sessionTwo, sessionThree))

    @BeforeEach
    fun beforeEach() {
        whenever(sessionOne.counterparty).thenReturn(MEMBER_ONE)
        whenever(sessionTwo.counterparty).thenReturn(MEMBER_TWO)
        whenever(sessionThree.counterparty).thenReturn(MEMBER_THREE)

        whenever(memberLookup.lookup(MEMBER_ONE)).thenReturn(memberInfoOne)
        whenever(memberLookup.lookup(MEMBER_TWO)).thenReturn(memberInfoTwo)
        whenever(memberLookup.lookup(MEMBER_THREE)).thenReturn(memberInfoThree)
        whenever(memberLookup.myInfo()).thenReturn(localMember)

        whenever(memberInfoOne.name).thenReturn(MEMBER_ONE)
        whenever(memberInfoTwo.name).thenReturn(MEMBER_TWO)
        whenever(memberInfoThree.name).thenReturn(MEMBER_THREE)

        whenever(memberInfoOne.platformVersion).thenReturn(200)
        whenever(memberInfoTwo.platformVersion).thenReturn(LOWEST_COMMON_VERSION)
        whenever(memberInfoThree.platformVersion).thenReturn(150)
        whenever(localMember.platformVersion).thenReturn(180)

        whenever(versionedFlowFactory.versionedInstanceOf).thenReturn(MyFlow::class.java)
        whenever(versionedFlowFactory.create(any(), eq(listOf(sessionOne, sessionTwo, sessionThree)))).thenReturn(MY_FLOW)

        whenever(flowEngine.subFlow(MY_FLOW)).thenReturn(MyFlow.RESULT)

        flow.flowEngine = flowEngine
        flow.memberLookup = memberLookup
        flow.serializationService = serializationService
        flow.versioningService = versioningService
    }

    @Test
    fun `when there is a previously agreed platform version that version and the original sessions are passed into the versioned flow factory`() {
        whenever(versioningService.peekCurrentVersioning()).thenReturn(PREVIOUSLY_AGREED_VERSION)
        whenever(versionedFlowFactory.create(any(), versionedFlowFactorySessionCaptor.capture())).thenReturn(MY_FLOW)
        flow.call()
        verify(versionedFlowFactory).create(PREVIOUSLY_AGREED_VERSION.first, listOf(sessionOne, sessionTwo, sessionThree))
        assertThat(versionedFlowFactorySessionCaptor.firstValue).allMatch { it !is VersionSendingFlowSession }
    }

    @Test
    fun `when there is a previously agreed platform version executes the subFlow returned from the versioned flow factory and returns its result`() {
        whenever(versioningService.peekCurrentVersioning()).thenReturn(PREVIOUSLY_AGREED_VERSION)
        assertThat(flow.call()).isEqualTo(MyFlow.RESULT)
        verify(flowEngine).subFlow(MY_FLOW)
    }

    @Test
    fun `when there is a previously agreed platform version and no passed in sessions the existing version is passed into the versioned flow factory`() {
        whenever(versioningService.peekCurrentVersioning()).thenReturn(PREVIOUSLY_AGREED_VERSION)
        VersioningFlow(versionedFlowFactory, emptyList()).also { flow ->
            flow.flowEngine = flowEngine
            flow.memberLookup = memberLookup
            flow.serializationService = serializationService
            flow.versioningService = versioningService
        }.call()
        verify(versionedFlowFactory).create(PREVIOUSLY_AGREED_VERSION.first, emptyList())
    }

    @Test
    fun `when there is no previously agreed platform version the common version is decided and passed into the versioned flow factory `() {
        whenever(versioningService.peekCurrentVersioning()).thenReturn(null)
        flow.call()
        verify(versionedFlowFactory).create(eq(LOWEST_COMMON_VERSION), any())
    }

    @Test
    fun `when there is no previously agreed platform version the common version is decided and set`() {
        whenever(versioningService.peekCurrentVersioning()).thenReturn(null)
        flow.call()
        verify(versioningService).setCurrentVersioning(LOWEST_COMMON_VERSION)
    }

    @Test
    fun `when there is no previously agreed platform version the common version is decided and sessions to lazy send the version are created`() {
        whenever(versioningService.peekCurrentVersioning()).thenReturn(null)
        whenever(versionedFlowFactory.create(any(), versionedFlowFactorySessionCaptor.capture())).thenReturn(MY_FLOW)
        flow.call()
        verify(versionedFlowFactory).create(any(), any<List<VersionSendingFlowSession>>())
        assertThat(versionedFlowFactorySessionCaptor.firstValue).allMatch { it is VersionSendingFlowSession }
    }

    @Test
    fun `when there is no previously agreed platform version executes the subFlow returned from the versioned flow factory and returns its result`() {
        whenever(versioningService.peekCurrentVersioning()).thenReturn(null)
        whenever(versionedFlowFactory.create(any(), any<List<VersionSendingFlowSession>>())).thenReturn(MY_FLOW)
        assertThat(flow.call()).isEqualTo(MyFlow.RESULT)
        verify(flowEngine).subFlow(MY_FLOW)
    }

    @Test
    fun `when there is no previously agreed platform version and no passed in sessions the local platform version is passed into the versioned flow factory`() {
        whenever(versioningService.peekCurrentVersioning()).thenReturn(null)
        whenever(localMember.platformVersion).thenReturn(LOWEST_COMMON_VERSION)
        VersioningFlow(versionedFlowFactory, emptyList()).also { flow ->
            flow.flowEngine = flowEngine
            flow.memberLookup = memberLookup
            flow.serializationService = serializationService
            flow.versioningService = versioningService
        }.call()
        verify(versionedFlowFactory).create(LOWEST_COMMON_VERSION, emptyList())
    }

    @Test
    fun `the common version is set to the lowest platform version amongst the local member and the peer sessions`() {
        whenever(versioningService.peekCurrentVersioning()).thenReturn(null)
        whenever(memberInfoOne.platformVersion).thenReturn(200)
        whenever(memberInfoTwo.platformVersion).thenReturn(LOWEST_COMMON_VERSION)
        whenever(memberInfoThree.platformVersion).thenReturn(150)
        whenever(localMember.platformVersion).thenReturn(180)
        flow.call()
        verify(versionedFlowFactory).create(eq(LOWEST_COMMON_VERSION), any())
    }

    @Test
    fun `IllegalArgumentExceptions thrown from the versioned flow factory are caught and rethrown`() {
        whenever(versionedFlowFactory.create(any(), any())).thenThrow(IllegalArgumentException("break"))
        assertThatThrownBy { flow.call() }.isExactlyInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("There is no version $LOWEST_COMMON_VERSION")

    }

    @Test
    fun `other exception types thrown from the versioned flow factory are not caught`() {
        whenever(versionedFlowFactory.create(any(), any())).thenThrow(IllegalStateException("break"))
        assertThatThrownBy { flow.call() }.isExactlyInstanceOf(IllegalStateException::class.java).hasMessage("break")
    }

    @Test
    fun `throws an exception if a member does not exist in the MGM`() {
        whenever(memberLookup.lookup(MEMBER_TWO)).thenReturn(null)
        assertThatThrownBy { flow.call() }.isExactlyInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("does not exist")
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