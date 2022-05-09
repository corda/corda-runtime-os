package net.corda.virtualnode.read.fake

import net.corda.virtualnode.VirtualNodeInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import java.io.StringReader

internal class VirtualNodeInfoReadServiceFakeParserTest {
    @Test
    fun `parse yaml`() {
        val vnode = VirtualNodeInfo.alice
        val reader = StringReader("""
        virtualNodeInfos:
          - cpiIdentifier:
              name: ${vnode.cpiIdentifier.name}
              version: ${vnode.cpiIdentifier.version}
            cryptoDmlConnectionId: ${vnode.cryptoDmlConnectionId}
            holdingIdentity:
              groupId: ${vnode.holdingIdentity.groupId}
              x500Name: ${vnode.holdingIdentity.x500Name}
            vaultDmlConnectionId: ${vnode.vaultDmlConnectionId}
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