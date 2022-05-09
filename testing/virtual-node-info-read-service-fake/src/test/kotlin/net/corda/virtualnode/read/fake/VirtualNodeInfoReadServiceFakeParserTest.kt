package net.corda.virtualnode.read.fake

import net.corda.virtualnode.VirtualNodeInfo
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import java.io.StringReader

internal class VirtualNodeInfoReadServiceFakeParserTest : BaseTest() {
    @Test
    fun `parse yaml`() {
        val reader = StringReader("""
        virtualNodeInfos:
          - cpiIdentifier:
              name: ${alice.cpiIdentifier.name}
              version: ${alice.cpiIdentifier.version}
            cryptoDmlConnectionId: ${alice.cryptoDmlConnectionId}
            holdingIdentity:
              groupId: ${alice.holdingIdentity.groupId}
              x500Name: ${alice.holdingIdentity.x500Name}
            vaultDmlConnectionId: ${alice.vaultDmlConnectionId}
        """.trimIndent())

        val all = VirtualNodeInfoReadServiceFakeParser.loadFrom(reader)
        assertEquals(listOf(alice), all, "parsed virtual node infos")
    }

    @Test
    fun `file does not exist`() {
        val file = File("dont-exist")
        assertEquals(emptyList<VirtualNodeInfo>(), VirtualNodeInfoReadServiceFakeParser.loadFrom(file))
    }
}