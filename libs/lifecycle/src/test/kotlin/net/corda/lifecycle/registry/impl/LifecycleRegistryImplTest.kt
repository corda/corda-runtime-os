package net.corda.lifecycle.registry.impl

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.registry.CoordinatorStatus
import net.corda.lifecycle.registry.LifecycleRegistryException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock

class LifecycleRegistryImplTest {

    private val initialStatusMap = mapOf(
        "Alice" to CoordinatorStatus("Alice", LifecycleStatus.DOWN, "Just created"),
        "Bob" to CoordinatorStatus("Bob", LifecycleStatus.DOWN, "Just created"),
        "Charlie" to CoordinatorStatus("Charlie", LifecycleStatus.DOWN, "Just created")
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
        registry.updateStatus("Alice", LifecycleStatus.UP, "Alice is up")
        val statuses = registry.componentStatus()
        assertEquals(CoordinatorStatus("Alice", LifecycleStatus.UP, "Alice is up"), statuses["Alice"])
    }

    @Test
    fun `updates to a registry do not affect any returned maps`() {
        val registry = LifecycleRegistryImpl()
        initialRegistrySetup(registry)
        val statuses = registry.componentStatus()
        registry.updateStatus("Alice", LifecycleStatus.UP, "Alice is up")
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
        registry.registerCoordinator("Alice", aliceCoordinator)
        registry.registerCoordinator("Bob", bobCoordinator)
        assertEquals(aliceCoordinator, registry.getCoordinator("Alice"))
        assertEquals(bobCoordinator, registry.getCoordinator("Bob"))
    }

    @Test
    fun `exception is thrown if an unknown coordinator is requested`() {
        val registry = LifecycleRegistryImpl()
        val aliceCoordinator = mock<LifecycleCoordinator>()
        val bobCoordinator = mock<LifecycleCoordinator>()
        registry.registerCoordinator("Alice", aliceCoordinator)
        registry.registerCoordinator("Bob", bobCoordinator)
        assertThrows<LifecycleRegistryException> {
            registry.getCoordinator("Charlie")
        }
    }

    @Test
    fun `exception is thrown if attempt made to register a coordinator under an already registered name`() {
        val registry = LifecycleRegistryImpl()
        val aliceCoordinator = mock<LifecycleCoordinator>()
        val bobCoordinator = mock<LifecycleCoordinator>()
        registry.registerCoordinator("Alice", aliceCoordinator)
        registry.registerCoordinator("Bob", bobCoordinator)
        assertThrows<LifecycleRegistryException> {
            registry.registerCoordinator("Alice", bobCoordinator)
        }
    }
}