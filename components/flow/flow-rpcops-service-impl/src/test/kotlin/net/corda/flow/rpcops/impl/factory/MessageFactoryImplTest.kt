package net.corda.flow.rpcops.impl.factory

import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

internal class MessageFactoryImplTest {

    private val messageFactory = MessageFactoryImpl()
    private val clientRequestId = UUID.randomUUID().toString()
    private val flow1 = "flow1"

    private val vaultDdlConnectionId = "16929514-237c-11ed-861d-0242ac120001"
    private val vaultDmlConnectionId = "26929514-237c-11ed-861d-0242ac120002"
    private val cryptoDdlConnectionId = "36929514-237c-11ed-861d-0242ac120003"
    private val cryptoDmlConnectionId = "46929514-237c-11ed-861d-0242ac120004"
    private val uniquenessDdlConnectionId = "56929514-237c-11ed-861d-0242ac120005"
    private val uniquenessDmlConnectionId = "66929514-237c-11ed-861d-0242ac120006"
    private val hsmConnectionId = "76929514-237c-11ed-861d-0242ac120007"

    private fun getStubVirtualNode(): VirtualNodeInfo {
        return VirtualNodeInfo(
            createTestHoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", ""),
            CpiIdentifier(
                "", "",
                SecureHash("", "bytes".toByteArray())
            ),
            UUID.fromString(vaultDdlConnectionId),
            UUID.fromString(vaultDmlConnectionId),
            UUID.fromString(cryptoDdlConnectionId),
            UUID.fromString(cryptoDmlConnectionId),
            UUID.fromString(uniquenessDdlConnectionId),
            UUID.fromString(uniquenessDmlConnectionId),
            UUID.fromString(hsmConnectionId),
            version = 0,
            timestamp = Instant.now()
        )
    }

    @Test
    fun `createFlowStatusResponse returns last updated timestamp`() {
        val virtualNodeInfo = getStubVirtualNode()
        val status = messageFactory.createStartFlowStatus(clientRequestId, virtualNodeInfo.toAvro(), flow1)
        val response = messageFactory.createFlowStatusResponse(status)
        assertThat(response.timestamp).isEqualTo(status.lastUpdateTimestamp)
    }
}