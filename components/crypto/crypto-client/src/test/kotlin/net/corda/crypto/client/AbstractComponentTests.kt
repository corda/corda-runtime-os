package net.corda.crypto.client

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.isAccessible
import kotlin.test.assertEquals

class AbstractComponentTests {
    private lateinit var coordinator: LifecycleCoordinator
    private lateinit var coordinatorFactory: LifecycleCoordinatorFactory
    private lateinit var resourcesToAllocate: Resources
    private lateinit var testComponent: TestAbstractComponent
    private var coordinatorIsRunning = false

    @BeforeEach
    fun setup() {
        coordinator = mock {
            on { start() } doAnswer {
                coordinatorIsRunning = true
            }
            on { stop() } doAnswer {
                coordinatorIsRunning = false
            }
            on { isRunning }.thenAnswer { coordinatorIsRunning }
            on { postEvent(any()) } doAnswer {
                val event = it.getArgument(0, LifecycleEvent::class.java)
                testComponent::class.memberFunctions.first { f -> f.name == "eventHandler" }.let { ff ->
                    ff.isAccessible = true
                    ff.call(testComponent, event, coordinator)
                }
                Unit
            }
        }
        coordinatorFactory = mock {
            on { createCoordinator(any(), any()) } doReturn coordinator
        }
        resourcesToAllocate = Resources()
        testComponent = TestAbstractComponent(
            resourcesToAllocate,
            coordinatorFactory,
            LifecycleCoordinatorName.forComponent<TestAbstractComponent>()
        )
    }

    @Test
    @Timeout(5)
    fun `Should start component and create resources only after the component is up`() {
        assertFalse(testComponent.isRunning)
        assertNull(testComponent.resources)
        testComponent.start()
        assertTrue(testComponent.isRunning)
        assertNotNull(testComponent.resources)
    }

    @Test
    @Timeout(5)
    fun `Should cleanup created resources when component is stopped`() {
        testComponent.start()
        assertTrue(testComponent.isRunning)
        assertNotNull(testComponent.resources)
        testComponent.stop()
        assertFalse(testComponent.isRunning)
        assertNull(testComponent.resources)
        assertEquals(1, resourcesToAllocate.closingCounter.get())
    }

    @Test
    @Timeout(5)
    fun `Should not be able to create resources when component is not started`() {
        assertFalse(testComponent.isRunning)
        assertNull(testComponent.resources)
        assertFalse(testComponent.isRunning)
        assertNull(testComponent.resources)
    }

    class Resources : AutoCloseable {
        var closingCounter = AtomicInteger()
        override fun close() {
            closingCounter.incrementAndGet()
        }
    }

    class TestAbstractComponent(
        private val resourcesToAllocate: Resources,
        coordinatorFactory: LifecycleCoordinatorFactory,
        coordinatorName: LifecycleCoordinatorName
    ) : AbstractComponent<Resources>(coordinatorFactory, coordinatorName) {
        override fun allocateResources(): Resources = resourcesToAllocate
    }
}