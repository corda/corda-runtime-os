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
    val FLOW1 = "flow1"
    val instantTimestamp = Instant.now().minusMillis(1)

    private fun getStubVirtualNode(): VirtualNodeInfo {
        return VirtualNodeInfo(
            createTestHoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", ""),
            CpiIdentifier(
                "", "",
                SecureHash("", "bytes".toByteArray())
            ),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            version = 0,
            timestamp = Instant.now()
        )
    }

    @Test
    fun `createFlowStatusResponse returns last updated timestamp`() {
        val virtualNodeInfo = getStubVirtualNode()
        val status = messageFactory.createStartFlowStatus(clientRequestId, virtualNodeInfo.toAvro(), FLOW1)
        val response = messageFactory.createFlowStatusResponse(status)
        assertThat(response.timestamp).isEqualTo(status.lastUpdateTimestamp)
    }
}