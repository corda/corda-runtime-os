package net.corda.lifecycle.impl

import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleException
import net.corda.lifecycle.LifecycleStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock

class LifecycleCoordinatorFactoryImplTest {

    @Test
    fun `factory generates a coordinator with the correct name and set to down`() {
        val factory = LifecycleCoordinatorFactoryImpl(mock(), LifecycleCoordinatorSchedulerFactoryImpl())
        val name = LifecycleCoordinatorName("Alice")
        val coordinator = factory.createCoordinator(name) { _, _ -> }
        assertEquals(name, coordinator.name)
        assertEquals(LifecycleStatus.DOWN, coordinator.status)
    }

    @Test
    fun `exception is thrown if the batch size is less than 1`() {
        val factory = LifecycleCoordinatorFactoryImpl(mock(), LifecycleCoordinatorSchedulerFactoryImpl())
        val name = LifecycleCoordinatorName("Alice")
        assertThrows<LifecycleException> {
            factory.createCoordinator(name, 0) { _, _ -> }
        }
        assertThrows<LifecycleException> {
            factory.createCoordinator(name, -1) { _, _ -> }
        }
    }
}
