package net.corda.membership.grouppolicy

import net.corda.cpiinfo.CpiInfoListener
import net.corda.cpiinfo.read.CpiInfoReaderComponent
import net.corda.membership.GroupPolicy
import net.corda.packaging.CPI
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.membership.identity.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReaderComponent
import net.corda.virtualnode.service.VirtualNodeInfoListener
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Unit tests for [GroupPolicyProviderImpl]
 */
class GroupPolicyProviderImplTest {
    lateinit var groupPolicyProvider: GroupPolicyProviderImpl

    val GROUP_ID_KEY = "groupId"
    val TEST_ATTR_KEY = "testAttribute"

    val groupId1 = "ABC123"
    val groupId2 = "DEF456"

    val testAttr1 = "foo"
    val testAttr2 = "bar"
    val testAttr3 = "baz"

    val alice = MemberX500Name("Alice", "London", "GB")
    val bob = MemberX500Name("Bob", "London", "GB")

    val groupPolicy1 = "{\"$TEST_ATTR_KEY\": \"$testAttr1\", \"$GROUP_ID_KEY\": \"$groupId1\"}"
    val groupPolicy2 = "{\"$TEST_ATTR_KEY\": \"$testAttr2\", \"$GROUP_ID_KEY\": \"$groupId1\"}"
    val groupPolicy3 = "{\"$TEST_ATTR_KEY\": \"$testAttr3\", \"$GROUP_ID_KEY\": \"$groupId2\"}"
    val groupPolicy4: String? = null

    val holdingIdentity1 = HoldingIdentity(alice.toString(), groupId1)
    val holdingIdentity2 = HoldingIdentity(bob.toString(), groupId1)
    val holdingIdentity3 = HoldingIdentity(alice.toString(), groupId2)
    val holdingIdentity4 = HoldingIdentity(bob.toString(), groupId2)

    val cpiMetadata1 = mock<CPI.Metadata>().apply { doReturn(groupPolicy1).whenever(this).groupPolicy }
    val cpiMetadata2 = mock<CPI.Metadata>().apply { doReturn(groupPolicy2).whenever(this).groupPolicy }
    val cpiMetadata3 = mock<CPI.Metadata>().apply { doReturn(groupPolicy3).whenever(this).groupPolicy }
    val cpiMetadata4 = mock<CPI.Metadata>().apply { doReturn(groupPolicy4).whenever(this).groupPolicy }

    val cpiIdentifier1: CPI.Identifier = mock()
    val cpiIdentifier2: CPI.Identifier = mock()
    val cpiIdentifier3: CPI.Identifier = mock()
    val cpiIdentifier4: CPI.Identifier = mock()

    var virtualNodeListener: VirtualNodeInfoListener? = null
    var cpiInfoListener: CpiInfoListener? = null

    val virtualNodeInfoReader: VirtualNodeInfoReaderComponent = mock<VirtualNodeInfoReaderComponent>().apply {
        doReturn(VirtualNodeInfo(holdingIdentity1, cpiIdentifier1)).whenever(this).get(eq(holdingIdentity1))
        doReturn(VirtualNodeInfo(holdingIdentity2, cpiIdentifier2)).whenever(this).get(eq(holdingIdentity2))
        doReturn(VirtualNodeInfo(holdingIdentity3, cpiIdentifier3)).whenever(this).get(eq(holdingIdentity3))
        doReturn(VirtualNodeInfo(holdingIdentity4, cpiIdentifier4)).whenever(this).get(eq(holdingIdentity4))
        doAnswer {
            virtualNodeListener = it.arguments[0] as VirtualNodeInfoListener
            mock<AutoCloseable>()
        }
            .whenever(this)
            .registerCallback(any())
    }

    val cpiInfoReader: CpiInfoReaderComponent = mock<CpiInfoReaderComponent>().apply {
        doReturn(cpiMetadata1).whenever(this).get(cpiIdentifier1)
        doReturn(cpiMetadata2).whenever(this).get(cpiIdentifier2)
        doReturn(cpiMetadata3).whenever(this).get(cpiIdentifier3)
        doReturn(cpiMetadata4).whenever(this).get(cpiIdentifier4)
        doAnswer {
            cpiInfoListener = it.arguments[0] as CpiInfoListener
            mock<AutoCloseable>()
        }
            .whenever(this)
            .registerCallback(any())
    }

    @BeforeEach
    fun setUp() {
        virtualNodeListener = null
        cpiInfoListener = null
        groupPolicyProvider = GroupPolicyProviderImpl(virtualNodeInfoReader, cpiInfoReader)
    }

    fun assertExpectedGroupPolicy(
        groupPolicy: GroupPolicy,
        groupId: String?,
        testAttr: String?,
        expectedSize: Int = 2
    ) {
        assertEquals(expectedSize, groupPolicy.size)
        assertEquals(groupId, groupPolicy[GROUP_ID_KEY])
        assertEquals(testAttr, groupPolicy[TEST_ATTR_KEY])
    }

    @Test
    fun `Correct group policy is returned when CPI metadata contains group policy string and service has started`() {
        groupPolicyProvider.start()
        assertExpectedGroupPolicy(
            groupPolicyProvider.getGroupPolicy(groupId1, alice),
            groupId1,
            testAttr1
        )
        assertExpectedGroupPolicy(
            groupPolicyProvider.getGroupPolicy(groupId1, bob),
            groupId1,
            testAttr2
        )
        assertExpectedGroupPolicy(
            groupPolicyProvider.getGroupPolicy(groupId2, alice),
            groupId2,
            testAttr3
        )
        assertExpectedGroupPolicy(
            groupPolicyProvider.getGroupPolicy(groupId2, bob),
            null,
            null,
            0
        )
    }

    @Test
    fun `Group policy read fails if service hasn't started`() {
        assertThrows<CordaRuntimeException> {
            groupPolicyProvider.getGroupPolicy(groupId1, alice)
        }
    }

    @Test
    fun `Same group policy is returned if it has already been parsed`() {
        groupPolicyProvider.start()
        val result1 = groupPolicyProvider.getGroupPolicy(groupId1, alice)
        val result2 = groupPolicyProvider.getGroupPolicy(groupId1, alice)

        assertEquals(result1, result2)
    }

    @Test
    fun `Different group policy is returned if the service restarts`() {
        groupPolicyProvider.start()
        val result1 = groupPolicyProvider.getGroupPolicy(groupId1, alice)
        groupPolicyProvider.stop()
        groupPolicyProvider.start()
        val result2 = groupPolicyProvider.getGroupPolicy(groupId1, alice)

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
        groupPolicyProvider.start()
        assertNotNull(virtualNodeListener)
        val original = groupPolicyProvider.getGroupPolicy(groupId1, alice)
        assertExpectedGroupPolicy(original, groupId1, testAttr1)

        virtualNodeListener?.onUpdate(
            setOf(holdingIdentity1),
            mapOf(holdingIdentity1 to VirtualNodeInfo(holdingIdentity1, cpiIdentifier2))
        )

        val updated = groupPolicyProvider.getGroupPolicy(groupId1, alice)
        assertNotEquals(original, updated)
        assertExpectedGroupPolicy(updated, groupId1, testAttr2)
    }

    @Test
    fun `Group policy not yet cached is created when a holding identity updates their CPI`() {
        assertNull(virtualNodeListener)
        groupPolicyProvider.start()
        assertNotNull(virtualNodeListener)

        virtualNodeListener?.onUpdate(
            setOf(holdingIdentity1),
            mapOf(holdingIdentity1 to VirtualNodeInfo(holdingIdentity1, cpiIdentifier2))
        )

        val updated = groupPolicyProvider.getGroupPolicy(groupId1, alice)
        assertExpectedGroupPolicy(updated, groupId1, testAttr2)
    }
}