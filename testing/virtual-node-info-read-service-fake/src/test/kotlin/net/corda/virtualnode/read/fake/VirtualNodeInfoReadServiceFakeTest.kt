package net.corda.virtualnode.read.fake

import net.corda.libs.packaging.CpiIdentifier
import net.corda.lifecycle.impl.LifecycleCoordinatorFactoryImpl
import net.corda.lifecycle.impl.registry.LifecycleRegistryImpl
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoListener
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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

    private fun createService(
        vararg virtualNodeInfos: VirtualNodeInfo,
        callbacks: List<VirtualNodeInfoListener> = emptyList(),
    ): VirtualNodeInfoReadServiceFake {
        val service = VirtualNodeInfoReadServiceFake(
            virtualNodeInfos.associateBy { it.holdingIdentity },
            callbacks,
            LifecycleCoordinatorFactoryImpl(LifecycleRegistryImpl())
        )
        service.start()
        service.waitUntilRunning()
        return service
    }

    private fun snapshot(vararg virtualNodeInfos: VirtualNodeInfo): Map<HoldingIdentity, VirtualNodeInfo> {
        return virtualNodeInfos.associateBy { it.holdingIdentity }
    }

    private fun keys(vararg virtualNodeInfos: VirtualNodeInfo): Set<HoldingIdentity> {
        return virtualNodeInfos.map { it.holdingIdentity }.toSet()
    }

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

        val all = createService().apply { loadFrom(reader) }.getAll()
        assertEquals(listOf(alice), all, "parsed virtual node infos")
    }

    @Test
    fun getAll() {
        assertEquals(
            listOf(alice, bob),
            createService(alice, bob).getAll(),
            "Onboarded virtual nodes"
        )

        assertEquals(
            emptyList<VirtualNodeInfo>(),
            createService().getAll(),
            "Onboarded virtual nodes"
        )
    }

    @Test
    fun get() {
        val service = createService(alice, bob)
        assertEquals(bob, service.get(bob.holdingIdentity), "Virtual Node Info")
        assertNull(service.get(carol.holdingIdentity), "Virtual Node Info")
    }

    @Test
    fun getById() {
        val service = createService(alice, bob)
        assertEquals(bob, service.getById(bob.holdingIdentity.id), "Virtual Node Info")
        assertNull(service.getById(carol.holdingIdentity.id), "Virtual Node Info")
    }

    @Test
    fun `callback is called immediately when registered`() {
        val listener = VirtualNodeInfoListenerSpy()

        createService(alice, bob).registerCallback(listener)

        assertEquals(1, listener.timesCalled, "times called")
        assertEquals(keys(alice, bob), listener.keys[0], "changed keys")
        assertEquals(snapshot(alice, bob), listener.snapshots[0], "current snapshot")
    }

    @Test
    fun `add vnode`() {
        val listener = VirtualNodeInfoListenerSpy()
        val service = createService(alice, callbacks = listOf(listener))

        service.addOrUpdate(bob)

        assertEquals(listOf(alice, bob), service.getAll(), "all vnodes")
        assertEquals(1, listener.timesCalled, "times called listener1")
        assertEquals(keys(bob), listener.keys[0], "keys added")
        assertEquals(snapshot(alice, bob), listener.snapshots[0], "snapshot")
    }

    @Test
    fun `remove vnode`() {
        val listener = VirtualNodeInfoListenerSpy()
        val service = createService(alice, bob, callbacks = listOf(listener))

        service.remove(alice)

        assertEquals(listOf(bob), service.getAll(), "all vnodes")
        assertEquals(1, listener.timesCalled, "times called listener1")
        assertEquals(keys(alice), listener.keys[0], "keys removed")
        assertEquals(snapshot(bob), listener.snapshots[0], "snapshot")
    }

    @Test
    fun `update vnode`() {
        // How does the update should behave?
        // Does it make sense to have it?
    }

    private class VirtualNodeInfoListenerSpy : VirtualNodeInfoListener {

        private val _keys = mutableListOf<Set<HoldingIdentity>>()
        private val _snapshots = mutableListOf<Map<HoldingIdentity, VirtualNodeInfo>>()

        val keys: List<Set<HoldingIdentity>>
            get() = _keys

        val snapshots: List<Map<HoldingIdentity, VirtualNodeInfo>>
            get() = _snapshots

        val timesCalled: Int
            get() = _keys.size

        override fun onUpdate(
            changedKeys: Set<HoldingIdentity>,
            currentSnapshot: Map<HoldingIdentity, VirtualNodeInfo>,
        ) {
            _keys += changedKeys
            _snapshots += currentSnapshot
        }
    }
}