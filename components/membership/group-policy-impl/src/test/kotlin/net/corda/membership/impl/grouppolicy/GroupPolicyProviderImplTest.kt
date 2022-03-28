package net.corda.membership.impl.grouppolicy

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.libs.packaging.CpiIdentifier
import net.corda.libs.packaging.CpiMetadata
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.GroupPolicy
import net.corda.membership.exceptions.BadGroupPolicyException
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoListener
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

/**
 * Unit tests for [GroupPolicyProviderImpl]
 */
class GroupPolicyProviderImplTest {
    lateinit var groupPolicyProvider: GroupPolicyProviderImpl

    val groupIdKey = "groupId"
    val testAttrKey = "testAttribute"

    val groupId1 = "ABC123"
    val groupId2 = "DEF456"

    val testAttr1 = "foo"
    val testAttr2 = "bar"
    val testAttr3 = "baz"

    val alice = MemberX500Name("Alice", "London", "GB")
    val bob = MemberX500Name("Bob", "London", "GB")

    val groupPolicy1 = "{\"$testAttrKey\": \"$testAttr1\", \"$groupIdKey\": \"$groupId1\"}"
    val groupPolicy2 = "{\"$testAttrKey\": \"$testAttr2\", \"$groupIdKey\": \"$groupId1\"}"
    val groupPolicy3 = "{\"$testAttrKey\": \"$testAttr3\", \"$groupIdKey\": \"$groupId2\"}"
    val groupPolicy4: String? = null

    val holdingIdentity1 = HoldingIdentity(alice.toString(), groupId1)
    val holdingIdentity2 = HoldingIdentity(bob.toString(), groupId1)
    val holdingIdentity3 = HoldingIdentity(alice.toString(), groupId2)
    val holdingIdentity4 = HoldingIdentity(bob.toString(), groupId2)

    fun mockMetadata(resultGroupPolicy: String?) = mock<CpiMetadata> {
        on { groupPolicy } doReturn resultGroupPolicy
    }

    val cpiMetadata1 = mockMetadata(groupPolicy1)
    val cpiMetadata2 = mockMetadata(groupPolicy2)
    val cpiMetadata3 = mockMetadata(groupPolicy3)
    val cpiMetadata4 = mockMetadata(groupPolicy4)

    val cpiIdentifier1: CpiIdentifier = mock()
    val cpiIdentifier2: CpiIdentifier = mock()
    val cpiIdentifier3: CpiIdentifier = mock()
    val cpiIdentifier4: CpiIdentifier = mock()

    var virtualNodeListener: VirtualNodeInfoListener? = null

    fun createVirtualNodeInfo(holdingIdentity: HoldingIdentity, cpiIdentifier: CpiIdentifier) = VirtualNodeInfo(
        holdingIdentity, cpiIdentifier, null, UUID.randomUUID(), null, UUID.randomUUID()
    )
    val virtualNodeInfoReadService: VirtualNodeInfoReadService = mock {
        on { get(eq(holdingIdentity1)) } doReturn createVirtualNodeInfo(holdingIdentity1, cpiIdentifier1)
        on { get(eq(holdingIdentity2)) } doReturn createVirtualNodeInfo(holdingIdentity2, cpiIdentifier2)
        on { get(eq(holdingIdentity3)) } doReturn createVirtualNodeInfo(holdingIdentity3, cpiIdentifier3)
        on { get(eq(holdingIdentity4)) } doReturn createVirtualNodeInfo(holdingIdentity4, cpiIdentifier4)
        on { registerCallback(any()) } doAnswer {
            virtualNodeListener = it.arguments[0] as VirtualNodeInfoListener
            mock()
        }
    }

    val cpiInfoReader: CpiInfoReadService = mock {
        on { get(cpiIdentifier1) } doReturn cpiMetadata1
        on { get(cpiIdentifier2) } doReturn cpiMetadata2
        on { get(cpiIdentifier3) } doReturn cpiMetadata3
        on { get(cpiIdentifier4) } doReturn cpiMetadata4
    }

    var handler: LifecycleEventHandler? = null

    var coordinatorIsRunning = false
    var coordinatorStatus = LifecycleStatus.DOWN
    val coordinator: LifecycleCoordinator = mock {
        on { start() } doAnswer {
            coordinatorIsRunning = true
            handler?.processEvent(StartEvent(), mock)
        }
        on { stop() } doAnswer {
            coordinatorIsRunning = false
            handler?.processEvent(StopEvent(), mock)
        }
        on { isRunning } doAnswer { coordinatorIsRunning }
        on { updateStatus(any(), any()) } doAnswer { coordinatorStatus = it.arguments[0] as LifecycleStatus }
        on { status } doAnswer { coordinatorStatus }
    }
    val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), any()) } doAnswer {
            handler = it.arguments[1] as LifecycleEventHandler
            coordinator
        }
    }

    fun registrationChange(status: LifecycleStatus = LifecycleStatus.UP) {
        handler?.processEvent(RegistrationStatusChangeEvent(mock(), status), coordinator)
    }

    @BeforeEach
    fun setUp() {
        groupPolicyProvider = GroupPolicyProviderImpl(
            virtualNodeInfoReadService,
            cpiInfoReader,
            lifecycleCoordinatorFactory
        )
    }

    fun startComponentAndDependencies() {
        groupPolicyProvider.start()
        registrationChange()
    }

    fun assertExpectedGroupPolicy(
        groupPolicy: GroupPolicy?,
        groupId: String?,
        testAttr: String?,
        expectedSize: Int = 2
    ) {
        assertNotNull(groupPolicy)
        assertEquals(expectedSize, groupPolicy!!.size)
        assertEquals(groupId, groupPolicy[groupIdKey])
        assertEquals(testAttr, groupPolicy[testAttrKey])
    }

    @Test
    fun `Correct group policy is returned when CPI metadata contains group policy string and service has started`() {
        startComponentAndDependencies()
        assertExpectedGroupPolicy(
            groupPolicyProvider.getGroupPolicy(holdingIdentity1),
            groupId1,
            testAttr1
        )
        assertExpectedGroupPolicy(
            groupPolicyProvider.getGroupPolicy(holdingIdentity2),
            groupId1,
            testAttr2
        )
        assertExpectedGroupPolicy(
            groupPolicyProvider.getGroupPolicy(holdingIdentity3),
            groupId2,
            testAttr3
        )
        assertThrows<BadGroupPolicyException> { groupPolicyProvider.getGroupPolicy(holdingIdentity4) }
    }

    @Test
    fun `Group policy read fails if service hasn't started`() {
        assertThrows<IllegalStateException> { groupPolicyProvider.getGroupPolicy(holdingIdentity1) }
    }

    @Test
    fun `Group policy read fails if service isn't up`() {
        groupPolicyProvider.start()
        assertThrows<IllegalStateException> { groupPolicyProvider.getGroupPolicy(holdingIdentity1) }
    }

    @Test
    fun `Same group policy is returned if it has already been parsed`() {
        startComponentAndDependencies()
        val result1 = groupPolicyProvider.getGroupPolicy(holdingIdentity1)
        val result2 = groupPolicyProvider.getGroupPolicy(holdingIdentity1)

        assertEquals(result1, result2)
    }

    @Test
    fun `Different group policy is returned if the service restarts`() {
        startComponentAndDependencies()
        val result1 = groupPolicyProvider.getGroupPolicy(holdingIdentity1)
        groupPolicyProvider.stop()
        startComponentAndDependencies()
        val result2 = groupPolicyProvider.getGroupPolicy(holdingIdentity1)

        assertNotEquals(result1, result2)
    }

    @Test
    fun `isRunning is set as expected when restarting the service`() {
        assertFalse(groupPolicyProvider.isRunning)
        groupPolicyProvider.start()
        assertTrue(groupPolicyProvider.isRunning)
        groupPolicyProvider.stop()
        assertFalse(groupPolicyProvider.isRunning)
        groupPolicyProvider.start()
        assertTrue(groupPolicyProvider.isRunning)
    }

    @Test
    fun `Cached group policy is updated when a holding identity updates their CPI`() {
        assertNull(virtualNodeListener)
        startComponentAndDependencies()
        assertTrue(groupPolicyProvider.isRunning)
        assertNotNull(virtualNodeListener)
        val original = groupPolicyProvider.getGroupPolicy(holdingIdentity1)
        assertExpectedGroupPolicy(original, groupId1, testAttr1)

        virtualNodeListener?.onUpdate(
            setOf(holdingIdentity1),
            mapOf(
                holdingIdentity1 to VirtualNodeInfo(
                    holdingIdentity1,
                    cpiIdentifier2,
                    null,
                    UUID.randomUUID(),
                    null,
                    UUID.randomUUID()
                )
            )
        )

        val updated = groupPolicyProvider.getGroupPolicy(holdingIdentity1)
        assertNotEquals(original, updated)
        assertExpectedGroupPolicy(updated, groupId1, testAttr2)
    }

    @Test
    fun `Group policy not yet cached is created when a holding identity updates their CPI`() {
        assertNull(virtualNodeListener)
        startComponentAndDependencies()
        assertNotNull(virtualNodeListener)

        virtualNodeListener?.onUpdate(
            setOf(holdingIdentity1),
            mapOf(
                holdingIdentity1 to VirtualNodeInfo(
                    holdingIdentity1,
                    cpiIdentifier2,
                    null,
                    UUID.randomUUID(),
                    null,
                    UUID.randomUUID()
                )
            )
        )

        val updated = groupPolicyProvider.getGroupPolicy(holdingIdentity1)
        assertExpectedGroupPolicy(updated, groupId1, testAttr2)
    }

    @Test
    fun `Component goes down when followed components go down and data can't be accessed`() {
        startComponentAndDependencies()
        assertTrue(groupPolicyProvider.isRunning)
        assertNotNull(handler)

        registrationChange(LifecycleStatus.DOWN)

        assertThrows<IllegalStateException> { groupPolicyProvider.getGroupPolicy(holdingIdentity1) }
    }

    @Test
    fun `Component goes down and then comes back up when followed components go down and up again`() {
        startComponentAndDependencies()
        assertTrue(groupPolicyProvider.isRunning)
        assertNotNull(handler)

        registrationChange(LifecycleStatus.DOWN)
        registrationChange()

        assertTrue(groupPolicyProvider.isRunning)
        assertExpectedGroupPolicy(
            groupPolicyProvider.getGroupPolicy(holdingIdentity1),
            groupId1,
            testAttr1
        )
    }

    @Test
    fun `Group policy is removed from cache if exception occurs when parsing during virtual node update callback`() {
        // start component
        startComponentAndDependencies()

        // test holding identity
        val holdingIdentity = HoldingIdentity(alice.toString(), "FOO-BAR")

        // set up mock for new CPI and send update to virtual node callback
        fun setCpi(cpiIdentifier: CpiIdentifier) {
            val vnode = createVirtualNodeInfo(holdingIdentity, cpiIdentifier)
            doReturn(vnode).whenever(virtualNodeInfoReadService).get(holdingIdentity)
            virtualNodeListener?.onUpdate(
                setOf(holdingIdentity),
                mapOf(holdingIdentity to vnode)
            )
        }

        // Configure initial CPI with valid group policy for holding identity
        setCpi(cpiIdentifier1)

        // Look up group policy to set initial cache value
        val initial = groupPolicyProvider.getGroupPolicy(holdingIdentity)
        assertExpectedGroupPolicy(initial, groupId1, testAttr1)

        // Trigger callback where an invalid group policy is loaded
        // This should cause an exception in parsing which is caught and the cached value should be removed
        assertDoesNotThrow { setCpi(cpiIdentifier4) }

        // Now there is no cached value so the service will parse again instead of reading from the cache.
        // Assert for exception to prove we are now parsing and not relying on the cache.
        assertThrows<BadGroupPolicyException> { groupPolicyProvider.getGroupPolicy(holdingIdentity) }

        // reset to valid group policy
        assertDoesNotThrow { setCpi(cpiIdentifier1) }

        // group policy retrieval works again
        val result = assertDoesNotThrow { groupPolicyProvider.getGroupPolicy(holdingIdentity) }
        assertExpectedGroupPolicy(result, groupId1, testAttr1)
    }
}
