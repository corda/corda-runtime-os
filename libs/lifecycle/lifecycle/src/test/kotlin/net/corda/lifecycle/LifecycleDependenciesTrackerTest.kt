package net.corda.lifecycle

import net.corda.lifecycle.LifecycleDependenciesTracker.Companion.track
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times

class LifecycleDependenciesTrackerTest {
    @Test
    fun `Should throw IllegalArgumentException when the list of dependencies is empty`() {
        val coordinator = mock<LifecycleCoordinator> {
            on { name } doReturn LifecycleCoordinatorName.forComponent<LifecycleDependenciesTrackerTest>()
        }
        assertThrows<IllegalArgumentException> {
            LifecycleDependenciesTracker(coordinator, emptySet())
        }
    }

    @Test
    fun `areUpAfter close registration handle`() {
        val handles = mutableListOf<RegistrationHandle>()
        val dependencyNames = mutableListOf<LifecycleCoordinatorName>()
        val coordinator = mock<LifecycleCoordinator> {
            on { name } doReturn LifecycleCoordinatorName.forComponent<LifecycleDependenciesTrackerTest>()
            on { followStatusChangesByName(any()) } doAnswer {
                val names = it.getArgument(0) as Set<LifecycleCoordinatorName>
                assertEquals(1, names.size)
                dependencyNames.add(names.first())
                val handle = mock<RegistrationHandle>()
                handles.add(handle)
                handle
            }
        }
        val name1 = LifecycleCoordinatorName("name1")
        val name2 = LifecycleCoordinatorName("name2")
        val tracker = coordinator.track(name1, name2)
        assertEquals(2, handles.size)
        val event1 = RegistrationStatusChangeEvent(handles[0], LifecycleStatus.UP)
        val event2 = RegistrationStatusChangeEvent(handles[1], LifecycleStatus.UP)
        assertFalse(tracker.areUpAfter(event1, coordinator))
        assertTrue(tracker.areUpAfter(event2, coordinator))
        tracker.close()
        handles.forEach {
            Mockito.verify(it, times(1)).close()
        }
    }

    @Test
    fun `areUpAfter should return that all components are up only after all dependencies are up`() {
        val handles = mutableListOf<RegistrationHandle>()
        val dependencyNames = mutableListOf<LifecycleCoordinatorName>()
        val coordinator = mock<LifecycleCoordinator> {
            on { name } doReturn LifecycleCoordinatorName.forComponent<LifecycleDependenciesTrackerTest>()
            on { followStatusChangesByName(any()) } doAnswer {
                val names = it.getArgument(0) as Set<LifecycleCoordinatorName>
                assertEquals(1, names.size)
                dependencyNames.add(names.first())
                val handle = mock<RegistrationHandle>()
                handles.add(handle)
                handle
            }
        }
        val name1 = LifecycleCoordinatorName("name1")
        val name2 = LifecycleCoordinatorName("name2")
        val tracker = coordinator.track(name1, name2)
        assertEquals(2, handles.size)
        val event1 = RegistrationStatusChangeEvent(handles[0], LifecycleStatus.UP)
        val event2 = RegistrationStatusChangeEvent(handles[1], LifecycleStatus.UP)
        assertFalse(tracker.areUpAfter(event1, coordinator))
        assertTrue(tracker.areUpAfter(event2, coordinator))
        handles.forEach {
            Mockito.verify(it, never()).close()
        }
    }

    @Test
    fun `processEvent and areUp should return that all components are up only after all dependencies are up`() {
        val handles = mutableListOf<RegistrationHandle>()
        val dependencyNames = mutableListOf<LifecycleCoordinatorName>()
        val coordinator = mock<LifecycleCoordinator> {
            on { name } doReturn LifecycleCoordinatorName.forComponent<LifecycleDependenciesTrackerTest>()
            on { followStatusChangesByName(any()) } doAnswer {
                val names = it.getArgument(0) as Set<LifecycleCoordinatorName>
                assertEquals(1, names.size)
                dependencyNames.add(names.first())
                val handle = mock<RegistrationHandle>()
                handles.add(handle)
                handle
            }
        }
        val name1 = LifecycleCoordinatorName("name1")
        val name2 = LifecycleCoordinatorName("name2")
        val tracker = coordinator.track(name1, name2)
        assertEquals(2, handles.size)
        val event1 = RegistrationStatusChangeEvent(handles[0], LifecycleStatus.UP)
        val event2 = RegistrationStatusChangeEvent(handles[1], LifecycleStatus.UP)
        tracker.processEvent(event1, coordinator)
        assertFalse(tracker.areUp())
        tracker.processEvent(event2, coordinator)
        assertTrue(tracker.areUp())
        handles.forEach {
            Mockito.verify(it, never()).close()
        }
    }

    @Test
    fun `Should construct tracker from types`() {
        val handles = mutableListOf<RegistrationHandle>()
        val dependencyNames = mutableListOf<LifecycleCoordinatorName>()
        val coordinator = mock<LifecycleCoordinator> {
            on { name } doReturn LifecycleCoordinatorName.forComponent<LifecycleDependenciesTrackerTest>()
            on { followStatusChangesByName(any()) } doAnswer {
                val names = it.getArgument(0) as Set<LifecycleCoordinatorName>
                assertEquals(1, names.size)
                dependencyNames.add(names.first())
                val handle = mock<RegistrationHandle>()
                handles.add(handle)
                handle
            }
        }
        val tracker = coordinator.track(Component1::class.java, Component2::class.java)
        assertEquals(2, handles.size)
        val event1 = RegistrationStatusChangeEvent(handles[0], LifecycleStatus.UP)
        val event2 = RegistrationStatusChangeEvent(handles[1], LifecycleStatus.UP)
        assertFalse(tracker.areUpAfter(event1, coordinator))
        assertTrue(tracker.areUpAfter(event2, coordinator))
        handles.forEach {
            Mockito.verify(it, never()).close()
        }
    }

    private class Component1 : Lifecycle {
        override val isRunning: Boolean = true
        override fun start() {
        }
        override fun stop() {
        }
    }

    private class Component2 : Lifecycle {
        override val isRunning: Boolean = true
        override fun start() {
        }
        override fun stop() {
        }
    }
}