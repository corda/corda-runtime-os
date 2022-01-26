package net.corda.lifecycle

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import kotlin.reflect.jvm.javaField

class DependentComponentsTest {

    interface Component1 : Lifecycle
    interface Component2 : Lifecycle

    private val component1 = mock<Component1>()
    private val component2 = mock<Component2>()

    val expectedDependentComponentNames = setOf(
        LifecycleCoordinatorName.forComponent<Component1>(),
        LifecycleCoordinatorName.forComponent<Component2>())


    private val registrationHandle = mock<RegistrationHandle>()
    private val coordinator = mock<LifecycleCoordinator>() {
        on { followStatusChangesByName(any()) }.doReturn(registrationHandle)
    }

    @Test
    fun `dependent components return set`() {
        val dependentComponents = DependentComponents.of(::component1, ::component2)

        assertThat(dependentComponents.coordinatorNames).isEqualTo(expectedDependentComponentNames)
    }

    @Test
    fun `registerAndStartAll registers and starts all dependents`() {
        val dependentComponents = DependentComponents.of(::component1, ::component2)
        dependentComponents.registerAndStartAll(coordinator)

        verify(coordinator).followStatusChangesByName(expectedDependentComponentNames)
        verify(component1).start()
        verify(component2).start()
    }

    @Test
    fun `stopAll unregisters and stops all dependents`() {
        val dependentComponents = DependentComponents.of(::component1, ::component2)
        dependentComponents.registerAndStartAll(coordinator)
        dependentComponents.stopAll()

        verify(registrationHandle).close()
        verify(component1).stop()
        verify(component2).stop()
    }

    @Test
    fun `start and stop are tolerant of empty sets`() {
        assertDoesNotThrow {
            val dependentComponents = DependentComponents.of()
            dependentComponents.registerAndStartAll(coordinator)
            dependentComponents.stopAll()
        }
    }

    @Test
    fun `default to null instanceId`() {
        val dependentComponents = DependentComponents.of(::component1, ::component2)

        assertThat(dependentComponents.coordinatorNames).allMatch {
            it.instanceId == null
        }
    }

    @Test
    fun `instanceId set`() {
        val dependentComponents = DependentComponents
            .with(::component1, "one")

        assertThat(dependentComponents.coordinatorNames.single().instanceId).isEqualTo("one")
    }

    @Test
    fun `all added`() {
        val dependentComponents = DependentComponents
            .with(::component1, "one")
            .with(::component2, "two")

        assertThat(dependentComponents.coordinatorNames.count()).isEqualTo(2)
        assertThat(dependentComponents.coordinatorNames).anyMatch {
            it.instanceId == "one" && it.componentName == ::component1.javaField!!.type.name
        }
        assertThat(dependentComponents.coordinatorNames).anyMatch {
            it.instanceId == "two" && it.componentName == ::component2.javaField!!.type.name
        }
    }
}