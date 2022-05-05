package net.corda.sandbox.fakes

import net.corda.libs.packaging.CpiIdentifier
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import org.assertj.core.internal.bytebuddy.implementation.bind.MethodDelegationBinder.MethodInvoker.Virtual
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.StringReader
import java.util.*

class VirtualNodeInfoReadServiceFakeTest {

    private val alice = VirtualNodeInfo(
        HoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", "flow-worker-dev"),
        CpiIdentifier("flow-worker-dev", "5.0.0.0-SNAPSHOT", null),
        cryptoDmlConnectionId = UUID.randomUUID(),
        vaultDmlConnectionId = UUID.randomUUID()
    )

    private val bob = VirtualNodeInfo(
        HoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "flow-worker-dev"),
        CpiIdentifier("flow-worker-dev", "5.0.0.0-SNAPSHOT", null),
        cryptoDmlConnectionId = UUID.randomUUID(),
        vaultDmlConnectionId = UUID.randomUUID()
    )

    private val carol = VirtualNodeInfo(
        HoldingIdentity("CN=Carol, O=Carol Corp, L=LDN, C=GB", "flow-worker-dev"),
        CpiIdentifier("flow-worker-dev", "5.0.0.0-SNAPSHOT", null),
        cryptoDmlConnectionId = UUID.randomUUID(),
        vaultDmlConnectionId = UUID.randomUUID()
    )

    @Test
    fun `parse file`() {
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

        val all = VirtualNodeInfoReadServiceFake().apply { loadFrom(reader) }.getAll()
        assertEquals(listOf(alice), all, "parsed virtual node infos")
    }

    @Test
    fun getAll() {
        assertEquals(
            listOf(alice, bob),
            VirtualNodeInfoReadServiceFake(alice, bob).getAll(),
            "Onboarded virtual nodes"
        )

        assertEquals(
            emptyList<VirtualNodeInfo>(),
            VirtualNodeInfoReadServiceFake().getAll(),
            "Onboarded virtual nodes"
        )
    }

    @Test
    fun get() {
        val fake = VirtualNodeInfoReadServiceFake(alice, bob)
        assertEquals(bob, fake.get(bob.holdingIdentity), "Virtual Node Info")
        assertNull(fake.get(carol.holdingIdentity), "Virtual Node Info")
    }

    @Test
    fun getById() {
        val fake = VirtualNodeInfoReadServiceFake(alice, bob)
        assertEquals(bob, fake.getById(bob.holdingIdentity.id), "Virtual Node Info")
        assertNull(fake.getById(carol.holdingIdentity.id), "Virtual Node Info")
    }

    @Test
    fun `callback is called immediately when registered`() {
        var called = false
        val fake = VirtualNodeInfoReadServiceFake(alice, bob)
        fake.registerCallback { changedKeys, currentSnapshot ->
            called = true
            assertEquals(
                setOf(alice.holdingIdentity, bob.holdingIdentity),
                changedKeys, "Changed keys")
            assertEquals(
                mapOf(alice.holdingIdentity to alice, bob.holdingIdentity to bob),
                currentSnapshot, "Current snapshot")
        }
        assertTrue(called, "Callback called")
    }

    @Test
    fun `callbacks are called when a new virtual node is onboarded`() {

    }

    @Test
    fun `callbacks are called when updating an existing virtual node`() {

    }

    @Test
    fun `callbacks are called when removing an existing virtual node`() {

    }
}