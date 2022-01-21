package net.corda.membership.impl.read.component

import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.crypto.CryptoLibraryFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.membership.impl.read.TestProperties.Companion.GROUP_ID_1
import net.corda.membership.impl.read.TestProperties.Companion.aliceName
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.membership.conversion.PropertyConverter
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [MembershipGroupReaderProviderImpl]. Test are kept to a minimum because the implementation doesn't contain
 * much actual implementation code but rather uses other classes which have more specific purposes. Each are tested
 * separately to this class.
 */
class MembershipGroupReaderProviderImplTest {

    private lateinit var membershipGroupReadService: MembershipGroupReaderProviderImpl

    private val memberName = aliceName

    var coordinatorIsRunning = false
    private val coordinator: LifecycleCoordinator = mock<LifecycleCoordinator>().apply {
        doAnswer { coordinatorIsRunning }.whenever(this).isRunning
        doAnswer { coordinatorIsRunning = true }.whenever(this).start()
        doAnswer { coordinatorIsRunning = false }.whenever(this).stop()
    }

    private val cpiInfoReader: CpiInfoReadService = mock()
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService = mock()
    private val subscriptionFactory: SubscriptionFactory = mock()
    private val configurationReadService: ConfigurationReadService = mock()
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>().apply {
        doReturn(coordinator).whenever(this).createCoordinator(any(), any())
    }
    private val converter: PropertyConverter = mock()
    private val cryptoLibraryFactory: CryptoLibraryFactory = mock {
        on { getKeyEncodingService() } doReturn mock()
    }

    @BeforeEach
    fun setUp() {
        membershipGroupReadService = MembershipGroupReaderProviderImpl(
            virtualNodeInfoReadService,
            cpiInfoReader,
            configurationReadService,
            subscriptionFactory,
            lifecycleCoordinatorFactory,
            converter,
            cryptoLibraryFactory
        )
    }

    @Test
    fun `Component is not running before starting and after stopping`() {
        assertFalse(membershipGroupReadService.isRunning)
        membershipGroupReadService.start()
        assertTrue(membershipGroupReadService.isRunning)
        membershipGroupReadService.stop()
        assertFalse(membershipGroupReadService.isRunning)
    }

    @Test
    fun `Lifecycle coordinator is started when starting this component`() {
        verify(coordinator, never()).start()
        membershipGroupReadService.start()
        verify(coordinator).start()
    }

    @Test
    fun `Lifecycle coordinator is stopped when stopping this component`() {
        membershipGroupReadService.start()

        verify(coordinator, never()).stop()
        membershipGroupReadService.stop()
        verify(coordinator).stop()
    }

    @Test
    fun `Get group reader throws exception if component hasn't started`() {
        val e = assertThrows<CordaRuntimeException> {
            membershipGroupReadService.getGroupReader(HoldingIdentity(memberName.toString(), GROUP_ID_1))
        }
        assertEquals(MembershipGroupReaderProviderImpl.ACCESS_TOO_EARLY, e.message)
    }
}
