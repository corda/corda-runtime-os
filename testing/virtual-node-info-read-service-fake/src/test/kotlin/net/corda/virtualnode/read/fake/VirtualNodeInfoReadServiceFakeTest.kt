package net.corda.virtualnode.read.fake

import net.corda.virtualnode.VirtualNodeInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class VirtualNodeInfoReadServiceFakeTest: BaseTest() {

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
}