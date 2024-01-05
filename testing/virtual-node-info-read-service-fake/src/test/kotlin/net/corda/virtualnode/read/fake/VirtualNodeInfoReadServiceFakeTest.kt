package net.corda.virtualnode.read.fake

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class VirtualNodeInfoReadServiceFakeTest {

    private val alice = TestCatalogue.VirtualNode.create(
        TestCatalogue.Identity.alice("group-id"),
        TestCatalogue.CpiId.version5Snapshot("cpi1")
    )

    private val bob = TestCatalogue.VirtualNode.create(
        TestCatalogue.Identity.bob("group-id"),
        TestCatalogue.CpiId.version5Snapshot("cpi1")
    )

    private val carol = TestCatalogue.VirtualNode.create(
        TestCatalogue.Identity.carol("group-id"),
        TestCatalogue.CpiId.version5Snapshot("cpi1")
    )

    @Test
    fun getAll() {
        assertThat(createService(alice, bob).getAll())
            .containsExactlyInAnyOrder(alice, bob)

        assertThat(createService().getAll())
            .isEmpty()
    }

    @Test
    fun get() {
        val service = createService(alice, bob)

        assertThat(service.get(carol.holdingIdentity)).isNull()
        assertThat(service.get(bob.holdingIdentity)).isEqualTo(bob)
    }

    @Test
    fun getById() {
        val service = createService(alice, bob)

        assertThat(service.getByHoldingIdentityShortHash(carol.holdingIdentity.shortHash)).isNull()
        assertThat(service.getByHoldingIdentityShortHash(bob.holdingIdentity.shortHash)).isEqualTo(bob)
    }

    @Test
    fun `callback is called immediately when registered`() {
        val listener = VirtualNodeInfoListenerSpy()

        createService(alice, bob).registerCallback(listener)
        assertThat(listener.timesCalled).isEqualTo(1)
        assertThat(listener.keys[0]).isEqualTo(keys(alice, bob))
        assertThat(listener.snapshots[0]).isEqualTo(snapshot(alice, bob))
    }

    @Test
    fun `add or update vnode`() {
        val listener = VirtualNodeInfoListenerSpy()
        val service = createService(alice, callbacks = listOf(listener))

        service.addOrUpdate(bob)
        assertThat(service.getAll()).containsExactlyInAnyOrder(alice, bob)
        assertThat(listener.timesCalled).isEqualTo(1)
        assertThat(listener.keys[0]).isEqualTo(keys(bob))
        assertThat(listener.snapshots[0]).isEqualTo(snapshot(alice, bob))
    }

    @Test
    fun `remove vnode`() {
        val listener = VirtualNodeInfoListenerSpy()
        val service = createService(alice, bob, callbacks = listOf(listener))

        service.remove(alice.holdingIdentity)
        assertThat(service.getAll()).containsExactlyInAnyOrder(bob)
        assertThat(listener.timesCalled).isEqualTo(1)
        assertThat(listener.keys[0]).isEqualTo(keys(alice))
        assertThat(listener.snapshots[0]).isEqualTo(snapshot(bob))
    }
}
