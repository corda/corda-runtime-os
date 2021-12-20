package net.corda.membership.impl.read.component

import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.read.CpiInfoReaderComponent
import net.corda.data.membership.SignedMemberInfo
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.packaging.CPI
import net.corda.v5.membership.identity.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReaderComponent
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MembershipGroupReadServiceImplTest {

    private lateinit var membershipGroupReadService: MembershipGroupReadServiceImpl

    private val groupId = "GROUP_ID"
    private val memberName = MemberX500Name("Alice", "London", "GB")

    private lateinit var cpiInfoReader: CpiInfoReaderComponent
    private lateinit var virtualNodeInfoReader: VirtualNodeInfoReaderComponent
    private lateinit var subscriptionFactory: SubscriptionFactory
    private lateinit var configurationReadService: ConfigurationReadService
    private lateinit var lifecycleCoordinatorFactory: LifecycleCoordinatorFactory

    @BeforeEach
    fun setUp() {
        configureMocks()
        membershipGroupReadService = MembershipGroupReadServiceImpl(
            virtualNodeInfoReader,
            cpiInfoReader,
            configurationReadService,
            subscriptionFactory,
            lifecycleCoordinatorFactory
        )
    }

//    @Test
//    fun `Group reader cannot be retrieved if service hasn't started yet`() {
//        assertThrows<CordaRuntimeException> {
//            membershipGroupReadService.getGroupReader(groupId, memberName)
//        }
//    }
//
//    @Test
//    fun `Group reader can be retrieved if service has started`() {
//        membershipGroupReadService.start()
//        membershipGroupReadService.getGroupReader(groupId, memberName)
//    }
//
//    @Test
//    fun `Group reader cannot be retrieved if service has stopped`() {
//        membershipGroupReadService.start()
//        membershipGroupReadService.stop()
//        assertThrows<CordaRuntimeException> {
//            membershipGroupReadService.getGroupReader(groupId, memberName)
//        }
//    }
//
//    @Test
//    fun `Service is not running if start has not been called`() {
//        assertFalse(membershipGroupReadService.isRunning)
//    }
//
//    @Test
//    fun `Service is running if start has been called`() {
//        membershipGroupReadService.start()
//        assertTrue(membershipGroupReadService.isRunning)
//    }
//
//    @Test
//    fun `Service is not running if stop has been called`() {
//        membershipGroupReadService.start()
//        membershipGroupReadService.stop()
//        assertFalse(membershipGroupReadService.isRunning)
//    }
//
//    @Test
//    fun `Group reader is returned from the cache if already created`() {
//        membershipGroupReadService.start()
//        val lookup1 = membershipGroupReadService.getGroupReader(groupId, memberName)
//        val lookup2 = membershipGroupReadService.getGroupReader(groupId, memberName)
//        assertEquals(lookup1, lookup2)
//    }
//
//    @Test
//    fun `Group reader cache is cleared after restarting the service`() {
//        membershipGroupReadService.start()
//        val lookup1 = membershipGroupReadService.getGroupReader(groupId, memberName)
//        membershipGroupReadService.stop()
//        membershipGroupReadService.start()
//        val lookup2 = membershipGroupReadService.getGroupReader(groupId, memberName)
//        assertNotEquals(lookup1, lookup2)
//    }
//
//    @Test
//    fun `handleConfigEvent restarts the subscriptions`() {
//        // When service starts, subscription is created
//        membershipGroupReadService.start()
//        verify(subscriptionFactory, times(1))
//            .createCompactedSubscription(
//                any(),
//                any<CompactedProcessor<String, SignedMemberInfo>>(),
//                any()
//            )
//
//        // When config event is handled, the subscription is started again
//        membershipGroupReadService.handleConfigEvent(mock())
//        verify(subscriptionFactory, times(2))
//            .createCompactedSubscription(
//                any(),
//                any<CompactedProcessor<String, SignedMemberInfo>>(),
//                any()
//            )
//    }
//
//    @Test
//    fun `handleConfigEvent recreates the caches`() {
//        // start the service
//        membershipGroupReadService.start()
//
//        // create and cache group reader
//        val reader1 = membershipGroupReadService.getGroupReader(groupId, memberName)
//        // get reader from cache
//        val reader2 = membershipGroupReadService.getGroupReader(groupId, memberName)
//        assertEquals(reader1, reader2)
//
//        // handle config event
//        membershipGroupReadService.handleConfigEvent(mock())
//
//        // next reader should be new since cache should have been reset
//        val reader3 = membershipGroupReadService.getGroupReader(groupId, memberName)
//        // get reader from cache
//        val reader4 = membershipGroupReadService.getGroupReader(groupId, memberName)
//        assertEquals(reader3, reader4)
//
//        // current reader should not equal previous reader from before handling the config
//        assertNotEquals(reader1, reader3)
//    }
//
//    @Test
//    fun `Service is running after handling a config event`() {
//        assertFalse(membershipGroupReadService.isRunning)
//        membershipGroupReadService.start()
//        assertTrue(membershipGroupReadService.isRunning)
//        membershipGroupReadService.handleConfigEvent(mock())
//        assertTrue(membershipGroupReadService.isRunning)
//    }
//
private fun configureMocks() {
    var memberListSubIsRunning = false

    val cpi: CPI.Identifier = mock()
    val virtualNodeInfo = VirtualNodeInfo(HoldingIdentity(memberName.toString(), groupId), cpi)
    val cpiMetadata = mock<CPI.Metadata>().apply {
        doReturn("").whenever(this).groupPolicy
    }
    val mockMemberListSub = mock<CompactedSubscription<String, SignedMemberInfo>>().apply {
        doAnswer { memberListSubIsRunning = true }.whenever(this).start()
        doAnswer { memberListSubIsRunning = false }.whenever(this).stop()
        doAnswer { memberListSubIsRunning }.whenever(this).isRunning
    }

    cpiInfoReader = mock<CpiInfoReaderComponent>().apply {
        doReturn(cpiMetadata).whenever(this).get(eq(cpi))
    }
    virtualNodeInfoReader = mock<VirtualNodeInfoReaderComponent>().apply {
        doReturn(virtualNodeInfo).whenever(this).get(any())
    }
    subscriptionFactory = mock<SubscriptionFactory>().apply {
        doReturn(mockMemberListSub).whenever(this).createCompactedSubscription(
            any(),
            any<CompactedProcessor<String, SignedMemberInfo>>(),
            any()
        )
    }
}
}