package net.corda.virtualnode.read.fake

import net.corda.virtualnode.VirtualNodeInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import java.io.StringReader

internal class VirtualNodeInfoReadServiceFakeParserTest {
    @Test
    fun `parse yaml`() {
        val vnode = TestCatalogue.VirtualNode.create(
            TestCatalogue.Identity.alice("group-id"),
            TestCatalogue.CpiId.version5Snapshot("cpi1")
        )

        val reader = StringReader("""
        virtualNodeInfos:
          - cpiIdentifier:
              name: ${vnode.cpiIdentifier.name}
              version: ${vnode.cpiIdentifier.version}
              signerSummaryHash: ${vnode.cpiIdentifier.signerSummaryHash}
            cryptoDmlConnectionId: ${vnode.cryptoDmlConnectionId}
            holdingIdentity:
              groupId: ${vnode.holdingIdentity.groupId}
              x500Name: ${vnode.holdingIdentity.x500Name}
            vaultDmlConnectionId: ${vnode.vaultDmlConnectionId}
            uniquenessDmlConnectionId: ${vnode.uniquenessDmlConnectionId}
            timestamp: ${vnode.timestamp}
        """.trimIndent())

        val all = VirtualNodeInfoReadServiceFakeParser.loadFrom(reader)
        assertEquals(listOf(vnode), all, "parsed virtual node infos")
    }

    @Test
    fun `file does not exist`() {
        val file = File("dont-exist")
        assertEquals(emptyList<VirtualNodeInfo>(), VirtualNodeInfoReadServiceFakeParser.loadFrom(file))
    }
}
