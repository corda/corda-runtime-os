package net.corda.processors.db.internal.reconcile.db

import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.virtualnode.datamodel.HoldingIdentityEntity
import net.corda.libs.virtualnode.datamodel.VirtualNodeEntity
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.VirtualNodeInfo
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID
import kotlin.streams.toList

class VirtualNodeDbReconcilerReaderTest {
    companion object {
        const val x500name = "O=Alice,L=London,C=GB"
        val cpiSignerSummaryHash = SecureHash.parse("ABC:1234567890ABCDEF")
        const val cpiName = "test"
        const val cpiVersion = "1.0"
        const val holdingId = "ABCDEF1234567890"
        const val groupId = "123456"
        val vaultDmlConnectionId = UUID.randomUUID()
        val cryptoDmlConnectionId = UUID.randomUUID()
        val timestamp = Instant.now()
        const val entityVersion = 999
    }

    private fun mockVirtualNodeEntity(): VirtualNodeEntity {
        val mockHoldingIdentity = mockHoldingIdentity()
        return mock {
            whenever(it.cpiSignerSummaryHash).then { cpiSignerSummaryHash.toString() }
            whenever(it.cpiName).then { cpiName }
            whenever(it.cpiVersion).then { cpiVersion }
            whenever(it.holdingIdentity).then { mockHoldingIdentity }
            whenever(it.insertTimestamp).then { timestamp }
            whenever(it.entityVersion).then { entityVersion }
            whenever(it.virtualNodeState).then { VirtualNodeInfo.DEFAULT_INITIAL_STATE.name }
        }
    }

    private fun mockHoldingIdentity(): HoldingIdentityEntity {
        return mock {
            whenever(it.holdingIdentityShortHash).then { holdingId }
            whenever(it.x500Name).then { x500name }
            whenever(it.mgmGroupId).then { groupId }
            whenever(it.vaultDMLConnectionId).then { vaultDmlConnectionId }
            whenever(it.vaultDDLConnectionId).then { null }
            whenever(it.cryptoDMLConnectionId).then { cryptoDmlConnectionId }
            whenever(it.cryptoDDLConnectionId).then { null }
            whenever(it.hsmConnectionId).then { null }
        }
    }

    @Test
    fun `can convert db vnode to corda vnode`() {
        val entities = listOf(mockVirtualNodeEntity())

        val versionedRecords = virtualNodeEntitiesToVersionedRecords(entities.stream())
        val record = versionedRecords.toList().single()

        val expectedKey = createTestHoldingIdentity(x500name, groupId)

        Assertions.assertThat(record.key).isEqualTo(expectedKey)

        val expectedValue = VirtualNodeInfo(
            holdingIdentity = expectedKey,
            cpiIdentifier = CpiIdentifier(cpiName, cpiVersion, cpiSignerSummaryHash),
            vaultDdlConnectionId = null,
            vaultDmlConnectionId = vaultDmlConnectionId,
            cryptoDdlConnectionId = null,
            cryptoDmlConnectionId = cryptoDmlConnectionId,
            hsmConnectionId = null,
            version = entityVersion,
            timestamp = timestamp
        )

        Assertions.assertThat(record.value).isEqualTo(expectedValue)
    }
}
