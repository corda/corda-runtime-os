package net.corda.processors.db.internal.reconcile.db

import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.VirtualNodeInfo
import org.assertj.core.api.Assertions.assertThat
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
        const val groupId = "123456"
        val vaultDmlConnectionId = UUID.randomUUID()
        val vaultDdlConnectionId = UUID.randomUUID()
        val cryptoDmlConnectionId = UUID.randomUUID()
        val cryptoDdlConnectionId = UUID.randomUUID()
        val uniquenessDmlConnectionId = UUID.randomUUID()
        val uniquenessDdlConnectionId = UUID.randomUUID()
        val timestamp = Instant.now()
        const val entityVersion = 999
    }

    private fun mockVirtualNodeEntity(): VirtualNodeInfo {
        val mockHoldingIdentity = mockHoldingIdentity()
        val cpiMock = mock<CpiIdentifier> {
            whenever(it.name).then { cpiName }
            whenever(it.version).then { cpiVersion}
            whenever(it.signerSummaryHash).then { cpiSignerSummaryHash}
        }
        return mock {
            whenever(it.cpiIdentifier).then { cpiMock}
            whenever(it.holdingIdentity).then { mockHoldingIdentity }
            whenever(it.timestamp).then { timestamp }
            whenever(it.version).then { entityVersion }
            whenever(it.state).then { VirtualNodeInfo.DEFAULT_INITIAL_STATE.name }
            whenever(it.vaultDmlConnectionId).then { vaultDmlConnectionId }
            whenever(it.vaultDdlConnectionId).then { vaultDdlConnectionId }
            whenever(it.cryptoDmlConnectionId).then { cryptoDmlConnectionId }
            whenever(it.cryptoDdlConnectionId).then { cryptoDdlConnectionId }
            whenever(it.uniquenessDmlConnectionId).then { uniquenessDmlConnectionId }
            whenever(it.uniquenessDdlConnectionId).then { uniquenessDdlConnectionId }
            whenever(it.hsmConnectionId).then { null }
        }
    }

    private fun mockHoldingIdentity() =
        createTestHoldingIdentity(x500name, groupId)

    @Test
    fun `can convert db vnode to corda vnode`() {
        val entities = listOf(mockVirtualNodeEntity())

        val versionedRecords = virtualNodeEntitiesToVersionedRecords(entities.stream())
        val record = versionedRecords.toList().single()

        val expectedKey = createTestHoldingIdentity(x500name, groupId)

        assertThat(record.key).isEqualTo(expectedKey)

        assertThat(record.value.holdingIdentity).isEqualTo(expectedKey)
        assertThat(record.value.cpiIdentifier.name).isEqualTo(cpiName)
        assertThat(record.value.cpiIdentifier.version).isEqualTo(cpiVersion)
        assertThat(record.value.cpiIdentifier.signerSummaryHash).isEqualTo(cpiSignerSummaryHash)
        assertThat(record.value.vaultDdlConnectionId).isEqualTo(vaultDdlConnectionId)
        assertThat(record.value.vaultDmlConnectionId).isEqualTo(vaultDmlConnectionId)
        assertThat(record.value.cryptoDdlConnectionId).isEqualTo(cryptoDdlConnectionId)
        assertThat(record.value.cryptoDmlConnectionId).isEqualTo(cryptoDmlConnectionId)
        assertThat(record.value.uniquenessDdlConnectionId).isEqualTo(uniquenessDdlConnectionId)
        assertThat(record.value.uniquenessDmlConnectionId).isEqualTo(uniquenessDmlConnectionId)
        assertThat(record.value.hsmConnectionId).isEqualTo(null)
        assertThat(record.value.version).isEqualTo(entityVersion)
        assertThat(record.value.timestamp).isEqualTo(timestamp)
        assertThat(record.value.isDeleted).isEqualTo(false)
    }
}
