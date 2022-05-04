package net.corda.sandbox.fakes

import net.corda.libs.packaging.CpiIdentifier
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.StringReader
import java.util.*

class FakeVirtualNodeInfoReadServiceTest {

    @Test
    fun `parse file`() {
        val expected = VirtualNodeInfo(
            HoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", "flow-worker-dev"),
            CpiIdentifier("flow-worker-dev", "5.0.0.0-SNAPSHOT", null),
            cryptoDmlConnectionId = UUID.randomUUID(),
            vaultDmlConnectionId = UUID.randomUUID()
        )

        val reader = StringReader("""
        virtualNodeInfos:
          - cpiIdentifier:
              name: ${expected.cpiIdentifier.name}
              version: ${expected.cpiIdentifier.version}
            cryptoDmlConnectionId: ${expected.cryptoDmlConnectionId}
            holdingIdentity:
              groupId: ${expected.holdingIdentity.groupId}
              x500Name: ${expected.holdingIdentity.x500Name}
            vaultDmlConnectionId: ${expected.vaultDmlConnectionId}
        """.trimIndent())

        val all = FakeVirtualNodeInfoReadService().apply { loadFrom(reader) }.getAll()
        assertEquals(listOf(expected), all, "parsed virtual node infos")
    }

    @Test
    fun `add or update`() {
        // worth doing it?
    }

    @Test
    fun `remove`() {
        // worth doing it
    }
}