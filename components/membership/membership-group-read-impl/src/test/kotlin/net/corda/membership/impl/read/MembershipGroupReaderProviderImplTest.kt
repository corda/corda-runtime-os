package net.corda.membership.impl.read

import net.corda.configuration.read.ConfigurationReadService
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.impl.read.TestProperties.Companion.GROUP_ID_1
import net.corda.membership.impl.read.TestProperties.Companion.aliceName
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.HoldingIdentity
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

    private lateinit var membershipGroupReaderProvider: MembershipGroupReaderProviderImpl

    private val memberName = aliceName

    var coordinatorIsRunning = false
    var lifecycleStatus = LifecycleStatus.DOWN
    private val coordinator: LifecycleCoordinator = mock<LifecycleCoordinator>().apply {
        doAnswer { coordinatorIsRunning }.whenever(this).isRunning
        doAnswer { lifecycleStatus }.whenever(this).status

        doAnswer {
            coordinatorIsRunning = true
            lifecycleStatus = LifecycleStatus.UP
        }.whenever(this).start()
        doAnswer {
            coordinatorIsRunning = false
            lifecycleStatus = LifecycleStatus.DOWN
        }.whenever(this).stop()
    }

    private val subscriptionFactory: SubscriptionFactory = mock()
    private val configurationReadService: ConfigurationReadService = mock()
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>().apply {
        doReturn(coordinator).whenever(this).createCoordinator(any(), any())
    }
    private val groupPolicyProvider: GroupPolicyProvider = mock()
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory = mock()

    @BeforeEach
    fun setUp() {
        membershipGroupReaderProvider = MembershipGroupReaderProviderImpl(
            configurationReadService,
            subscriptionFactory,
            lifecycleCoordinatorFactory,
            layeredPropertyMapFactory,
            groupPolicyProvider
        )
    }

    @Test
    fun `Component is not running before starting and after stopping`() {
        assertFalse(membershipGroupReaderProvider.isRunning)
        membershipGroupReaderProvider.start()
        assertTrue(membershipGroupReaderProvider.isRunning)
        membershipGroupReaderProvider.stop()
        assertFalse(membershipGroupReaderProvider.isRunning)
    }

    @Test
    fun `Lifecycle coordinator is started when starting this component`() {
        verify(coordinator, never()).start()
        membershipGroupReaderProvider.start()
        verify(coordinator).start()
    }

    @Test
    fun `Lifecycle coordinator is stopped when stopping this component`() {
        membershipGroupReaderProvider.start()

        verify(coordinator, never()).stop()
        membershipGroupReaderProvider.stop()
        verify(coordinator).stop()
    }

    @Test
    fun `Get group reader throws exception if component hasn't started`() {
        val e = assertThrows<CordaRuntimeException> {
            membershipGroupReaderProvider.getGroupReader(HoldingIdentity(memberName.toString(), GROUP_ID_1))
        }
        assertEquals(MembershipGroupReaderProviderImpl.ILLEGAL_ACCESS, e.message)
    }
}
