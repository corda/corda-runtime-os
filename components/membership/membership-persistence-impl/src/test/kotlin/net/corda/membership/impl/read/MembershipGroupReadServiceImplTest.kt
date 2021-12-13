package net.corda.membership.impl.read

import net.corda.cpiinfo.CpiInfoReader
import net.corda.data.membership.SignedMemberInfo
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.packaging.CPI
import net.corda.v5.membership.identity.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReaderComponent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MembershipGroupReadServiceImplTest {

    private val cpiInfoReader: CpiInfoReader = mock()
    private val virtualNodeInfoReader: VirtualNodeInfoReaderComponent = mock()
    private val subscriptionFactory: SubscriptionFactory = mock()

    private lateinit var membershipGroupReadService: MembershipGroupReadServiceImpl

    private val testGroupId = "GROUP_ID"
    private val testMemberName = MemberX500Name("Alice", "London", "GB")
    private var memberListSubIsRunning = false

    /**
     * Set mocks before each test.
     */
    @BeforeEach
    fun setUp() {
        membershipGroupReadService = MembershipGroupReadServiceImpl(
            cpiInfoReader,
            virtualNodeInfoReader,
            subscriptionFactory
        )
        val cpi: CPI.Identifier = mock()
        val virtualNodeInfo = VirtualNodeInfo(HoldingIdentity(testMemberName.toString(), testGroupId), cpi)
        val cpiMetadata: CPI.Metadata = mock()
        val mockMemberListSub: CompactedSubscription<String, SignedMemberInfo> = mock()

        whenever(virtualNodeInfoReader.get(any())).thenReturn(virtualNodeInfo)
        whenever(cpiInfoReader.get(eq(cpi))).thenReturn(cpiMetadata)
        // default group policy to empty string
        whenever(cpiMetadata.groupPolicy).thenReturn("")
        whenever(
            subscriptionFactory.createCompactedSubscription(
                any(),
                any<CompactedProcessor<String, SignedMemberInfo>>(),
                any()
            )
        ).thenReturn(mockMemberListSub)
        whenever(mockMemberListSub.isRunning).thenAnswer {
            memberListSubIsRunning
        }
        whenever(mockMemberListSub.start()).thenAnswer {
            memberListSubIsRunning = true
            Unit
        }
        whenever(mockMemberListSub.stop()).thenAnswer {
            memberListSubIsRunning = false
            Unit
        }
    }

    @Test
    fun `Group reader cannot be retrieved if service hasn't started yet`() {
        assertThrows<NullPointerException> {
            membershipGroupReadService.getGroupReader(testGroupId, testMemberName)
        }
    }

    @Test
    fun `Group reader can be retrieved if service has started`() {
        membershipGroupReadService.start()
        membershipGroupReadService.getGroupReader(testGroupId, testMemberName)
    }

    @Test
    fun `Group reader cannot be retrieved if service has stopped`() {
        membershipGroupReadService.start()
        membershipGroupReadService.stop()
        assertThrows<NullPointerException> {
            membershipGroupReadService.getGroupReader(testGroupId, testMemberName)
        }
    }

    @Test
    fun `Service is not running if start has not been called`() {
        assertFalse(membershipGroupReadService.isRunning)
    }

    @Test
    fun `Service is running if start has been called`() {
        membershipGroupReadService.start()
        assertTrue(membershipGroupReadService.isRunning)
    }

    @Test
    fun `Service is not running if stop has been called`() {
        membershipGroupReadService.start()
        membershipGroupReadService.stop()
        assertFalse(membershipGroupReadService.isRunning)
    }

    @Test
    fun `Group reader is returned from the cache if already created`() {
        membershipGroupReadService.start()
        val lookup1 = membershipGroupReadService.getGroupReader(testGroupId, testMemberName)
        val lookup2 = membershipGroupReadService.getGroupReader(testGroupId, testMemberName)
        assertEquals(lookup1, lookup2)
    }

    @Test
    fun `Group reader cache is cleared after restarting the service`() {
        membershipGroupReadService.start()
        val lookup1 = membershipGroupReadService.getGroupReader(testGroupId, testMemberName)
        membershipGroupReadService.stop()
        membershipGroupReadService.start()
        val lookup2 = membershipGroupReadService.getGroupReader(testGroupId, testMemberName)
        assertNotEquals(lookup1, lookup2)
    }

    @Test
    fun `handleConfigEvent restarts the subscriptions`() {
        // When service starts, subscription is created
        membershipGroupReadService.start()
        verify(subscriptionFactory, times(1))
            .createCompactedSubscription(
                any(),
                any<CompactedProcessor<String, SignedMemberInfo>>(),
                any()
            )

        // When config event is handled, the subscription is started again
        membershipGroupReadService.handleConfigEvent(mock())
        verify(subscriptionFactory, times(2))
            .createCompactedSubscription(
                any(),
                any<CompactedProcessor<String, SignedMemberInfo>>(),
                any()
            )
    }

    @Test
    fun `handleConfigEvent recreates the caches`() {
        // start the service
        membershipGroupReadService.start()

        // create and cache group reader
        val reader1 = membershipGroupReadService.getGroupReader(testGroupId, testMemberName)
        // get reader from cache
        val reader2 = membershipGroupReadService.getGroupReader(testGroupId, testMemberName)
        assertEquals(reader1, reader2)

        // handle config event
        membershipGroupReadService.handleConfigEvent(mock())

        // next reader should be new since cache should have been reset
        val reader3 = membershipGroupReadService.getGroupReader(testGroupId, testMemberName)
        // get reader from cache
        val reader4 = membershipGroupReadService.getGroupReader(testGroupId, testMemberName)
        assertEquals(reader3, reader4)

        // current reader should not equal previous reader from before handling the config
        assertNotEquals(reader1, reader3)
    }

}