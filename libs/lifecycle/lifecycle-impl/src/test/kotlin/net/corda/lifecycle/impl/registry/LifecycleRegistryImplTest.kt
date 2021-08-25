package net.corda.lifecycle.impl.registry

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.registry.CoordinatorStatus
import net.corda.lifecycle.registry.LifecycleRegistryException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock

class LifecycleRegistryImplTest {

    companion object {
        private val aliceName = LifecycleCoordinatorName("Alice")
        private val bobName = LifecycleCoordinatorName("Bob")
        private val charlieName = LifecycleCoordinatorName("Charlie")
    }

    private val initialStatusMap = mapOf(
        aliceName to CoordinatorStatus(aliceName, LifecycleStatus.DOWN, "Just created"),
        bobName to CoordinatorStatus(bobName, LifecycleStatus.DOWN, "Just created"),
        charlieName to CoordinatorStatus(charlieName, LifecycleStatus.DOWN, "Just created")
    )

    private fun initialRegistrySetup(registry: LifecycleRegistryImpl) {
        initialStatusMap.values.forEach {
            registry.updateStatus(it.name, it.status, it.reason)
        }
    }

    @Test
    fun `new statuses are published to the registry`() {
        val registry = LifecycleRegistryImpl()
        initialRegistrySetup(registry)
        val statuses = registry.componentStatus()
        assertEquals(initialStatusMap, statuses)
    }

    @Test
    fun `existing statuses can be updated`() {
        val registry = LifecycleRegistryImpl()
        initialRegistrySetup(registry)
        registry.updateStatus(aliceName, LifecycleStatus.UP, "Alice is up")
        val statuses = registry.componentStatus()
        assertEquals(CoordinatorStatus(aliceName, LifecycleStatus.UP, "Alice is up"), statuses[aliceName])
    }

    @Test
    fun `updates to a registry do not affect any returned maps`() {
        val registry = LifecycleRegistryImpl()
        initialRegistrySetup(registry)
        val statuses = registry.componentStatus()
        registry.updateStatus(aliceName, LifecycleStatus.UP, "Alice is up")
        assertEquals(initialStatusMap, statuses)
    }

    @Test
    fun `empty map is returned if no coordinators are registered`() {
        val registry = LifecycleRegistryImpl()
        val statuses = registry.componentStatus()
        assertEquals(mapOf<String, CoordinatorStatus>(), statuses)
    }

    @Test
    fun `can retrieve registered coordinators by name`() {
        val registry = LifecycleRegistryImpl()
        val aliceCoordinator = mock<LifecycleCoordinator>()
        val bobCoordinator = mock<LifecycleCoordinator>()
        registry.registerCoordinator(aliceName, aliceCoordinator)
        registry.registerCoordinator(bobName, bobCoordinator)
        assertEquals(aliceCoordinator, registry.getCoordinator(aliceName))
        assertEquals(bobCoordinator, registry.getCoordinator(bobName))
    }

    @Test
    fun `exception is thrown if an unknown coordinator is requested`() {
        val registry = LifecycleRegistryImpl()
        val aliceCoordinator = mock<LifecycleCoordinator>()
        val bobCoordinator = mock<LifecycleCoordinator>()
        registry.registerCoordinator(aliceName, aliceCoordinator)
        registry.registerCoordinator(bobName, bobCoordinator)
        assertThrows<LifecycleRegistryException> {
            registry.getCoordinator(charlieName)
        }
    }

    @Test
    fun `exception is thrown if attempt made to register a coordinator under an already registered name`() {
        val registry = LifecycleRegistryImpl()
        val aliceCoordinator = mock<LifecycleCoordinator>()
        val bobCoordinator = mock<LifecycleCoordinator>()
        registry.registerCoordinator(aliceName, aliceCoordinator)
        registry.registerCoordinator(bobName, bobCoordinator)
        assertThrows<LifecycleRegistryException> {
            registry.registerCoordinator(aliceName, bobCoordinator)
        }
    }
}